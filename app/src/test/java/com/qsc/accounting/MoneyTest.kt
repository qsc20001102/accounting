package com.qsc.accounting

import com.qsc.accounting.core.Money
import com.qsc.accounting.core.ValidationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MoneyTest {

    @Test
    fun parses_common_amounts() {
        assertEquals(1L, Money.parseToCents("0.01"))
        assertEquals(1250L, Money.parseToCents("12.5"))
        assertEquals(1200L, Money.parseToCents("12.00"))
        assertEquals(1200L, Money.parseToCents("0012"))
        assertEquals(99_999_999_999L, Money.parseToCents("999999999.99"))
    }

    @Test
    fun rejects_invalid_amounts() {
        listOf("", "0", "0.00", "-1", "1.234", "abc", "1e5", " 1.2.3 ").forEach { raw ->
            assertThrows("应拒绝金额：$raw", ValidationException::class.java) {
                Money.parseToCents(raw)
            }
        }
    }

    @Test
    fun formats_with_two_decimals() {
        assertEquals("0.01", Money.format(1))
        assertEquals("12.50", Money.format(1250))
        assertEquals("-3.05", Money.format(-305))
        assertEquals("999999999.99", Money.format(99_999_999_999L))
        assertEquals("12", Money.formatPlain(1200))
    }
}
