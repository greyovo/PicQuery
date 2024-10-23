/**
 * Copyright 2023 Viacheslav Barkov
 */

package me.grey.picquery.data.model

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object Converters {
    @TypeConverter
    fun fromString(value: String?): FloatArray {
        val json = Json
        val floatList = value?.let { json.decodeFromString<List<Float>>(it) }
        val floatArray = floatList?.toFloatArray()
        return floatArray?.copyOfRange(0, floatArray.size) ?: FloatArray(0)

    }

    @TypeConverter
    fun fromFloatArray(array: FloatArray): String {
        val json = Json.encodeToString(array)
        return json
    }
}