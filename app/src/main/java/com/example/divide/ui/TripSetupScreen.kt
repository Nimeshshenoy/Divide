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
import com.example.divide.data.LocalRepository
import com.example.divide.data.Trip
import com.example.divide.data.User
import kotlinx.coroutines.launch

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
    // Collect the list of trips reactively from the repository.
    val trips by LocalRepository.trips.collectAsState()
    // Observe the profile for the local user's name
    val profile by LocalRepository.profile.collectAsState()
    
    // Find the specific trip we are editing, if an ID was provided.
    val existingTrip = remember(tripId, trips) { tripId?.let { id -> trips.find { it.id == id } } }

    var tripName by remember(existingTrip) { mutableStateOf(existingTrip?.name ?: "") }
    var currentUsers by remember(existingTrip, profile) { 
        mutableStateOf(
            existingTrip?.users ?: listOf(User(name = profile.name, isLocalPlayer = true))
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
            // Header: Trip name entry
            TextField(
                value = tripName,
                onValueChange = { tripName = it },
                label = { Text("Trip Name (e.g. Udaipur Trip)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User Slots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                repeat(4) { index ->
                    val user = currentUsers.getOrNull(index + 1)
                    UserSlot(user = user) {
                        if (currentUsers.size < 5) {
                            currentUsers = currentUsers + User(name = "Friend ${currentUsers.size}")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Expenses Section
            Text(
                text = "Expenses",
                style = MaterialTheme.typography.titleLarge
            )
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(existingTrip?.expenses ?: emptyList()) { expense ->
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

            // Footer
            Button(
                onClick = {
                    if (tripName.isNotBlank() && currentUsers.size > 1) {
                        val finalTripId = if (tripId == null) {
                            val newTrip = Trip(name = tripName, users = currentUsers)
                            LocalRepository.addTrip(newTrip)
                            newTrip.id
                        } else {
                            val updatedTrip = existingTrip!!.copy(name = tripName, users = currentUsers)
                            LocalRepository.updateTrip(updatedTrip)
                            tripId
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
