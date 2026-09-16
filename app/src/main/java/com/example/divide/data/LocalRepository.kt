package com.example.divide.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object LocalRepository {
    private val _events = MutableStateFlow<List<ExpenseEvent>>(emptyList())
    val events: StateFlow<List<ExpenseEvent>> = _events.asStateFlow()

    fun addEvent(event: ExpenseEvent) {
        _events.update { it + event }
    }

    fun updateEvent(event: ExpenseEvent) {
        _events.update { list ->
            list.map { if (it.id == event.id) event else it }
        }
    }

    fun getEvent(eventId: String): ExpenseEvent? {
        return _events.value.find { it.id == eventId }
    }
}
