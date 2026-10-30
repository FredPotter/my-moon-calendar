package com.fredypot.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fredypot.MoonCalendarApp
import com.fredypot.data.BackupCodec
import com.fredypot.data.BackupFiles
import com.fredypot.data.CycleRepository
import com.fredypot.data.InvalidBackupException
import com.fredypot.domain.CyclePredictor
import com.fredypot.domain.DayLog
import com.fredypot.domain.Period
import com.fredypot.domain.PeriodMarking
import com.fredypot.domain.Prediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

data class CycleUiState(
    val isLoaded: Boolean = false,
    val periods: List<Period> = emptyList(),
    val logs: Map<LocalDate, DayLog> = emptyMap(),
    val prediction: Prediction? = null,
)

sealed interface UiMessage {
    data object NoPeriodStart : UiMessage
    data object ExportDone : UiMessage
    data object ExportFailed : UiMessage
    data class ImportDone(val periods: Int, val days: Int) : UiMessage
    data object ImportFailed : UiMessage
}

class CycleViewModel(
    private val repository: CycleRepository,
    private val backupFiles: BackupFiles,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    val state: StateFlow<CycleUiState> = combine(repository.periods, repository.dayLogs) { periods, logs ->
        CycleUiState(
            isLoaded = true,
            periods = periods,
            logs = logs.associateBy { it.date },
            prediction = CyclePredictor.predict(periods, logs, today()),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CycleUiState())

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    private val assumedPeriodLength: Int
        get() = state.value.prediction?.stats?.averagePeriodLength ?: CyclePredictor.POPULATION_PERIOD_LENGTH.toInt()

    fun markPeriodStart(date: LocalDate) =
        apply(PeriodMarking.markStart(state.value.periods, date, assumedPeriodLength))

    fun markPeriodEnd(date: LocalDate) = apply(PeriodMarking.markEnd(state.value.periods, date))

    fun deletePeriod(id: Long) {
        viewModelScope.launch { repository.deletePeriod(id) }
    }

    fun saveDayLog(log: DayLog) {
        viewModelScope.launch { repository.saveDayLog(log) }
    }

    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val json = BackupCodec.encode(repository.exportBackup(), today())
                withContext(Dispatchers.IO) { backupFiles.write(uri, json) }
                UiMessage.ExportDone
            } catch (e: IOException) {
                UiMessage.ExportFailed
            } catch (e: SecurityException) {
                UiMessage.ExportFailed
            }
            messageChannel.trySend(message)
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val backup = withContext(Dispatchers.IO) { BackupCodec.decode(backupFiles.read(uri)) }
                repository.importBackup(backup)
                UiMessage.ImportDone(periods = backup.periods.size, days = backup.logs.size)
            } catch (e: IOException) {
                UiMessage.ImportFailed
            } catch (e: SecurityException) {
                UiMessage.ImportFailed
            } catch (e: InvalidBackupException) {
                UiMessage.ImportFailed
            }
            messageChannel.trySend(message)
        }
    }

    private fun apply(change: PeriodMarking.Change) {
        when (change) {
            is PeriodMarking.Change.Save -> viewModelScope.launch { repository.savePeriod(change.period) }
            PeriodMarking.Change.NoStartBefore -> messageChannel.trySend(UiMessage.NoPeriodStart)
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MoonCalendarApp
                CycleViewModel(app.repository, app.backupFiles)
            }
        }
    }
}
