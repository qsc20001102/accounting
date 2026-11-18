package com.qsc.accounting.data.db

import androidx.room.TypeConverter
import com.qsc.accounting.data.model.EntrySource
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency

class Converters {
    @TypeConverter
    fun entryTypeToString(value: EntryType): String = value.wire

    @TypeConverter
    fun stringToEntryType(value: String): EntryType = EntryType.fromWire(value)

    @TypeConverter
    fun frequencyToString(value: Frequency): String = value.wire

    @TypeConverter
    fun stringToFrequency(value: String): Frequency = Frequency.fromWire(value)

    @TypeConverter
    fun sourceToString(value: EntrySource): String = value.wire

    @TypeConverter
    fun stringToSource(value: String): EntrySource = EntrySource.fromWire(value)
}
