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
import androidx.activity.viewModels
import com.example.divide.data.ExpenseDatabase
import com.example.divide.data.LocalRepository
import com.example.divide.ui.AddExpenseScreen
import com.example.divide.ui.MainScreen
import com.example.divide.ui.ProfileScreen
import com.example.divide.ui.SyncViewModel
import com.example.divide.ui.TripSetupScreen
import com.example.divide.ui.theme.DivideTheme

/**
 * The main activity of the application.
 */
class MainActivity : ComponentActivity() {

    private val syncViewModel: SyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the database and repository
        val database = ExpenseDatabase.getDatabase(this)
        LocalRepository.initialize(database.expenseDao())
        
        enableEdgeToEdge()
        setContent {
            DivideTheme {
                DivideApp(syncViewModel)
            }
        }
    }
}

/**
 * Root Composable for the app's navigation graph.
 */
@Composable
fun DivideApp(syncViewModel: SyncViewModel) {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "main") {
        // Main Screen: Summary of all trips
        composable("main") {
            MainScreen(
                syncViewModel = syncViewModel,
                onCreateTrip = { navController.navigate("setup_trip") },
                onTripClick = { tripId -> navController.navigate("add_expense/$tripId") },
                onProfileClick = { navController.navigate("profile") }
            )
        }
        
        // Profile Screen
        composable("profile") {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        
        // Trip Setup Screen (previously Create Event)
        composable(
            route = "setup_trip?tripId={tripId}",
            arguments = listOf(navArgument("tripId") { 
                type = NavType.StringType
                nullable = true
                defaultValue = null 
            })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId")
            TripSetupScreen(
                tripId = tripId,
                onSaveAndAddExpense = { id -> 
                    navController.navigate("add_expense/$id") {
                        popUpTo("main") { saveState = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // Add Expense Screen: Dashboard for a specific trip
        composable(
            route = "add_expense/{tripId}",
            arguments = listOf(navArgument("tripId") { type = NavType.StringType })
        ) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: return@composable
            AddExpenseScreen(
                tripId = tripId,
                syncViewModel = syncViewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
