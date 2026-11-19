package com.example.divide.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.divide.data.Expense
import com.example.divide.data.LocalRepository
import com.example.divide.sync.nearby.rememberNearbyPermissionsLauncher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen used to add/edit individual expenses within a specific trip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    tripId: String,
    syncViewModel: SyncViewModel,
    onBack: () -> Unit
) {
    // Observe the list of trips (with details) and current profile
    val trips by LocalRepository.trips.collectAsState()
    val profile by LocalRepository.profile.collectAsState()
    
    // Find the specific trip we are currently viewing
    val tripDetails = trips.find { it.group.id == tripId } ?: return
    val group = tripDetails.group
    
    // UI State for form fields
    var expenseName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    
    // Adjust local player's name and upiId based on current profile
    val displayUsers = remember(tripDetails.users, profile) {
        tripDetails.users.map { if (it.isLocalPlayer) it.copy(name = profile.name, upiId = profile.upiId) else it }
    }

    var paidBy by remember(displayUsers) { mutableStateOf(displayUsers.firstOrNull() ?: displayUsers.first()) }
    var sharedBy by remember(displayUsers) { mutableStateOf(displayUsers) }
    
    var editingExpenseId by remember { mutableStateOf<String?>(null) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    
    val focusManager = LocalFocusManager.current
    var paidByExpanded by remember { mutableStateOf(false) }
    var sharedByExpanded by remember { mutableStateOf(false) }
    
    val dateFormatter = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }

    val syncState by syncViewModel.syncState.collectAsState()
    var showSyncDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberNearbyPermissionsLauncher(
        onPermissionsGranted = { showSyncDialog = true }
    )

    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            title = { Text("Sync Trip") },
            text = { Text("Do you want to host this trip for others to join, or join an existing host?") },
            confirmButton = {
                TextButton(onClick = {
                    syncViewModel.startHosting(tripId)
                    showSyncDialog = false
                }) { Text("Host") }
            },
            dismissButton = {
                TextButton(onClick = {
                    syncViewModel.startJoining(tripId)
                    showSyncDialog = false
                }) { Text("Join") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${group.name} Expenses") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    val tint = if (syncState == SyncState.CONNECTED) Color.Green else LocalContentColor.current
                    TextButton(onClick = {
                        if (syncState == SyncState.IDLE) {
                            permissionLauncher()
                        } else {
                            syncViewModel.stopSync()
                        }
                    }) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (syncState == SyncState.CONNECTED) Color.Green else Color.Red,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (syncState == SyncState.IDLE) Icons.Default.Sync else Icons.Default.Close,
                            contentDescription = "Sync",
                            tint = tint,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (syncState) {
                                SyncState.IDLE -> "Sync"
                                SyncState.ADVERTISING -> "Hosting"
                                SyncState.DISCOVERING -> "Searching"
                                SyncState.CONNECTED -> "Connected"
                            },
                            color = tint
                        )
                    }
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
            // Expense input field
            Text(if (editingExpenseId == null) "Expense Name" else "Editing Expense (Name cannot be changed)")
            TextField(
                value = expenseName,
                onValueChange = { expenseName = it },
                placeholder = { Text("e.g. Lunch at hotel") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = editingExpenseId != null,
                enabled = editingExpenseId == null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Amount input field
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Enter Amount", modifier = Modifier.weight(1f))
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.width(150.dp),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dropdowns for Payer and Participants
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box {
                    OutlinedButton(onClick = { paidByExpanded = true }) {
                        Text("Paid by: ${paidBy.name}")
                    }
                    DropdownMenu(expanded = paidByExpanded, onDismissRequest = { paidByExpanded = false }) {
                        displayUsers.forEach { user ->
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

                Box {
                    OutlinedButton(onClick = { sharedByExpanded = true }) {
                        Text("To be shared by: ${if (sharedBy.size == displayUsers.size) "All" else "${sharedBy.size} users"}")
                    }
                    DropdownMenu(expanded = sharedByExpanded, onDismissRequest = { sharedByExpanded = false }) {
                        displayUsers.forEach { user ->
                            val isSelected = sharedBy.any { it.id == user.id }
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = isSelected, onCheckedChange = null)
                                        Text(user.name)
                                    }
                                },
                                onClick = {
                                    sharedBy = if (isSelected) sharedBy.filterNot { it.id == user.id } else sharedBy + user
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action: Save Expense (Adds data to DB and logs SyncEvent)
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull() ?: 0.0
                    if (expenseName.isNotBlank() && amountDouble > 0 && sharedBy.isNotEmpty()) {
                        if (editingExpenseId != null) {
                            val originalExpense = tripDetails.expenses.find { it.id == editingExpenseId }
                            originalExpense?.let {
                                val updatedExpense = it.copy(
                                    amount = amountDouble,
                                    payerId = paidBy.id,
                                    participantIds = sharedBy.map { u -> u.id }
                                )
                                LocalRepository.updateExpense(updatedExpense)
                            }
                        } else {
                            val newExpense = Expense(
                                groupId = tripId,
                                name = expenseName,
                                amount = amountDouble,
                                payerId = paidBy.id,
                                participantIds = sharedBy.map { it.id }
                            )
                            LocalRepository.addExpense(newExpense)
                        }
                        
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

            Text(
                text = "expenses",
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // List of added expenses (Active only) with Edit/Delete options
            val activeExpenses = remember(tripDetails.expenses) {
                tripDetails.expenses.filter { !it.isDeleted }
            }
            
            var expandedExpenseId by remember { mutableStateOf<String?>(null) }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(activeExpenses) { expense ->
                    val isExpanded = expandedExpenseId == expense.id

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                expandedExpenseId = if (isExpanded) null else expense.id
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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

                                if (isExpanded) {
                                    val payerName = displayUsers.find { it.id == expense.payerId }?.name ?: "Unknown"
                                    val sharedNames = displayUsers
                                        .filter { expense.participantIds.contains(it.id) }
                                        .joinToString(", ") { it.name }
                                        .ifEmpty { "None" }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "paid by: $payerName",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "shared by: $sharedNames",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = {
                                editingExpenseId = expense.id
                                expenseName = expense.name
                                amount = expense.amount.toString()
                                paidBy = displayUsers.find { it.id == expense.payerId } ?: displayUsers.first()
                                sharedBy = displayUsers.filter { expense.participantIds.contains(it.id) }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Expense")
                            }

                            IconButton(onClick = { expenseToDelete = expense }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Expense", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // Deletion confirmation dialog
    expenseToDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete Expense") },
            text = { Text("You are about to delete this expense (${expense.name}), are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        LocalRepository.deleteExpense(expense)
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
