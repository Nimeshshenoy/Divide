package com.example.divide.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
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
    // Observe the list of trips and profile from our repository
    val trips by LocalRepository.trips.collectAsState()
    val profile by LocalRepository.profile.collectAsState()
    
    // Separate trips into Unsettled and Settled categories based on the 'group' metadata
    val unsettled = trips.filter { !it.group.isSettled }
    val settled = trips.filter { it.group.isSettled }

    // Dialog State for Payments & Nearby Sync
    var activePayUser by remember { mutableStateOf<Pair<User, Double>?>(null) }
    var missingUpiUser by remember { mutableStateOf<User?>(null) }
    var pendingPayUser by remember { mutableStateOf<Pair<User, Double>?>(null) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showNameSetupDialog by remember { mutableStateOf(false) }
    var setupNameInput by remember(profile) { mutableStateOf(if (profile.name == "Me") "" else profile.name) }

    // Observe Sync ViewModel State for Live Payment Info Requests
    val syncState = syncViewModel?.syncState?.collectAsState()?.value ?: SyncState.IDLE
    val discoveredUsers = syncViewModel?.discoveredUsers?.collectAsState()?.value ?: emptyList()
    val connectedParticipant = syncViewModel?.connectedParticipant?.collectAsState()?.value
    val paymentInfoState = syncViewModel?.paymentInfoState?.collectAsState()?.value ?: PaymentInfoState.Idle
    val pendingConnection = syncViewModel?.pendingConnection?.collectAsState()?.value

    pendingConnection?.let { pending ->
        AuthPinDialog(
            pendingConnection = pending,
            onAccept = { syncViewModel?.acceptPendingConnection() },
            onReject = { syncViewModel?.rejectPendingConnection() }
        )
    }

    val permissionLauncher = com.example.divide.sync.nearby.rememberNearbyPermissionsLauncher(
        onPermissionsGranted = {
            if (!profile.isProfileConfigured) {
                showNameSetupDialog = true
            } else {
                showSyncDialog = true
            }
        }
    )

    // Handle incoming live payment info response or timeout
    LaunchedEffect(paymentInfoState) {
        when (val state = paymentInfoState) {
            is PaymentInfoState.Success -> {
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
                pendingPayUser = null
                syncViewModel?.clearPaymentInfoState()
            }
            is PaymentInfoState.Error -> {
                // Fall back to stored local DB info if available
                pendingPayUser?.let { (user, amount) ->
                    if (!user.upiId.isNullOrBlank()) {
                        activePayUser = user to amount
                    } else {
                        missingUpiUser = user
                    }
                }
                pendingPayUser = null
                syncViewModel?.clearPaymentInfoState()
            }
            else -> {}
        }
    }

    val handlePayClick: (User, Double) -> Unit = { user, amount ->
        pendingPayUser = user to amount
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
            pendingPayUser = null
        }
    }

    if (syncState == SyncState.DISCONNECTED) {
        ConnectionLostDialog(
            onReconnect = {
                syncViewModel?.dismissDisconnectedState()
                permissionLauncher()
            },
            onDismiss = {
                syncViewModel?.dismissDisconnectedState()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Divide") },
                actions = {
                    val indicatorColor = when (syncState) {
                        SyncState.CONNECTED -> Color.Green
                        SyncState.DISCONNECTED -> Color(0xFFFFA000)
                        SyncState.ERROR -> Color.Red
                        else -> Color.Gray
                    }
                    val labelText = when (syncState) {
                        SyncState.IDLE -> "Sync"
                        SyncState.ADVERTISING -> "Hosting"
                        SyncState.DISCOVERING -> "Searching"
                        SyncState.CONNECTING -> "Connecting"
                        SyncState.CONNECTED -> "Connected"
                        SyncState.DISCONNECTED -> "Connection lost"
                        SyncState.ERROR -> "Sync error"
                    }
                    TextButton(onClick = {
                        if (syncState == SyncState.IDLE || syncState == SyncState.DISCONNECTED) {
                            permissionLauncher()
                        } else {
                            syncViewModel?.stopSync()
                        }
                    }) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color = indicatorColor, shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (syncState == SyncState.IDLE || syncState == SyncState.DISCONNECTED) Icons.Default.Sync else Icons.Default.Close,
                            contentDescription = "Sync",
                            tint = if (syncState == SyncState.CONNECTED) Color.Green else LocalContentColor.current,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = labelText,
                            color = if (syncState == SyncState.CONNECTED) Color.Green else LocalContentColor.current
                        )
                    }
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
            Spacer(modifier = Modifier.height(24.dp))

            // Row with Create Trip and Join Nearby Trip buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(70.dp)
                        .border(2.dp, Color.Black)
                        .clickable { onCreateTrip() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Create trip",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(70.dp)
                        .border(2.dp, Color.Black)
                        .clickable { permissionLauncher() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Join nearby trip",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

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

    // First-time Profile Name Setup Dialog before launching Nearby Sync
    if (showNameSetupDialog) {
        AlertDialog(
            onDismissRequest = { showNameSetupDialog = false },
            title = { Text("Set Your Display Name") },
            text = {
                Column {
                    Text(
                        text = "Choose a display name your friends will recognize before searching for nearby devices.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = setupNameInput,
                        onValueChange = { setupNameInput = it },
                        label = { Text("Display Name") },
                        placeholder = { Text("e.g. Alex, Sam, Nimesh") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = setupNameInput.trim()
                        if (trimmed.isNotBlank()) {
                            LocalRepository.updateProfile(trimmed)
                            showNameSetupDialog = false
                            showSyncDialog = true
                        }
                    },
                    enabled = setupNameInput.trim().isNotBlank()
                ) {
                    Text("Save & Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameSetupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Nearby Host/Join Dialog
    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = {
                if (syncState != SyncState.CONNECTED) {
                    syncViewModel?.stopSync()
                }
                showSyncDialog = false
            },
            title = { Text(if (syncState == SyncState.IDLE) "Sync / Join Nearby Trip" else "Nearby Device Search") },
            text = {
                Column {
                    when (syncState) {
                        SyncState.IDLE -> {
                            Text("Choose Host to share your trip with nearby friends, Join to discover a nearby host, or Simulate to test with 1 phone.")
                        }
                        SyncState.DISCOVERING, SyncState.ADVERTISING -> {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        if (discoveredUsers.isEmpty()) "Searching nearby devices (0 found)..." else "Found ${discoveredUsers.size} device(s):",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (discoveredUsers.isEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Make sure your friend's phone has Bluetooth and Location turned on.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    discoveredUsers.forEach { peer ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(peer.endpointName, fontWeight = FontWeight.SemiBold)
                                            Button(onClick = { syncViewModel?.inviteFriend(peer.endpointId) }) {
                                                Text("Connect")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SyncState.CONNECTING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Connecting to device...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        SyncState.CONNECTED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Connected",
                                    tint = Color.Green,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Connected! ${connectedParticipant?.name ?: "Friend"} joined.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        SyncState.ERROR, SyncState.DISCONNECTED -> {
                            Text("Connection issue occurred. Tap below to retry.")
                        }
                    }
                }
            },
            confirmButton = {
                if (syncState == SyncState.IDLE) {
                    TextButton(onClick = {
                        val activeTripId = trips.firstOrNull()?.group?.id ?: "pending"
                        syncViewModel?.startHosting(activeTripId)
                    }) { Text("Host") }
                }
            },
            dismissButton = {
                if (syncState == SyncState.IDLE) {
                    TextButton(onClick = {
                        syncViewModel?.joinNearbyTrip()
                    }) { Text("Join") }
                } else {
                    TextButton(onClick = {
                        if (syncState != SyncState.CONNECTED) {
                            syncViewModel?.stopSync()
                        }
                        showSyncDialog = false
                    }) {
                        Text(if (syncState == SyncState.CONNECTED) "Done" else "Close")
                    }
                }
            }
        )
    }

    // Requesting Payment Info Progress Dialog
    if (paymentInfoState is PaymentInfoState.Requesting && pendingPayUser != null) {
        AlertDialog(
            onDismissRequest = {
                syncViewModel?.clearPaymentInfoState()
                pendingPayUser?.let { (user, amount) ->
                    if (!user.upiId.isNullOrBlank()) {
                        activePayUser = user to amount
                    } else {
                        missingUpiUser = user
                    }
                }
                pendingPayUser = null
            },
            title = { Text("Requesting Payment Info") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text("Contacting ${pendingPayUser?.first?.name}'s device...")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        syncViewModel?.clearPaymentInfoState()
                        pendingPayUser?.let { (user, amount) ->
                            if (!user.upiId.isNullOrBlank()) {
                                activePayUser = user to amount
                            } else {
                                missingUpiUser = user
                            }
                        }
                        pendingPayUser = null
                    }
                ) {
                    Text("Use Saved Info")
                }
            }
        )
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
 * A dialog showing the 4-digit authentication PIN to verify nearby device connections securely.
 */
@Composable
fun AuthPinDialog(
    pendingConnection: PendingConnection,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Confirm Device Connection") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Verify that the 4-digit PIN matches on ${pendingConnection.endpointName}'s screen:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = pendingConnection.authDigits,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Confirm & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("Reject")
            }
        }
    )
}

/**
 * A reassuring dialog displayed when a nearby peer disconnects.
 */
@Composable
fun ConnectionLostDialog(
    onReconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connection Lost")
            }
        },
        text = {
            Column {
                Text(
                    text = "Connection to the nearby device was lost.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔒 Your local data is safe.\nAll expenses recorded on this device are saved locally. You can reconnect whenever the other device is available.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onReconnect) {
                Text("Reconnect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
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
