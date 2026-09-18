package com.example.divide.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Helper class for Room to convert complex Kotlin types (like Lists)
 * into simple Strings that can be stored in SQLite columns.
 */
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType) ?: emptyList()
    }

    // These are kept for backward compatibility if needed, though 
    // the new schema uses normalized tables for Users and Expenses.
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
