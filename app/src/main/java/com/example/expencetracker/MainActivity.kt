@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.expencetracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Fill
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.time.Instant
import java.util.*
import androidx.compose.ui.text.style.TextAlign

import androidx.room.*
import androidx.room.Room
import kotlinx.coroutines.launch
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TealTheme {
                ExpenseApp()
            }
        }
    }
}

// ---------- Theme ----------
private val Teal = Color(0xFF00897B)
private val TealDark = Color(0xFF00695C)
private val TealLight = Color(0xFF4DB6AC)

@Composable
fun TealTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = Teal,
        onPrimary = Color.White,
        primaryContainer = TealLight,
        onPrimaryContainer = Color.Black,
        secondary = TealDark,
        onSecondary = Color.White,
        surface = Color(0xFFF7F8F8),
        onSurface = Color(0xFF1B1B1B)
    )
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}

// ---------- Data layer ----------
/** Fixed categories for the app. Users cannot customize. */
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

// ---------- Room persistence (SQLite) ----------
@Entity(tableName = "expenses", indices = [Index("occurredAtEpochMs"), Index("category")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val title: String,
    val amount: Double,
    val category: String,
    val occurredAtEpochMs: Long // stored in UTC
)

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(e: ExpenseEntity)

    @Update
    suspend fun update(e: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: String)

    @Query("""
        SELECT * FROM expenses
        WHERE occurredAtEpochMs BETWEEN :start AND :end
        ORDER BY occurredAtEpochMs DESC
    """)
    suspend fun listByMonth(start: Long, end: Long): List<ExpenseEntity>

    @Query("""
        SELECT COALESCE(SUM(amount), 0)
        FROM expenses
        WHERE occurredAtEpochMs BETWEEN :start AND :end
    """)
    suspend fun monthlyTotal(start: Long, end: Long): Double

    @Query("""
        SELECT category, SUM(amount) AS total
        FROM expenses
        WHERE occurredAtEpochMs BETWEEN :start AND :end
        GROUP BY category
        ORDER BY total DESC
    """)
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

private fun monthBounds(yearMonth: YearMonth, zone: ZoneId): Pair<Long, Long> {
    val start = yearMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    return start to end
}

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var amount: Double,
    var category: String,
    var occurredAt: LocalDateTime = LocalDateTime.now()
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

    suspend fun totalsByCategory(month: YearMonth, zone: ZoneId): List<CategoryTotal> {
        val (start, end) = monthBounds(month, zone)
        return dao.totalsByCategory(start, end)
    }
}

// ---------- ViewModel-like holder (simple, no AndroidX ViewModel to keep single file) ----------
class ExpenseState(val repo: ExpenseRepository) {
    var currentMonth by mutableStateOf(YearMonth.now())
    fun expensesForMonth(month: YearMonth): List<Expense> =
        repo.items.sortedByDescending { it.occurredAt }
}

// ---------- UI ----------
@Composable
fun ExpenseApp() {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val db = remember { Room.databaseBuilder(context, AppDb::class.java, "expenses.db").build() }
    val repo = remember { ExpenseRepository(db.expenseDao()) }
    val state = remember { ExpenseState(repo) }
    val scope = rememberCoroutineScope()
    var showStats by remember { mutableStateOf(false) }
    val month = state.currentMonth
    val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.CANADA)
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.CANADA) }

    LaunchedEffect(month) {
        state.repo.loadMonth(month, zone)
    }

    var editing by remember { mutableStateOf<Expense?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Tracker") },
                actions = {
                    if (!showStats) {
                        TextButton(onClick = { showStats = true }) { Text("Stat") }
                    } else {
                        TextButton(onClick = { showStats = false }) { Text("List") }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!showStats) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.height(8.dp))
                ExpenseEntryForm(
                    onSubmit = { title, amount, category, date ->
                        scope.launch {
                            state.repo.add(
                                Expense(
                                    title = title,
                                    amount = amount,
                                    category = category,
                                    occurredAt = date
                                )
                            )
                            state.repo.loadMonth(state.currentMonth, zone)
                        }
                    }
                )
                Spacer(Modifier.height(16.dp))
                // Monthly header with edge-pinned arrows and total for the month
                val monthlyTotal = state.expensesForMonth(month).sumOf { it.amount }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Center: month and total
                    Text(
                        text = "${month.format(formatter)} • Total: ${currency.format(monthlyTotal)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    // Left edge: previous month
                    IconButton(
                        onClick = { state.currentMonth = state.currentMonth.minusMonths(1) },
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Show previous month")
                    }
                    // Right edge: next month
                    IconButton(
                        onClick = { state.currentMonth = state.currentMonth.plusMonths(1) },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Show next month")
                    }
                }
                Spacer(Modifier.height(8.dp))
                ExpenseList(
                    items = state.expensesForMonth(month),
                    onEdit = { editing = it },
                    onDelete = { id ->
                        scope.launch {
                            state.repo.delete(id)
                            state.repo.loadMonth(state.currentMonth, zone)
                        }
                    }
                )
            }
        } else {
            StatsScreen(
                state = state,
                month = month,
                zone = zone,
                contentPadding = innerPadding
            )
        }
    }

    // Edit dialog
    editing?.let { exp ->
        EditExpenseDialog(
            expense = exp,
            onDismiss = { editing = null },
            onSave = { updated ->
                scope.launch {
                    state.repo.update(updated)
                    state.repo.loadMonth(state.currentMonth, zone)
                    editing = null
                }
            }
        )
    }
}

@Composable
fun StatsScreen(
    state: ExpenseState,
    month: YearMonth,
    zone: ZoneId,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.CANADA) }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.CANADA) }
    val monthlyTotal = state.expensesForMonth(month).sumOf { it.amount }

    // Load category totals from DB for accuracy
    val totals by produceState(initialValue = emptyList<CategoryTotal>(), month, zone) {
        value = state.repo.totalsByCategory(month, zone)
    }

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${month.format(formatter)} • Total: ${currency.format(monthlyTotal)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = { state.currentMonth = state.currentMonth.minusMonths(1) },
                modifier = Modifier.align(Alignment.CenterStart)
            ) { Icon(Icons.Default.ArrowBack, contentDescription = "Show previous month") }
            IconButton(
                onClick = { state.currentMonth = state.currentMonth.plusMonths(1) },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) { Icon(Icons.Default.ArrowForward, contentDescription = "Show next month") }
        }

        Spacer(Modifier.height(8.dp))

        if (totals.isEmpty() || totals.sumOf { it.total } == 0.0) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No data for this month")
            }
        } else {
            PieChartWithLegend(totals = totals)
        }
    }
}

@Composable
fun PieChartWithLegend(totals: List<CategoryTotal>) {
    val sum = totals.sumOf { it.total }.coerceAtLeast(0.000001)
    val currency = NumberFormat.getCurrencyInstance(Locale.CANADA)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.inversePrimary,
        MaterialTheme.colorScheme.surfaceTint,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )

    // Pie
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            var startAngle = -90f
            val pieSize = Size(size.minDimension, size.minDimension)
            val topLeft = Offset(
                (this.size.width - pieSize.width) / 2f,
                0f
            )
            totals.forEachIndexed { index, ct ->
                val sweep = ((ct.total / sum) * 360f).toFloat()
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = topLeft,
                    size = pieSize,
                    style = Fill
                )
                startAngle += sweep
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // Legend
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        totals.forEachIndexed { index, ct ->
            val percent = (ct.total / sum * 100).let { String.format(Locale.CANADA, "%.1f%%", it) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(colors[index % colors.size], CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ct.category,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${currency.format(ct.total)} ($percent)",
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun ExpenseEntryForm(
    onSubmit: (String, Double, String, LocalDateTime) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(CATEGORIES.first()) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale.CANADA) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Add expense", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            DateFieldWithCalendar(
                date = date,
                onDateChanged = { date = it }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Item") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("Amount (${currencyFormatter.currency.currencyCode})") },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            CategoryDropdown(selected = category, onSelected = { category = it })
            Spacer(Modifier.height(12.dp))
            val isValid = title.isNotBlank() && amountInput.toDoubleOrNull() != null
            Button(
                onClick = {
                    val amount = amountInput.toDoubleOrNull() ?: return@Button
                    val dt = LocalDateTime.of(date, LocalTime.now())
                    onSubmit(title.trim(), amount, category, dt)
                    title = ""
                    amountInput = ""
                    category = CATEGORIES.first()
                    date = LocalDate.now()
                },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Add")
            }
        }
    }
}

@Composable
fun CategoryDropdown(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CATEGORIES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DateFieldWithCalendar(date: LocalDate, onDateChanged: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd") }
    val zoneUtc = remember { ZoneOffset.UTC }
    val initialMillis = remember(date) { date.atStartOfDay(zoneUtc).toInstant().toEpochMilli() }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    ExposedDropdownMenuBox(expanded = false, onExpandedChange = {}) {
        OutlinedTextField(
            value = date.format(formatter),
            onValueChange = {},
            readOnly = true,
            label = { Text("Date") },
            trailingIcon = {
                IconButton(onClick = { open = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Open calendar")
                }
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .clickable { open = true }
        )
    }

    if (open) {
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = state.selectedDateMillis
                        if (millis != null) {
                            val newDate = Instant.ofEpochMilli(millis).atZone(zoneUtc).toLocalDate()
                            onDateChanged(newDate)
                        }
                        open = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun ExpenseList(
    items: List<Expense>,
    onEdit: (Expense) -> Unit,
    onDelete: (String) -> Unit
) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.CANADA) }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No expenses for this month")
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        items.forEach { exp ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(exp.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${exp.category} • ${exp.occurredAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Text(currency.format(exp.amount))
                    IconButton(onClick = { onEdit(exp) }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { onDelete(exp.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            }
        }
    }
}

@Composable
fun EditExpenseDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit
) {
    var title by remember { mutableStateOf(expense.title) }
    var amountInput by remember { mutableStateOf(expense.amount.toString()) }
    var category by remember { mutableStateOf(expense.category) }
    var date by remember { mutableStateOf(expense.occurredAt.toLocalDate()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit expense") },
        text = {
            Column {
                DateFieldWithCalendar(date = date, onDateChanged = { date = it })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Item") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Amount (CAD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Spacer(Modifier.height(8.dp))
                CategoryDropdown(selected = category, onSelected = { category = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val updated = expense.copy(
                    title = title.trim(),
                    amount = amountInput.toDoubleOrNull() ?: expense.amount,
                    category = category,
                    occurredAt = LocalDateTime.of(date, expense.occurredAt.toLocalTime())
                )
                onSave(updated)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewExpenseApp() {
    TealTheme { ExpenseApp() }
}