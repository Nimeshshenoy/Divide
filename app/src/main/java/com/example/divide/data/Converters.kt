package com.example.divide.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromUserList(value: List<User>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toUserList(value: String): List<User> {
        val listType = object : TypeToken<List<User>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromExpenseList(value: List<Expense>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toExpenseList(value: String): List<Expense> {
        val listType = object : TypeToken<List<Expense>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }
}
