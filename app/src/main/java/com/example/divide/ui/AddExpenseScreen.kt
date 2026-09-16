package com.example.divide.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.divide.data.Expense
import com.example.divide.data.LocalRepository

/**
 * Screen used to add an individual expense (e.g. "Lunch at Hotel")
 * to a specific trip/event.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    eventId: String,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    // Retrieve the trip container from the repository using the ID
    val event = LocalRepository.getEvent(eventId) ?: return
    
    // UI State for the form fields
    var expenseName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var paidBy by remember { mutableStateOf(event.users.first()) }
    var sharedBy by remember { mutableStateOf(event.users) }
    
    // Controls for dropdown menus
    var paidByExpanded by remember { mutableStateOf(false) }
    var sharedByExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Expense") },
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
            // Field 1: Name of the individual expense
            Text("Expense Name")
            TextField(
                value = expenseName,
                onValueChange = { expenseName = it },
                placeholder = { Text("e.g. Lunch at hotel") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Field 2: Amount entry with numeric keyboard
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Enter Amount", modifier = Modifier.weight(1f))
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(150.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Field 3: Dropdown to select who paid for this expense
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

            Spacer(modifier = Modifier.height(16.dp))

            // Field 4: Multi-select dropdown to choose who shares this cost
            // By default, it includes all participants of the trip.
            Box {
                OutlinedButton(onClick = { sharedByExpanded = true }) {
                    Text("To be shared by: ${if (sharedBy.size == event.users.size) "All" else "${sharedBy.size} users"}")
                }
                DropdownMenu(expanded = sharedByExpanded, onDismissRequest = { sharedByExpanded = false }) {
                    event.users.forEach { user ->
                        val isSelected = sharedBy.contains(user)
                        DropdownMenuItem(
                            text = { 
                                Row {
                                    Checkbox(checked = isSelected, onCheckedChange = null)
                                    Text(user.name)
                                }
                            },
                            onClick = {
                                // Toggle user selection
                                sharedBy = if (isSelected) {
                                    sharedBy - user
                                } else {
                                    sharedBy + user
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action: Save the expense to the trip and go back to event details
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull() ?: 0.0
                    if (expenseName.isNotBlank() && amountDouble > 0 && sharedBy.isNotEmpty()) {
                        val newExpense = Expense(
                            name = expenseName,
                            amount = amountDouble,
                            payerId = paidBy.id,
                            participantIds = sharedBy.map { it.id }
                        )
                        // Update the event in the repository by adding this new expense
                        LocalRepository.updateEvent(event.copy(expenses = event.expenses + newExpense))
                        onSave()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Expense")
            }
        }
    }
}
