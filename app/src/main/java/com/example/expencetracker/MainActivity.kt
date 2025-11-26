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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MenuAnchorType
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
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Room
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

enum class StatsMode { CategoryPie, MonthlyBars }

@Composable
fun ExpenseApp() {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    val db = remember { Room.databaseBuilder(context, AppDb::class.java, "expenses.db").build() }
    val repo = remember { ExpenseRepository(db.expenseDao()) }
    val viewModel: ExpenseViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ExpenseViewModel(repo) as T
            }
        }
    )
    val state = viewModel
    var showSettings by remember { mutableStateOf(false) }

    // Load budget from SharedPreferences once
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("settings", 0)
        val saved = prefs.getFloat("monthly_budget", 2000f).toDouble()
        state.monthlyBudget = saved
    }

    var showStats by remember { mutableStateOf(false) }
    var statsMode by remember { mutableStateOf(StatsMode.CategoryPie) }
    val month = state.currentMonth
    val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.CANADA)
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.CANADA) }

    LaunchedEffect(month) {
        state.loadMonth(month, zone)
    }

    var editing by remember { mutableStateOf<Expense?>(null) }
    var selectedCategoryForStats by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when {
                        showSettings -> Text("Settings")
                        selectedCategoryForStats != null -> Text("Expenses: ${selectedCategoryForStats}")
                        showStats && statsMode == StatsMode.MonthlyBars -> Text("Monthly Totals")
                        showStats -> Text("Expense Stats")
                        else -> Text("Expense Tracker")
                    }
                },
                navigationIcon = {
                    when {
                        showSettings -> {
                            IconButton(onClick = { showSettings = false }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                        selectedCategoryForStats != null -> {
                            IconButton(onClick = { selectedCategoryForStats = null }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to stats"
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (!showSettings && selectedCategoryForStats == null) {
                        if (!showStats) {
                            TextButton(onClick = { showStats = true }) {
                                Text("Stat")
                            }
                        } else {
                            if (statsMode == StatsMode.CategoryPie) {
                                TextButton(onClick = { statsMode = StatsMode.MonthlyBars }) {
                                    Text("Monthly")
                                }
                            } else if (statsMode == StatsMode.MonthlyBars) {
                                TextButton(onClick = { statsMode = StatsMode.CategoryPie }) {
                                    Text("Stats")
                                }
                            }
                            TextButton(onClick = { showStats = false }) {
                                Text("List")
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            showSettings -> {
                SettingsScreen(
                    currentBudget = state.monthlyBudget,
                    contentPadding = innerPadding,
                    onBudgetChange = { newBudget ->
                        state.monthlyBudget = newBudget
                        val prefs = context.getSharedPreferences("settings", 0)
                        prefs.edit {
                            putFloat("monthly_budget", newBudget.toFloat())
                        }
                        showSettings = false
                    }
                )
            }
            selectedCategoryForStats != null -> {
                CategoryExpensesScreen(
                    state = state,
                    category = selectedCategoryForStats!!,
                    month = month,
                    contentPadding = innerPadding,
                    onEdit = { editing = it },
                    onDelete = { id ->
                        state.deleteExpense(id, zone)
                    }
                )
            }
            !showStats -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(8.dp))
                    ExpenseEntryForm(
                        onSubmit = { title, amount, category, date ->
                            state.addExpense(title, amount, category, date, zone)
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    // Monthly expense List header
                    val monthlyTotal = state.expensesForMonth().sumOf { it.amount }
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Show previous month")
                        }
                        // Right edge: next month
                        IconButton(
                            onClick = { state.currentMonth = state.currentMonth.plusMonths(1) },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Show next month")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ExpenseList(
                        items = state.expensesForMonth(),
                        onEdit = { editing = it },
                        onDelete = { id ->
                            state.deleteExpense(id, zone)
                        }
                    )
                }
            }
            else -> {
                when (statsMode) {
                    StatsMode.CategoryPie -> {
                        StatsScreen(
                            state = state,
                            month = month,
                            zone = zone,
                            contentPadding = innerPadding,
                            onCategoryClick = { category ->
                                selectedCategoryForStats = category
                            }
                        )
                    }
                    StatsMode.MonthlyBars -> {
                        MonthlyTotalsScreen(
                            state = state,
                            currentMonth = month,
                            zone = zone,
                            budget = state.monthlyBudget,
                            contentPadding = innerPadding,
                            onOpenSettings = {
                                showSettings = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Edit dialog
    editing?.let { exp ->
        EditExpenseDialog(
            expense = exp,
            onDismiss = { editing = null },
            onSave = { updated ->
                state.updateExpense(updated, zone)
                editing = null
            }
        )
    }
}
@Composable
fun SettingsScreen(
    currentBudget: Double,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onBudgetChange: (Double) -> Unit
) {
    var budgetInput by rememberSaveable { mutableStateOf(currentBudget.toInt().toString()) }
    val isValid = budgetInput.toDoubleOrNull()?.let { it > 0 } == true

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
        Text("Monthly budget", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = budgetInput,
            onValueChange = { input ->
                // Only allow digits
                budgetInput = input.filter { ch -> ch.isDigit() }
            },
            label = { Text("Budget (CAD)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val value = budgetInput.toDoubleOrNull()
                if (value != null && value > 0) {
                    onBudgetChange(value)
                }
            },
            enabled = isValid
        ) {
            Text("Save")
        }
    }
}

@Composable
fun StatsScreen(
    state: ExpenseViewModel,
    month: YearMonth,
    zone: ZoneId,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onCategoryClick: (String) -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.CANADA) }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.CANADA) }
    val monthlyTotal = state.expensesForMonth().sumOf { it.amount }

    val totals by produceState(initialValue = emptyList<CategoryTotal>(), month, zone) {
        value = state.totalsByCategory(month, zone)
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
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Show previous month") }
            IconButton(
                onClick = { state.currentMonth = state.currentMonth.plusMonths(1) },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Show next month") }
        }

        Spacer(Modifier.height(8.dp))
        if (totals.isEmpty() || totals.sumOf { it.total } == 0.0) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No data for this month")
            }
        } else {
            PieChartWithLegend(
                totals = totals,
                onCategoryClick = onCategoryClick
            )
        }
    }
}

@Composable
fun PieChartWithLegend(totals: List<CategoryTotal>, onCategoryClick: (String) -> Unit) {
    val sum = totals.sumOf { it.total }.coerceAtLeast(0.000001)
    val currency = remember {
        NumberFormat.getCurrencyInstance(Locale.CANADA).apply {
            maximumFractionDigits = 0
        }
    }
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

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        totals.forEachIndexed { index, ct ->
            val percent = (ct.total / sum * 100).let { String.format(Locale.CANADA, "%.1f%%", it) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCategoryClick(ct.category) }
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
fun CategoryExpensesScreen(
    state: ExpenseViewModel,
    category: String,
    month: YearMonth,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onEdit: (Expense) -> Unit,
    onDelete: (String) -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM yyyy", Locale.CANADA) }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.CANADA) }
    val expenses = state.expensesForMonth().filter { it.category == category }
    val total = expenses.sumOf { it.amount }

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${month.format(formatter)} • $category • Total: ${currency.format(total)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
        ExpenseList(
            items = expenses,
            onEdit = onEdit,
            onDelete = onDelete
        )
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
                label = { Text("Amount (${currencyFormatter.currency?.currencyCode ?: "CAD"})") },
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
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
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
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
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
@Composable
fun MonthlyTotalsScreen(
    state: ExpenseViewModel,
    currentMonth: YearMonth,
    zone: ZoneId,
    budget: Double,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenSettings: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMM", Locale.CANADA) }
    val currency = remember {
        NumberFormat.getCurrencyInstance(Locale.CANADA).apply {
            maximumFractionDigits = 0
        }
    }
    val months = remember(currentMonth) {
        (5 downTo 0).map { currentMonth.minusMonths(it.toLong()) }
    }
    val monthlyTotals by produceState(
        initialValue = emptyList<Pair<YearMonth, Double>>(),
        months,
        zone
    ) {
        val list = months.map { m ->
            val total = state.monthlyTotal(m, zone)
            m to total
        }
        value = list
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
                text = "Last 6 months",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = { state.currentMonth = state.currentMonth.minusMonths(6) },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Show previous months"
                )
            }
            IconButton(
                onClick = { state.currentMonth = state.currentMonth.plusMonths(6) },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Show next months"
                )
            }
        }

        if (monthlyTotals.isEmpty() || monthlyTotals.all { it.second == 0.0 }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No data for these months")
            }
        } else {
            val safeMax = budget.coerceAtLeast(1.0)
            val axisCurrency = remember {
                NumberFormat.getCurrencyInstance(Locale.CANADA).apply {
                    maximumFractionDigits = 0
                }
            }
            val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

            Spacer(Modifier.height(8.dp))

            val chartHeight = 180.dp
            val axisWidth = 72.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
                verticalAlignment = Alignment.Bottom
            ) {
                val steps = 4
                Column(
                    modifier = Modifier
                        .width(axisWidth)
                        .fillMaxHeight()
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in steps downTo 0) {
                        val value = safeMax * i / steps
                        Text(
                            text = axisCurrency.format(value),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Bars area with light horizontal grid lines in the background
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Background grid
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val gridSteps = 4
                        for (i in 0..gridSteps) {
                            val y = size.height * (i / gridSteps.toFloat())
                            drawLine(
                                color = gridColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                    // Bars (only the rectangles)
                    Row(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        monthlyTotals.forEach { (_, total) ->
                            val fraction = (total / safeMax).toFloat().coerceIn(0f, 1f)
                            val barColor =
                                if (total > budget) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight(fraction)
                                        .width(18.dp)
                                        .background(
                                            barColor,
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Labels row: month name and total
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(
                    modifier = Modifier
                        .width(axisWidth + 8.dp)
                )
                monthlyTotals.forEach { (month, total) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = month.format(formatter),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = currency.format(total),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(onClick = onOpenSettings) {
                    Text("Settings")
                }
            }
        }
    }
}