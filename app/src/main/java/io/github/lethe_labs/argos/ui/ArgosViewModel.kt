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
import io.github.lethe_labs.argos.core.seal.SealCode
import io.github.lethe_labs.argos.platform.ArgosAdminReceiver
import io.github.lethe_labs.argos.platform.SnapshotCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UiState {
    data object Loading : UiState
    data class Idle(val adminActive: Boolean) : UiState
    data class SealShown(val code: String) : UiState
    data class Away(val departedAtMs: Long, val adminActive: Boolean) : UiState
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
        )
        val code = SealCode.derive(departure.sealRecord()) { data ->
            container.sealKeys.hmacOrNull(data) ?: error("seal key unavailable")
        }
        container.sessions.save(departure)
        _state.value = UiState.SealShown(code)
    }

    fun sealNoted() = refresh()

    fun comeBack(typedSeal: String) = runGuarded {
        val departure = container.sessions.load()
        val journal = container.journal.readAll()
        val expected = departure?.let { d ->
            runCatching {
                SealCode.derive(d.sealRecord()) { data ->
                    container.sealKeys.hmacOrNull(data) ?: error("seal key missing")
                }
            }.getOrNull()
        }
        val report = ReportBuilder.build(
            departure = departure,
            typedSeal = typedSeal,
            expectedSeal = expected,
            now = SnapshotCollector.collect(getApplication()),
            journal = journal,
        )
        container.journal.append(JournalEventType.RETURN)
        container.sessions.clear()
        _state.value = UiState.ReportShown(report)
    }

    fun closeReport() = refresh()

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
