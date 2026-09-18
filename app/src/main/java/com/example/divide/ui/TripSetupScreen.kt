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
import com.example.divide.data.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Screen used to setup a new trip or edit trip participants.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripSetupScreen(
    tripId: String? = null,
    onSaveAndAddExpense: (String) -> Unit,
    onBack: () -> Unit
) {
    // Observe all trips and current profile from the centralized repository
    val trips by LocalRepository.trips.collectAsState()
    val profile by LocalRepository.profile.collectAsState()
    
    // Find the specific trip details if editing
    val existingTripDetails = remember(tripId, trips) { tripId?.let { id -> trips.find { it.group.id == id } } }

    var tripName by remember(existingTripDetails) { mutableStateOf(existingTripDetails?.group?.name ?: "") }
    
    // Local state for the temporary users list
    var currentParticipants by remember(existingTripDetails, profile) { 
        mutableStateOf(
            existingTripDetails?.users ?: listOf(
                User(
                    id = UUID.randomUUID().toString(),
                    name = profile.name,
                    groupId = tripId ?: "pending", // Will be updated on save if new
                    isLocalPlayer = true
                )
            )
        ) 
    }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tripId == null) "Setup Trip" else "Trip Details") },
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
            // Field for the Trip/Group name
            TextField(
                value = tripName,
                onValueChange = { tripName = it },
                label = { Text("Trip Name (e.g. Udaipur Trip)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Participant selection: 4 slots with + buttons to add people
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(4) { index ->
                    // Index 0 is the creator, slots are for index 1, 2, 3, 4
                    val user = currentParticipants.getOrNull(index + 1)
                    UserSlot(user = user) {
                        if (currentParticipants.size < 5) {
                            currentParticipants = currentParticipants + User(
                                id = UUID.randomUUID().toString(),
                                name = "Friend ${currentParticipants.size}",
                                groupId = tripId ?: "pending"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Show existing expenses if viewing an existing trip
            Text(
                text = "Expenses",
                style = MaterialTheme.typography.titleLarge
            )
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(existingTripDetails?.expenses ?: emptyList()) { expense ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = expense.name)
                            Text(text = "₹${expense.amount}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Save Action: Writes to normalized tables and logs SyncEvent
            Button(
                onClick = {
                    if (tripName.isNotBlank() && currentParticipants.size > 1) {
                        val finalTripId = tripId ?: UUID.randomUUID().toString()
                        
                        // Prep the Group and Users with the final ID
                        val group = Group(id = finalTripId, name = tripName)
                        val usersToSave = currentParticipants.map { it.copy(groupId = finalTripId) }
                        
                        if (tripId == null) {
                            LocalRepository.addTrip(group, usersToSave)
                        } else {
                            // Logic for updating group name/users can be added here
                        }
                        
                        onSaveAndAddExpense(finalTripId)
                    } else {
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
 * A square slot that displays a user's name or an Add icon.
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
