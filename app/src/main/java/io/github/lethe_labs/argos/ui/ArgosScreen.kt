package io.github.lethe_labs.argos.ui

import android.app.admin.DevicePolicyManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lethe_labs.argos.R
import io.github.lethe_labs.argos.core.report.IntegrityReport
import io.github.lethe_labs.argos.core.report.SealStatus
import io.github.lethe_labs.argos.core.snapshot.Anomaly
import io.github.lethe_labs.argos.core.snapshot.Severity
import io.github.lethe_labs.argos.core.usage.AppVisits
import io.github.lethe_labs.argos.platform.ArgosAdminReceiver
import io.github.lethe_labs.argos.platform.UsageReader
import io.github.lethe_labs.argos.ui.theme.ArgosColors
import io.github.lethe_labs.argos.ui.theme.ArgosType
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
    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { vm.refresh() }
    val eyes = Eyes(activateAdmin, grantUsage = { usageLauncher.launch(UsageReader.settingsIntent()) })

    // The Surface sets the default text colour: without it, text with no
    // explicit colour falls back to black, invisible on the night blue.
    Surface(Modifier.fillMaxSize(), color = ArgosColors.Night, contentColor = ArgosColors.Text) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            Text(stringResource(R.string.app_name).uppercase(), style = ArgosType.Brand, color = ArgosColors.Gold)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f)) {
                when (val s = state) {
                    UiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = ArgosColors.Watch)
                    is UiState.Idle -> RestContent(s.adminActive, s.usageAccess, eyes, vm::depart, vm::reportMissingDeparture)
                    is UiState.WordsShown -> WordsContent(s.words, vm::wordsNoted)
                    is UiState.Away -> AwayContent(s, eyes, vm::startReturn)
                    is UiState.Confirming -> ConfirmContent(s, vm::confirmDeparture)
                    is UiState.ReportShown -> ReportContent(s.report, vm::closeReport)
                    is UiState.Failed -> Text(
                        stringResource(R.string.error_generic, s.message),
                        style = ArgosType.Body,
                        color = ArgosColors.Critical,
                    )
                }
            }
        }
    }
}

/** Content scrolls if it must; the actions stay pinned to the bottom. */
@Composable
private fun Screen(actions: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

// ---- Screens ----

@Composable
private fun RestContent(
    adminActive: Boolean,
    usageAccess: Boolean,
    eyes: Eyes,
    onDepart: () -> Unit,
    onMissingDeparture: () -> Unit,
) = Screen(
    actions = {
        PrimaryButton(stringResource(R.string.depart), onDepart)
        TextButton(onClick = onMissingDeparture, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.had_session), style = ArgosType.Body, color = ArgosColors.Gold)
        }
    },
) {
    Emblem(
        ring = List(Watcher.entries.size) { EyeLook(EyeState.CLOSED, ArgosColors.Asleep) },
        center = EyeLook(EyeState.CLOSED, ArgosColors.Muted),
        size = 240.dp,
        description = stringResource(R.string.emblem_asleep),
        modifier = Modifier.padding(top = 16.dp),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.rest_title), style = ArgosType.Title, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.rest_body),
            style = ArgosType.Body, color = ArgosColors.Muted, textAlign = TextAlign.Center,
        )
    }
    if (adminActive && usageAccess) ReadyPill() else BlindCards(adminActive, usageAccess, eyes)
}

@Composable
private fun WordsContent(words: List<String>, onNoted: () -> Unit) = Screen(
    actions = { PrimaryButton(stringResource(R.string.understood), onNoted) },
) {
    // The one animation of the app: Argos opening its eyes as it starts watching.
    val opening = remember { Animatable(0f) }
    LaunchedEffect(Unit) { opening.animateTo(1f, tween(1400, easing = LinearEasing)) }
    Emblem(
        ring = List(Watcher.entries.size) { EyeLook(EyeState.OPEN, ArgosColors.Watch) },
        center = EyeLook(EyeState.OPEN, ArgosColors.Watch),
        size = 200.dp,
        description = stringResource(R.string.emblem_watching),
        opening = opening.value,
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.watching_title), style = ArgosType.Title, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.words_instructions),
            style = ArgosType.Body, color = ArgosColors.Muted, textAlign = TextAlign.Center,
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        words.forEach { WordTablet(it, 32.sp, Modifier.fillMaxWidth()) }
    }
    Text(
        stringResource(R.string.words_hint),
        style = ArgosType.Small, color = ArgosColors.Muted, textAlign = TextAlign.Center,
    )
}

@Composable
private fun AwayContent(state: UiState.Away, eyes: Eyes, onBack: () -> Unit) = Screen(
    actions = { PrimaryButton(stringResource(R.string.come_back), onBack) },
) {
    val pulse by rememberInfiniteTransition(label = "watch").animateFloat(
        initialValue = 0.4f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2200), RepeatMode.Reverse),
        label = "glow",
    )
    val blind = blindWatchers(state.adminActive, state.usageAccess)
    val open = EyeLook(EyeState.OPEN, ArgosColors.Watch)
    Emblem(
        ring = Watcher.entries.map { if (it in blind) EyeLook(EyeState.BLIND, ArgosColors.Warning) else open },
        center = open,
        size = 230.dp,
        description = stringResource(R.string.emblem_watching),
        glow = pulse,
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.watching_since).uppercase(), style = ArgosType.Label, color = ArgosColors.Muted)
        Text(formatDeparture(state.departedAtMs), style = ArgosType.Display, textAlign = TextAlign.Center)
    }
    BlindCards(state.adminActive, state.usageAccess, eyes)
    Text(
        stringResource(R.string.eyes_open_count, Watcher.entries.size - blind.size, Watcher.entries.size),
        style = ArgosType.Small, color = ArgosColors.Muted,
    )
}

@Composable
private fun ConfirmContent(state: UiState.Confirming, onAnswer: (Boolean) -> Unit) = Screen(
    actions = {
        PrimaryButton(stringResource(R.string.confirm_yes)) { onAnswer(true) }
        OutlinedButton(
            onClick = { onAnswer(false) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, ArgosColors.Line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ArgosColors.Text),
        ) { Text(stringResource(R.string.confirm_no), style = ArgosType.Strong) }
    },
) {
    Emblem(
        ring = List(Watcher.entries.size) { EyeLook(EyeState.OPEN, ArgosColors.Gold) },
        center = EyeLook(EyeState.OPEN, ArgosColors.Gold),
        size = 160.dp,
        description = stringResource(R.string.emblem_recognising),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.recognise_title), style = ArgosType.Title, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.departed_with_words, formatDeparture(state.departedAtMs)),
            style = ArgosType.Body, color = ArgosColors.Muted, textAlign = TextAlign.Center,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        state.words.forEach { WordTablet(it, 22.sp, Modifier.weight(1f)) }
    }
    Text(
        stringResource(R.string.recognise_hint),
        style = ArgosType.Small, color = ArgosColors.Muted, textAlign = TextAlign.Center,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportContent(report: IntegrityReport, onDone: () -> Unit) = Screen(
    actions = { PrimaryButton(stringResource(R.string.done), onDone) },
) {
    val sightings = sightings(report)
    val gazeBlind = report.anomalies.any { it.kind == Anomaly.Kind.USAGE_ACCESS_MISSING }
    val quiet = Watcher.entries.filter { it !in sightings }
    val verdictColor = when (report.verdict) {
        IntegrityReport.Verdict.CLEAN -> ArgosColors.Watch
        IntegrityReport.Verdict.SUSPICIOUS -> ArgosColors.Warning
        IntegrityReport.Verdict.TAMPERED -> ArgosColors.Critical
    }
    val verdictText = when (report.verdict) {
        IntegrityReport.Verdict.CLEAN -> stringResource(R.string.verdict_clean)
        IntegrityReport.Verdict.SUSPICIOUS -> stringResource(R.string.verdict_suspicious)
        IntegrityReport.Verdict.TAMPERED -> stringResource(R.string.verdict_tampered)
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Emblem(
            ring = Watcher.entries.map { watcher ->
                if (watcher == Watcher.GAZE && gazeBlind) {
                    EyeLook(EyeState.BLIND, ArgosColors.Muted)
                } else {
                    EyeLook(EyeState.OPEN, sightings[watcher]?.color ?: ArgosColors.Watch)
                }
            },
            center = EyeLook(EyeState.OPEN, verdictColor),
            size = 120.dp,
            description = verdictText,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.verdict_label).uppercase(), style = ArgosType.Label, color = ArgosColors.Muted)
            Text(verdictText, style = ArgosType.Display, color = verdictColor)
        }
    }

    val res = LocalContext.current.resources
    Text(
        res.getQuantityString(R.plurals.eyes_saw, sightings.size, sightings.size) + " · " +
            res.getQuantityString(R.plurals.eyes_quiet, quiet.size, quiet.size),
        style = ArgosType.Small, color = ArgosColors.Muted,
        modifier = Modifier.fillMaxWidth(),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sightings.forEach { (watcher, sighting) -> SightingCard(watcher, sighting) }
    }

    if (quiet.isNotEmpty()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.nothing_seen).uppercase(), style = ArgosType.Label, color = ArgosColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quiet.forEach { watcher ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .border(1.dp, ArgosColors.Line, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        WatcherIcon(watcher, ArgosColors.Watch, 18.dp, stringResource(watcher.role))
                    }
                }
            }
        }
    }

    if (Watcher.SEAL !in sightings) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            WatcherIcon(Watcher.SEAL, ArgosColors.Watch, 18.dp)
            Text(stringResource(R.string.seal_intact), style = ArgosType.Small, color = ArgosColors.Muted)
        }
    }
}

// ---- Report model for display ----

private class Sighting(val color: Color, val lines: List<String>)

/** What each eye saw, in emblem order. Eyes that saw nothing are absent. */
@Composable
private fun sightings(report: IntegrityReport): Map<Watcher, Sighting> {
    val out = linkedMapOf<Watcher, Sighting>()
    val byWatcher = report.anomalies.groupBy { Watcher.of(it.kind) }
    Watcher.entries.forEach { watcher ->
        if (watcher == Watcher.SEAL) {
            val lines = sealLines(report)
            if (lines.isNotEmpty()) out[watcher] = Sighting(ArgosColors.Critical, lines)
        } else {
            val seen = byWatcher[watcher] ?: return@forEach
            val worst = seen.maxOf { it.severity }
            out[watcher] = Sighting(severityColor(worst), seen.map { describe(it) })
        }
    }
    return out
}

@Composable
private fun sealLines(report: IntegrityReport): List<String> = buildList {
    when (report.sealStatus) {
        SealStatus.NO_SESSION -> add(stringResource(R.string.seal_status_no_session))
        SealStatus.KEY_MISSING -> add(stringResource(R.string.seal_status_key_missing))
        SealStatus.REJECTED -> add(stringResource(R.string.seal_status_rejected))
        SealStatus.MATCH -> Unit
    }
    if (!report.journalIntact) add(stringResource(R.string.journal_broken))
    if (!report.departureAnchorFound && report.sealStatus != SealStatus.NO_SESSION) {
        add(stringResource(R.string.anchor_missing))
    }
}

private fun severityColor(severity: Severity): Color = when (severity) {
    Severity.CRITICAL -> ArgosColors.Critical
    Severity.WARNING -> ArgosColors.Warning
    Severity.INFO -> ArgosColors.Watch
}

// ---- Pieces ----

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ArgosColors.Gold, contentColor = ArgosColors.OnGold),
    ) { Text(text, style = ArgosType.Button) }
}

@Composable
private fun WordTablet(word: String, fontSize: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    Surface(
        color = ArgosColors.Surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ArgosColors.Gold.copy(alpha = 0.4f)),
        modifier = modifier,
    ) {
        Box(Modifier.padding(horizontal = 6.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(
                word,
                style = ArgosType.Word.copy(fontSize = fontSize),
                color = ArgosColors.Gold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun ReadyPill() {
    Row(
        Modifier
            .border(1.dp, ArgosColors.Line, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).background(ArgosColors.Watch, CircleShape))
        Text(stringResource(R.string.eyes_ready, Watcher.entries.size), style = ArgosType.Small, color = ArgosColors.Muted)
    }
}

/** Opens the system screen that gives a blind eye its sight back. */
private class Eyes(val activateAdmin: () -> Unit, val grantUsage: () -> Unit)

private fun blindWatchers(adminActive: Boolean, usageAccess: Boolean): Set<Watcher> = buildSet {
    if (!adminActive) add(Watcher.LOCK)
    if (!usageAccess) add(Watcher.GAZE)
}

@Composable
private fun BlindCards(adminActive: Boolean, usageAccess: Boolean, eyes: Eyes) {
    if (!adminActive) {
        BlindCard(Watcher.LOCK, R.string.blind_lock_title, R.string.blind_lock_body, eyes.activateAdmin)
    }
    if (!usageAccess) {
        BlindCard(Watcher.GAZE, R.string.blind_gaze_title, R.string.blind_gaze_body, eyes.grantUsage)
    }
}

/** An eye that cannot see, and the button that opens it. */
@Composable
private fun BlindCard(watcher: Watcher, @StringRes title: Int, @StringRes body: Int, onActivate: () -> Unit) {
    Card(ArgosColors.Warning) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WatcherIcon(watcher, ArgosColors.Warning)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = ArgosType.Strong, color = ArgosColors.Warning)
                Text(stringResource(body), style = ArgosType.Small, color = ArgosColors.Muted)
            }
        }
        OutlinedButton(
            onClick = onActivate,
            modifier = Modifier.height(44.dp),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, ArgosColors.Warning),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ArgosColors.Warning),
        ) { Text(stringResource(R.string.reopen_eye), style = ArgosType.Strong) }
    }
}

@Composable
private fun SightingCard(watcher: Watcher, sighting: Sighting) {
    Card(sighting.color) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WatcherIcon(watcher, sighting.color)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(watcher.label), style = ArgosType.Strong, color = sighting.color)
                sighting.lines.forEach { Text(it, style = ArgosType.Small, color = ArgosColors.Muted) }
            }
        }
    }
}

@Composable
private fun Card(accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = ArgosColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
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
    Anomaly.Kind.SIM_REMOVED -> stringResource(R.string.anomaly_sim_removed, formatTime(a.detail))
    Anomaly.Kind.SIM_INSERTED -> stringResource(R.string.anomaly_sim_inserted, formatTime(a.detail))
    Anomaly.Kind.UNLOCK_FAILED -> stringResource(R.string.anomaly_unlock_failed, formatTime(a.detail))
    Anomaly.Kind.UNLOCK_SUCCEEDED -> if (a.severity == Severity.INFO) {
        stringResource(R.string.anomaly_unlock_last, formatTime(a.detail))
    } else {
        stringResource(R.string.anomaly_unlock_succeeded, formatTime(a.detail))
    }
    Anomaly.Kind.ADMIN_DISABLED -> stringResource(R.string.anomaly_admin_disabled, formatTime(a.detail))
    Anomaly.Kind.APP_OPENED -> AppVisits.decode(a.detail)?.let { v ->
        stringResource(
            if (a.severity == Severity.INFO) R.string.anomaly_app_opened_last else R.string.anomaly_app_opened,
            appLabel(v.packageName),
            formatTime(v.startMs),
            formatDuration(v.durationMs),
        )
    } ?: a.detail
    Anomaly.Kind.USAGE_ACCESS_REVOKED -> stringResource(R.string.anomaly_usage_revoked)
    Anomaly.Kind.USAGE_ACCESS_MISSING -> stringResource(R.string.anomaly_usage_missing)
}

/** The name shown under the app's icon, or its package if it is gone. */
@Composable
private fun appLabel(packageName: String): String {
    val pm = LocalContext.current.packageManager
    return remember(packageName) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }
            .getOrDefault(packageName)
    }
}

@Composable
private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    return if (minutes < 1) {
        stringResource(R.string.duration_under_minute)
    } else {
        stringResource(R.string.duration_minutes, minutes.toInt())
    }
}

private fun formatTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMs))

// Full date with the weekday: the departure time is itself part of what the
// user recognises, so it is spelled out the way they would remember it.
private fun formatDeparture(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(epochMs))

private fun formatTime(epochMs: String): String =
    epochMs.toLongOrNull()?.let { formatTime(it) } ?: epochMs
