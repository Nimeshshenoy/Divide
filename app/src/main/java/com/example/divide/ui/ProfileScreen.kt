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
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        LocalRepository.updateProfile(name)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save Profile")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Setting your name helps friends identify you when splitting expenses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
