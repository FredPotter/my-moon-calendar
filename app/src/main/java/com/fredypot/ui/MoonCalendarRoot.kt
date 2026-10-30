package com.fredypot.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fredypot.R
import com.fredypot.ui.calendar.CalendarScreen
import com.fredypot.ui.history.HistoryScreen

private enum class Tab(@StringRes val title: Int, val icon: ImageVector) {
    CALENDAR(R.string.tab_calendar, Icons.Filled.DateRange),
    HISTORY(R.string.tab_history, Icons.AutoMirrored.Filled.List),
}

@Composable
fun MoonCalendarRoot(viewModel: CycleViewModel = viewModel(factory = CycleViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.CALENDAR) }
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                UiMessage.NoPeriodStart -> resources.getString(R.string.error_no_start)
                UiMessage.ExportDone -> resources.getString(R.string.export_done)
                UiMessage.ExportFailed -> resources.getString(R.string.export_failed)
                is UiMessage.ImportDone -> resources.getString(R.string.import_done, message.periods, message.days)
                UiMessage.ImportFailed -> resources.getString(R.string.import_failed)
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.title)) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (tab) {
            Tab.CALENDAR -> CalendarScreen(
                state = state,
                onMarkStart = viewModel::markPeriodStart,
                onMarkEnd = viewModel::markPeriodEnd,
                onDeletePeriod = viewModel::deletePeriod,
                onSaveLog = viewModel::saveDayLog,
                modifier = modifier,
            )
            Tab.HISTORY -> HistoryScreen(
                state = state,
                onDeletePeriod = viewModel::deletePeriod,
                onExport = viewModel::exportTo,
                onImport = viewModel::importFrom,
                modifier = modifier,
            )
        }
    }
}
