package com.example.divide.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.divide.data.ExpenseEvent
import com.example.divide.data.LocalRepository
import com.example.divide.data.User
import kotlinx.coroutines.launch

/**
 * Screen used to create a new "Trip" or "Event" container, or view an existing one.
 * It allows defining the trip name and participants.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    eventId: String? = null, // Optional: if provided, we are editing/viewing an existing trip
    onSaveAndAddExpense: (String) -> Unit,
    onBack: () -> Unit
) {
    // Collect the list of events reactively from the repository.
    val events by LocalRepository.events.collectAsState()
    // Observe the profile for the local user's name
    val profile by LocalRepository.profile.collectAsState()
    
    // Find the specific event we are editing, if an ID was provided.
    val existingEvent = remember(eventId, events) { eventId?.let { id -> events.find { it.id == id } } }

    var eventName by remember(existingEvent) { mutableStateOf(existingEvent?.name ?: "") }
    var currentUsers by remember(existingEvent, profile) { 
        mutableStateOf(
            existingEvent?.users ?: listOf(User(name = profile.name, isLocalPlayer = true))
        ) 
    }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (eventId == null) "Create Event" else "Event Details") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Header: Trip name entry
            TextField(
                value = eventName,
                onValueChange = { eventName = it },
                label = { Text("Event Name (e.g. Udaipur Trip)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User Slots: 4 squares with + inside to add users via BT/Wi-Fi (simulated)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(4) { index ->
                    // We show the users starting from the second slot (index 0 is usually 'Me')
                    val user = currentUsers.getOrNull(index + 1)
                    UserSlot(user = user) {
                        if (currentUsers.size < 5) {
                            currentUsers = currentUsers + User(name = "Friend ${currentUsers.size}")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Expenses Section: Shows the list of individual costs added to this trip


            // Footer: The primary action button
            Button(
                onClick = {
                    // Logic check: User must provide a name and add at least one friend
                    if (eventName.isNotBlank() && currentUsers.size > 1) {
                        val finalEventId = if (eventId == null) {
                            // Creating a new Trip
                            val newEvent = ExpenseEvent(name = eventName, users = currentUsers)
                            LocalRepository.addEvent(newEvent)
                            newEvent.id
                        } else {
                            // Updating existing Trip metadata (like name or users)
                            val updatedEvent = existingEvent!!.copy(name = eventName, users = currentUsers)
                            LocalRepository.updateEvent(updatedEvent)
                            eventId
                        }
                        // Navigate to add a specific expense (like lunch) inside this trip
                        onSaveAndAddExpense(finalEventId)
                    } else {
                        // Warning message if requirements are not met
                        scope.launch {
                            snackbarHostState.showSnackbar("please add atleast 1 user and save the name to divide the expenses")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save and Add Expenses")
            }
        }
    }
}

/**
 * Square slot for participant visualization.
 */
@Composable
fun UserSlot(user: User?, onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .size(70.dp)
            .border(1.dp, Color.Gray)
            .clickable(enabled = user == null) { onAdd() },
        contentAlignment = Alignment.Center
    ) {
        if (user != null) {
            Text(text = user.name, style = MaterialTheme.typography.bodySmall)
        } else {
            Icon(Icons.Default.Add, contentDescription = "Add User")
        }
    }
}
