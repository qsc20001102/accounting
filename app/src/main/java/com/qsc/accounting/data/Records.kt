package com.qsc.accounting.data

import com.qsc.accounting.core.Dates
import com.qsc.accounting.core.fail
import com.qsc.accounting.data.db.TransactionEntity
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.SortOrder

/** 明细筛选条件，对应插件 accounting_service.record_filters()。 */
data class RecordFilter(
    val startDate: String? = null,
    val endDate: String? = null,
    val type: EntryType? = null,
    val categoryId: Long? = null,
    val search: String? = null,
    val minAmountCents: Long? = null,
    val maxAmountCents: Long? = null,
) {
    fun toSql(): SqlFragment {
        val parts = mutableListOf<String>()
        val values = mutableListOf<Any?>()

        if (startDate != null && endDate != null && startDate > endDate) {
            fail("开始日期不能晚于结束日期")
        }
        startDate?.let {
            Dates.parse(it, "开始日期")
            parts += "transaction_date >= ?"
            values += it
        }
        endDate?.let {
            Dates.parse(it, "结束日期")
            parts += "transaction_date <= ?"
            values += it
        }
        type?.let {
            parts += "type = ?"
            values += it.wire
        }
        categoryId?.let {
            parts += "category_id = ?"
            values += it
        }
        minAmountCents?.let {
            parts += "amount_cents >= ?"
            values += it
        }
        maxAmountCents?.let {
            parts += "amount_cents <= ?"
            values += it
        }
        search?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (it.length > 500) fail("备注搜索不能超过 500 个字符")
            // instr 是字面子串匹配，不会把 % 和 _ 当通配符，和插件一致。
            parts += "instr(note, ?) > 0"
            values += it
        }

        return SqlFragment(
            clause = if (parts.isEmpty()) "" else " WHERE " + parts.joinToString(" AND "),
            args = values,
        )
    }
}

data class SqlFragment(val clause: String, val args: List<Any?>)

data class RecordQuery(
    val filter: RecordFilter = RecordFilter(),
    val sort: SortOrder = SortOrder.DATE_DESC,
    val page: Int = 1,
    val pageSize: Int = 50,
) {
    init {
        if (page < 1) fail("页码必须大于 0")
        if (pageSize !in 1..500) fail("每页条数必须在 1～500 之间")
    }
}

data class RecordPage(
    val items: List<TransactionEntity>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
) {
    val pageCount: Int get() = if (total <= 0) 1 else (total + pageSize - 1) / pageSize
    val hasNext: Boolean get() = page < pageCount
    val hasPrevious: Boolean get() = page > 1
}

/** 新增/编辑一笔收支的输入。编辑时以 id 定位，分类快照语义由仓库层保证。 */
data class EntryInput(
    val id: Long? = null,
    val type: EntryType = EntryType.EXPENSE,
    val categoryId: Long = 0,
    val amount: String = "",
    val date: String,
    val note: String = "",
)

data class CategoryInput(
    val id: Long? = null,
    val name: String = "",
    val type: EntryType = EntryType.EXPENSE,
    val color: String = "#5264df",
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
)
