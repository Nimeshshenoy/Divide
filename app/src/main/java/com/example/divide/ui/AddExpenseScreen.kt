package com.example.divide.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.divide.data.Expense
import com.example.divide.data.LocalRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen used to add an individual expense (e.g. "Lunch at Hotel")
 * to a specific trip/event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    eventId: String,
    onBack: () -> Unit
) {
    // Collect the list of events and find the one we are interested in.
    val events by LocalRepository.events.collectAsState()
    val profile by LocalRepository.profile.collectAsState()
    val event = events.find { it.id == eventId } ?: return
    
    // UI State for the form fields
    var expenseName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    
    // Map users to show the profile name for the local player
    val displayUsers = remember(event.users, profile) {
        event.users.map { if (it.isLocalPlayer) it.copy(name = profile.name) else it }
    }

    var paidBy by remember(displayUsers) { mutableStateOf(displayUsers.first()) }
    var sharedBy by remember(displayUsers) { mutableStateOf(displayUsers) }
    
    // Tracking if we are editing an existing expense
    var editingExpenseId by remember { mutableStateOf<String?>(null) }
    
    val focusManager = LocalFocusManager.current

    // Controls for dropdown menus
    var paidByExpanded by remember { mutableStateOf(false) }
    var sharedByExpanded by remember { mutableStateOf(false) }
    
    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${event.name} Expenses") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Form Section
            Text(if (editingExpenseId == null) "Expense Name" else "Edit Expense Name")
            TextField(
                value = expenseName,
                onValueChange = { expenseName = it },
                placeholder = { Text("e.g. Lunch at hotel") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true, // Requirement 3: Disable enter expanding the box
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Enter Amount", modifier = Modifier.weight(1f))
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    modifier = Modifier.width(150.dp),
                    singleLine = true // Requirement 3: Disable enter expanding the box
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Paid By Dropdown
                Box {
                    OutlinedButton(onClick = { paidByExpanded = true }) {
                        Text("Paid by: ${paidBy.name}")
                    }
                    DropdownMenu(expanded = paidByExpanded, onDismissRequest = { paidByExpanded = false }) {
                        event.users.forEach { user ->
                            DropdownMenuItem(
                                text = { Text(user.name) },
                                onClick = {
                                    paidBy = user
                                    paidByExpanded = false
                                }
                            )
                        }
                    }
                }

                // Shared By Dropdown
                Box {
                    OutlinedButton(onClick = { sharedByExpanded = true }) {
                        Text("To be shared by: ${if (sharedBy.size == event.users.size) "All" else "${sharedBy.size} users"}")
                    }
                    DropdownMenu(expanded = sharedByExpanded, onDismissRequest = { sharedByExpanded = false }) {
                        event.users.forEach { user ->
                            val isSelected = sharedBy.contains(user)
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = isSelected, onCheckedChange = null)
                                        Text(user.name)
                                    }
                                },
                                onClick = {
                                    sharedBy = if (isSelected) sharedBy - user else sharedBy + user
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action: Save or Update the expense
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull() ?: 0.0
                    if (expenseName.isNotBlank() && amountDouble > 0 && sharedBy.isNotEmpty()) {
                        val newExpenses = if (editingExpenseId != null) {
                            // Update existing expense
                            event.expenses.map {
                                if (it.id == editingExpenseId) {
                                    it.copy(
                                        name = expenseName,
                                        amount = amountDouble,
                                        payerId = paidBy.id,
                                        participantIds = sharedBy.map { id -> id.id }
                                    )
                                } else it
                            }
                        } else {
                            // Add new expense
                            event.expenses + Expense(
                                name = expenseName,
                                amount = amountDouble,
                                payerId = paidBy.id,
                                participantIds = sharedBy.map { it.id }
                            )
                        }
                        
                        LocalRepository.updateEvent(event.copy(expenses = newExpenses))
                        
                        // Clear fields and reset editing state
                        expenseName = ""
                        amount = ""
                        editingExpenseId = null
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (editingExpenseId == null) "Save Expense" else "Update Expense")
            }

            if (editingExpenseId != null) {
                TextButton(
                    onClick = {
                        editingExpenseId = null
                        expenseName = ""
                        amount = ""
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Cancel Edit")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Requirement: "expenses" section with underline
            Text(
                text = "expenses",
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Requirement: Show added expenses with an Edit button
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(event.expenses) { expense ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = expense.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = dateFormatter.format(Date(expense.timestamp)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(text = "₹${"%.2f".format(expense.amount)}", fontWeight = FontWeight.Bold)
                            }
                            // Requirement 4: Add an edit button in each entry
                            IconButton(onClick = {
                                editingExpenseId = expense.id
                                expenseName = expense.name
                                amount = expense.amount.toString()
                                paidBy = displayUsers.find { it.id == expense.payerId } ?: displayUsers.first()
                                sharedBy = displayUsers.filter { expense.participantIds.contains(it.id) }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Expense")
                            }
                        }
                    }
                }
            }
        }
    }
}
