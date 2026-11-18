package com.qsc.accounting.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.qsc.accounting.data.model.EntryType

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, sort_order, id")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE type = :type AND name = :name AND id != :excludeId")
    suspend fun countSameName(type: EntryType, name: String, excludeId: Long): Int

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY transaction_date DESC, id DESC")
    suspend fun all(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @RawQuery
    suspend fun rawRows(query: SupportSQLiteQuery): List<TransactionEntity>

    @RawQuery
    suspend fun rawCount(query: SupportSQLiteQuery): List<CountRow>

    @RawQuery
    suspend fun ledgerRows(query: SupportSQLiteQuery): List<LedgerRow>

    @Insert
    suspend fun insert(row: TransactionEntity): Long

    @Insert
    suspend fun insertAll(rows: List<TransactionEntity>)

    @Update
    suspend fun update(row: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_rules ORDER BY id DESC")
    suspend fun rules(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_versions")
    suspend fun versions(): List<RecurringVersionEntity>

    @Query("SELECT * FROM recurring_executions")
    suspend fun executions(): List<RecurringExecutionEntity>

    @Query("SELECT * FROM transaction_allocations")
    suspend fun allocations(): List<TransactionAllocationEntity>

    @Query("SELECT * FROM transaction_allocations WHERE transaction_id = :transactionId")
    suspend fun allocation(transactionId: Long): TransactionAllocationEntity?

    @Update
    suspend fun updateAllocation(allocation: TransactionAllocationEntity)

    @Query("DELETE FROM transaction_allocations WHERE transaction_id IN (:ids)")
    suspend fun deleteAllocationsFor(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE category_id = :categoryId")
    suspend fun countRulesByCategory(categoryId: Long): Int

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun ruleById(id: Long): RecurringRuleEntity?

    @Insert
    suspend fun insertRule(rule: RecurringRuleEntity): Long

    @Update
    suspend fun updateRule(rule: RecurringRuleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExecutionIgnore(execution: RecurringExecutionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocation(allocation: TransactionAllocationEntity)

    @Insert
    suspend fun insertVersion(version: RecurringVersionEntity): Long

    @Query("DELETE FROM recurring_versions WHERE rule_id = :ruleId AND valid_from >= :validFrom")
    suspend fun deleteVersionsFrom(ruleId: Long, validFrom: String)

    @Query(
        "UPDATE recurring_versions SET valid_until = :validFrom " +
            "WHERE rule_id = :ruleId AND valid_from < :validFrom " +
            "AND (valid_until IS NULL OR valid_until > :validFrom)",
    )
    suspend fun closeVersionsBefore(ruleId: Long, validFrom: String)

    @Query("UPDATE recurring_rules SET processed_through = :date WHERE id = :ruleId")
    suspend fun setProcessedThrough(ruleId: Long, date: String)

    @Query("UPDATE recurring_rules SET last_execution_date = :date WHERE id = :ruleId")
    suspend fun touchLastExecution(ruleId: Long, date: String)

    @Query("SELECT COUNT(*) FROM recurring_versions WHERE category_id = :categoryId")
    suspend fun countVersionsByCategory(categoryId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RecurringRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersions(versions: List<RecurringVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecutions(executions: List<RecurringExecutionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocations(allocations: List<TransactionAllocationEntity>)

    @Query("DELETE FROM recurring_rules")
    suspend fun deleteRules()

    @Query("DELETE FROM recurring_versions")
    suspend fun deleteVersions()

    @Query("DELETE FROM recurring_executions")
    suspend fun deleteExecutions()

    @Query("DELETE FROM transaction_allocations")
    suspend fun deleteAllocations()
}
