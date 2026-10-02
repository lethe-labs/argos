package io.github.lethe_labs.argos.ui

import android.app.admin.DevicePolicyManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lethe_labs.argos.R
import io.github.lethe_labs.argos.core.report.IntegrityReport
import io.github.lethe_labs.argos.core.report.SealStatus
import io.github.lethe_labs.argos.core.snapshot.Anomaly
import io.github.lethe_labs.argos.core.snapshot.Severity
import io.github.lethe_labs.argos.platform.ArgosAdminReceiver
import java.text.DateFormat
import java.util.Date

@Composable
fun ArgosScreen(vm: ArgosViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val adminLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.refresh() }
    val activateAdmin = {
        adminLauncher.launch(
            Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ArgosAdminReceiver.component(context))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.admin_explanation)),
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            when (val s = state) {
                UiState.Loading -> CircularProgressIndicator()
                is UiState.Idle -> IdleContent(s.adminActive, activateAdmin, vm::depart, vm::reportMissingDeparture)
                is UiState.WordsShown -> WordsContent(s.words, vm::wordsNoted)
                is UiState.Away -> AwayContent(s, activateAdmin, vm::startReturn)
                is UiState.Confirming -> ConfirmContent(s, vm::confirmDeparture)
                is UiState.ReportShown -> ReportContent(s.report, vm::closeReport)
                is UiState.Failed -> Text(
                    stringResource(R.string.error_generic, s.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun AdminStatus(adminActive: Boolean, onActivate: () -> Unit) {
    if (adminActive) {
        Text(stringResource(R.string.admin_active), style = MaterialTheme.typography.bodySmall)
    } else {
        Text(stringResource(R.string.admin_inactive))
        OutlinedButton(onClick = onActivate) { Text(stringResource(R.string.admin_activate)) }
    }
}

@Composable
private fun IdleContent(
    adminActive: Boolean,
    onActivate: () -> Unit,
    onDepart: () -> Unit,
    onMissingDeparture: () -> Unit,
) {
    Text(stringResource(R.string.tagline))
    AdminStatus(adminActive, onActivate)
    Button(onClick = onDepart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.depart)) }
    Text(stringResource(R.string.depart_hint), style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = onMissingDeparture) { Text(stringResource(R.string.had_session)) }
}

@Composable
private fun WordChip(word: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun WordsContent(words: List<String>, onNoted: () -> Unit) {
    Text(stringResource(R.string.departed_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.words_instructions))
    words.forEach { WordChip(it, Modifier.fillMaxWidth()) }
    Text(stringResource(R.string.words_hint), style = MaterialTheme.typography.bodySmall)
    Button(onClick = onNoted, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.understood)) }
}

@Composable
private fun AwayContent(state: UiState.Away, onActivate: () -> Unit, onBack: () -> Unit) {
    Text(stringResource(R.string.away_since, formatDeparture(state.departedAtMs)))
    AdminStatus(state.adminActive, onActivate)
    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.come_back)) }
}

@Composable
private fun ConfirmContent(state: UiState.Confirming, onAnswer: (Boolean) -> Unit) {
    Text(stringResource(R.string.welcome_back), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.departed_with_words, formatDeparture(state.departedAtMs)))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        state.words.forEach { WordChip(it, Modifier.weight(1f)) }
    }
    Text(stringResource(R.string.confirm_question), style = MaterialTheme.typography.titleMedium)
    Button(onClick = { onAnswer(true) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.confirm_yes))
    }
    OutlinedButton(onClick = { onAnswer(false) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.confirm_no))
    }
}

@Composable
private fun ReportContent(report: IntegrityReport, onDone: () -> Unit) {
    val (verdictText, verdictColor) = when (report.verdict) {
        IntegrityReport.Verdict.CLEAN -> stringResource(R.string.verdict_clean) to MaterialTheme.colorScheme.primary
        IntegrityReport.Verdict.SUSPICIOUS -> stringResource(R.string.verdict_suspicious) to MaterialTheme.colorScheme.tertiary
        IntegrityReport.Verdict.TAMPERED -> stringResource(R.string.verdict_tampered) to MaterialTheme.colorScheme.error
    }
    Text(verdictText, style = MaterialTheme.typography.titleLarge, color = verdictColor)

    when (report.sealStatus) {
        SealStatus.NO_SESSION -> Text(stringResource(R.string.seal_status_no_session))
        SealStatus.KEY_MISSING -> Text(stringResource(R.string.seal_status_key_missing))
        SealStatus.REJECTED -> Text(stringResource(R.string.seal_status_rejected))
        SealStatus.MATCH -> Unit
    }
    if (!report.journalIntact) Text(stringResource(R.string.journal_broken))
    if (!report.departureAnchorFound && report.sealStatus != SealStatus.NO_SESSION) {
        Text(stringResource(R.string.anchor_missing))
    }

    report.anomalies.forEach { anomaly ->
        Text("•  " + describe(anomaly), color = severityColor(anomaly.severity))
    }

    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.done)) }
}

@Composable
private fun severityColor(severity: Severity): Color = when (severity) {
    Severity.CRITICAL -> MaterialTheme.colorScheme.error
    Severity.WARNING -> MaterialTheme.colorScheme.tertiary
    Severity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun describe(a: Anomaly): String = when (a.kind) {
    Anomaly.Kind.REBOOTED -> stringResource(R.string.anomaly_rebooted, a.detail)
    Anomaly.Kind.CLOCK_CHANGED -> stringResource(R.string.anomaly_clock_changed)
    Anomaly.Kind.ADB_ENABLED -> stringResource(R.string.anomaly_adb_enabled)
    Anomaly.Kind.DEVELOPER_OPTIONS_ENABLED -> stringResource(R.string.anomaly_dev_options)
    Anomaly.Kind.APP_INSTALLED -> stringResource(R.string.anomaly_app_installed, a.detail)
    Anomaly.Kind.APP_REMOVED -> stringResource(R.string.anomaly_app_removed, a.detail)
    Anomaly.Kind.ACCESSIBILITY_SERVICE_ADDED -> stringResource(R.string.anomaly_accessibility, a.detail)
    Anomaly.Kind.DEVICE_ADMIN_ADDED -> stringResource(R.string.anomaly_device_admin, a.detail)
    Anomaly.Kind.SIM_CHANGED -> stringResource(R.string.anomaly_sim_changed, a.detail)
    Anomaly.Kind.UNLOCK_FAILED -> stringResource(R.string.anomaly_unlock_failed, formatTime(a.detail))
    Anomaly.Kind.UNLOCK_SUCCEEDED -> if (a.severity == Severity.INFO) {
        stringResource(R.string.anomaly_unlock_last, formatTime(a.detail))
    } else {
        stringResource(R.string.anomaly_unlock_succeeded, formatTime(a.detail))
    }
    Anomaly.Kind.ADMIN_DISABLED -> stringResource(R.string.anomaly_admin_disabled, formatTime(a.detail))
}

private fun formatTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

// Full date with the weekday: the departure time is itself part of what the
// user recognises, so it is spelled out the way they would remember it.
private fun formatDeparture(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(epochMs))

private fun formatTime(epochMs: String): String =
    epochMs.toLongOrNull()?.let { formatTime(it) } ?: epochMs
