package io.github.lethe_labs.argos.ui

import android.app.Application
import android.app.admin.DevicePolicyManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lethe_labs.argos.ArgosApp
import io.github.lethe_labs.argos.core.journal.JournalEventType
import io.github.lethe_labs.argos.core.report.Departure
import io.github.lethe_labs.argos.core.report.IntegrityReport
import io.github.lethe_labs.argos.core.report.ReportBuilder
import io.github.lethe_labs.argos.core.seal.SealWords
import io.github.lethe_labs.argos.core.seal.WordLists
import io.github.lethe_labs.argos.platform.ArgosAdminReceiver
import io.github.lethe_labs.argos.platform.SimReceiver
import io.github.lethe_labs.argos.platform.SnapshotCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

sealed interface UiState {
    data object Loading : UiState
    data class Idle(val adminActive: Boolean) : UiState
    data class WordsShown(val words: List<String>) : UiState
    data class Away(val departedAtMs: Long, val adminActive: Boolean) : UiState
    data class Confirming(val departedAtMs: Long, val words: List<String>) : UiState
    data class ReportShown(val report: IntegrityReport) : UiState
    data class Failed(val message: String) : UiState
}

class ArgosViewModel(app: Application) : AndroidViewModel(app) {

    private val container = ArgosApp.container(app)
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val departure = container.sessions.load()
            _state.value = if (departure == null) {
                UiState.Idle(isAdminActive())
            } else {
                UiState.Away(departure.departedAtMs, isAdminActive())
            }
        }
    }

    fun depart() = runGuarded {
        // Baseline for the SIM events journaled while away, in case no SIM
        // broadcast has arrived since install.
        SimReceiver.record(getApplication())
        // The departure entry is written first and becomes the anchor bound
        // into the seal: if the journal is later wiped, the report notices.
        val head = container.journal.append(JournalEventType.DEPARTURE)
            ?: error("journal unreadable")
        val snapshot = SnapshotCollector.collect(getApplication())
        container.sealKeys.rotate()

        val departure = Departure(
            sessionId = UUID.randomUUID().toString(),
            departedAtMs = head.timestampMs,
            snapshot = snapshot,
            journalHeadSeq = head.seq,
            journalHeadHash = head.hash,
            wordLanguage = WordLists.languageFor(Locale.getDefault().language),
        )
        val words = sealWords(departure) ?: error("seal key unavailable")
        container.sessions.save(departure)
        _state.value = UiState.WordsShown(words)
    }

    fun wordsNoted() = refresh()

    fun startReturn() = runGuarded {
        val departure = container.sessions.load() ?: return@runGuarded finishReturn(null, false, false)
        val words = sealWords(departure)
        if (words == null) {
            // Key gone: the words cannot be shown, and that alone means reset.
            finishReturn(departure, sealKeyPresent = false, confirmed = false)
        } else {
            _state.value = UiState.Confirming(departure.departedAtMs, words)
        }
    }

    fun confirmDeparture(isMine: Boolean) = runGuarded {
        finishReturn(container.sessions.load(), sealKeyPresent = true, confirmed = isMine)
    }

    /** From the home screen: "I had started an absence", yet the app has none. */
    fun reportMissingDeparture() = runGuarded {
        finishReturn(container.sessions.load(), sealKeyPresent = false, confirmed = false)
    }

    fun closeReport() = refresh()

    private fun finishReturn(departure: Departure?, sealKeyPresent: Boolean, confirmed: Boolean) {
        val report = ReportBuilder.build(
            departure = departure,
            sealKeyPresent = sealKeyPresent,
            departureConfirmed = confirmed,
            now = SnapshotCollector.collect(getApplication()),
            journal = container.journal.readAll(),
        )
        container.journal.append(JournalEventType.RETURN)
        container.sessions.clear()
        _state.value = UiState.ReportShown(report)
    }

    // Recomputed from the Keystore key every time, never stored (see SealWords).
    private fun sealWords(departure: Departure): List<String>? = runCatching {
        SealWords.derive(departure.sealRecord(), departure.wordLanguage) { data ->
            container.sealKeys.hmacOrNull(data) ?: error("seal key missing")
        }
    }.getOrNull()

    private fun isAdminActive(): Boolean {
        val app = getApplication<Application>()
        return app.getSystemService(DevicePolicyManager::class.java)
            ?.isAdminActive(ArgosAdminReceiver.component(app)) == true
    }

    private fun runGuarded(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (t: Throwable) {
                _state.value = UiState.Failed(t.message ?: t.javaClass.simpleName)
            }
        }
    }
}
