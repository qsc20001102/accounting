package com.qsc.accounting.core

/**
 * 金额以「分」为整数存储，与插件 amount_cents 完全一致，避免浮点误差。
 * 解析规则照搬 utils/money.py：只接受十进制字符串，最多两位小数。
 */
object Money {
    const val MAX_CENTS: Long = 99_999_999_999L

    private val DECIMAL = Regex("""[0-9]+(?:\.[0-9]{1,2})?""")

    fun parseToCents(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty() || value.length > 20 || !DECIMAL.matches(value)) {
            fail("金额必须是大于 0 的数字，最多两位小数")
        }
        val parts = value.split('.')
        val whole = parts[0].toLongOrNull() ?: fail("金额超出可用范围")
        val fraction = if (parts.size == 2) parts[1].padEnd(2, '0').toLong() else 0L
        if (whole > MAX_CENTS / 100) fail("金额必须大于 0 且不超过 999999999.99")
        val cents = whole * 100 + fraction
        if (cents <= 0 || cents > MAX_CENTS) fail("金额必须大于 0 且不超过 999999999.99")
        return cents
    }

    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val absolute = kotlin.math.abs(cents)
        return "$sign${absolute / 100}.${(absolute % 100).toString().padStart(2, '0')}"
    }

    /** 输入框里的金额回显：去掉无意义的小数位，用不着显示 .00。 */
    fun formatPlain(cents: Long): String {
        val text = format(cents)
        return if (text.endsWith(".00")) text.dropLast(3) else text
    }
}
