package com.example.divide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.divide.data.ExpenseDatabase
import com.example.divide.data.LocalRepository
import com.example.divide.ui.AddExpenseScreen
import com.example.divide.ui.CreateEventScreen
import com.example.divide.ui.MainScreen
import com.example.divide.ui.ProfileScreen
import com.example.divide.ui.theme.DivideTheme

/**
 * The main activity of the application.
 * Sets up the Compose environment and initializes the top-level app navigation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the database and repository
        val database = ExpenseDatabase.getDatabase(this)
        LocalRepository.initialize(database.expenseDao())
        
        enableEdgeToEdge()
        setContent {
            DivideTheme {
                DivideApp()
            }
        }
    }
}

/**
 * Root Composable for the app's navigation graph.
 * Manages transitions between the Main list, Event creation/details, and Expense addition.
 */
@Composable
fun DivideApp() {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "main") {
        // Main Screen: Showing the summary of trips (Unsettled/Settled)
        composable("main") {
            MainScreen(
                onCreateEvent = { navController.navigate("create_event") },
                // Requirement: Redirect directly to Add Expense page when clicking a trip
                onEventClick = { eventId -> navController.navigate("add_expense/$eventId") },
                onProfileClick = { navController.navigate("profile") }
            )
        }
        
        // Profile Screen: Edit user settings
        composable("profile") {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        
        // Create Event Screen: Handled as a container for a "Trip" (e.g. Udaipur)
        composable(
            route = "create_event?eventId={eventId}",
            arguments = listOf(navArgument("eventId") { 
                type = NavType.StringType
                nullable = true
                defaultValue = null 
            })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId")
            CreateEventScreen(
                eventId = eventId,
                onSaveAndAddExpense = { id -> 
                    // Redirect to add expenses immediately after creating the trip container
                    navController.navigate("add_expense/$id") {
                        // Pop create_event from backstack so back button goes to main
                        popUpTo("main") { saveState = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Add Expense Screen: Now acts as the primary dashboard for adding costs to a trip
        composable(
            route = "add_expense/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            AddExpenseScreen(
                eventId = eventId,
                // Requirement: No longer redirecting on save, so we only need the back action
                onBack = { navController.popBackStack() }
            )
        }
    }
}
