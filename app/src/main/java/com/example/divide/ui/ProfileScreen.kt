package com.example.divide.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.divide.data.LocalRepository

/**
 * Screen to edit the local user's profile settings and UPI payment details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    // Observe the profile from the repository
    val profile by LocalRepository.profile.collectAsState()
    
    // Local state for the text fields
    var name by remember(profile) { mutableStateOf(profile.name) }
    var upiInput by remember(profile) { mutableStateOf(profile.upiId ?: "") }

    // State for showing dialogs
    var showWarning by remember { mutableStateOf(false) }
    var showUpiDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
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
            Text(text = "Username", style = MaterialTheme.typography.labelLarge)
            TextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Enter your real name") }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isNotBlank() && trimmedName.lowercase() != "me") {
                        if (profile.name != "Me" && profile.name != trimmedName) {
                            showWarning = true
                        } else {
                            LocalRepository.updateProfile(trimmedName)
                            onBack()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.trim().isNotEmpty() && name.trim().lowercase() != "me"
            ) {
                Text(text = "Save Profile")
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Note: Choose a name your friends will recognize. Avoid generic names like 'Me'.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(24.dp))

            // Payment Details (UPI) Section
            Text(text = "Payment Details (UPI)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (!profile.upiId.isNullOrBlank()) {
                Text(
                    text = "UPI ID: ${profile.upiId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showUpiDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Edit UPI ID")
                }
            } else {
                Text(
                    text = "No UPI ID set. Add your UPI ID so friends can pay you directly via QR or UPI apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showUpiDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Set up payments")
                }
            }
        }
    }

    // UPI Setup / Edit Dialog
    if (showUpiDialog) {
        AlertDialog(
            onDismissRequest = { showUpiDialog = false },
            title = { Text("Set up payments") },
            text = {
                Column {
                    Text(
                        text = "Enter your personal UPI ID (VPA) to generate automatic payment QR codes for your friends.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = upiInput,
                        onValueChange = { upiInput = it },
                        label = { Text("UPI ID") },
                        placeholder = { Text("e.g. nimesh@okaxis") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedUpi = upiInput.trim()
                        LocalRepository.updateProfile(
                            newName = profile.name,
                            newUpiId = trimmedUpi.ifEmpty { null }
                        )
                        showUpiDialog = false
                    }
                ) {
                    Text("Save UPI ID")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpiDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Warning Dialog to prevent "random" renaming after initial setup
    if (showWarning) {
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("Change Username?") },
            text = { 
                Text("Changing your name will update your identity in all existing and future trips. " +
                     "Friends you've already synced with will see your new name. Are you sure you want to proceed?") 
            },
            confirmButton = {
                Button(
                    onClick = {
                        LocalRepository.updateProfile(name.trim())
                        showWarning = false
                        onBack()
                    }
                ) {
                    Text("Yes, Change")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun String?.isNullOrBlank(): Boolean = this == null || this.trim().isEmpty()
