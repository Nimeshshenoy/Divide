package com.example.divide.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
    syncViewModel: SyncViewModel? = null,
    onSaveAndAddExpense: (String) -> Unit,
    onBack: () -> Unit
) {
    // Observe all trips and current profile from the centralized repository
    val trips by LocalRepository.trips.collectAsState()
    val profile by LocalRepository.profile.collectAsState()
    
    // Keep one real trip ID for discovery and the eventual save.
    val draftTripId = remember(tripId) { tripId ?: UUID.randomUUID().toString() }

    val existingTripDetails = remember(tripId, trips) { tripId?.let { id -> trips.find { it.group.id == id } } }

    var tripName by remember(existingTripDetails) { mutableStateOf(existingTripDetails?.group?.name ?: "") }
    
    // Local state for the temporary users list
    var currentParticipants by remember(existingTripDetails, profile) {
        mutableStateOf(
            existingTripDetails?.users ?: listOf(
                User(
                    id = profile.userId,
                    name = profile.name,
                    groupId = draftTripId,
                    isLocalPlayer = true,
                    upiId = profile.upiId
                )
            )
        )
    }
    
    var showAddParticipantDialog by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var newParticipantName by remember { mutableStateOf("") }
    var nearbySearchRequested by remember { mutableStateOf(false) }
    val syncState = syncViewModel?.syncState?.collectAsState()?.value ?: SyncState.IDLE
    val discoveredUsers = syncViewModel?.discoveredUsers?.collectAsState()?.value ?: emptyList()
    val syncError = syncViewModel?.syncError?.collectAsState()?.value

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Participants", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${currentParticipants.size} people",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        newParticipantName = ""
                        showManualEntry = false
                        showAddParticipantDialog = true
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add people")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                items(currentParticipants, key = { it.id }) { participant ->
                    ListItem(
                        headlineContent = { Text(participant.name) },
                        supportingContent = {
                            Text(if (participant.isLocalPlayer) "You" else "Trip participant")
                        },
                        trailingContent = {
                            if (!participant.isLocalPlayer) {
                                IconButton(onClick = {
                                    currentParticipants = currentParticipants.filterNot { it.id == participant.id }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove participant")
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Show existing expenses if viewing an existing trip
            Text(
                text = "Expenses",
                style = MaterialTheme.typography.titleLarge
            )
            
            val activeExpenses = remember(existingTripDetails?.expenses) {
                existingTripDetails?.expenses?.filter { !it.isDeleted } ?: emptyList()
            }
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(activeExpenses) { expense ->
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
                    if (tripName.isNotBlank() && currentParticipants.isNotEmpty()) {
                        val group = (existingTripDetails?.group ?: Group(id = draftTripId, name = tripName))
                            .copy(name = tripName)
                        val usersToSave = currentParticipants.map { it.copy(groupId = draftTripId) }

                        if (tripId == null) {
                            LocalRepository.addTrip(group, usersToSave)
                        } else {
                            LocalRepository.updateTrip(group, usersToSave)
                        }

                        onSaveAndAddExpense(draftTripId)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please enter a trip name to save.")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save and Add Expenses")
            }
        }
    }

    // Nearby discovery and manual entry for adding a participant
    if (showAddParticipantDialog) {
        val permissionLauncher = com.example.divide.sync.nearby.rememberNearbyPermissionsLauncher(
            onPermissionsGranted = { syncViewModel?.startSearchingForFriends(draftTripId) }
        )

        LaunchedEffect(nearbySearchRequested) {
            if (nearbySearchRequested) permissionLauncher()
        }

        AlertDialog(
            onDismissRequest = {
                syncViewModel?.stopSync()
                showAddParticipantDialog = false 
            },
            title = { Text("Add Participant") },
            text = {
                Column {
                    Text(
                        "Find and connect to your friend's device to add them to this trip.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    if (showManualEntry) {
                        OutlinedTextField(
                            value = newParticipantName,
                            onValueChange = { newParticipantName = it },
                            label = { Text("Participant name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (syncState == SyncState.DISCOVERING || syncState == SyncState.CONNECTING) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (syncState == SyncState.CONNECTING) "Connecting to device..." else "Searching nearby...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        discoveredUsers.forEach { peer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(peer.endpointName)
                                TextButton(onClick = { syncViewModel?.inviteFriend(peer.endpointId) }) {
                                    Text("Connect")
                                }
                            }
                        }
                    } else if (syncState == SyncState.IDLE || syncState == SyncState.ERROR) {
                        OutlinedButton(
                            onClick = { nearbySearchRequested = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (syncState == SyncState.ERROR) "Retry nearby search" else "Find nearby devices")
                        }
                    }

                    if (!showManualEntry && syncState !in listOf(SyncState.DISCOVERING, SyncState.CONNECTING, SyncState.CONNECTED)) {
                        TextButton(onClick = { showManualEntry = true }) {
                            Text("Add manually instead")
                        }
                    }

                    if (showManualEntry) {
                        TextButton(onClick = { showManualEntry = false }) {
                            Text("Use nearby device")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameTrimmed = newParticipantName.trim()
                        if (nameTrimmed.isNotBlank()) {
                            currentParticipants = currentParticipants + User(
                                id = UUID.randomUUID().toString(),
                                name = nameTrimmed,
                                groupId = draftTripId
                            )
                        }
                        syncViewModel?.stopSync()
                        showAddParticipantDialog = false
                        nearbySearchRequested = false
                    },
                    enabled = showManualEntry && newParticipantName.trim().isNotBlank()
                ) {
                    Text("Add participant")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    syncViewModel?.stopSync()
                    showAddParticipantDialog = false 
                }) {
                    Text("Cancel")
                }
            }
        )
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
