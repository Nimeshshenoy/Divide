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
import com.example.divide.ui.AddExpenseScreen
import com.example.divide.ui.CreateEventScreen
import com.example.divide.ui.MainScreen
import com.example.divide.ui.theme.DivideTheme

/**
 * The main activity of the application.
 * Sets up the Compose environment and initializes the top-level app navigation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                onEventClick = { eventId -> navController.navigate("create_event/$eventId") }
            )
        }
        
        // Create Event Screen: Handled as a container for a "Trip" (e.g. Udaipur)
        // Supports both new creation and viewing existing details via optional {eventId}
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
                    navController.navigate("add_expense/$id") 
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Backward compatibility route or explicit route for existing events
        composable(
            route = "create_event/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId")
            CreateEventScreen(
                eventId = eventId,
                onSaveAndAddExpense = { id -> 
                    navController.navigate("add_expense/$id") 
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Add Expense Screen: Where specific costs (e.g. Lunch) are added to a trip
        composable(
            route = "add_expense/{eventId}",
            arguments = listOf(navArgument("eventId") { type = NavType.StringType })
        ) { backStackEntry ->
            val eventId = backStackEntry.arguments?.getString("eventId") ?: return@composable
            AddExpenseScreen(
                eventId = eventId,
                onSave = { 
                    // After saving an expense, we go back to the Trip details screen
                    navController.popBackStack() 
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
