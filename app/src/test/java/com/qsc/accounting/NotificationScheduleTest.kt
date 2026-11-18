package com.qsc.accounting

import com.qsc.accounting.notify.NotificationScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 提醒时间计算：过了点顺延、月末回退、跨年跨月。 */
class NotificationScheduleTest {

    private fun at(text: String): LocalDateTime = LocalDateTime.parse(text)

    private fun millisOf(dateTime: LocalDateTime): Long =
        dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun daily_schedules_today_when_time_is_still_ahead() {
        val next = NotificationScheduler.nextDaily(at("2026-11-18T08:00"), 21, 30)
        assertEquals(millisOf(at("2026-11-18T21:30")), next)
    }

    @Test
    fun daily_rolls_to_tomorrow_when_time_has_passed() {
        val next = NotificationScheduler.nextDaily(at("2026-11-18T22:00"), 21, 30)
        assertEquals(millisOf(at("2026-11-19T21:30")), next)
    }

    @Test
    fun daily_rolls_over_month_and_year_ends() {
        assertEquals(
            millisOf(at("2027-01-01T07:00")),
            NotificationScheduler.nextDaily(at("2026-12-31T23:00"), 7, 0),
        )
    }

    @Test
    fun monthly_uses_the_configured_day() {
        assertEquals(
            millisOf(at("2026-11-25T09:00")),
            NotificationScheduler.nextMonthly(at("2026-11-18T10:00"), 25, 9, 0),
        )
    }

    @Test
    fun monthly_falls_back_to_month_end_for_short_months() {
        // 31 号在 2 月按 28 日算（2026 非闰年）。
        assertEquals(
            millisOf(at("2027-02-28T09:00")),
            NotificationScheduler.nextMonthly(at("2027-01-31T10:00"), 31, 9, 0),
        )
    }

    @Test
    fun monthly_moves_to_next_month_when_past() {
        assertEquals(
            millisOf(at("2026-12-01T09:00")),
            NotificationScheduler.nextMonthly(at("2026-11-01T09:01"), 1, 9, 0),
        )
    }
}
