package com.qsc.accounting

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qsc.accounting.data.LedgerRepository
import com.qsc.accounting.data.Prefs
import com.qsc.accounting.data.RecurringInput
import com.qsc.accounting.data.RecurringRepository
import com.qsc.accounting.data.db.AppDatabase
import com.qsc.accounting.data.db.RecurringRuleEntity
import com.qsc.accounting.data.model.EntrySource
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * 固定收支的集成测试：真的开一个 Room 内存库，走完整的生成流程。
 * 这里验证的是 SQL 层行为（唯一约束、幂等凭据、游标推进），纯函数测试覆盖不到。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecurringRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var recurring: RecurringRepository
    private var incomeCategoryId = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        ledger = LedgerRepository(db, Prefs(context))
        recurring = RecurringRepository(db)
        ledger.ensureSeeded()
        incomeCategoryId = ledger.categories(EntryType.INCOME).first().id
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun monthlyRule(
        day: Int = 18,
        amount: String = "3100.00",
        start: String = "2026-01-01",
        end: String? = null,
        enabled: Boolean = true,
    ): RecurringRuleEntity = recurring.save(
        RecurringInput(
            name = "工资",
            type = EntryType.INCOME,
            categoryId = incomeCategoryId,
            amount = amount,
            frequency = Frequency.MONTHLY,
            dayOfMonth = day,
            startDate = start,
            endDate = end,
            enabled = enabled,
        ),
        today = LocalDate.parse(start),
    )

    @Test
    fun generates_every_due_occurrence_and_advances_the_cursor() = runBlocking {
        monthlyRule()
        val today = LocalDate.parse("2026-11-18")

        val result = recurring.runDue(today)

        assertEquals(11, result.generated)
        val rows = db.transactions().all()
        assertEquals(11, rows.size)
        assertTrue(rows.all { it.source == EntrySource.RECURRING })
        assertEquals("2026-01-18", rows.minOf { it.transactionDate })
        assertEquals("2026-11-18", rows.maxOf { it.transactionDate })
        assertEquals(11, db.recurring().allocations().size)
        assertEquals(11, db.recurring().executions().size)
        assertEquals("2026-11-18", db.recurring().rules().first().processedThrough)
    }

    @Test
    fun running_twice_does_not_duplicate_anything() = runBlocking {
        monthlyRule()
        val today = LocalDate.parse("2026-11-18")

        val first = recurring.runDue(today)
        val second = recurring.runDue(today)

        assertEquals(11, first.generated)
        assertEquals(0, second.generated)
        assertEquals(11, db.transactions().all().size)
    }

    @Test
    fun paused_rule_generates_nothing() = runBlocking {
        val rule = monthlyRule()
        recurring.setEnabled(rule.id, false, LocalDate.parse("2026-06-01"))

        val result = recurring.runDue(LocalDate.parse("2026-11-18"))

        assertEquals(0, result.generated)
        assertTrue(db.transactions().all().isEmpty())
    }

    @Test
    fun deleted_rule_keeps_history_and_stops_generation() = runBlocking {
        val rule = monthlyRule()
        recurring.runDue(LocalDate.parse("2026-03-18"))
        assertEquals(3, db.transactions().all().size)

        recurring.delete(rule.id, LocalDate.parse("2026-04-01"))
        val result = recurring.runDue(LocalDate.parse("2026-11-18"))

        assertEquals(0, result.generated)
        assertEquals(3, db.transactions().all().size)
        assertTrue(recurring.ruleViews(LocalDate.parse("2026-11-18")).isEmpty())
    }

    @Test
    fun editing_the_amount_only_affects_records_from_today_on() = runBlocking {
        val rule = monthlyRule()
        recurring.runDue(LocalDate.parse("2026-02-18"))

        recurring.save(
            RecurringInput(
                id = rule.id,
                name = "工资",
                type = EntryType.INCOME,
                categoryId = incomeCategoryId,
                amount = "3500.00",
                frequency = Frequency.MONTHLY,
                dayOfMonth = 18,
                startDate = "2026-01-01",
            ),
            today = LocalDate.parse("2026-03-01"),
        )
        val due = recurring.runDue(LocalDate.parse("2026-03-18"))

        assertEquals(1, due.generated)
        val rows = db.transactions().all().sortedBy { it.transactionDate }
        assertEquals(3, rows.size)
        assertEquals(310_000L, rows[0].amountCents)
        assertEquals(310_000L, rows[1].amountCents)
        assertEquals(350_000L, rows[2].amountCents)
    }

    @Test
    fun generation_stops_at_the_rule_end_date() = runBlocking {
        monthlyRule(day = 31, start = "2026-01-01", end = "2026-03-31")

        val result = recurring.runDue(LocalDate.parse("2026-11-18"))

        assertEquals(3, result.generated)
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31"),
            db.transactions().all().map { it.transactionDate }.sorted(),
        )
    }

    @Test
    fun generated_records_carry_an_allocation_period() = runBlocking {
        monthlyRule()

        recurring.runDue(LocalDate.parse("2026-01-31"))

        val allocation = db.recurring().allocations().single()
        assertEquals("2026-01-01", allocation.periodStart)
        assertEquals("2026-01-31", allocation.periodEnd)
    }

    @Test
    fun user_deleted_record_is_not_regenerated() = runBlocking {
        monthlyRule()
        val today = LocalDate.parse("2026-02-28")
        recurring.runDue(today)

        val january = db.transactions().all().first { it.transactionDate == "2026-01-18" }
        ledger.deleteEntries(listOf(january.id))

        val again = recurring.runDue(today)
        assertEquals(0, again.generated)
        assertEquals(1, db.transactions().all().size)
    }
}
