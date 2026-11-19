package com.example.divide.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.divide.data.LocalRepository
import com.example.divide.data.TripWithDetails
import com.example.divide.data.User
import com.example.divide.logic.SplitLogic
import com.example.divide.logic.UserSettlementStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The main landing screen of the app.
 * Displays a large button to create a new trip and lists existing trips
 * categorized by their settlement status along with local settlement details and UPI payments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    syncViewModel: SyncViewModel? = null,
    onCreateTrip: () -> Unit,
    onTripClick: (String) -> Unit,
    onProfileClick: () -> Unit
) {
    // Observe the list of trips (with their nested details) from our repository
    val trips by LocalRepository.trips.collectAsState()
    
    // Separate trips into Unsettled and Settled categories based on the 'group' metadata
    val unsettled = trips.filter { !it.group.isSettled }
    val settled = trips.filter { it.group.isSettled }

    // Dialog State for Payments
    var activePayUser by remember { mutableStateOf<Pair<User, Double>?>(null) }
    var missingUpiUser by remember { mutableStateOf<User?>(null) }

    // Observe Sync ViewModel State for Live Payment Info Requests
    val syncState = syncViewModel?.syncState?.collectAsState()?.value ?: SyncState.IDLE
    val paymentInfoState = syncViewModel?.paymentInfoState?.collectAsState()?.value ?: PaymentInfoState.Idle

    // Handle incoming live payment info response
    LaunchedEffect(paymentInfoState) {
        val state = paymentInfoState
        if (state is PaymentInfoState.Success) {
            val payload = state.payload
            if (!payload.upiId.isNullOrBlank()) {
                activePayUser = User(
                    id = payload.payeeUserId,
                    name = payload.payeeName,
                    groupId = payload.groupId,
                    upiId = payload.upiId
                ) to payload.amountRupees
            } else {
                missingUpiUser = User(
                    id = payload.payeeUserId,
                    name = payload.payeeName,
                    groupId = payload.groupId
                )
            }
            syncViewModel?.clearPaymentInfoState()
        } else if (state is PaymentInfoState.Error) {
            syncViewModel?.clearPaymentInfoState()
        }
    }

    val handlePayClick: (User, Double) -> Unit = { user, amount ->
        val isConnected = syncState == SyncState.CONNECTED
        var liveRequested = false

        if (isConnected && syncViewModel != null) {
            liveRequested = syncViewModel.requestLivePaymentInfo(
                targetPayeeUserId = user.id,
                amountRupees = amount
            )
        }

        // Offline / disconnected fallback
        if (!liveRequested) {
            if (!user.upiId.isNullOrBlank()) {
                activePayUser = user to amount
            } else {
                missingUpiUser = user
            }
        }
    }

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
                    val settlements = remember(tripDetails) {
                        SplitLogic.getLocalUserSettlements(tripDetails)
                    }
                    TripItem(
                        tripDetails = tripDetails,
                        settlements = settlements,
                        onClick = { onTripClick(tripDetails.group.id) },
                        onPayClick = handlePayClick
                    )
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }

                // Section for trips that have been fully paid off
                item {
                    SectionHeader("settled expenses")
                }
                items(settled) { tripDetails ->
                    val settlements = remember(tripDetails) {
                        SplitLogic.getLocalUserSettlements(tripDetails)
                    }
                    TripItem(
                        tripDetails = tripDetails,
                        settlements = settlements,
                        onClick = { onTripClick(tripDetails.group.id) },
                        onPayClick = handlePayClick
                    )
                }
            }
        }
    }

    // Payment Dialog with Generated QR Code
    activePayUser?.let { (toUser, amount) ->
        val upiUri = remember(toUser, amount) {
            QrCodeUtils.createUpiUri(toUser.upiId ?: "", toUser.name, amount)
        }
        val qrBitmap = remember(upiUri) {
            QrCodeUtils.generateQrCodeBitmap(upiUri, 512)
        }
        val context = LocalContext.current

        AlertDialog(
            onDismissRequest = { activePayUser = null },
            title = { Text("Pay ${toUser.name}") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Amount: ₹${"%.2f".format(amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = "UPI Payment QR Code",
                            modifier = Modifier.size(220.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "UPI ID: ${toUser.upiId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                            context.startActivity(Intent.createChooser(intent, "Pay via UPI"))
                        } catch (e: Exception) {
                            // Handled gracefully if no UPI app installed
                        }
                    }
                ) {
                    Text("Open UPI App")
                }
            },
            dismissButton = {
                TextButton(onClick = { activePayUser = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Missing UPI ID Dialog
    missingUpiUser?.let { user ->
        AlertDialog(
            onDismissRequest = { missingUpiUser = null },
            title = { Text("No UPI ID Set") },
            text = { Text("${user.name} has not set up their UPI payment ID yet.") },
            confirmButton = {
                TextButton(onClick = { missingUpiUser = null }) {
                    Text("OK")
                }
            }
        )
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
 * Shows the trip name, total cost, creation info, settlement breakdown, and a Pay button.
 */
@Composable
fun TripItem(
    tripDetails: TripWithDetails,
    settlements: List<UserSettlementStatus>,
    onClick: () -> Unit,
    onPayClick: (toUser: User, amount: Double) -> Unit
) {
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
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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

            Spacer(modifier = Modifier.height(12.dp))

            // Settlements breakdown sub-section
            Column(modifier = Modifier.fillMaxWidth()) {
                settlements.forEach { settlement ->
                    when (settlement) {
                        is UserSettlementStatus.Owes -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "You owe ${settlement.toUser.name} ₹${"%.2f".format(settlement.amountRupees)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { onPayClick(settlement.toUser, settlement.amountRupees) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Pay ${settlement.toUser.name}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        is UserSettlementStatus.Owed -> {
                            Text(
                                text = "${settlement.fromUser.name} owes You ₹${"%.2f".format(settlement.amountRupees)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        is UserSettlementStatus.SettledUp -> {
                            Text(
                                text = "You are settled up",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
