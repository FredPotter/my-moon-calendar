package com.fredypot.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fredypot.R
import com.fredypot.domain.DayKind
import com.fredypot.domain.DayLog
import com.fredypot.domain.Prediction
import com.fredypot.ui.AppLocale
import com.fredypot.ui.CycleUiState
import com.fredypot.ui.label
import com.fredypot.ui.monthTitle
import com.fredypot.ui.theme.LocalCycleColors
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit

private const val PAGE_COUNT = 1200
private const val CENTER_PAGE = PAGE_COUNT / 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(
    state: CycleUiState,
    onMarkStart: (LocalDate) -> Unit,
    onMarkEnd: (LocalDate) -> Unit,
    onDeletePeriod: (Long) -> Unit,
    onSaveLog: (DayLog) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prediction = state.prediction
    val today = prediction?.today ?: LocalDate.now()
    val baseMonth = remember(today) { YearMonth.from(today) }
    val pagerState = rememberPagerState(initialPage = CENTER_PAGE) { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    var selectedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        StatusCard(prediction = prediction.takeIf { state.isLoaded })
        Spacer(Modifier.height(24.dp))

        val month = baseMonth.plusMonths((pagerState.currentPage - CENTER_PAGE).toLong())
        MonthHeader(
            month = month,
            onPrevious = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
            onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            onToday = { scope.launch { pagerState.animateScrollToPage(CENTER_PAGE) } },
        )
        WeekdayRow()
        HorizontalPager(state = pagerState, verticalAlignment = Alignment.Top) { page ->
            MonthGrid(
                month = baseMonth.plusMonths((page - CENTER_PAGE).toLong()),
                today = today,
                days = prediction?.days.orEmpty(),
                logs = state.logs,
                onDayClick = { selectedDate = it },
            )
        }
        Spacer(Modifier.height(12.dp))
        Legend()
        Spacer(Modifier.height(20.dp))
    }

    selectedDate?.let { date ->
        DayEditorSheet(
            date = date,
            today = today,
            state = state,
            onMarkStart = { onMarkStart(date) },
            onMarkEnd = { onMarkEnd(date) },
            onDeletePeriod = onDeletePeriod,
            onSaveLog = { log ->
                onSaveLog(log)
                selectedDate = null
            },
            onDismiss = { selectedDate = null },
        )
    }
}

@Composable
private fun StatusCard(prediction: Prediction?) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 22.dp)) {
            val nextStart = prediction?.nextPeriodStart
            if (prediction == null || nextStart == null) {
                Text(stringResource(R.string.status_empty_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.status_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val today = prediction.today
            val todayKind = prediction.days[today]
            val daysUntil = ChronoUnit.DAYS.between(today, nextStart).toInt()
            val cycleDay = prediction.cycleDay ?: 1

            val (caption, headline) = when {
                todayKind == DayKind.PERIOD -> null to stringResource(R.string.status_period_day, cycleDay)
                prediction.delayDays > 0 -> stringResource(R.string.status_delay) to
                    pluralStringResource(R.plurals.days, prediction.delayDays, prediction.delayDays)
                daysUntil <= 0 -> null to stringResource(R.string.status_period_today)
                else -> stringResource(R.string.status_until_period) to
                    pluralStringResource(R.plurals.days, daysUntil, daysUntil)
            }
            caption?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(headline, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(10.dp))

            val ovulationText = when {
                todayKind == DayKind.OVULATION -> stringResource(R.string.status_ovulation_today)
                prediction.isFertileToday -> stringResource(R.string.status_fertile_today)
                else -> prediction.nextOvulation?.let { ovulation ->
                    val inDays = ChronoUnit.DAYS.between(today, ovulation).toInt()
                    stringResource(R.string.status_ovulation_in, pluralStringResource(R.plurals.days, inDays, inDays))
                }
            }
            Text(
                text = listOfNotNull(stringResource(R.string.status_cycle_day, cycleDay), ovulationText)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.confidence_label, stringResource(prediction.stats.confidence.label)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.month_prev))
        }
        Text(
            text = month.monthTitle().replaceFirstChar { it.titlecase(AppLocale) },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToday)
                .padding(vertical = 6.dp),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.month_next))
        }
    }
}

@Composable
private fun WeekdayRow() {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT_STANDALONE, AppLocale),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    days: Map<LocalDate, DayKind>,
    logs: Map<LocalDate, DayLog>,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    // Always 6 weeks so the pager keeps the same height for every month.
    Column {
        repeat(6) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val dayIndex = week * 7 + weekday - leadingBlanks
                    val cellModifier = Modifier.weight(1f)
                    if (dayIndex in 0 until month.lengthOfMonth()) {
                        val date = firstDay.plusDays(dayIndex.toLong())
                        DayCell(
                            date = date,
                            kind = days[date],
                            isToday = date == today,
                            hasLog = logs[date]?.isEmpty == false,
                            onClick = { onDayClick(date) },
                            modifier = cellModifier,
                        )
                    } else {
                        Spacer(cellModifier.aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    kind: DayKind?,
    isToday: Boolean,
    hasLog: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cycleColors = LocalCycleColors.current
    val scheme = MaterialTheme.colorScheme
    val (fill, textColor) = when (kind) {
        DayKind.PERIOD -> cycleColors.period to cycleColors.onPeriod
        DayKind.PREDICTED_PERIOD -> cycleColors.predictedPeriod to scheme.onSurface
        DayKind.OVULATION -> cycleColors.ovulation to cycleColors.onOvulation
        DayKind.FERTILE -> cycleColors.fertile to scheme.onSurface
        null -> Color.Transparent to scheme.onSurface
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(fill)
            .then(if (kind == DayKind.PREDICTED_PERIOD) Modifier.dashedCircle(cycleColors.predictedPeriodBorder) else Modifier)
            .then(if (isToday) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
        )
        if (hasLog) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (kind == DayKind.PERIOD) cycleColors.onPeriod else scheme.primary),
            )
        }
    }
}

private fun Modifier.dashedCircle(color: Color): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawCircle(
        color = color,
        radius = size.minDimension / 2 - stroke,
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        ),
    )
}

@Composable
private fun Legend() {
    val cycleColors = LocalCycleColors.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LegendItem(R.string.legend_period, cycleColors.period)
        LegendItem(R.string.legend_predicted, cycleColors.predictedPeriod, dashedBorder = cycleColors.predictedPeriodBorder)
        LegendItem(R.string.legend_fertile, cycleColors.fertile)
        LegendItem(R.string.legend_ovulation, cycleColors.ovulation)
    }
}

@Composable
private fun LegendItem(label: Int, color: Color, dashedBorder: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color)
                .then(if (dashedBorder != null) Modifier.dashedCircle(dashedBorder) else Modifier),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
