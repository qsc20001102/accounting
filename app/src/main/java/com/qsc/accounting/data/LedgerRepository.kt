package com.qsc.accounting.data

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.qsc.accounting.core.Dates
import com.qsc.accounting.core.Money
import com.qsc.accounting.core.fail
import com.qsc.accounting.data.db.AppDatabase
import com.qsc.accounting.data.db.CategoryEntity
import com.qsc.accounting.data.db.LedgerRow
import com.qsc.accounting.data.db.TransactionEntity
import com.qsc.accounting.data.db.TransactionAllocationEntity
import com.qsc.accounting.data.model.EntrySource
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.StatisticsMode
import com.qsc.accounting.domain.Amortization
import com.qsc.accounting.domain.PlannedRule
import com.qsc.accounting.domain.StatsEntry
import com.qsc.accounting.domain.StatsQuery
import com.qsc.accounting.domain.StatsWindow
import com.qsc.accounting.domain.StatisticsEngine
import com.qsc.accounting.domain.Summary
import java.time.LocalDate

class LedgerRepository(
    private val db: AppDatabase,
    private val prefs: Prefs,
) {
    private val categoryDao = db.categories()
    private val transactionDao = db.transactions()
    private val recurringDao = db.recurring()

    // ---------------------------------------------------------------- 初始化

    /** 首次启动灌入与插件一致的默认分类。 */
    suspend fun ensureSeeded() {
        if (prefs.seeded) return
        db.withTransaction {
            if (categoryDao.count() == 0) {
                val now = Dates.timestamp()
                val rows = mutableListOf<CategoryEntity>()
                DEFAULTS.forEach { (type, names) ->
                    names.forEachIndexed { index, name ->
                        val offset = if (type == EntryType.EXPENSE) 2 else 0
                        rows += CategoryEntity(
                            name = name,
                            type = type,
                            color = PALETTE[(index + offset) % PALETTE.size],
                            sortOrder = index,
                            createdAt = now,
                            updatedAt = now,
                        )
                    }
                }
                categoryDao.insertAll(rows)
            }
        }
        prefs.seeded = true
    }

    // ---------------------------------------------------------------- 分类

    suspend fun categories(): List<CategoryEntity> = categoryDao.all()

    suspend fun categories(type: EntryType): List<CategoryEntity> =
        categoryDao.all().filter { it.type == type && it.enabled }

    suspend fun saveCategory(input: CategoryInput): CategoryEntity {
        val name = input.name.trim()
        if (name.isEmpty()) fail("分类名称不能为空")
        if (name.length > 40) fail("分类名称不能超过 40 个字符")
        if (!COLOR.matches(input.color)) fail("分类颜色必须为六位十六进制颜色")
        if (input.sortOrder !in 0..10_000) fail("排序必须在 0～10000 之间")

        return db.withTransaction {
            val existing = input.id?.let { categoryDao.byId(it) ?: fail("分类不存在") }
            if (categoryDao.countSameName(input.type, name, existing?.id ?: -1) > 0) {
                fail("同一收支类型下已存在同名分类")
            }
            val now = Dates.timestamp()
            if (existing == null) {
                val entity = CategoryEntity(
                    name = name,
                    type = input.type,
                    color = input.color,
                    sortOrder = input.sortOrder,
                    enabled = input.enabled,
                    createdAt = now,
                    updatedAt = now,
                )
                entity.copy(id = categoryDao.insert(entity))
            } else {
                if (existing.type != input.type && isCategoryUsed(existing.id)) {
                    fail("已被记录或规则使用的分类不能更改收支归属")
                }
                val updated = existing.copy(
                    name = name,
                    type = input.type,
                    color = input.color,
                    sortOrder = input.sortOrder,
                    enabled = input.enabled,
                    updatedAt = now,
                )
                categoryDao.update(updated)
                updated
            }
        }
    }

    suspend fun deleteCategory(id: Long) {
        db.withTransaction {
            categoryDao.byId(id) ?: fail("分类不存在")
            if (isCategoryUsed(id)) {
                fail("分类已被使用，不能删除；请编辑分类并停用，历史信息会保留")
            }
            categoryDao.delete(id)
        }
    }

    private suspend fun isCategoryUsed(id: Long): Boolean =
        transactionDao.countByCategory(id) > 0 ||
            recurringDao.countRulesByCategory(id) > 0 ||
            recurringDao.countVersionsByCategory(id) > 0

    // ---------------------------------------------------------------- 收支

    suspend fun entry(id: Long): TransactionEntity? = transactionDao.byId(id)

    suspend fun saveEntry(input: EntryInput): TransactionEntity {
        val cents = Money.parseToCents(input.amount)
        val date = Dates.parse(input.date)
        val note = input.note.trim()
        if (note.length > 500) fail("备注不能超过 500 个字符")
        if (input.categoryId <= 0) fail("请选择分类")

        return db.withTransaction {
            val category = categoryDao.byId(input.categoryId) ?: fail("分类不存在或已删除")
            if (category.type != input.type) fail("分类与收支类型不一致")

            val existing = input.id?.let { transactionDao.byId(it) ?: fail("记录不存在或已删除") }
            val now = Dates.timestamp()

            if (existing == null) {
                if (!category.enabled) fail("该分类已停用，请选择启用的分类")
                val row = TransactionEntity(
                    type = input.type,
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryColor = category.color,
                    amountCents = cents,
                    transactionDate = date.toString(),
                    note = note,
                    source = EntrySource.WEB,
                    createdAt = now,
                    updatedAt = now,
                )
                row.copy(id = transactionDao.insert(row))
            } else {
                val unchanged = existing.categoryId == category.id && existing.type == input.type
                if (!category.enabled && !unchanged) fail("该分类已停用，请选择启用的分类")
                // 只改金额/备注时保留原分类快照，显式换分类才切换名称与颜色。
                val updated = existing.copy(
                    type = input.type,
                    categoryId = category.id,
                    categoryName = if (unchanged) existing.categoryName else category.name,
                    categoryColor = if (unchanged) existing.categoryColor else category.color,
                    amountCents = cents,
                    transactionDate = date.toString(),
                    note = note,
                    updatedAt = now,
                )
                transactionDao.update(updated)
                // 固定收支改了到账日，均摊周期快照要跟着挪。
                if (existing.transactionDate != updated.transactionDate) {
                    recurringDao.allocation(existing.id)?.let { allocation ->
                        val (start, end) =
                            Amortization.allocationPeriod(allocation.frequency, date)
                        recurringDao.updateAllocation(
                            allocation.copy(periodStart = start.toString(), periodEnd = end.toString()),
                        )
                    }
                }
                updated
            }
        }
    }

    suspend fun deleteEntries(ids: List<Long>) {
        val unique = ids.distinct()
        if (unique.isEmpty() || unique.size > 100) fail("每次请选择 1～100 条记录")
        db.withTransaction {
            for (id in unique) {
                transactionDao.byId(id) ?: fail("记录不存在或已删除")
            }
            recurringDao.deleteAllocationsFor(unique)
            transactionDao.deleteByIds(unique)
        }
    }

    suspend fun list(query: RecordQuery): RecordPage {
        val fragment = query.filter.toSql()
        val offset = (query.page - 1).toLong() * query.pageSize
        val select = "SELECT * FROM transactions" + fragment.clause +
            " ORDER BY " + query.sort.sql + " LIMIT ? OFFSET ?"
        val rows = transactionDao.rawRows(
            SimpleSQLiteQuery(select, (fragment.args + listOf(query.pageSize, offset)).toTypedArray()),
        )
        val count = transactionDao.rawCount(
            SimpleSQLiteQuery("SELECT count(*) AS total FROM transactions" + fragment.clause, fragment.args.toTypedArray()),
        ).firstOrNull()?.total ?: 0
        return RecordPage(rows, count, query.page, query.pageSize)
    }

    // ---------------------------------------------------------------- 统计

    suspend fun summary(
        query: StatsQuery,
        mode: StatisticsMode,
        includePending: Boolean,
        today: LocalDate = Dates.today(),
    ): Summary {
        val window = StatsWindow.of(query, today)
        val fragment = RecordFilter(
            type = query.type,
            categoryId = query.categoryId,
            search = query.search,
        ).toSql()

        // 与插件同款取数：普通记录按日期命中窗口；固定收支按均摊周期与窗口相交命中。
        val sql = buildString {
            append("SELECT t.*, a.period_start AS period_start, a.period_end AS period_end ")
            append("FROM transactions t LEFT JOIN transaction_allocations a ON a.transaction_id = t.id")
            append(fragment.clause)
            append(if (fragment.clause.isEmpty()) " WHERE " else " AND ")
            append("((a.transaction_id IS NULL AND t.transaction_date BETWEEN ? AND ?)")
            append(" OR (a.period_start <= ? AND a.period_end >= ?))")
        }
        val args = fragment.args + listOf(
            window.loadStart.toString(),
            window.end.toString(),
            window.end.toString(),
            window.loadStart.toString(),
        )
        val rows = transactionDao.ledgerRows(SimpleSQLiteQuery(sql, args.toTypedArray()))
        val entries = rows.mapNotNull { it.toStatsEntry() }

        val needsPlan = mode == StatisticsMode.AMORTIZED && includePending
        val rules = if (needsPlan) loadPlannedRules() else emptyList()
        val executed = if (needsPlan) {
            recurringDao.executions().map { it.ruleId to it.occurrenceDate }.toSet()
        } else {
            emptySet()
        }

        return StatisticsEngine.summary(window, mode, includePending, entries, rules, executed)
    }

    private suspend fun loadPlannedRules(): List<PlannedRule> {
        val categories = categoryDao.all().associateBy { it.id }
        return recurringDao.versions().mapNotNull { version ->
            val category = categories[version.categoryId] ?: return@mapNotNull null
            PlannedRule(
                ruleId = version.ruleId,
                type = version.type,
                categoryId = version.categoryId,
                categoryName = category.name,
                categoryColor = category.color,
                amountCents = version.amountCents,
                frequency = version.frequency,
                startDate = Dates.parse(version.startDate),
                endDate = version.endDate?.let { Dates.parse(it) },
                dayOfMonth = version.dayOfMonth ?: 1,
                monthOfYear = version.monthOfYear ?: 1,
                validFrom = Dates.parse(version.validFrom),
                validUntil = version.validUntil?.let { Dates.parse(it) },
                enabled = version.enabled && category.enabled,
            )
        }
    }

    private fun LedgerRow.toStatsEntry(): StatsEntry? {
        val date = runCatching { Dates.parse(entry.transactionDate) }.getOrNull() ?: return null
        return StatsEntry(
            type = entry.type,
            categoryId = entry.categoryId,
            categoryName = entry.categoryName,
            categoryColor = entry.categoryColor,
            amountCents = entry.amountCents,
            date = date,
            periodStart = periodStart?.let { runCatching { Dates.parse(it) }.getOrNull() },
            periodEnd = periodEnd?.let { runCatching { Dates.parse(it) }.getOrNull() },
        )
    }

    // ---------------------------------------------------------------- 备份

    suspend fun snapshot() = BackupCodec.snapshot(db)

    suspend fun importBackup(raw: String, confirmed: Boolean) {
        if (!confirmed) fail("请确认恢复将替换现有分类、记录与规则")
        val data = BackupCodec.decode(raw)
        BackupCodec.replaceAll(db, data)
        prefs.seeded = true
    }

    suspend fun exportJson(): String = BackupCodec.encode(snapshot())

    suspend fun exportCsv(query: RecordQuery): String {
        val fragment = query.filter.toSql()
        val rows = transactionDao.rawRows(
            SimpleSQLiteQuery(
                "SELECT * FROM transactions" + fragment.clause + " ORDER BY transaction_date, id",
                fragment.args.toTypedArray(),
            ),
        )
        return buildString {
            append('\uFEFF')
            append("ID,类型,分类,金额,日期,备注,来源,固定规则ID\r\n")
            for (row in rows) {
                append(row.id).append(',')
                append(if (row.type == EntryType.INCOME) "收入" else "支出").append(',')
                append(csvEscape(row.categoryName)).append(',')
                append(Money.format(row.amountCents)).append(',')
                append(row.transactionDate).append(',')
                append(csvEscape(row.note)).append(',')
                append(row.source.wire).append(',')
                append(row.recurringRuleId ?: "").append("\r\n")
            }
        }
    }

    /** 防止电子表格把不可信文本当公式执行，与插件同样前置单引号。 */
    private fun csvEscape(value: String): String {
        val text = value.replace("\"", "\"\"")
        val guarded = if (text.trimStart().startsWithAnyOf("=", "+", "-", "@")) "'$text" else text
        return if (guarded.contains(',') || guarded.contains('"') || guarded.contains('\n')) {
            "\"$guarded\""
        } else {
            guarded
        }
    }

    private fun String.startsWithAnyOf(vararg prefixes: String): Boolean =
        prefixes.any { startsWith(it) }

    companion object {
        private val COLOR = Regex("""#[0-9a-fA-F]{6}""")

        private val DEFAULTS = linkedMapOf(
            EntryType.INCOME to listOf("工资", "奖金", "兼职", "理财", "退款", "其他"),
            EntryType.EXPENSE to listOf("吃饭", "购物", "交通", "游玩", "住房", "水电", "通讯", "医疗", "娱乐", "其他"),
        )

        private val PALETTE = listOf("#169b7b", "#5264df", "#e99651", "#8c6dd8", "#d87580", "#7890a0")
    }
}
