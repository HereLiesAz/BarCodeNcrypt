package com.hereliesaz.barcodencrypt.data

import androidx.room.TypeConverter

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromKeyType(value: KeyType): String {
        return value.name
    }

    @TypeConverter
    fun toKeyType(value: String): KeyType {
        // Degrade to SINGLE_BARCODE rather than crashing the query on a legacy/unknown value.
        return runCatching { KeyType.valueOf(value) }.getOrDefault(KeyType.SINGLE_BARCODE)
    }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { Gson().toJson(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let {
            val listType = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(it, listType)
        }
    }
}
