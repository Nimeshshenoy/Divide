package com.example.divide.sync.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Helper object and Compose wrapper for checking and requesting
 * required runtime permissions for Google Nearby Connections.
 */
object NearbyPermissions {

    /**
     * Returns the array of permissions required for Nearby Connections.
     * Note: Android 12+ requires both ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION together in runtime requests.
     */
    fun getRequiredPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    /**
     * Checks if all required permissions are currently granted.
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}

/**
 * Composable function that creates and returns a trigger lambda.
 * Calling the returned lambda checks if permissions are granted;
 * if not, it prompts the user with the system permission dialog.
 */
@Composable
fun rememberNearbyPermissionsLauncher(
    onPermissionsGranted: () -> Unit,
    onPermissionsDenied: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val allGranted = permissionsResult.values.all { it }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            onPermissionsDenied()
        }
    }

    return {
        if (NearbyPermissions.hasRequiredPermissions(context)) {
            onPermissionsGranted()
        } else {
            launcher.launch(NearbyPermissions.getRequiredPermissions())
        }
    }
}
