package com.qsc.accounting.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qsc.accounting.core.Dates
import com.qsc.accounting.core.ValidationException
import com.qsc.accounting.data.CategoryInput
import com.qsc.accounting.data.EntryInput
import com.qsc.accounting.data.LedgerRepository
import com.qsc.accounting.data.Prefs
import com.qsc.accounting.data.RecordFilter
import com.qsc.accounting.data.RecordQuery
import com.qsc.accounting.data.RecurringInput
import com.qsc.accounting.data.RecurringRepository
import com.qsc.accounting.data.RuleView
import com.qsc.accounting.data.db.CategoryEntity
import com.qsc.accounting.data.db.RecurringRuleEntity
import com.qsc.accounting.data.db.TransactionEntity
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.StatisticsMode
import com.qsc.accounting.domain.Period
import com.qsc.accounting.domain.StatsQuery
import com.qsc.accounting.domain.Summary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class AppViewModel(
    private val repository: LedgerRepository,
    private val recurring: RecurringRepository,
    private val prefs: Prefs,
    /** 记账数据变化后刷新桌面小组件。 */
    private val onLedgerChanged: () -> Unit = {},
    /** 通知开关或时间变化后重排闹钟。 */
    private val onNotificationSettingsChanged: () -> Unit = {},
    /** 通知权限是否已经授予；首次启动据此决定要不要提示。 */
    private val isNotificationPermissionGranted: () -> Boolean = { true },
    /** 立刻发一条测试通知；回调里给出真实结果，而不是笼统的「已发送」。 */
    private val onSendTestNotification: ((String) -> Unit) -> Unit = {},
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val busy: Boolean = false,
        val today: LocalDate = LocalDate.now(),
        val mode: StatisticsMode = StatisticsMode.CASH,
        val includePending: Boolean = true,
        val categories: List<CategoryEntity> = emptyList(),
        val daySummary: Summary = Summary.empty(Period.DAY),
        val monthSummary: Summary = Summary.empty(Period.MONTH),
        val records: List<TransactionEntity> = emptyList(),
        val recordTotal: Int = 0,
        val filterType: EntryType? = null,
        val search: String = "",
        val month: YearMonth = YearMonth.now(),
        val stats: Summary = Summary.empty(Period.MONTH),
        val statsPeriod: Period = Period.MONTH,
        val statsDate: String = "",
        val rules: List<RuleView> = emptyList(),
        val favorites: Set<Long> = emptySet(),
        val notifyEnabled: Boolean = false,
        val dailyEnabled: Boolean = false,
        val dailyHour: Int = 21,
        val dailyMinute: Int = 0,
        val monthlyEnabled: Boolean = false,
        val monthlyDay: Int = 1,
        val monthlyHour: Int = 9,
        val monthlyMinute: Int = 0,
        val message: String? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(
        State(
            mode = prefs.statisticsMode,
            includePending = prefs.includePending,
            month = YearMonth.now(),
            statsDate = defaultStatsDate(Period.MONTH, LocalDate.now()),
            favorites = prefs.favoriteCategories,
            notifyEnabled = prefs.notificationsEnabled,
            dailyEnabled = prefs.dailyEnabled,
            dailyHour = prefs.dailyHour,
            dailyMinute = prefs.dailyMinute,
            monthlyEnabled = prefs.monthlyEnabled,
            monthlyDay = prefs.monthlyDay,
            monthlyHour = prefs.monthlyHour,
            monthlyMinute = prefs.monthlyMinute,
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        run {
            // 先把已有数据放上屏，再补记固定收支，最后刷一次：
            // 补记量大时（比如导入了一个跑了两年的每天规则）界面不会一直转圈。
            loadState()
            val due = recurring.runDue()
            if (due.generated > 0) {
                _state.update { it.copy(message = "已补记 ${due.generated} 笔固定收支") }
            }
            loadState()
        }
    }

    fun refresh() = run { loadState() }

    private suspend fun loadState() {
        repository.ensureSeeded()
        val snapshot = _state.value
        val today = Dates.today()
        val categories = repository.categories()
        val daySummary = repository.summary(
            StatsQuery(period = Period.DAY, date = today.toString()),
            snapshot.mode,
            snapshot.includePending,
            today,
        )
        val monthSummary = repository.summary(
            StatsQuery(period = Period.MONTH, date = Dates.yearMonth(today)),
            snapshot.mode,
            snapshot.includePending,
            today,
        )
        val page = repository.list(recordQuery(snapshot))
        val stats = repository.summary(
            statsQuery(snapshot),
            snapshot.mode,
            snapshot.includePending,
            today,
        )
        val rules = recurring.ruleViews(today)
        _state.update {
            it.copy(
                loading = false,
                today = today,
                categories = categories,
                daySummary = daySummary,
                monthSummary = monthSummary,
                records = page.items,
                recordTotal = page.total,
                stats = stats,
                rules = rules,
            )
        }
    }

    // ------------------------------------------------------------ 明细筛选

    fun setFilterType(type: EntryType?) = run {
        _state.update { it.copy(filterType = type) }
        val s = _state.value
        val page = repository.list(recordQuery(s))
        _state.update { it.copy(records = page.items, recordTotal = page.total) }
    }

    fun applySearch(text: String) = run {
        _state.update { it.copy(search = text.trim()) }
        val s = _state.value
        val page = repository.list(recordQuery(s))
        _state.update { it.copy(records = page.items, recordTotal = page.total) }
    }

    fun shiftMonth(months: Long) = run {
        val next = _state.value.month.plusMonths(months)
        _state.update { it.copy(month = next) }
        val s = _state.value
        val page = repository.list(recordQuery(s))
        _state.update { it.copy(records = page.items, recordTotal = page.total) }
    }

    fun resetMonth() = run {
        _state.update { it.copy(month = YearMonth.now()) }
        val s = _state.value
        val page = repository.list(recordQuery(s))
        _state.update { it.copy(records = page.items, recordTotal = page.total) }
    }

    // ------------------------------------------------------------ 统计

    fun setStatsPeriod(period: Period) = run {
        val today = _state.value.today
        _state.update {
            it.copy(
                statsPeriod = period,
                statsDate = if (it.statsPeriod == period && it.statsDate.isNotEmpty()) {
                    it.statsDate
                } else {
                    defaultStatsDate(period, today)
                },
            )
        }
        reloadStats()
    }

    fun shiftStats(delta: Long) = run {
        val s = _state.value
        val next = when (s.statsPeriod) {
            Period.DAY -> LocalDate.parse(s.statsDate).plusDays(delta).toString()
            Period.MONTH -> YearMonth.parse(s.statsDate).plusMonths(delta).let { ym ->
                "%04d-%02d".format(ym.year, ym.monthValue)
            }

            Period.YEAR -> (s.statsDate.toInt() + delta).toString()
            Period.CUSTOM -> s.statsDate
        }
        _state.update { it.copy(statsDate = next) }
        reloadStats()
    }

    fun resetStatsDate() = run {
        val s = _state.value
        _state.update { it.copy(statsDate = defaultStatsDate(s.statsPeriod, s.today)) }
        reloadStats()
    }

    private suspend fun reloadStats() {
        val s = _state.value
        val stats = repository.summary(statsQuery(s), s.mode, s.includePending, s.today)
        _state.update { it.copy(stats = stats) }
    }

    fun setMode(mode: StatisticsMode) = run {
        prefs.statisticsMode = mode
        _state.update { it.copy(mode = mode) }
        val s = _state.value
        val today = s.today
        val daySummary = repository.summary(
            StatsQuery(period = Period.DAY, date = today.toString()),
            mode,
            s.includePending,
            today,
        )
        val monthSummary = repository.summary(
            StatsQuery(period = Period.MONTH, date = Dates.yearMonth(today)),
            mode,
            s.includePending,
            today,
        )
        val stats = repository.summary(statsQuery(s), mode, s.includePending, today)
        _state.update { it.copy(daySummary = daySummary, monthSummary = monthSummary, stats = stats) }
    }

    fun setIncludePending(include: Boolean) = run {
        prefs.includePending = include
        _state.update { it.copy(includePending = include) }
        setMode(_state.value.mode)
    }

    // ------------------------------------------------------------ 记账

    fun saveEntry(input: EntryInput, onSaved: () -> Unit = {}) = run {
        repository.saveEntry(input)
        _state.update { it.copy(message = "已记账") }
        onSaved.invoke()
        onLedgerChanged()
        refresh()
    }

    fun deleteEntry(id: Long) = run {
        repository.deleteEntries(listOf(id))
        _state.update { it.copy(message = "已删除") }
        onLedgerChanged()
        refresh()
    }

    fun saveCategory(input: CategoryInput) = run {
        repository.saveCategory(input)
        _state.update { it.copy(message = "分类已保存") }
        refresh()
    }

    fun deleteCategory(id: Long) = run {
        repository.deleteCategory(id)
        if (id in prefs.favoriteCategories) {
            val remaining = prefs.favoriteCategories - id
            prefs.favoriteCategories = remaining
            _state.update { it.copy(favorites = remaining) }
        }
        _state.update { it.copy(message = "分类已删除") }
        refresh()
    }

    /** 收藏只是记一笔页面的排序偏好：默认露出收藏的分类，其余收在「更多」里。 */
    fun toggleCategoryFavorite(id: Long) = run {
        val next = prefs.favoriteCategories.toMutableSet()
        if (!next.add(id)) next.remove(id)
        prefs.favoriteCategories = next
        _state.update { it.copy(favorites = next) }
    }

    // ------------------------------------------------------------ 固定收支

    suspend fun recurringRule(id: Long): RecurringRuleEntity? = recurring.rule(id)

    fun saveRecurring(input: RecurringInput, onSaved: () -> Unit = {}) = run {
        recurring.save(input)
        _state.update { it.copy(message = "固定收支已保存") }
        onSaved.invoke()
        refresh()
    }

    fun deleteRecurring(id: Long) = run {
        recurring.delete(id)
        _state.update { it.copy(message = "规则已删除，历史记录保留") }
        refresh()
    }

    fun setRecurringEnabled(id: Long, enabled: Boolean) = run {
        recurring.setEnabled(id, enabled)
        _state.update { it.copy(message = if (enabled) "已恢复" else "已暂停，暂停期间不会记账") }
        refresh()
    }

    fun runRecurringNow() = run {
        val result = recurring.runDue()
        _state.update {
            it.copy(
                message = if (result.generated > 0) {
                    "已生成 ${result.generated} 笔固定收支"
                } else {
                    "没有到期的固定收支"
                },
            )
        }
        onLedgerChanged()
        refresh()
    }

    // ------------------------------------------------------------ 通知

    /** 首次启动且还没授权时弹一次提示；授权过或问过就不再打扰。 */
    fun shouldPromptNotificationPermission(): Boolean =
        !prefs.permissionPromptShown && !isNotificationPermissionGranted()

    fun markNotificationPromptShown() {
        prefs.permissionPromptShown = true
    }

    fun sendTestNotification() {
        onSendTestNotification { message ->
            _state.update { it.copy(message = message) }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) = run {
        prefs.notificationsEnabled = enabled
        _state.update { it.copy(notifyEnabled = enabled) }
        onNotificationSettingsChanged()
    }

    fun setDailyEnabled(enabled: Boolean) = run {
        prefs.dailyEnabled = enabled
        _state.update { it.copy(dailyEnabled = enabled) }
        onNotificationSettingsChanged()
    }

    fun setDailyTime(hour: Int, minute: Int) = run {
        prefs.dailyHour = hour
        prefs.dailyMinute = minute
        _state.update { it.copy(dailyHour = hour, dailyMinute = minute) }
        onNotificationSettingsChanged()
    }

    fun setMonthlyEnabled(enabled: Boolean) = run {
        prefs.monthlyEnabled = enabled
        _state.update { it.copy(monthlyEnabled = enabled) }
        onNotificationSettingsChanged()
    }

    fun setMonthlyDay(day: Int) = run {
        val safe = day.coerceIn(1, 31)
        prefs.monthlyDay = safe
        _state.update { it.copy(monthlyDay = safe) }
        onNotificationSettingsChanged()
    }

    fun setMonthlyTime(hour: Int, minute: Int) = run {
        prefs.monthlyHour = hour
        prefs.monthlyMinute = minute
        _state.update { it.copy(monthlyHour = hour, monthlyMinute = minute) }
        onNotificationSettingsChanged()
    }

    // ------------------------------------------------------------ 备份

    suspend fun backupJson(): String = repository.exportJson()

    suspend fun csvText(): String = repository.exportCsv(recordQuery(_state.value))

    fun importBackup(raw: String) = run {
        _state.update { it.copy(busy = true) }
        try {
            repository.importBackup(raw, confirmed = true)
            _state.update { it.copy(message = "备份已恢复") }
        } finally {
            _state.update { it.copy(busy = false) }
        }
        onLedgerChanged()
        refresh()
    }

    fun notify(text: String) = _state.update { it.copy(message = text) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    // ------------------------------------------------------------ 内部

    private fun run(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (error: ValidationException) {
                _state.update { it.copy(message = error.message, busy = false) }
            } catch (error: Exception) {
                _state.update { it.copy(error = error.message ?: "操作失败", busy = false) }
            }
        }
    }

    private fun recordQuery(state: State): RecordQuery {
        val start = state.month.atDay(1)
        val end = state.month.atEndOfMonth()
        return RecordQuery(
            filter = RecordFilter(
                startDate = start.toString(),
                endDate = end.toString(),
                type = state.filterType,
                search = state.search.takeIf { it.isNotBlank() },
            ),
            page = 1,
            pageSize = 500,
        )
    }

    private fun statsQuery(state: State): StatsQuery = StatsQuery(
        period = state.statsPeriod,
        date = state.statsDate,
    )

    companion object {
        private fun defaultStatsDate(period: Period, today: LocalDate): String = when (period) {
            Period.DAY -> today.toString()
            Period.MONTH -> Dates.yearMonth(today)
            Period.YEAR -> today.year.toString()
            Period.CUSTOM -> today.toString()
        }

        fun factory(
            repository: LedgerRepository,
            recurring: RecurringRepository,
            prefs: Prefs,
            onLedgerChanged: () -> Unit = {},
            onNotificationSettingsChanged: () -> Unit = {},
            isNotificationPermissionGranted: () -> Boolean = { true },
            onSendTestNotification: ((String) -> Unit) -> Unit = {},
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    AppViewModel(
                        repository = repository,
                        recurring = recurring,
                        prefs = prefs,
                        onLedgerChanged = onLedgerChanged,
                        onNotificationSettingsChanged = onNotificationSettingsChanged,
                        isNotificationPermissionGranted = isNotificationPermissionGranted,
                        onSendTestNotification = onSendTestNotification,
                    )
                }
            }
    }
}
