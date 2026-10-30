package com.fredypot.ui.history

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fredypot.R
import com.fredypot.domain.CycleRecord
import com.fredypot.domain.CycleStats
import com.fredypot.ui.CycleUiState
import com.fredypot.ui.dayMonth
import com.fredypot.ui.label
import com.fredypot.ui.shortDate
import com.fredypot.ui.theme.LocalCycleColors
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    state: CycleUiState,
    onDeletePeriod: (Long) -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prediction = state.prediction
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                BackupMenu(onExport = onExport, onImport = onImport)
            }
        }
        if (prediction == null || prediction.history.isEmpty()) {
            if (state.isLoaded) {
                item {
                    Text(
                        stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            return@LazyColumn
        }
        item { StatsCard(prediction.stats) }

        val longest = prediction.history.maxOf { it.cycleLength ?: prediction.cycleDay ?: 0 }.coerceAtLeast(1)
        items(prediction.history, key = { it.periodId }) { record ->
            CycleItem(
                record = record,
                currentDay = prediction.cycleDay,
                longestCycle = longest,
                onDelete = { pendingDelete = record.periodId },
            )
        }
    }

    val id = pendingDelete
    val record = prediction?.history?.firstOrNull { it.periodId == id }
    if (id != null && record != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_text, record.start.dayMonth())) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePeriod(id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete_period)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun BackupMenu(onExport: (Uri) -> Unit, onImport: (Uri) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExport) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImport)
    }
    val fileName = stringResource(R.string.export_file_name, LocalDate.now().toString())

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.export_data)) },
                onClick = {
                    expanded = false
                    exportLauncher.launch(fileName)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.import_data)) },
                onClick = {
                    expanded = false
                    // Some file managers report .json files as octet-stream or plain text.
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                },
            )
        }
    }
}

@Composable
private fun StatsCard(stats: CycleStats) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth()) {
                StatTile(R.string.stat_cycle, stats.averageCycleLength.roundToInt(), Modifier.weight(1f))
                StatTile(R.string.stat_period, stats.averagePeriodLength, Modifier.weight(1f))
                StatTile(R.string.stat_luteal, stats.lutealLength.roundToInt(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.history_cycles_used, stats.cyclesUsed) + " · " +
                    stringResource(R.string.confidence_label, stringResource(stats.confidence.label)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatTile(label: Int, days: Int, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(stringResource(R.string.days_short, days), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CycleItem(record: CycleRecord, currentDay: Int?, longestCycle: Int, onDelete: () -> Unit) {
    val cycleColors = LocalCycleColors.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 18.dp, end = 4.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = record.periodEnd?.let {
                            stringResource(R.string.history_period_range, record.start.dayMonth(), it.shortDate())
                        } ?: stringResource(R.string.history_period_open, record.start.shortDate()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = record.cycleLength?.let { stringResource(R.string.days_short, it) }
                            ?.let { "${stringResource(R.string.stat_cycle)} $it" }
                            ?: stringResource(R.string.history_current, currentDay ?: 1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete_period),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Proportional bar: period part in rose, the rest of the cycle in a pale tone.
            val length = record.cycleLength ?: currentDay ?: 1
            val periodDays = (record.periodLength ?: 0).coerceAtMost(length)
            Row(
                Modifier
                    .padding(top = 10.dp, end = 14.dp)
                    .fillMaxWidth(length.toFloat() / longestCycle * 0.95f + 0.05f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(cycleColors.fertile),
            ) {
                if (periodDays > 0) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(periodDays.toFloat() / length)
                            .background(cycleColors.period),
                    )
                }
            }

            val details = buildList {
                record.periodLength?.let { add(stringResource(R.string.history_period_length, stringResource(R.string.days_short, it))) }
                record.ovulation?.let { ovulation ->
                    val signals = ovulation.signals.sortedBy { it.ordinal }.map { stringResource(it.label) }
                    add(stringResource(R.string.history_ovulation_detected, ovulation.date.dayMonth(), signals.joinToString(", ")))
                }
            }
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, end = 14.dp),
                )
            }
            if (record.isOutlier) {
                Text(
                    stringResource(R.string.history_outlier),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp, end = 14.dp),
                )
            }
        }
    }
}
