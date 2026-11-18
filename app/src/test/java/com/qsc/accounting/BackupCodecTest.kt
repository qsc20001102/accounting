package com.qsc.accounting

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qsc.accounting.core.ValidationException
import com.qsc.accounting.data.BackupCodec
import com.qsc.accounting.data.EntryInput
import com.qsc.accounting.data.LedgerRepository
import com.qsc.accounting.data.Prefs
import com.qsc.accounting.data.RecurringInput
import com.qsc.accounting.data.RecurringRepository
import com.qsc.accounting.data.db.AppDatabase
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency
import com.qsc.accounting.data.model.StatisticsMode
import com.qsc.accounting.domain.Period
import com.qsc.accounting.domain.StatsQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** 备份往返：这是「和插件互通」的唯一保障，必须用真库跑一遍。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupCodecTest {

    private fun newDatabase(context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

    @Test
    fun backup_round_trip_preserves_all_tables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = newDatabase(context)
        val ledger = LedgerRepository(source, Prefs(context))
        val recurring = RecurringRepository(source)
        ledger.ensureSeeded()

        val categoryId = ledger.categories(EntryType.EXPENSE).first().id
        ledger.saveEntry(
            EntryInput(
                type = EntryType.EXPENSE,
                categoryId = categoryId,
                amount = "25.50",
                date = "2026-11-18",
                note = "午饭",
            ),
        )
        recurring.save(
            RecurringInput(
                name = "房租",
                type = EntryType.EXPENSE,
                categoryId = categoryId,
                amount = "3000",
                frequency = Frequency.MONTHLY,
                dayOfMonth = 1,
                startDate = "2026-01-01",
            ),
            today = LocalDate.parse("2026-01-01"),
        )
        recurring.runDue(LocalDate.parse("2026-03-01"))

        val json = BackupCodec.encode(BackupCodec.snapshot(source))
        assertTrue(json.contains("\"format\": \"accounting-backup\""))

        val target = newDatabase(context)
        BackupCodec.replaceAll(target, BackupCodec.decode(json))

        assertEquals(source.categories().all().size, target.categories().all().size)
        assertEquals(4, target.transactions().all().size) // 1 笔手动 + 1/2/3 月各一笔房租
        assertEquals(
            source.recurring().allocations().size,
            target.recurring().allocations().size,
        )
        assertEquals(source.recurring().versions().size, target.recurring().versions().size)
        assertEquals(source.recurring().executions().size, target.recurring().executions().size)

        val manual = target.transactions().all().first { it.note == "午饭" }
        assertEquals(2_550L, manual.amountCents)

        // 恢复后的账本仍然能正常统计，均摊快照没有断链。
        val restored = LedgerRepository(target, Prefs(context))
        val summary = restored.summary(
            StatsQuery(period = Period.MONTH, date = "2026-01"),
            StatisticsMode.AMORTIZED,
            includePending = true,
            today = LocalDate.parse("2026-03-01"),
        )
        assertEquals(300_000L, summary.expenseCents)

        source.close()
        target.close()
    }

    @Test
    fun rejects_files_that_are_not_plugin_backups() {
        assertThrows(ValidationException::class.java) {
            BackupCodec.decode("{\"hello\":1}")
        }
        assertThrows(ValidationException::class.java) {
            BackupCodec.decode("not json at all")
        }
    }
}
