package com.example.expencetracker

import androidx.room.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import androidx.compose.runtime.mutableStateListOf

val CATEGORIES: List<String> = listOf(
    "Housing",
    "Utilities",
    "Groceries",
    "Transportation",
    "Dining Out",
    "Shopping",
    "Health & Insurance",
    "Entertainment",
    "Education",
    "Subscriptions",
    "Savings & Investments",
    "Others"
)

@Entity(
    tableName = "expenses",
    indices = [Index("occurredAtEpochMs"), Index("category")]
)
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amount: Double,
    val category: String,
    val occurredAtEpochMs: Long
)

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(e: ExpenseEntity)

    @Update
    suspend fun update(e: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        """
        SELECT * FROM expenses
        WHERE occurredAtEpochMs BETWEEN :start AND :end
        ORDER BY occurredAtEpochMs DESC
    """
    )
    suspend fun listByMonth(start: Long, end: Long): List<ExpenseEntity>

    @Query(
        """
        SELECT COALESCE(SUM(amount), 0)
        FROM expenses
        WHERE occurredAtEpochMs BETWEEN :start AND :end
    """
    )
    suspend fun monthlyTotal(start: Long, end: Long): Double

    @Query(
        """
        SELECT category, SUM(amount) AS total
        FROM expenses
        WHERE occurredAtEpochMs BETWEEN :start AND :end
        GROUP BY category
        ORDER BY total DESC
    """
    )
    suspend fun totalsByCategory(start: Long, end: Long): List<CategoryTotal>
}

data class CategoryTotal(
    val category: String,
    val total: Double
)

@Database(entities = [ExpenseEntity::class], version = 1)
abstract class AppDb : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
}

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var amount: Double,
    var category: String,
    var occurredAt: LocalDateTime = LocalDateTime.now()
)

private fun monthBounds(yearMonth: YearMonth, zone: ZoneId): Pair<Long, Long> {
    val start = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return start to end
}

private fun Expense.toEntity(zone: ZoneId = ZoneId.systemDefault()): ExpenseEntity =
    ExpenseEntity(
        id = id,
        title = title,
        amount = amount,
        category = category,
        occurredAtEpochMs = occurredAt.atZone(zone).toInstant().toEpochMilli()
    )

private fun ExpenseEntity.toModel(zone: ZoneId = ZoneId.systemDefault()): Expense =
    Expense(
        id = id,
        title = title,
        amount = amount,
        category = category,
        occurredAt = Instant.ofEpochMilli(occurredAtEpochMs).atZone(zone).toLocalDateTime()
    )

/** Room-backed repository. Keeps a local state list for Compose while persisting to SQLite. */
class ExpenseRepository(private val dao: ExpenseDao) {
    private val _items = mutableStateListOf<Expense>()
    val items: List<Expense> get() = _items

    suspend fun loadMonth(month: YearMonth, zone: ZoneId) {
        val (start, end) = monthBounds(month, zone)
        val list = dao.listByMonth(start, end).map { it.toModel(zone) }
        _items.clear()
        _items.addAll(list)
    }

    suspend fun add(expense: Expense) {
        _items.add(expense)
        dao.insert(expense.toEntity())
    }

    suspend fun update(expense: Expense) {
        val idx = _items.indexOfFirst { it.id == expense.id }
        if (idx >= 0) _items[idx] = expense
        dao.update(expense.toEntity())
    }

    suspend fun delete(id: String) {
        _items.removeAll { it.id == id }
        dao.delete(id)
    }

    suspend fun monthlyTotal(month: YearMonth, zone: ZoneId): Double {
        val (start, end) = monthBounds(month, zone)
        return dao.monthlyTotal(start, end)
    }

    suspend fun totalsByCategory(month: YearMonth, zone: ZoneId): List<CategoryTotal> {
        val (start, end) = monthBounds(month, zone)
        return dao.totalsByCategory(start, end)
    }
}
