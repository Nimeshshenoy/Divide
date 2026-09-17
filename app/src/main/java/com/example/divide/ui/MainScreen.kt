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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.divide.data.ExpenseEvent
import com.example.divide.data.LocalRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The main landing screen of the app.
 * Displays a large button to create a new event and lists existing events
 * categorized by their settlement status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onCreateEvent: () -> Unit,
    onEventClick: (String) -> Unit,
    onProfileClick: () -> Unit
) {
    // Observe the list of events from our repository
    val events by LocalRepository.events.collectAsState()
    
    // Separate events into Unsettled and Settled categories
    val unsettled = events.filter { !it.isSettled }
    val settled = events.filter { !unsettled.contains(it) } // Simplified for logic check

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

            // Large, centered "Create expense event" button as per the mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .border(2.dp, Color.Black)
                    .clickable { onCreateEvent() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Create expense event",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Section for Unsettled Expenses
                item {
                    SectionHeader("unsettled expenses")
                }
                items(unsettled) { event ->
                    EventItem(event) { onEventClick(event.id) }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }

                // Section for Settled Expenses
                item {
                    SectionHeader("settled expenses")
                }
                items(settled) { event ->
                    EventItem(event) { onEventClick(event.id) }
                }
            }
        }
    }
}

/**
 * A styled header with an underline as seen in the user's design image.
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
 * Represents a single event card in the list.
 * Shows the event name and the total sum of all expenses within that event.
 */
@Composable
fun EventItem(event: ExpenseEvent, onClick: () -> Unit) {
    // Calculate the total cost of this specific trip/event
    val totalAmount = event.expenses.sumOf { it.amount }
    
    // Formatting the creation date
    val dateFormatter = remember { SimpleDateFormat("dd/MM/yy", Locale.getDefault()) }
    val formattedDate = dateFormatter.format(Date(event.createdAt))
    
    // Identifying the creator (first user in the list, usually the local player)
    val creatorName = event.users.firstOrNull { it.isLocalPlayer }?.name 
        ?: event.users.firstOrNull()?.name 
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
                Text(text = event.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "created on $formattedDate by $creatorName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Displaying the total amount for the event
            Text(
                text = "₹${"%.2f".format(totalAmount)}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
