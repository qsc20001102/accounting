package com.qsc.accounting.data.model

/** 收支方向。wire 值与插件数据库里的 type 字段完全一致。 */
enum class EntryType(val wire: String, val label: String) {
    INCOME("income", "收入"),
    EXPENSE("expense", "支出");

    companion object {
        fun fromWire(value: String?): EntryType =
            entries.firstOrNull { it.wire == value } ?: throw IllegalArgumentException("收支类型无效：$value")
    }
}

enum class Frequency(val wire: String, val label: String) {
    DAILY("daily", "每天"),
    MONTHLY("monthly", "每月"),
    YEARLY("yearly", "每年");

    companion object {
        fun fromWire(value: String?): Frequency =
            entries.firstOrNull { it.wire == value } ?: throw IllegalArgumentException("周期无效：$value")
    }
}

enum class EntrySource(val wire: String, val label: String) {
    WEB("web", "手动"),
    COMMAND("command", "指令"),
    RECURRING("recurring", "固定");

    companion object {
        fun fromWire(value: String?): EntrySource =
            entries.firstOrNull { it.wire == value } ?: throw IllegalArgumentException("来源无效：$value")
    }
}

/** 统计方式：按到账日，或把月度/年度固定收支均摊到每一天。 */
enum class StatisticsMode(val wire: String, val label: String) {
    CASH("cash", "按到账日"),
    AMORTIZED("amortized", "按周期均摊");

    companion object {
        fun fromWire(value: String?): StatisticsMode = entries.firstOrNull { it.wire == value } ?: CASH
    }
}

enum class StatsPeriod(val wire: String, val label: String) {
    DAY("day", "日"),
    MONTH("month", "月"),
    YEAR("year", "年"),
    CUSTOM("custom", "自定义");

    companion object {
        fun fromWire(value: String?): StatsPeriod =
            entries.firstOrNull { it.wire == value } ?: MONTH
    }
}

enum class SortOrder(val sql: String, val label: String) {
    DATE_DESC("transaction_date DESC, id DESC", "日期（新→旧）"),
    DATE_ASC("transaction_date, id", "日期（旧→新）"),
    AMOUNT_DESC("amount_cents DESC, id DESC", "金额（高→低）"),
    AMOUNT_ASC("amount_cents, id", "金额（低→高）");
}
