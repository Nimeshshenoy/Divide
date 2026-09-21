package com.example.divide.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.divide.data.LocalRepository

/**
 * Screen to edit the local user's profile settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    // Observe the profile from the repository
    val profile by LocalRepository.profile.collectAsState()
    
    // Local state for the text field
    var name by remember(profile) { mutableStateOf(profile.name) }
    
    // State for showing the warning dialog
    var showWarning by remember { mutableStateOf(false) }

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
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isNotBlank() && trimmedName.lowercase() != "me") {
                        // If a name was already set (not "Me"), show a warning before changing
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
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Note: Choose a name your friends will recognize. Avoid generic names like 'Me'.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
