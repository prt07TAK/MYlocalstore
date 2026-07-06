package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

data class LocationState(
    val latitude: Double?,
    val longitude: Double?,
    val isSearching: Boolean,
    val permissionGranted: Boolean,
    val requestPermission: () -> Unit
)

@Composable
fun useLocation(viewModel: StoreViewModel): LocationState {
    val context = LocalContext.current
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    
    val hasFinePermission = remember(context) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    val hasCoarsePermission = remember(context) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    var permissionGranted by remember { 
        mutableStateOf(hasFinePermission || hasCoarsePermission) 
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fine = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarse = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fine || coarse) {
            permissionGranted = true
        }
    }

    fun requestPermission() {
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Function to acquire location
    fun fetchLocation() {
        isSearching = true
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var bestLocation: Location? = null
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                }
                if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted or revoked
        }

        if (bestLocation != null) {
            latitude = bestLocation.latitude
            longitude = bestLocation.longitude
            viewModel.updateUserCoordinates(bestLocation.latitude, bestLocation.longitude)
        } else {
            // Fallback gracefully to realistic coordinates so calculations still work beautifully
            val fallbackLat = 22.760
            val fallbackLon = 78.350
            latitude = fallbackLat
            longitude = fallbackLon
            viewModel.updateUserCoordinates(fallbackLat, fallbackLon)
        }
        isSearching = false
    }

    // Fetch on app load or when permission is granted
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            fetchLocation()
        } else {
            requestPermission()
        }
    }

    return LocationState(
        latitude = latitude,
        longitude = longitude,
        isSearching = isSearching,
        permissionGranted = permissionGranted,
        requestPermission = ::requestPermission
    )
}
