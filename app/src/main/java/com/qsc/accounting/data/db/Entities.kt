package com.qsc.accounting.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.qsc.accounting.data.model.EntrySource
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency

/**
 * 表结构与插件的 schema v3 对齐（表名、列名、约束语义一致），
 * 这样插件导出的 JSON 备份可以原样导入，将来也能导回去。
 */

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["type", "name"], unique = true),
        Index(value = ["sort_order"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: EntryType,
    val color: String,
    val icon: String = "",
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["transaction_date", "type"]),
        Index(value = ["category_id", "transaction_date"]),
        Index(value = ["recurring_rule_id", "recurring_occurrence_date"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EntryType,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    /** 分类名与颜色是写入时的快照，改了分类也不影响历史记录，与插件行为一致。 */
    @ColumnInfo(name = "category_name") val categoryName: String,
    @ColumnInfo(name = "category_color") val categoryColor: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "transaction_date") val transactionDate: String,
    val note: String = "",
    val source: EntrySource = EntrySource.WEB,
    @ColumnInfo(name = "recurring_rule_id") val recurringRuleId: Long? = null,
    @ColumnInfo(name = "recurring_occurrence_date") val recurringOccurrenceDate: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: EntryType,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val frequency: Frequency,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String? = null,
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int? = null,
    @ColumnInfo(name = "month_of_year") val monthOfYear: Int? = null,
    val enabled: Boolean = true,
    val note: String = "",
    @ColumnInfo(name = "effective_from") val effectiveFrom: String,
    @ColumnInfo(name = "processed_through") val processedThrough: String? = null,
    @ColumnInfo(name = "last_execution_date") val lastExecutionDate: String? = null,
    val deleted: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "updated_at") val updatedAt: String,
)

/** 规则的不可变版本历史：改金额/分类不影响已经入账的历史。 */
@Entity(
    tableName = "recurring_versions",
    indices = [Index(value = ["rule_id", "valid_from"], unique = true)],
)
data class RecurringVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "rule_id") val ruleId: Long,
    @ColumnInfo(name = "valid_from") val validFrom: String,
    @ColumnInfo(name = "valid_until") val validUntil: String? = null,
    val type: EntryType,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val frequency: Frequency,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String? = null,
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int? = null,
    @ColumnInfo(name = "month_of_year") val monthOfYear: Int? = null,
    val enabled: Boolean = true,
    val note: String = "",
)

/** 生成凭据：用户主动删掉某笔固定收支后，重新检查也不会再生成同一笔。 */
@Entity(tableName = "recurring_executions", primaryKeys = ["rule_id", "occurrence_date"])
data class RecurringExecutionEntity(
    @ColumnInfo(name = "rule_id") val ruleId: Long,
    @ColumnInfo(name = "occurrence_date") val occurrenceDate: String,
    @ColumnInfo(name = "created_at") val createdAt: String,
)

/** 每笔固定收支归属的均摊周期快照；手动记账没有这一行。 */
@Entity(tableName = "transaction_allocations")
data class TransactionAllocationEntity(
    @PrimaryKey @ColumnInfo(name = "transaction_id") val transactionId: Long,
    val frequency: Frequency,
    @ColumnInfo(name = "period_start") val periodStart: String,
    @ColumnInfo(name = "period_end") val periodEnd: String,
)

/** 收支 + 可选均摊周期的联表结果。 */
data class LedgerRow(
    @Embedded val entry: TransactionEntity,
    @ColumnInfo(name = "period_start") val periodStart: String?,
    @ColumnInfo(name = "period_end") val periodEnd: String?,
)

data class CountRow(@ColumnInfo(name = "total") val total: Int)
