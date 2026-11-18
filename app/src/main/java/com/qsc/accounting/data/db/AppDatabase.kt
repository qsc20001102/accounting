package com.qsc.accounting.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        RecurringRuleEntity::class,
        RecurringVersionEntity::class,
        RecurringExecutionEntity::class,
        TransactionAllocationEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categories(): CategoryDao

    abstract fun transactions(): TransactionDao

    abstract fun recurring(): RecurringDao

    companion object {
        private const val NAME = "accounting.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .build()
    }
}
