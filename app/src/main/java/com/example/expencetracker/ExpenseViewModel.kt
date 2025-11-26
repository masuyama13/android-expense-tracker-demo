package com.example.expencetracker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class ExpenseViewModel(private val repo: ExpenseRepository) : ViewModel() {

    var currentMonth by mutableStateOf(YearMonth.now())
    var monthlyBudget by mutableStateOf(2000.0)

    fun expensesForMonth(): List<Expense> =
        repo.items.sortedByDescending { it.occurredAt }

    fun loadMonth(month: YearMonth, zone: ZoneId) {
        viewModelScope.launch {
            repo.loadMonth(month, zone)
        }
    }

    fun addExpense(
        title: String,
        amount: Double,
        category: String,
        date: LocalDateTime,
        zone: ZoneId
    ) {
        viewModelScope.launch {
            repo.add(
                Expense(
                    title = title,
                    amount = amount,
                    category = category,
                    occurredAt = date
                )
            )
            repo.loadMonth(currentMonth, zone)
        }
    }

    fun deleteExpense(id: String, zone: ZoneId) {
        viewModelScope.launch {
            repo.delete(id)
            repo.loadMonth(currentMonth, zone)
        }
    }

    fun updateExpense(expense: Expense, zone: ZoneId) {
        viewModelScope.launch {
            repo.update(expense)
            repo.loadMonth(currentMonth, zone)
        }
    }

    suspend fun monthlyTotal(month: YearMonth, zone: ZoneId): Double {
        return repo.monthlyTotal(month, zone)
    }

    suspend fun totalsByCategory(month: YearMonth, zone: ZoneId): List<CategoryTotal> {
        return repo.totalsByCategory(month, zone)
    }
}
