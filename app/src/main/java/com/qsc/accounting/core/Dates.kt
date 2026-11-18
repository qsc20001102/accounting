package com.qsc.accounting.core

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** 日期一律以 ISO 字符串（yyyy-MM-dd）落库，与插件 schema 保持一致，便于直接导入导出。 */
object Dates {
    val MIN: LocalDate = LocalDate.of(1900, 1, 1)
    val MAX: LocalDate = LocalDate.of(2199, 12, 31)

    private val DATE_ONLY = Regex("""\d{4}-\d{2}-\d{2}""")
    private val STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx")

    fun parse(value: String?, label: String = "日期"): LocalDate {
        if (value == null || !DATE_ONLY.matches(value)) fail("$label 必须为 YYYY-MM-DD")
        val parsed = try {
            LocalDate.parse(value)
        } catch (_: Exception) {
            fail("$label 不是有效日期")
        }
        if (parsed.isBefore(MIN) || parsed.isAfter(MAX)) fail("$label 年份必须在 1900～2199 之间")
        return parsed
    }

    fun iso(date: LocalDate): String = date.toString()

    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone)

    /** 与插件 timestamp() 同格式的 UTC 时间戳（含 +00:00 偏移），保证备份可回灌。 */
    fun timestamp(): String = OffsetDateTime.now(ZoneOffset.UTC).format(STAMP)

    fun yearMonth(date: LocalDate): String = "%04d-%02d".format(date.year, date.monthValue)
}
