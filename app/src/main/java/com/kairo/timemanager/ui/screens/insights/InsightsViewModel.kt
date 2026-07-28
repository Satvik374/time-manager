package com.kairo.timemanager.ui.screens.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.timemanager.core.TimeUtils
import com.kairo.timemanager.data.repo.AnalyticsRepository
import com.kairo.timemanager.data.repo.Period
import com.kairo.timemanager.data.repo.PeriodReport
import com.kairo.timemanager.data.repo.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InsightsUiState(
    val period: Period = Period.WEEKLY,
    val anchorDay: Long = TimeUtils.todayEpochDay(),
    val report: PeriodReport? = null,
    val previous: PeriodReport? = null,
    val hourBands: List<Int> = emptyList(),
    val weekdayProfile: Map<Int, Float> = emptyMap(),
    val checklistCompleted: Int = 0,
    val totalFocusMinutes: Int = 0,
    val perfectDays: Int = 0,
    val selectedDay: Long? = null
) {
    /** Period-over-period delta in completion rate, in percentage points. */
    val deltaPoints: Int?
        get() {
            val current = report?.completionRate ?: return null
            val prior = previous?.completionRate ?: return null
            if (previous.planned == 0) return null
            return ((current - prior) * 100).toInt()
        }
}

@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val period = MutableStateFlow(Period.WEEKLY)
    private val anchor = MutableStateFlow(TimeUtils.todayEpochDay())
    private val selectedDay = MutableStateFlow<Long?>(null)
    private val derived = MutableStateFlow(DerivedStats())

    private data class DerivedStats(
        val hourBands: List<Int> = emptyList(),
        val weekdayProfile: Map<Int, Float> = emptyMap(),
        val checklistCompleted: Int = 0,
        val previous: PeriodReport? = null
    )

    @Suppress("OPT_IN_USAGE")
    val state: StateFlow<InsightsUiState> = combine(
        combine(period, anchor) { p, a -> p to a }
            .flatMapLatest { (p, a) -> analyticsRepository.observeReport(p, a) },
        period,
        anchor,
        selectedDay,
        combine(derived, progressRepository.observeStats()) { d, stats -> d to stats }
    ) { report, currentPeriod, currentAnchor, day, bits ->
        val (extra, stats) = bits
        InsightsUiState(
            period = currentPeriod,
            anchorDay = currentAnchor,
            report = report,
            previous = extra.previous,
            hourBands = extra.hourBands,
            weekdayProfile = extra.weekdayProfile,
            checklistCompleted = extra.checklistCompleted,
            totalFocusMinutes = stats.totalFocusMinutes,
            perfectDays = stats.perfectDays,
            selectedDay = day
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsUiState())

    init {
        refreshDerived()
    }

    fun setPeriod(next: Period) {
        if (period.value == next) return
        period.value = next
        selectedDay.value = null
        refreshDerived()
    }

    fun shiftPeriod(delta: Int) {
        anchor.value = when (period.value) {
            Period.DAILY -> anchor.value + delta
            Period.WEEKLY -> anchor.value + delta * 7
            Period.MONTHLY -> TimeUtils.date(anchor.value).plusMonths(delta.toLong()).toEpochDay()
        }
        selectedDay.value = null
        refreshDerived()
    }

    fun goToNow() {
        anchor.value = TimeUtils.todayEpochDay()
        selectedDay.value = null
        refreshDerived()
    }

    fun selectDay(epochDay: Long) {
        selectedDay.value = if (selectedDay.value == epochDay) null else epochDay
    }

    /** The parts of the report that need suspending queries rather than a live flow. */
    private fun refreshDerived() {
        viewModelScope.launch {
            val currentPeriod = period.value
            val currentAnchor = anchor.value
            val range = analyticsRepository.rangeFor(currentPeriod, currentAnchor)
            val priorAnchor = when (currentPeriod) {
                Period.DAILY -> currentAnchor - 1
                Period.WEEKLY -> currentAnchor - 7
                Period.MONTHLY -> TimeUtils.date(currentAnchor).minusMonths(1).toEpochDay()
            }
            derived.value = DerivedStats(
                hourBands = analyticsRepository.hourBands(range.first, range.last),
                weekdayProfile = analyticsRepository.weekdayProfile(
                    // A single day is too thin for a weekday profile, so widen to the last 8 weeks.
                    from = if (currentPeriod == Period.DAILY) currentAnchor - 55 else range.first,
                    to = range.last
                ),
                checklistCompleted = analyticsRepository.checklistCompleted(range.first, range.last),
                previous = analyticsRepository.report(currentPeriod, priorAnchor)
            )
        }
    }
}
