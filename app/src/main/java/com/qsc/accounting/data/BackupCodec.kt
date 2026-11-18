package com.qsc.accounting.data

import androidx.room.withTransaction
import com.qsc.accounting.core.Dates
import com.qsc.accounting.core.Money
import com.qsc.accounting.core.fail
import com.qsc.accounting.data.db.AppDatabase
import com.qsc.accounting.data.db.CategoryEntity
import com.qsc.accounting.data.db.RecurringExecutionEntity
import com.qsc.accounting.data.db.RecurringRuleEntity
import com.qsc.accounting.data.db.RecurringVersionEntity
import com.qsc.accounting.data.db.TransactionAllocationEntity
import com.qsc.accounting.data.db.TransactionEntity
import com.qsc.accounting.data.model.EntrySource
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * JSON 备份的读写。文件结构：
 * {format, schema_version, exported_at, categories[], recurring_rules[], transactions[],
 *  recurring_executions[], transaction_allocations[], recurring_versions[]}
 *
 * 当前写 v3；导入时同时接受 v3 与 v2（v2 没有均摊快照与规则版本，按规则频率补齐）。
 */
object BackupCodec {
    const val FORMAT = "accounting-backup"

    /** 旧版本应用写出的标记，继续认，升级后老备份不至于导不进来。 */
    private val LEGACY_FORMATS = setOf("astrbot-accounting")
    const val SCHEMA_VERSION = 3
    private const val MAX_BYTES = 10 * 1024 * 1024
    private const val MAX_ROWS = 50_000

    data class Snapshot(
        val categories: List<CategoryEntity> = emptyList(),
        val rules: List<RecurringRuleEntity> = emptyList(),
        val transactions: List<TransactionEntity> = emptyList(),
        val executions: List<RecurringExecutionEntity> = emptyList(),
        val allocations: List<TransactionAllocationEntity> = emptyList(),
        val versions: List<RecurringVersionEntity> = emptyList(),
    ) {
        val rowCount: Int
            get() = categories.size + rules.size + transactions.size +
                executions.size + allocations.size + versions.size
    }

    suspend fun snapshot(db: AppDatabase): Snapshot = Snapshot(
        categories = db.categories().all(),
        rules = db.recurring().rules(),
        transactions = db.transactions().all(),
        executions = db.recurring().executions(),
        allocations = db.recurring().allocations(),
        versions = db.recurring().versions(),
    )

    suspend fun replaceAll(db: AppDatabase, data: Snapshot) {
        db.withTransaction {
            val categories = db.categories()
            val transactions = db.transactions()
            val recurring = db.recurring()
            recurring.deleteAllocations()
            recurring.deleteExecutions()
            recurring.deleteVersions()
            transactions.deleteAll()
            recurring.deleteRules()
            categories.deleteAll()

            categories.insertAll(data.categories)
            recurring.insertRules(data.rules)
            recurring.insertVersions(data.versions)
            recurring.insertExecutions(data.executions)
            transactions.insertAll(data.transactions)
            recurring.insertAllocations(data.allocations)
        }
    }

    // ---------------------------------------------------------------- 导出

    fun encode(snapshot: Snapshot): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("schema_version", SCHEMA_VERSION)
            .put("exported_at", Dates.timestamp())

        root.put("categories", JSONArray().apply {
            snapshot.categories.forEach { put(categoryJson(it)) }
        })
        root.put("recurring_rules", JSONArray().apply {
            snapshot.rules.forEach { put(ruleJson(it)) }
        })
        root.put("transactions", JSONArray().apply {
            snapshot.transactions.forEach { put(transactionJson(it)) }
        })
        root.put("recurring_executions", JSONArray().apply {
            snapshot.executions.forEach {
                put(
                    JSONObject()
                        .put("rule_id", it.ruleId)
                        .put("occurrence_date", it.occurrenceDate)
                        .put("created_at", it.createdAt),
                )
            }
        })
        root.put("transaction_allocations", JSONArray().apply {
            snapshot.allocations.forEach {
                put(
                    JSONObject()
                        .put("transaction_id", it.transactionId)
                        .put("frequency", it.frequency.wire)
                        .put("period_start", it.periodStart)
                        .put("period_end", it.periodEnd),
                )
            }
        })
        root.put("recurring_versions", JSONArray().apply {
            snapshot.versions.forEach { put(versionJson(it)) }
        })
        return root.toString(2)
    }

    private fun categoryJson(row: CategoryEntity) = JSONObject()
        .put("id", row.id)
        .put("name", row.name)
        .put("type", row.type.wire)
        .put("color", row.color)
        .put("icon", row.icon)
        .put("sort_order", row.sortOrder)
        .put("enabled", if (row.enabled) 1 else 0)
        .put("created_at", row.createdAt)
        .put("updated_at", row.updatedAt)

    private fun ruleJson(row: RecurringRuleEntity) = JSONObject()
        .put("id", row.id)
        .put("name", row.name)
        .put("type", row.type.wire)
        .put("category_id", row.categoryId)
        .put("amount_cents", row.amountCents)
        .put("frequency", row.frequency.wire)
        .put("start_date", row.startDate)
        .put("end_date", row.endDate ?: JSONObject.NULL)
        .put("day_of_month", row.dayOfMonth ?: JSONObject.NULL)
        .put("month_of_year", row.monthOfYear ?: JSONObject.NULL)
        .put("enabled", if (row.enabled) 1 else 0)
        .put("note", row.note)
        .put("effective_from", row.effectiveFrom)
        .put("processed_through", row.processedThrough ?: JSONObject.NULL)
        .put("last_execution_date", row.lastExecutionDate ?: JSONObject.NULL)
        .put("deleted", if (row.deleted) 1 else 0)
        .put("created_at", row.createdAt)
        .put("updated_at", row.updatedAt)

    private fun versionJson(row: RecurringVersionEntity) = JSONObject()
        .put("id", row.id)
        .put("rule_id", row.ruleId)
        .put("valid_from", row.validFrom)
        .put("valid_until", row.validUntil ?: JSONObject.NULL)
        .put("type", row.type.wire)
        .put("category_id", row.categoryId)
        .put("amount_cents", row.amountCents)
        .put("frequency", row.frequency.wire)
        .put("start_date", row.startDate)
        .put("end_date", row.endDate ?: JSONObject.NULL)
        .put("day_of_month", row.dayOfMonth ?: JSONObject.NULL)
        .put("month_of_year", row.monthOfYear ?: JSONObject.NULL)
        .put("enabled", if (row.enabled) 1 else 0)
        .put("note", row.note)

    private fun transactionJson(row: TransactionEntity) = JSONObject()
        .put("id", row.id)
        .put("type", row.type.wire)
        .put("category_id", row.categoryId)
        .put("category_name", row.categoryName)
        .put("category_color", row.categoryColor)
        .put("amount_cents", row.amountCents)
        .put("transaction_date", row.transactionDate)
        .put("note", row.note)
        .put("source", row.source.wire)
        .put("recurring_rule_id", row.recurringRuleId ?: JSONObject.NULL)
        .put("recurring_occurrence_date", row.recurringOccurrenceDate ?: JSONObject.NULL)
        .put("created_at", row.createdAt)
        .put("updated_at", row.updatedAt)

    // ---------------------------------------------------------------- 导入

    fun decode(raw: String): Snapshot {
        if (raw.toByteArray().size > MAX_BYTES) fail("备份文件不能超过 10 MiB")

        val root = try {
            JSONObject(raw.trim())
        } catch (_: Exception) {
            fail("文件不是有效 JSON 备份")
        }

        val format = root.optString("format")
        if (format != FORMAT && format !in LEGACY_FORMATS) {
            fail("这不是本应用的备份文件（缺少 format 标记）")
        }
        val version = root.optInt("schema_version", -1)
        if (version != 2 && version != SCHEMA_VERSION) {
            fail("备份格式或数据库版本不支持（收到 v$version）")
        }

        val categories = rows(root, "categories", required = true).map { it.toCategory() }
        val rules = rows(root, "recurring_rules", required = true).map { it.toRule() }
        val transactions = rows(root, "transactions", required = true).map { it.toTransaction() }
        val executions = rows(root, "recurring_executions", required = true).map { it.toExecution() }

        // v2 备份没有这两张表，按插件 migration 3 的规则补齐。
        val allocations: List<TransactionAllocationEntity>
        val versions: List<RecurringVersionEntity>
        if (version == 2) {
            versions = rules.map { rule ->
                RecurringVersionEntity(
                    ruleId = rule.id,
                    validFrom = rule.effectiveFrom,
                    validUntil = null,
                    type = rule.type,
                    categoryId = rule.categoryId,
                    amountCents = rule.amountCents,
                    frequency = rule.frequency,
                    startDate = rule.startDate,
                    endDate = rule.endDate,
                    dayOfMonth = rule.dayOfMonth,
                    monthOfYear = rule.monthOfYear,
                    enabled = rule.enabled && !rule.deleted,
                    note = rule.note,
                )
            }
            allocations = buildAllocationsForLegacy(transactions, rules)
        } else {
            allocations = rows(root, "transaction_allocations", required = true)
                .map { it.toAllocation() }
            versions = rows(root, "recurring_versions", required = true).map { it.toVersion() }
        }

        val total = categories.size + rules.size + transactions.size +
            executions.size + allocations.size + versions.size
        if (total > MAX_ROWS) fail("备份总行数不能超过 50000")

        validateReferences(categories, rules, transactions, versions)

        return Snapshot(categories, rules, transactions, executions, allocations, versions)
    }

    /** 旧版备份没有均摊快照：按规则频率与生效日反推，口径与历史版本一致。 */
    private fun buildAllocationsForLegacy(
        transactions: List<TransactionEntity>,
        rules: List<RecurringRuleEntity>,
    ): List<TransactionAllocationEntity> {
        val byId = rules.associateBy { it.id }
        return transactions.mapNotNull { tx ->
            val rule = tx.recurringRuleId?.let { byId[it] } ?: return@mapNotNull null
            val occurrence = tx.recurringOccurrenceDate ?: return@mapNotNull null
            val date = tx.transactionDate
            // 早于现版本生效日的记录沿用到账日，避免猜测原周期。
            val frequency = if (occurrence < rule.effectiveFrom) Frequency.DAILY else rule.frequency
            val chosen = Dates.parse(date)
            val periodStart: LocalDate
            val periodEnd: LocalDate
            when (frequency) {
                Frequency.DAILY -> {
                    periodStart = chosen
                    periodEnd = chosen
                }

                Frequency.MONTHLY -> {
                    periodStart = chosen.withDayOfMonth(1)
                    periodEnd = chosen.withDayOfMonth(chosen.lengthOfMonth())
                }

                Frequency.YEARLY -> {
                    periodStart = LocalDate.of(chosen.year, 1, 1)
                    periodEnd = LocalDate.of(chosen.year, 12, 31)
                }
            }
            TransactionAllocationEntity(
                transactionId = tx.id,
                frequency = frequency,
                periodStart = periodStart.toString(),
                periodEnd = periodEnd.toString(),
            )
        }
    }

    private fun validateReferences(
        categories: List<CategoryEntity>,
        rules: List<RecurringRuleEntity>,
        transactions: List<TransactionEntity>,
        versions: List<RecurringVersionEntity>,
    ) {
        val byId = categories.associateBy { it.id }
        transactions.forEach { tx ->
            val category = byId[tx.categoryId] ?: fail("备份里的收支引用了不存在的分类")
            if (category.type != tx.type) fail("备份分类与收支归属不一致")
            if (tx.amountCents !in 1..Money.MAX_CENTS) fail("备份金额分值无效")
            if (tx.recurringRuleId == null && tx.source == EntrySource.RECURRING) {
                fail("备份自动记账执行凭据缺失或来源不一致")
            }
        }
        rules.forEach { rule ->
            val category = byId[rule.categoryId] ?: fail("备份里的固定规则引用了不存在的分类")
            if (category.type != rule.type) fail("备份分类与收支归属不一致")
        }
        versions.forEach { version ->
            val category = byId[version.categoryId] ?: fail("备份里的规则版本引用了不存在的分类")
            if (category.type != version.type) fail("备份分类与收支归属不一致")
        }
    }

    private fun rows(root: JSONObject, table: String, required: Boolean): List<JSONObject> {
        if (!root.has(table) || root.isNull(table)) {
            if (required) fail("备份缺少 $table 数组")
            return emptyList()
        }
        val array = root.optJSONArray(table) ?: fail("备份的 $table 不是数组")
        return (0 until array.length()).map { index ->
            array.optJSONObject(index) ?: fail("备份的 $table 第 ${index + 1} 行不是对象")
        }
    }

    private fun JSONObject.toCategory(): CategoryEntity {
        val enabled = optInt("enabled", 1) == 1
        return CategoryEntity(
            id = getLong("id"),
            name = getString("name").also { if (it.isBlank() || it.length > 40) fail("备份分类名称无效") },
            type = EntryType.fromWire(optString("type")),
            color = getString("color").also {
                if (!Regex("""#[0-9a-fA-F]{6}""").matches(it)) fail("备份分类颜色无效")
            },
            icon = optString("icon", ""),
            sortOrder = optInt("sort_order", 0),
            enabled = enabled,
            createdAt = getString("created_at"),
            updatedAt = getString("updated_at"),
        )
    }

    private fun JSONObject.toRule() = RecurringRuleEntity(
        id = getLong("id"),
        name = getString("name"),
        type = EntryType.fromWire(optString("type")),
        categoryId = getLong("category_id"),
        amountCents = getLong("amount_cents").also {
            if (it !in 1..Money.MAX_CENTS) fail("备份金额分值无效")
        },
        frequency = Frequency.fromWire(optString("frequency")),
        startDate = getString("start_date"),
        endDate = nullableText("end_date"),
        dayOfMonth = nullableInt("day_of_month"),
        monthOfYear = nullableInt("month_of_year"),
        enabled = optInt("enabled", 1) == 1,
        note = optString("note", ""),
        effectiveFrom = getString("effective_from"),
        processedThrough = nullableText("processed_through"),
        lastExecutionDate = nullableText("last_execution_date"),
        deleted = optInt("deleted", 0) == 1,
        createdAt = getString("created_at"),
        updatedAt = getString("updated_at"),
    )

    private fun JSONObject.toVersion() = RecurringVersionEntity(
        id = getLong("id"),
        ruleId = getLong("rule_id"),
        validFrom = getString("valid_from"),
        validUntil = nullableText("valid_until"),
        type = EntryType.fromWire(optString("type")),
        categoryId = getLong("category_id"),
        amountCents = getLong("amount_cents"),
        frequency = Frequency.fromWire(optString("frequency")),
        startDate = getString("start_date"),
        endDate = nullableText("end_date"),
        dayOfMonth = nullableInt("day_of_month"),
        monthOfYear = nullableInt("month_of_year"),
        enabled = optInt("enabled", 1) == 1,
        note = optString("note", ""),
    )

    private fun JSONObject.toTransaction() = TransactionEntity(
        id = getLong("id"),
        type = EntryType.fromWire(optString("type")),
        categoryId = getLong("category_id"),
        categoryName = getString("category_name"),
        categoryColor = getString("category_color"),
        amountCents = getLong("amount_cents"),
        transactionDate = getString("transaction_date").also { Dates.parse(it) },
        note = optString("note", ""),
        source = EntrySource.fromWire(optString("source")),
        recurringRuleId = nullableLong("recurring_rule_id"),
        recurringOccurrenceDate = nullableText("recurring_occurrence_date"),
        createdAt = getString("created_at"),
        updatedAt = getString("updated_at"),
    )

    private fun JSONObject.toExecution() = RecurringExecutionEntity(
        ruleId = getLong("rule_id"),
        occurrenceDate = getString("occurrence_date"),
        createdAt = getString("created_at"),
    )

    private fun JSONObject.toAllocation() = TransactionAllocationEntity(
        transactionId = getLong("transaction_id"),
        frequency = Frequency.fromWire(optString("frequency")),
        periodStart = getString("period_start"),
        periodEnd = getString("period_end"),
    )

    private fun JSONObject.nullableText(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.nullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)

    private fun JSONObject.nullableInt(name: String): Int? =
        if (!has(name) || isNull(name)) null else getInt(name)
}
