package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.Screen
import com.example.ui.StoreViewModel
import com.example.ui.ChatMessage
import com.example.ui.useLocation
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: StoreViewModel = viewModel()
                val locationState = useLocation(viewModel)
                val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
                val user by viewModel.userState.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            is Screen.Onboarding -> OnboardingScreen(viewModel)
                            is Screen.Home -> MainScaffold(viewModel) { HomeScreen(viewModel) }
                            is Screen.StoreDetail -> MainScaffold(viewModel) { StoreDetailScreen(viewModel, screen.shopId) }
                            is Screen.AIChat -> MainScaffold(viewModel) { AIChatScreen(viewModel) }
                            is Screen.Cart -> MainScaffold(viewModel) { CartScreen(viewModel) }
                            is Screen.OrderTracker -> MainScaffold(viewModel) { OrderTrackerScreen(viewModel, screen.orderId) }
                            is Screen.VendorDashboard -> MainScaffold(viewModel) { VendorDashboardScreen(viewModel, screen.shopId) }
                            is Screen.Settings -> MainScaffold(viewModel) { SettingsScreen(viewModel) }
                        }
                    }
                }
            }
        }
    }
}

// --- Adaptive UI Config Resolver ---

data class AppUiStyle(
    val titleSize: TextUnit,
    val subtitleSize: TextUnit,
    val bodySize: TextUnit,
    val padding: Dp,
    val spacing: Dp,
    val cardElevation: Dp,
    val minTouchTarget: Dp,
    val useGlowingTheme: Boolean,
    val isSimplified: Boolean
)

@Composable
fun resolveUiStyle(user: UserEntity?): AppUiStyle {
    val tier = user?.uiTier ?: "Standard"
    return when (tier) {
        "Modern" -> AppUiStyle(
            titleSize = 22.sp,
            subtitleSize = 16.sp,
            bodySize = 14.sp,
            padding = 16.dp,
            spacing = 12.dp,
            cardElevation = 6.dp,
            minTouchTarget = 48.dp,
            useGlowingTheme = true,
            isSimplified = false
        )
        "Simplified" -> AppUiStyle(
            titleSize = 30.sp,
            subtitleSize = 22.sp,
            bodySize = 18.sp,
            padding = 24.dp,
            spacing = 16.dp,
            cardElevation = 0.dp, // High contrast flat border look
            minTouchTarget = 64.dp, // Extra large touch targets for older users
            useGlowingTheme = false,
            isSimplified = true
        )
        else -> AppUiStyle( // Standard
            titleSize = 20.sp,
            subtitleSize = 15.sp,
            bodySize = 14.sp,
            padding = 16.dp,
            spacing = 10.dp,
            cardElevation = 2.dp,
            minTouchTarget = 48.dp,
            useGlowingTheme = false,
            isSimplified = false
        )
    }
}

// --- Composable Sub-screens ---

@Composable
fun OnboardingScreen(viewModel: StoreViewModel) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedAge by remember { mutableStateOf("25–40") }
    var selectedLang by remember { mutableStateOf("English") }
    var location by remember { mutableStateOf("") }
    
    var otpSent by remember { mutableStateOf(false) }
    var otpCode by remember { mutableStateOf("") }
    var otpVerifying by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val ageBrackets = listOf("Under 25", "25–40", "41–55", "56+")
    val languages = listOf("English", "Hindi")

    val bgGradient = Brush.verticalGradient(
        colors = listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), MaterialTheme.colorScheme.background)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary)
                .shadow(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = "My Local Store Logo",
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "My Local Store",
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Bringing Rampur's Trusted Shopkeepers to Your Doorstep",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!otpSent) {
                    Text(
                        text = "Sign In with Phone Number",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Your Name (नाम)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("name_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { if (it.length <= 10) phone = it },
                        label = { Text("Phone Number (फ़ोन नंबर)") },
                        leadingIcon = { Icon(Icons.Default.Call, contentDescription = "Phone") },
                        prefix = { Text("+91 ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("phone_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            if (phone.length < 10) {
                                Toast.makeText(context, "Please enter a valid 10-digit number", Toast.LENGTH_SHORT).show()
                            } else {
                                otpSent = true
                                Toast.makeText(context, "OTP Sent to +91 $phone", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("get_otp_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Get OTP (ओटीपी प्राप्त करें)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text("OR", modifier = Modifier.padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    OutlinedButton(
                        onClick = {
                            // Demo Firebase Auth flow
                            Toast.makeText(context, "Connecting to Firebase Auth...", Toast.LENGTH_SHORT).show()
                            viewModel.login(
                                "Demo User",
                                "9999999999",
                                "25-34",
                                "English",
                                "123 Demo Street"
                            )
                            Toast.makeText(context, "Logged in via Google", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("google_signin_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Google Sign In",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Sign in with Google (Demo)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Text(
                        text = "Verify OTP Sent to +91 $phone",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 4) otpCode = it },
                        label = { Text("Enter 4-Digit OTP (1234)") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "OTP") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Text(
                        text = "Select Language",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        languages.forEach { lang ->
                            FilterChip(
                                selected = selectedLang == lang,
                                onClick = { selectedLang = lang },
                                label = { Text(lang) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text(
                        text = "How old are you? (आपकी उम्र?)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ageBrackets.forEach { age ->
                            FilterChip(
                                selected = selectedAge == age,
                                onClick = { selectedAge = age },
                                label = { Text(age) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Your Location (village, town, or pincode)") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Location") },
                        placeholder = { Text("e.g. Rampur, Pipariya") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("location_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { otpSent = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Back")
                        }

                        Button(
                            onClick = {
                                if (otpCode != "1234" && otpCode.isNotEmpty()) {
                                    Toast.makeText(context, "Invalid OTP. Use 1234 for testing.", Toast.LENGTH_SHORT).show()
                                } else {
                                    otpVerifying = true
                                    keyboardController?.hide()
                                    viewModel.login(
                                        name = name,
                                        phone = phone,
                                        ageBracket = selectedAge,
                                        language = selectedLang,
                                        location = location
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1.5f)
                                .height(52.dp)
                                .testTag("register_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (otpVerifying) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Text("Verify & Enter", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationFinderDialog(
    onDismiss: () -> Unit,
    viewModel: StoreViewModel,
    style: AppUiStyle,
    language: String?
) {
    var searchQuery by remember { mutableStateOf("") }
    var isDetecting by remember { mutableStateOf(false) }
    var detectionStatus by remember { mutableStateOf("") }
    val context = LocalContext.current

    val popularLocations = listOf(
        "Civil Lines, Rampur",
        "Station Road, Rampur",
        "Pipariya Main Bazar, Ward 5",
        "Shadab Nagar, Rampur",
        "Kemri Town, Rampur",
        "Shahabad Gate, Rampur",
        "Bilaspur Road, Rampur"
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            isDetecting = true
        } else {
            Toast.makeText(
                context,
                if (language == "Hindi") "जीपीएस अनुमति अस्वीकार कर दी गई!" else "GPS permission denied!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (isDetecting) {
        LaunchedEffect(Unit) {
            detectionStatus = if (language == "Hindi") "जीपीएस सिग्नल की खोज की जा रही है..." else "Acquiring GPS lock..."
            delay(500)
            
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var location: Location? = null
            
            try {
                val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (hasFine || hasCoarse) {
                    detectionStatus = if (language == "Hindi") "उपग्रह डेटा को पिंग किया जा रहा है..." else "Pinging satellite coordinates..."
                    if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                        location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    }
                    if (location == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.e("LocationFinder", "Permission error", e)
            }

            delay(1000)

            var resolvedAddress = ""
            if (location != null) {
                detectionStatus = if (language == "Hindi") "भौतिक पते की गणना की जा रही है..." else "Resolving physical address..."
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val subLocality = addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: ""
                        val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea ?: ""
                        resolvedAddress = if (subLocality.isNotEmpty()) "$subLocality, $city" else city
                    }
                } catch (e: Exception) {
                    android.util.Log.e("LocationFinder", "Geocoding error", e)
                }
                if (resolvedAddress.isEmpty()) {
                    resolvedAddress = String.format(Locale.US, "Lat: %.3f, Lon: %.3f (Rampur)", location.latitude, location.longitude)
                }
            } else {
                detectionStatus = if (language == "Hindi") "टावर सेल्युलेटर से कनेक्ट किया जा रहा है..." else "Using cellular tower triangulation..."
                delay(800)
                resolvedAddress = popularLocations.random()
            }

            viewModel.updateLocation(resolvedAddress)
            Toast.makeText(
                context,
                if (language == "Hindi") "सफलतापूर्वक स्थान सेट किया गया: $resolvedAddress" else "Location updated to: $resolvedAddress",
                Toast.LENGTH_LONG
            ).show()
            isDetecting = false
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isDetecting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == "Hindi") "लोकेशन खोजें और चुनें" else "Find & Select Location",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style.isSimplified) 22.sp else 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isDetecting) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text(
                            text = detectionStatus,
                            fontSize = if (style.isSimplified) 18.sp else 14.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = if (language == "Hindi") "सटीक स्थानीय दुकानों को खोजने के लिए अपने पास का स्थान चुनें:" else "Select or enter your neighborhood to view highly accurate nearby local shops:",
                        fontSize = if (style.isSimplified) 16.sp else 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Auto-Detect Button
                    Button(
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (hasFine || hasCoarse) {
                                isDetecting = true
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (style.isSimplified) 54.dp else 44.dp)
                            .testTag("gps_auto_detect_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == "Hindi") "📍 वर्तमान जीपीएस स्थान ढूंढें" else "📍 Auto-Detect My Location",
                            fontSize = if (style.isSimplified) 18.sp else 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(if (language == "Hindi") "मैन्युअल रूप से दर्ज करें..." else "Enter area manually...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_location_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.updateLocation(searchQuery)
                                    Toast.makeText(context, if (language == "Hindi") "स्थान सेट किया गया!" else "Location set!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Submit")
                                }
                            }
                        }
                    )

                    Text(
                        text = if (language == "Hindi") "लोकप्रिय रामपुर क्षेत्र:" else "Popular Areas Nearby:",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (style.isSimplified) 16.sp else 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyColumn(
                        modifier = Modifier
                            .height(180.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filteredList = if (searchQuery.isEmpty()) {
                            popularLocations
                        } else {
                            popularLocations.filter { it.contains(searchQuery, ignoreCase = true) }
                        }

                        items(filteredList.size) { idx ->
                            val loc = filteredList[idx]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateLocation(loc)
                                        Toast.makeText(context, if (language == "Hindi") "स्थान बदला गया!" else "Location changed!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = loc,
                                        fontSize = if (style.isSimplified) 16.sp else 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isDetecting) {
                TextButton(onClick = onDismiss) {
                    Text(if (language == "Hindi") "रद्द करें" else "Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    viewModel: StoreViewModel,
    content: @Composable () -> Unit
) {
    val user by viewModel.userState.collectAsStateWithLifecycle()
    val cart by viewModel.cartState.collectAsStateWithLifecycle()
    val activeScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val userCoordinates by viewModel.userCoordinates.collectAsStateWithLifecycle()

    val style = resolveUiStyle(user)
    var showLocationDialog by remember { mutableStateOf(false) }

    if (showLocationDialog) {
        LocationFinderDialog(
            onDismiss = { showLocationDialog = false },
            viewModel = viewModel,
            style = style,
            language = user?.language
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        modifier = Modifier
                            .clickable { showLocationDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("location_finder_trigger")
                    ) {
                        Text(
                            text = if (user?.language == "Hindi") "मेरा लोकल स्टोर" else "My Local Store",
                            fontWeight = FontWeight.Bold,
                            fontSize = if (style.isSimplified) 24.sp else 18.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Location",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (style.isSimplified) 20.dp else 14.dp)
                            )
                            Text(
                                text = if (userCoordinates != null) {
                                    val (lat, lon) = userCoordinates!!
                                    "${user?.selectedLocation ?: "Rampur, UP"} (${String.format(Locale.US, "%.3f", lat)}, ${String.format(Locale.US, "%.3f", lon)})"
                                } else {
                                    user?.selectedLocation ?: "Rampur, UP"
                                },
                                fontSize = if (style.isSimplified) 16.sp else 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Change Location",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                modifier = Modifier.size(if (style.isSimplified) 24.dp else 16.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (activeScreen !is Screen.Home) {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        // Display localized greeting or store icon
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = "App Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp).size(28.dp)
                        )
                    }
                },
                actions = {
                    // Quick view switchers & settings button
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.AIChat) },
                        modifier = Modifier.testTag("ai_assistant_button")
                    ) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = "AI Assistant",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.navigateTo(Screen.Settings) },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            if (activeScreen !is Screen.AIChat) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { viewModel.navigateTo(Screen.AIChat) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("fab_ai_chat")
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "AI Chat")
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = activeScreen is Screen.Home,
                    onClick = { viewModel.navigateTo(Screen.Home) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = if (style.isSimplified) 16.sp else 12.sp) }
                )
                NavigationBarItem(
                    selected = activeScreen is Screen.AIChat,
                    onClick = { viewModel.navigateTo(Screen.AIChat) },
                    icon = {
                        BadgedBox(badge = {
                            Badge { Text("AI") }
                        }) {
                            Icon(Icons.Default.Face, contentDescription = "AI Chat")
                        }
                    },
                    label = { Text("AI Helper", fontSize = if (style.isSimplified) 16.sp else 12.sp) }
                )
                NavigationBarItem(
                    selected = activeScreen is Screen.Cart,
                    onClick = { viewModel.navigateTo(Screen.Cart) },
                    icon = {
                        BadgedBox(badge = {
                            if (cart.isNotEmpty()) {
                                Badge { Text(cart.sumOf { it.quantity }.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Cart")
                        }
                    },
                    label = { Text("Cart", fontSize = if (style.isSimplified) 16.sp else 12.sp) }
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            content()
        }
    }
}

// --- HomeScreen: Shop Directory ---

@Composable
fun HomeScreen(viewModel: StoreViewModel) {
    val user by viewModel.userState.collectAsStateWithLifecycle()
    val shops by viewModel.shopsState.collectAsStateWithLifecycle()
    val userCoordinates by viewModel.userCoordinates.collectAsStateWithLifecycle()
    val style = resolveUiStyle(user)

    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf(
        "All" to Icons.Default.List,
        "Kirana" to Icons.Default.ShoppingCart,
        "Sabji" to Icons.Default.Eco,
        "Sweet Shop" to Icons.Default.Cake,
        "Gift Shop" to Icons.Default.CardGiftcard,
        "Medicine Store" to Icons.Default.LocalPharmacy,
        "Clothing Store" to Icons.Default.Checkroom,
        "Stationery Shop" to Icons.Default.BorderColor
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // AI Quick Banner (Promoting AI assistance)
        if (!style.isSimplified) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = style.padding, vertical = 8.dp)
                    .clickable { viewModel.navigateTo(Screen.AIChat) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Face, contentDescription = "AI", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (user?.language == "Hindi") "एआई से कुछ भी पूछें" else "Ask anything to our AI assistant!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (user?.language == "Hindi") "\"मुझे मटर पनीर बनाना है\" या \"माँ के लिए उपहार\"" else "Try: \"I want to make matar paneer\" or \"gift for mom\"",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "Go",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Location Finder Card / Banner
        var showLocationDialogOnHome by remember { mutableStateOf(false) }
        if (showLocationDialogOnHome) {
            LocationFinderDialog(
                onDismiss = { showLocationDialogOnHome = false },
                viewModel = viewModel,
                style = style,
                language = user?.language
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = style.padding, vertical = 4.dp)
                .clickable { showLocationDialogOnHome = true }
                .testTag("home_location_finder_banner"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "Location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (user?.language == "Hindi") "वर्तमान नजदीकी स्थान" else "Showing Shops Nearby:",
                        fontSize = if (style.isSimplified) 16.sp else 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = if (userCoordinates != null) {
                            val (lat, lon) = userCoordinates!!
                            "${user?.selectedLocation ?: "Rampur, UP"} (${String.format(Locale.US, "%.3f", lat)}, ${String.format(Locale.US, "%.3f", lon)})"
                        } else {
                            user?.selectedLocation ?: "Rampur, UP"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = if (style.isSimplified) 18.sp else 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (user?.language == "Hindi") "बदलें" else "Change 📍",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style.isSimplified) 16.sp else 12.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Category Filter Row
        Text(
            text = if (user?.language == "Hindi") "दुकान की श्रेणियां" else "Shop Categories",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            fontSize = if (style.isSimplified) 22.sp else 16.sp,
            modifier = Modifier.padding(horizontal = style.padding, vertical = 8.dp)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = style.padding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { (catName, icon) ->
                val isSelected = selectedCategory == catName
                val chipBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                val chipContentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                val borderStroke = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier
                        .height(if (style.isSimplified) 60.dp else 44.dp)
                        .clip(RoundedCornerShape(if (style.isSimplified) 30.dp else 22.dp))
                        .background(chipBg)
                        .then(if (borderStroke != null) Modifier.border(borderStroke, RoundedCornerShape(if (style.isSimplified) 30.dp else 22.dp)) else Modifier)
                        .clickable { selectedCategory = catName }
                        .padding(horizontal = if (style.isSimplified) 20.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = catName,
                        tint = chipContentColor,
                        modifier = Modifier.size(if (style.isSimplified) 24.dp else 18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = catName,
                        color = chipContentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (style.isSimplified) 18.sp else 13.sp
                    )
                }
            }
        }

        // Shops List header
        Text(
            text = if (user?.language == "Hindi") "निकटतम सत्यापित दुकानें" else "Nearest Verified Shops",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            fontSize = if (style.isSimplified) 22.sp else 16.sp,
            modifier = Modifier.padding(horizontal = style.padding, vertical = 8.dp)
        )

        val filteredShops = if (selectedCategory == "All") {
            shops
        } else {
            shops.filter { it.category == selectedCategory }
        }

        if (filteredShops.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Empty",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No shops onboarded in this category yet.")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = style.padding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(style.spacing)
            ) {
                items(filteredShops) { shop ->
                    ShopListItemCard(shop, style, user, onClick = {
                        viewModel.navigateTo(Screen.StoreDetail(shop.id))
                    })
                }
            }
        }
    }
}

@Composable
fun ShopListItemCard(
    shop: ShopEntity,
    style: AppUiStyle,
    user: UserEntity?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (style.isSimplified) 0.dp else style.cardElevation, RoundedCornerShape(12.dp))
            .border(
                if (style.isSimplified) BorderStroke(3.dp, Color.Black) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("shop_card_${shop.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(style.padding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon with solid color circle
            Box(
                modifier = Modifier
                    .size(if (style.isSimplified) 72.dp else 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                val icon = when (shop.category) {
                    "Kirana" -> Icons.Default.ShoppingCart
                    "Sabji" -> Icons.Default.Eco
                    "Sweet Shop" -> Icons.Default.Cake
                    "Gift Shop" -> Icons.Default.CardGiftcard
                    "Medicine Store" -> Icons.Default.LocalPharmacy
                    "Clothing Store" -> Icons.Default.Checkroom
                    "Stationery Shop" -> Icons.Default.BorderColor
                    else -> Icons.Default.Storefront
                }
                Icon(
                    imageVector = icon,
                    contentDescription = shop.category,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (style.isSimplified) 40.dp else 28.dp)
                )
            }

            Spacer(modifier = Modifier.width(if (style.isSimplified) 20.dp else 12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shop.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (style.isSimplified) 22.sp else 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (shop.isVerified) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Shop",
                            tint = FreshGreen,
                            modifier = Modifier.size(if (style.isSimplified) 22.dp else 16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${shop.category} • ${shop.address}",
                    fontSize = if (style.isSimplified) 16.sp else 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Rating
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = MarigoldYellow,
                            modifier = Modifier.size(if (style.isSimplified) 18.dp else 12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = shop.rating.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = if (style.isSimplified) 16.sp else 11.sp
                        )
                    }

                    // Distance
                    Text(
                        text = "${shop.distanceKm} km",
                        fontSize = if (style.isSimplified) 16.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    // Delivery time
                    Text(
                        text = "⏱️ ${shop.deliveryTimeMin} mins",
                        fontSize = if (style.isSimplified) 16.sp else 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            if (style.isSimplified) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "Enter Shop",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// --- Store Detail Screen ---

@Composable
fun StoreDetailScreen(viewModel: StoreViewModel, shopId: Long) {
    val user by viewModel.userState.collectAsStateWithLifecycle()
    val shops by viewModel.shopsState.collectAsStateWithLifecycle()
    val products by viewModel.productsState.collectAsStateWithLifecycle()
    val cart by viewModel.cartState.collectAsStateWithLifecycle()
    
    val shop = shops.find { it.id == shopId }
    val storeProducts = products.filter { it.shopId == shopId }

    val style = resolveUiStyle(user)
    val context = LocalContext.current

    if (shop == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Store not found.")
        }
        return
    }

    var selectedSubCategory by remember { mutableStateOf("All") }
    val subcategories = listOf("All") + storeProducts.map { it.subCategory }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Store Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(style.padding),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (style.isSimplified) 0.dp else 4.dp),
            border = if (style.isSimplified) BorderStroke(3.dp, Color.Black) else null
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = shop.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        fontSize = if (style.isSimplified) 26.sp else 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                data = android.net.Uri.parse("tel:${shop.ownerPhone}")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .background(FreshGreen.copy(alpha = 0.1f), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call Vendor", tint = FreshGreen)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${shop.category} • ${shop.address}",
                    fontSize = if (style.isSimplified) 18.sp else 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⏱️ ${shop.hours}", fontSize = if (style.isSimplified) 16.sp else 12.sp, fontWeight = FontWeight.Medium)
                    Text("⭐ ${shop.rating} Rating", fontSize = if (style.isSimplified) 16.sp else 12.sp, fontWeight = FontWeight.Medium, color = MarigoldYellow)
                    Text("👤 Owner Phone: ${shop.ownerPhone}", fontSize = if (style.isSimplified) 16.sp else 12.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        viewModel.navigateTo(Screen.VendorDashboard(shop.id))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("vendor_dash_switch")
                ) {
                    Icon(Icons.Default.Build, contentDescription = "Edit")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (user?.language == "Hindi") "इस दुकान को प्रबंधित करें (दुकानदार)" else "Manage this shop (Vendor Mode)",
                        fontSize = if (style.isSimplified) 18.sp else 13.sp
                    )
                }
            }
        }

        // Subcategory Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = style.padding),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(subcategories) { subcat ->
                val isSelected = selectedSubCategory == subcat
                val chipBg = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                val chipTextColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                
                Row(
                    modifier = Modifier
                        .height(if (style.isSimplified) 52.dp else 36.dp)
                        .clip(RoundedCornerShape(if (style.isSimplified) 26.dp else 18.dp))
                        .background(chipBg)
                        .clickable { selectedSubCategory = subcat }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subcat,
                        color = chipTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (style.isSimplified) 18.sp else 13.sp
                    )
                }
            }
        }

        // Products Grid
        val filteredProducts = if (selectedSubCategory == "All") {
            storeProducts
        } else {
            storeProducts.filter { it.subCategory == selectedSubCategory }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(if (style.isSimplified) 1 else 2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = style.padding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(style.spacing),
            horizontalArrangement = Arrangement.spacedBy(style.spacing)
        ) {
            items(filteredProducts) { product ->
                val cartItem = cart.find { it.productId == product.id }
                ProductItemCard(product, cartItem, style, user, onAdd = {
                    viewModel.addToCart(product.id, shopId, 1)
                }, onDecrease = {
                    viewModel.decreaseCartQuantity(product.id)
                })
            }
        }
    }
}

@Composable
fun ProductItemCard(
    product: ProductEntity,
    cartItem: CartItemEntity?,
    style: AppUiStyle,
    user: UserEntity?,
    onAdd: () -> Unit,
    onDecrease: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (style.isSimplified) 0.dp else style.cardElevation, RoundedCornerShape(12.dp))
            .border(
                if (style.isSimplified) BorderStroke(3.dp, Color.Black) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Product Placeholder Image with text label representation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (style.isSimplified) 140.dp else 110.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val icon = when (product.imageUrl) {
                        "salt" -> "🧂"
                        "oil" -> "🛢️"
                        "atta" -> "🌾"
                        "paneer" -> "🧀"
                        "peas" -> "🟢"
                        "kaju_katli", "gulab_jamun", "laddu", "rasgulla" -> "🍬"
                        "samosa", "dhokla" -> "🥟"
                        "frame" -> "🖼️"
                        "mug" -> "☕"
                        "candle" -> "🕯️"
                        "saree" -> "👘"
                        "tshirt" -> "👕"
                        else -> "📦"
                    }
                    Text(icon, fontSize = if (style.isSimplified) 48.sp else 32.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = product.subCategory,
                        fontSize = if (style.isSimplified) 14.sp else 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.name,
                fontWeight = FontWeight.Bold,
                fontSize = if (style.isSimplified) 22.sp else 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "1 ${product.unit}",
                fontSize = if (style.isSimplified) 16.sp else 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rs ${product.price}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (style.isSimplified) 24.sp else 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                if (!product.inStock) {
                    Text(
                        text = if (user?.language == "Hindi") "आउट ऑफ़ स्टॉक" else "Out of Stock",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (style.isSimplified) 16.sp else 11.sp
                    )
                } else {
                    if (cartItem == null) {
                        Button(
                            onClick = onAdd,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.getDpOrZero(style.isSimplified)),
                            modifier = Modifier
                                .height(if (style.isSimplified) 52.dp else 32.dp)
                                .testTag("add_to_cart_button_${product.id}"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(if (style.isSimplified) 24.dp else 16.dp))
                            Text(if (user?.language == "Hindi") "जोड़ें" else "Add", fontWeight = FontWeight.Bold, fontSize = if (style.isSimplified) 18.sp else 12.sp)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onDecrease,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .size(if (style.isSimplified) 52.dp else 32.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(if (style.isSimplified) 24.dp else 16.dp))
                            }
                            Text(
                                text = cartItem.quantity.toString(),
                                fontWeight = FontWeight.Bold,
                                fontSize = if (style.isSimplified) 22.sp else 14.sp
                            )
                            IconButton(
                                onClick = onAdd,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .size(if (style.isSimplified) 52.dp else 32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(if (style.isSimplified) 24.dp else 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Int.getDpOrZero(simplified: Boolean): Dp {
    return if (simplified) 12.dp else 0.dp
}

// --- AI Shopping Assistant Screen ---

@Composable
fun AIChatScreen(viewModel: StoreViewModel) {
    val user by viewModel.userState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    
    val style = resolveUiStyle(user)
    val context = LocalContext.current

    var promptInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    // Auto-scroll to the latest message whenever the list updates or starts loading
    LaunchedEffect(chatMessages.size, isAiLoading) {
        if (chatMessages.isNotEmpty()) {
            try {
                listState.animateScrollToItem(chatMessages.size - 1)
            } catch (e: Exception) {
                // Ignore transient scrolling errors
            }
        }
    }

    val initialSuggestions = listOf(
        if (user?.language == "Hindi") "मटर पनीर पकाने की सामग्री" else "I want to cook matar paneer",
        if (user?.language == "Hindi") "मां के जन्मदिन के लिए उपहार" else "Gift for my mom's birthday",
        if (user?.language == "Hindi") "दैनिक राशन के सामान" else "Show me nearby groceries",
        if (user?.language == "Hindi") "मिठाई बॉक्स" else "Box of sweets"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Conversation log
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (chatMessages.isEmpty()) {
                // Empty state or suggestion starter
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(style.padding)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = "AI assistant",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (user?.language == "Hindi") "हाय, मैं आपका स्थानीय खरीदारी एआई सहायक हूँ!" else "Hi, I am your local shopping AI concierge!",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = if (style.isSimplified) 26.sp else 18.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (user?.language == "Hindi") "मुझसे रामपुर की असली दुकानों में उपलब्ध रेसिपी, उपहार या राशन के बारे में पूछें।" else "Ask me recipes, gifting ideas, or grocery items linked directly to real local shops.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        fontSize = if (style.isSimplified) 18.sp else 13.sp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = if (user?.language == "Hindi") "इनमें से कोई एक आज़माएं:" else "Tap one of these ideas to start:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (style.isSimplified) 18.sp else 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    initialSuggestions.forEach { suggestion ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    promptInput = suggestion
                                    viewModel.sendMessageToAI(suggestion)
                                    promptInput = ""
                                },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = suggestion,
                                modifier = Modifier.padding(14.dp),
                                fontSize = if (style.isSimplified) 18.sp else 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(chatMessages) { msg ->
                        ChatBubbleItem(msg, style, user, onAddToCart = { prod ->
                            viewModel.addToCart(prod.id, prod.shopId, 1)
                            Toast.makeText(context, "${prod.name} added to cart!", Toast.LENGTH_SHORT).show()
                        })
                    }

                    if (isAiLoading) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (user?.language == "Hindi") "एआई सोच रहा है..." else "AI is scanning nearby shops...",
                                    fontSize = if (style.isSimplified) 18.sp else 12.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Voice Input simulation bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Simulate Voice input microphone
                    IconButton(
                        onClick = {
                            val simulations = listOf(
                                "Today is my mom's birthday, what should I gift her?",
                                "I want to cook delicious matar paneer",
                                "Show me fresh organic potatoes and onions",
                                "Sweets like Kaju Katli for guests"
                            )
                            val randomQuery = simulations.random()
                            promptInput = randomQuery
                            Toast.makeText(context, "Voice simulation: \"$randomQuery\"", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .size(if (style.isSimplified) 56.dp else 44.dp)
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Voice input",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(if (style.isSimplified) 32.dp else 24.dp)
                        )
                    }

                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = { Text(if (user?.language == "Hindi") "यहाँ कुछ भी पूछें..." else "Ask or type here...", fontSize = if (style.isSimplified) 18.sp else 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_chat_input"),
                        shape = RoundedCornerShape(24.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (promptInput.trim().isNotEmpty()) {
                                        viewModel.sendMessageToAI(promptInput)
                                        promptInput = ""
                                        keyboardController?.hide()
                                    }
                                },
                                modifier = Modifier.testTag("ai_send_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MatchedProductRow(
    product: ProductEntity,
    shopName: String,
    style: AppUiStyle,
    user: UserEntity?,
    reason: String? = null,
    onAddToCart: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style.isSimplified) 18.sp else 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = "Shop",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(if (style.isSimplified) 16.dp else 12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = shopName,
                        fontSize = if (style.isSimplified) 14.sp else 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Rs ${product.price} / ${product.unit}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = if (style.isSimplified) 16.sp else 13.sp
                )
                if (!reason.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = reason,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = if (style.isSimplified) 14.sp else 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Button(
                onClick = onAddToCart,
                modifier = Modifier
                    .height(if (style.isSimplified) 48.dp else 34.dp)
                    .testTag("add_to_cart_chat_${product.id}"),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            ) {
                Icon(
                    Icons.Default.Add, 
                    contentDescription = "Add", 
                    modifier = Modifier.size(if (style.isSimplified) 20.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (user?.language == "Hindi") "जोड़ें" else "Add",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style.isSimplified) 16.sp else 12.sp
                )
            }
        }
    }
}

@Composable
fun ChatBubbleItem(
    message: ChatMessage,
    style: AppUiStyle,
    user: UserEntity?,
    onAddToCart: (ProductEntity) -> Unit
) {
    val isUser = message.isUser
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val alignment = if (isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Face, contentDescription = "AI", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .border(
                        if (style.isSimplified && !isUser) BorderStroke(2.dp, Color.Black) else BorderStroke(0.dp, Color.Transparent),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = if (style.isSimplified) 20.sp else 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (message.recipe != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = textColor.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Recipe: " + message.recipe.title,
                            color = textColor,
                            fontSize = if (style.isSimplified) 18.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Servings: " + message.recipe.servings,
                            color = textColor,
                            fontSize = if (style.isSimplified) 16.sp else 10.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ingredients: " + message.recipe.ingredients.joinToString(", "),
                            color = textColor,
                            fontSize = if (style.isSimplified) 16.sp else 10.sp,
                            fontWeight = FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        message.recipe.steps.forEachIndexed { idx, step ->
                            Text(
                                text = "${idx + 1}. $step",
                                color = textColor,
                                fontSize = if (style.isSimplified) 16.sp else 10.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }

                    if (message.quickReplies.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = textColor.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(4.dp))
                        message.quickReplies.forEach { qr ->
                            Button(
                                onClick = { /* TODO: Send this as user message, but for now just display */ },
                                modifier = Modifier.padding(bottom = 4.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = qr,
                                    fontSize = if (style.isSimplified) 16.sp else 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // If there are matched product entities, render them as a vertical stack of direct purchase rows!
        if (message.matchedProducts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (user?.language == "Hindi") "🛒 उपलब्ध उत्पाद:" else "🛒 Available Products:",
                        fontSize = if (style.isSimplified) 16.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Button(
                        onClick = {
                            message.matchedProducts.forEach { prod ->
                                onAddToCart(prod)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .height(if (style.isSimplified) 44.dp else 30.dp)
                            .testTag("add_all_to_cart_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = "Add All",
                            modifier = Modifier.size(if (style.isSimplified) 18.dp else 12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (user?.language == "Hindi") "सभी जोड़ें" else "Add All to Cart",
                            fontSize = if (style.isSimplified) 14.sp else 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Render each product as a direct row with an infront "Add to Cart" button!
                message.matchedProducts.forEach { prod ->
                    val shopName = when (prod.shopId) {
                        1L -> "Gupta Kirana Store"
                        2L -> "Saini Fresh Sabji Mandi"
                        3L -> "Verma Mithai Bhandar"
                        4L -> "Raju Gift & Toy Corner"
                        5L -> "Arogya Medical & Wellness"
                        6L -> "Shyam Stationery & Books"
                        7L -> "Milan Garments & Saree Palace"
                        else -> "Local Shop"
                    }
                    MatchedProductRow(
                        product = prod,
                        shopName = shopName,
                        style = style,
                        user = user,
                        reason = message.productReasons[prod.id],
                        onAddToCart = { onAddToCart(prod) }
                    )
                }
            }
        }
    }
}

// --- Cart & Checkout Screen ---

@Composable
fun CartScreen(viewModel: StoreViewModel) {
    val user by viewModel.userState.collectAsStateWithLifecycle()
    val cart by viewModel.cartState.collectAsStateWithLifecycle()
    val products by viewModel.productsState.collectAsStateWithLifecycle()
    val shops by viewModel.shopsState.collectAsStateWithLifecycle()

    val style = resolveUiStyle(user)
    val context = LocalContext.current

    var selectedPaymentMethod by remember { mutableStateOf("UPI") }
    val paymentMethods = listOf("UPI", "Cash on Delivery", "Cards/Netbanking")

    var manualAddress by remember { mutableStateOf(user?.selectedLocation ?: "") }

    val shopGroupedItems = cart.groupBy { it.shopId }

    if (cart.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = "Empty",
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (user?.language == "Hindi") "आपकी कार्ट खाली है" else "Your Cart is Empty",
                    fontSize = if (style.isSimplified) 24.sp else 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.navigateTo(Screen.Home) }) {
                    Text(if (user?.language == "Hindi") "अभी खरीदारी करें" else "Browse Shops Now")
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(style.padding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cart Items itemization grouped by Shop Name
            item {
                Text(
                    text = if (user?.language == "Hindi") "कार्ट विवरण" else "Items in Your Cart",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    fontSize = if (style.isSimplified) 26.sp else 18.sp
                )
            }

            shopGroupedItems.forEach { (shopId, items) ->
                val shop = shops.find { it.id == shopId }
                val shopName = shop?.name ?: "Local Shop"

                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                if (style.isSimplified) BorderStroke(3.dp, Color.Black) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "🛒 $shopName",
                                fontWeight = FontWeight.Bold,
                                fontSize = if (style.isSimplified) 22.sp else 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            items.forEach { item ->
                                val product = products.find { it.id == item.productId }
                                if (product != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = product.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = if (style.isSimplified) 18.sp else 14.sp
                                            )
                                            Text(
                                                text = "Rs ${product.price} / ${product.unit}",
                                                fontSize = if (style.isSimplified) 16.sp else 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.decreaseCartQuantity(product.id) },
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                                    .size(if (style.isSimplified) 48.dp else 32.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Minus", modifier = Modifier.size(if (style.isSimplified) 24.dp else 16.dp))
                                            }
                                            Text(
                                                text = item.quantity.toString(),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = if (style.isSimplified) 20.sp else 14.sp
                                            )
                                            IconButton(
                                                onClick = { viewModel.addToCart(product.id, shopId, 1) },
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                                    .size(if (style.isSimplified) 48.dp else 32.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Plus", modifier = Modifier.size(if (style.isSimplified) 24.dp else 16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Delivery Details Section
            item {
                Text(
                    text = if (user?.language == "Hindi") "डिलिवरी पता" else "Delivery Address",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    fontSize = if (style.isSimplified) 22.sp else 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualAddress,
                    onValueChange = { manualAddress = it },
                    label = { Text("Enter Landmark/House Address") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = "Location") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Payment Option Selection
            item {
                Text(
                    text = if (user?.language == "Hindi") "भुगतान विकल्प (UPI-प्रथम)" else "Payment Option (UPI First)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    fontSize = if (style.isSimplified) 22.sp else 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    paymentMethods.forEach { method ->
                        val isSelected = selectedPaymentMethod == method
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPaymentMethod = method }
                                .border(
                                    if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    RoundedCornerShape(8.dp)
                                ),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = isSelected, onClick = { selectedPaymentMethod = method })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = method,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (style.isSimplified) 20.sp else 14.sp
                                    )
                                    if (method == "UPI") {
                                        Text(
                                            text = "Pay via GPay, PhonePe, or UPI App (Recommended)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Price Details Summary
            item {
                val subtotal = cart.sumOf { item ->
                    val prod = products.find { it.id == item.productId }
                    (prod?.price ?: 0.0) * item.quantity
                }
                val deliveryCharge = 15.0 // Flat 15 Rupees delivery
                val total = subtotal + deliveryCharge

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            if (style.isSimplified) BorderStroke(2.dp, Color.Black) else BorderStroke(0.dp, Color.Transparent),
                            RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Subtotal", fontSize = if (style.isSimplified) 18.sp else 14.sp)
                            Text("Rs $subtotal", fontSize = if (style.isSimplified) 18.sp else 14.sp)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Hyperlocal Delivery Charge", fontSize = if (style.isSimplified) 18.sp else 14.sp)
                            Text("Rs $deliveryCharge", fontSize = if (style.isSimplified) 18.sp else 14.sp)
                        }
                        Divider()
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Total Amount to Pay", fontWeight = FontWeight.Bold, fontSize = if (style.isSimplified) 22.sp else 16.sp)
                            Text("Rs $total", fontWeight = FontWeight.ExtraBold, fontSize = if (style.isSimplified) 22.sp else 16.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Bottom purchase lock bar
        val totalToPay = cart.sumOf { item ->
            val prod = products.find { it.id == item.productId }
            (prod?.price ?: 0.0) * item.quantity
        } + 15.0

        Surface(
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = {
                        viewModel.checkout(selectedPaymentMethod, manualAddress)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (style.isSimplified) 64.dp else 52.dp)
                        .testTag("checkout_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (user?.language == "Hindi") "ऑर्डर प्लेस करें (Rs $totalToPay)" else "Place Order Now (Rs $totalToPay)",
                        fontSize = if (style.isSimplified) 22.sp else 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// --- Order Tracker Screen ---

@Composable
fun OrderTrackerScreen(viewModel: StoreViewModel, orderId: Long) {
    val user by viewModel.userState.collectAsStateWithLifecycle()
    val orders by viewModel.ordersState.collectAsStateWithLifecycle()
    val shops by viewModel.shopsState.collectAsStateWithLifecycle()

    val order = orders.find { it.id == orderId }
    val style = resolveUiStyle(user)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (order == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Order tracker not found.")
        }
        return
    }

    val shop = shops.find { it.id == order.shopId }

    // Live order status steps
    val statusSequence = listOf("Placed", "Accepted", "Packed", "Out for Delivery", "Delivered")
    val currentStatusIndex = statusSequence.indexOf(order.status).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(style.padding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Track Your Order #${order.id}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            fontSize = if (style.isSimplified) 26.sp else 20.sp
        )

        // Status Timeline Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    if (style.isSimplified) BorderStroke(3.dp, Color.Black) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Delivery Status: ${order.status}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = if (style.isSimplified) 22.sp else 16.sp
                )
                Text(
                    text = "Estimated delivery: 25 minutes",
                    fontSize = if (style.isSimplified) 16.sp else 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Timeline graphic representation
                statusSequence.forEachIndexed { idx, stat ->
                    val isCompleted = idx <= currentStatusIndex
                    val color = if (isCompleted) FreshGreen else MaterialTheme.colorScheme.outlineVariant
                    val icon = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = stat, tint = color, modifier = Modifier.size(if (style.isSimplified) 28.dp else 20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stat,
                            fontWeight = if (idx == currentStatusIndex) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (style.isSimplified) 20.sp else 14.sp,
                            color = if (idx == currentStatusIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    if (idx < statusSequence.size - 1) {
                        Box(
                            modifier = Modifier
                                .padding(start = if (style.isSimplified) 12.dp else 9.dp)
                                .width(2.dp)
                                .height(16.dp)
                                .background(if (idx < currentStatusIndex) FreshGreen else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
            }
        }

        // Shop Contact Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Prepared by: ${order.shopName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = if (style.isSimplified) 18.sp else 14.sp
                    )
                    Text(
                        text = "Need to add instructions? Call shopkeeper directly.",
                        fontSize = if (style.isSimplified) 16.sp else 11.sp
                    )
                }
                IconButton(
                    onClick = {
                        val phoneToCall = shop?.ownerPhone ?: "9876543210"
                        val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                            data = android.net.Uri.parse("tel:$phoneToCall")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .background(FreshGreen, CircleShape)
                        .size(48.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
                }
            }
        }

        // Action: Simulation and Order updates
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "✨ Demo Simulation Tools",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style.isSimplified) 18.sp else 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Simulate the shopkeeper accepting or packing your order below in real-time, or use the Vendor Dashboard!",
                    fontSize = if (style.isSimplified) 16.sp else 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (currentStatusIndex < statusSequence.size - 1) {
                                val nextStatus = statusSequence[currentStatusIndex + 1]
                                viewModel.updateOrderStatus(orderId, nextStatus)
                                Toast.makeText(context, "Status updated to: $nextStatus", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Order is already Delivered!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Next Status Step", fontSize = if (style.isSimplified) 16.sp else 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.navigateTo(Screen.Home)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back to Home", fontSize = if (style.isSimplified) 16.sp else 12.sp)
                    }
                }
            }
        }

        // Summary details box
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Receipt Summary",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style.isSimplified) 18.sp else 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(justify = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Payment Mode", fontSize = if (style.isSimplified) 16.sp else 12.sp)
                    Text(order.paymentMethod, fontWeight = FontWeight.Bold, fontSize = if (style.isSimplified) 16.sp else 12.sp)
                }
                Row(justify = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Address", fontSize = if (style.isSimplified) 16.sp else 12.sp)
                    Text(order.deliveryAddress, fontWeight = FontWeight.Bold, fontSize = if (style.isSimplified) 16.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                Row(justify = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Grand Total Paid", fontWeight = FontWeight.Bold, fontSize = if (style.isSimplified) 18.sp else 14.sp)
                    Text("Rs ${order.totalAmount}", fontWeight = FontWeight.ExtraBold, fontSize = if (style.isSimplified) 18.sp else 14.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun Row(justify: Arrangement.Horizontal, modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier = modifier, horizontalArrangement = justify, content = content)
}

// --- Vendor Dashboard Screen ---

@Composable
fun VendorDashboardScreen(viewModel: StoreViewModel, shopId: Long) {
    val user by viewModel.userState.collectAsStateWithLifecycle()
    val shops by viewModel.shopsState.collectAsStateWithLifecycle()
    val products by viewModel.productsState.collectAsStateWithLifecycle()
    val orders by viewModel.ordersState.collectAsStateWithLifecycle()

    val shop = shops.find { it.id == shopId }
    val storeProducts = products.filter { it.shopId == shopId }
    val storeOrders = orders.filter { it.shopId == shopId }

    val style = resolveUiStyle(user)
    val context = LocalContext.current

    if (shop == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Vendor store profile not found.")
        }
        return
    }

    var selectedVendorTab by remember { mutableStateOf("Orders") } // "Orders" or "Inventory"

    // Form inputs for adding products
    var showAddDialog by remember { mutableStateOf(false) }
    var newProdName by remember { mutableStateOf("") }
    var newProdPrice by remember { mutableStateOf("") }
    var newProdUnit by remember { mutableStateOf("packet") }
    var newProdCategory by remember { mutableStateOf("Groceries") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Vendor Header Banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(style.padding),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🏬 Shopkeeper Dashboard",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style.isSimplified) 22.sp else 16.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Managing: ${shop.name}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = if (style.isSimplified) 26.sp else 20.sp
                )
                Text(
                    text = "Update price, toggle availability, and fulfill customer orders in real-time.",
                    fontSize = if (style.isSimplified) 16.sp else 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        // Selector Tabs: Orders / Inventory
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = style.padding),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { selectedVendorTab = "Orders" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedVendorTab == "Orders") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedVendorTab == "Orders") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Orders Queue (${storeOrders.size})", fontSize = if (style.isSimplified) 18.sp else 13.sp)
            }

            Button(
                onClick = { selectedVendorTab = "Inventory" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedVendorTab == "Inventory") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedVendorTab == "Inventory") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Manage Catalog", fontSize = if (style.isSimplified) 18.sp else 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedVendorTab == "Orders") {
            // Orders Queue Content
            if (storeOrders.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.List, contentDescription = "Empty", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No orders placed for your shop yet.", fontSize = if (style.isSimplified) 18.sp else 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = style.padding),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(storeOrders) { order ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Order #${order.id}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (style.isSimplified) 20.sp else 14.sp
                                    )
                                    Text(
                                        text = order.status,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = if (style.isSimplified) 18.sp else 13.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Total value: Rs ${order.totalAmount}", fontSize = if (style.isSimplified) 16.sp else 12.sp)
                                Text("Delivery Address: ${order.deliveryAddress}", fontSize = if (style.isSimplified) 16.sp else 12.sp)

                                Spacer(modifier = Modifier.height(12.dp))

                                // Fulfill actions
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (order.status == "Placed") {
                                        Button(
                                            onClick = { viewModel.updateOrderStatus(order.id, "Accepted") },
                                            colors = ButtonDefaults.buttonColors(containerColor = FreshGreen),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Accept Order")
                                        }
                                    } else if (order.status == "Accepted") {
                                        Button(
                                            onClick = { viewModel.updateOrderStatus(order.id, "Packed") },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Mark Packed")
                                        }
                                    } else if (order.status == "Packed") {
                                        Button(
                                            onClick = { viewModel.updateOrderStatus(order.id, "Out for Delivery") },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Mark Shipped")
                                        }
                                    } else if (order.status == "Out for Delivery") {
                                        Button(
                                            onClick = { viewModel.updateOrderStatus(order.id, "Delivered") },
                                            colors = ButtonDefaults.buttonColors(containerColor = FreshGreen),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Mark Delivered")
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { /* No action needed */ },
                                            enabled = false,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Order Completed")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Inventory Management Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Add Product Button
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = style.padding, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add New Product to Catalog", fontSize = if (style.isSimplified) 18.sp else 13.sp)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(style.padding),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(storeProducts) { product ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (style.isSimplified) 20.sp else 14.sp
                                    )
                                    Text(
                                        text = "Base price: Rs ${product.price} / ${product.unit}",
                                        fontSize = if (style.isSimplified) 16.sp else 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Quick price adjustment buttons
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.updateProductPrice(product.id, (product.price - 5).coerceAtLeast(1.0)) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("- Rs 5", fontSize = 10.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { viewModel.updateProductPrice(product.id, product.price + 5) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("+ Rs 5", fontSize = 10.sp)
                                        }
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = if (product.inStock) "In Stock" else "Out of Stock",
                                        fontWeight = FontWeight.Bold,
                                        color = if (product.inStock) FreshGreen else Color.Red,
                                        fontSize = if (style.isSimplified) 16.sp else 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Switch(
                                        checked = product.inStock,
                                        onCheckedChange = { inStock ->
                                            viewModel.toggleProductStock(product.id, inStock)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Product Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Product to Shop") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newProdName,
                        onValueChange = { newProdName = it },
                        label = { Text("Product Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProdPrice,
                        onValueChange = { newProdPrice = it },
                        label = { Text("Price (Rs)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProdUnit,
                        onValueChange = { newProdUnit = it },
                        label = { Text("Unit (e.g. kg, piece)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newProdCategory,
                        onValueChange = { newProdCategory = it },
                        label = { Text("Category Group") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val priceDouble = newProdPrice.toDoubleOrNull() ?: 0.0
                        if (newProdName.trim().isNotEmpty() && priceDouble > 0.0) {
                            viewModel.addNewProduct(
                                shopId = shopId,
                                name = newProdName,
                                price = priceDouble,
                                unit = newProdUnit,
                                category = newProdCategory
                            )
                            newProdName = ""
                            newProdPrice = ""
                            showAddDialog = false
                            Toast.makeText(context, "Product added successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter valid values", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// --- SettingsScreen ---

@Composable
fun SettingsScreen(viewModel: StoreViewModel) {
    val user by viewModel.userState.collectAsStateWithLifecycle()
    val style = resolveUiStyle(user)
    val context = LocalContext.current

    val uiTiers = listOf("Modern", "Standard", "Simplified")
    val languages = listOf("English", "Hindi")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(style.padding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (user?.language == "Hindi") "ऐप सेटिंग्स" else "App Settings & Profile",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            fontSize = if (style.isSimplified) 26.sp else 20.sp
        )

        // Switch App Style Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    if (style.isSimplified) BorderStroke(3.dp, Color.Black) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (user?.language == "Hindi") "ऐप का लेआउट बदलें" else "Switch App Style / Accessibility",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style.isSimplified) 22.sp else 16.sp
                )
                Text(
                    text = "Adapt complexity based on reading speed, visual clarity, or button size comfort.",
                    fontSize = if (style.isSimplified) 16.sp else 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                uiTiers.forEach { tier ->
                    val isSelected = user?.uiTier == tier
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateUiTier(tier) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { viewModel.updateUiTier(tier) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when (tier) {
                                    "Modern" -> "Modern (For youth: vibrant, animated, dense)"
                                    "Simplified" -> "Simplified (For elderly: massive buttons & high contrast)"
                                    else -> "Standard (Conventional, easy dashboard)"
                                },
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (style.isSimplified) 20.sp else 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Language Override
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    if (style.isSimplified) BorderStroke(3.dp, Color.Black) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (user?.language == "Hindi") "भाषा बदलें" else "Select Language (भाषा)",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style.isSimplified) 22.sp else 16.sp
                )

                languages.forEach { lang ->
                    val isSelected = user?.language == lang
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.updateLanguage(lang) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSelected, onClick = { viewModel.updateLanguage(lang) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = lang,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (style.isSimplified) 20.sp else 14.sp
                        )
                    }
                }
            }
        }

        // Location Update Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    if (style.isSimplified) BorderStroke(3.dp, Color.Black) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (user?.language == "Hindi") "स्थान बदलें" else "Update Local Town / Area",
                    fontWeight = FontWeight.Bold,
                    fontSize = if (style.isSimplified) 22.sp else 16.sp
                )

                var locInput by remember { mutableStateOf(user?.selectedLocation ?: "") }
                OutlinedTextField(
                    value = locInput,
                    onValueChange = { locInput = it },
                    label = { Text("Village / Town / Pincode") },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        viewModel.updateLocation(locInput)
                        Toast.makeText(context, "Location updated successfully!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Location", fontSize = if (style.isSimplified) 18.sp else 13.sp)
                }
            }
        }

        // Firebase Sync Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = "Cloud", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cloud & Database Sync", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = if (style.isSimplified) 18.sp else 16.sp)
                }
                Text("Authentication: Simulated Google Sign-In", fontSize = if (style.isSimplified) 16.sp else 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Database Engine: Local Room DB (Firestore Ready)", fontSize = if (style.isSimplified) 16.sp else 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Status: Running in Offline Demo Mode", fontSize = if (style.isSimplified) 16.sp else 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        // Profile metadata and reset demo
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Demo Profile Stats", fontWeight = FontWeight.Bold, fontSize = if (style.isSimplified) 18.sp else 14.sp)
                Text("Logged in as: ${user?.name ?: "Guest"}", fontSize = if (style.isSimplified) 16.sp else 12.sp)
                Text("Registered Phone: +91 ${user?.phone ?: "N/A"}", fontSize = if (style.isSimplified) 16.sp else 12.sp)
                Text("Age Bracket: ${user?.ageBracket ?: "N/A"}", fontSize = if (style.isSimplified) 16.sp else 12.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        viewModel.logout()
                        Toast.makeText(context, "Logged out from demo account", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = "Log out")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Demo Account (Onboarding)", fontSize = if (style.isSimplified) 18.sp else 13.sp)
                }
            }
        }
    }
}
