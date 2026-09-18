package com.example.divide.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.divide.data.LocalRepository
import com.example.divide.data.TripWithDetails
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The main landing screen of the app.
 * Displays a large button to create a new trip and lists existing trips
 * categorized by their settlement status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onCreateTrip: () -> Unit,
    onTripClick: (String) -> Unit,
    onProfileClick: () -> Unit
) {
    // Observe the list of trips (with their nested details) from our repository
    val trips by LocalRepository.trips.collectAsState()
    
    // Separate trips into Unsettled and Settled categories based on the 'group' metadata
    val unsettled = trips.filter { !it.group.isSettled }
    val settled = trips.filter { it.group.isSettled }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Divide") },
                actions = {
                    IconButton(onClick = onProfileClick) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Large, centered button to start a new trip setup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .border(2.dp, Color.Black)
                    .clickable { onCreateTrip() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Create expense trip",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Section for trips where payments are still pending
                item {
                    SectionHeader("unsettled expenses")
                }
                items(unsettled) { tripDetails ->
                    TripItem(tripDetails) { onTripClick(tripDetails.group.id) }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }

                // Section for trips that have been fully paid off
                item {
                    SectionHeader("settled expenses")
                }
                items(settled) { tripDetails ->
                    TripItem(tripDetails) { onTripClick(tripDetails.group.id) }
                }
            }
        }
    }
}

/**
 * A styled header with an underline.
 */
@Composable
fun SectionHeader(text: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline
        )
    }
}

/**
 * Represents a single trip card in the list.
 * Shows the trip name, total accumulated cost, and creation info.
 */
@Composable
fun TripItem(tripDetails: TripWithDetails, onClick: () -> Unit) {
    val group = tripDetails.group
    
    // Calculate the sum of all ACTIVE (non-deleted) individual expenses belonging to this trip
    val totalAmount = tripDetails.expenses.filter { !it.isDeleted }.sumOf { it.amount }
    
    // UI formatting for the date
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }
    val formattedDate = dateFormatter.format(Date(group.createdAt))
    
    // Show who created the trip (identifying by local player flag)
    val creatorName = tripDetails.users.firstOrNull { it.isLocalPlayer }?.name 
        ?: tripDetails.users.firstOrNull()?.name 
        ?: "Unknown"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = group.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "created on $formattedDate by $creatorName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Displays the aggregate total for the trip
            Text(
                text = "₹${"%.2f".format(totalAmount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
