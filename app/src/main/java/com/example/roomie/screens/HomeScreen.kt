package com.example.roomie.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// import androidx.compose.ui.window.Dialog // ChangeUsernameDialog está definido aquí
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.roomie.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
// import com.google.firebase.auth.ktx.auth // No es necesario si se pasa auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
// import kotlinx.coroutines.CoroutineScope // No se usa explícitamente aquí, se obtiene con rememberCoroutineScope
// import kotlinx.coroutines.Job // No se usa explícitamente
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.*
import java.util.Collections

// --- Definición de Rutas NavBar ---
sealed class BottomNavItem(val route: String, val iconResId: Int, val label: String) {
    object Home : BottomNavItem("home_overview", R.drawable.home, "Inicio")
    object Tasks : BottomNavItem("tasks", R.drawable.checksquare, "Tareas")
    object Expenses : BottomNavItem("expenses", R.drawable.dollarcircle, "Gastos")
    object Chat : BottomNavItem("chat", R.drawable.message, "Chat")
    object Shopping : BottomNavItem("shopping", R.drawable.shoppingcart, "Compras")
}

// --- Data Classes para API del Tiempo ---
data class WeatherInfo(
    val temp: Double? = null,
    val description: String? = null,
    val iconUrl: String? = null,
    val locationName: String? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class OpenWeatherMapResponse(
    @SerialName("weather") val weather: List<WeatherItem>? = null,
    @SerialName("main") val main: MainInfo? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("cod") val cod: Int? = null,
    @SerialName("message") val message: String? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class WeatherItem(
    @SerialName("description") val description: String? = null,
    @SerialName("icon") val icon: String? = null
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class MainInfo(
    @SerialName("temp") val temp: Double? = null,
    @SerialName("feels_like") val feelsLike: Double? = null,
    @SerialName("temp_min") val tempMin: Double? = null,
    @SerialName("temp_max") val tempMax: Double? = null,
    @SerialName("pressure") val pressure: Int? = null,
    @SerialName("humidity") val humidity: Int? = null
)

object NetworkClient {
    val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                })
            }
        }
    }
}

suspend fun fetchWeatherByAddress(address: String): Result<OpenWeatherMapResponse> {
    val apiKey = "9aca4a0cb3121a434c2d4ac9d5566890" // TU API KEY
    if (apiKey == "TU_API_KEY_DE_OPENWEATHERMAP" || apiKey.isBlank()) {
        return Result.failure(Exception("API Key no configurada"))
    }
    val formattedAddress = address.replace(" ", "+")
    val url = "https://api.openweathermap.org/data/2.5/weather"
    return try {
        Log.d("WeatherAPI", "Fetching weather for address: $formattedAddress")
        val response: OpenWeatherMapResponse = NetworkClient.httpClient.get(url){
            parameter("q", formattedAddress)
            parameter("appid", apiKey)
            parameter("units", "metric")
            parameter("lang", "es")
        }.body()
        Log.d("WeatherAPI", "API Response: $response")
        if (response.cod == 200 && response.weather != null && response.main != null) {
            Result.success(response)
        } else {
            Result.failure(Exception(response.message ?: "Error API: ${response.cod}, datos incompletos."))
        }
    } catch (e: io.ktor.client.plugins.ClientRequestException) {
        Log.e("WeatherAPI", "ClientRequestException fetching weather: ${e.response.status}", e)
        Result.failure(Exception("Error de cliente: ${e.response.status.value}"))
    }
    catch (e: Exception) {
        Log.e("WeatherAPI", "Error fetching weather", e)
        Result.failure(e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    pisoId: String,
    auth: FirebaseAuth,
    onLogout: () -> Unit
) {
    val db = Firebase.firestore
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUid = auth.currentUser?.uid

    var showProfileMenu by remember { mutableStateOf(false) }
    var showChangeUsernameDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
    var weatherLoading by remember { mutableStateOf(false) } // Cambio: iniciar en false si queremos mostrar datos cacheados primero
    var weatherError by remember { mutableStateOf<String?>(null) }

    var selectedRoute by remember { mutableStateOf(BottomNavItem.Home.route) }
    var isLoadingUser by remember { mutableStateOf(false) }

    val items = listOf(
        BottomNavItem.Home, BottomNavItem.Tasks, BottomNavItem.Expenses, BottomNavItem.Chat, BottomNavItem.Shopping
    )
    val navBarColor = Color(0xFF1A1A1A)
    val selectedIconColor = Color(0xFFF0B90B)
    val unselectedIconColor = Color.Gray
    val topAppBarColor = Color(0xFF1A1A1A)

    val coilImageLoader = remember {
        val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2).build()
        val okHttpClientForCoil = OkHttpClient.Builder()
            .connectionSpecs(Collections.singletonList(spec))
            .build()
        ImageLoader.Builder(context).okHttpClient(okHttpClientForCoil).build()
    }

    val updateUsername: (String) -> Unit = { newName ->
        if (currentUid != null && newName.isNotBlank()) {
            coroutineScope.launch {
                isLoadingUser = true
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()
                    auth.currentUser?.updateProfile(profileUpdates)?.await()
                    db.collection("users").document(currentUid).update("username", newName).await()
                    snackbarHostState.showSnackbar("Nombre actualizado.")
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Error updating username", e)
                    snackbarHostState.showSnackbar("Error: ${e.message}")
                } finally {
                    isLoadingUser = false
                }
            }
        } else { coroutineScope.launch { snackbarHostState.showSnackbar("Nombre inválido.") } }
    }

    // isLoadingPisoData se referirá a la carga inicial de la dirección del piso.
    // weatherLoading se referirá específicamente a la carga de la API del tiempo.
    var isLoadingPisoData by remember { mutableStateOf(true) }


    LaunchedEffect(pisoId) {
        if (pisoId.isNotBlank()) {
            // Solo mostrar carga de dirección si aún no tenemos weatherInfo (primera carga)
            if (weatherInfo == null) {
                isLoadingPisoData = true
            }
            weatherLoading = true // Siempre intentar cargar/actualizar el tiempo
            // weatherError = null // No limpiar error aquí para que persista si no se puede actualizar

            Log.d("HomeScreen", "LaunchedEffect: Cargando datos del piso y tiempo para pisoId: $pisoId")
            try {
                val pisoDoc = db.collection("pisos").document(pisoId).get().await()
                val address = pisoDoc.getString("direccion")
                isLoadingPisoData = false // Dirección (o intento) obtenida

                if (address != null && address.isNotBlank()) {
                    Log.d("HomeScreen", "Dirección: $address. Obteniendo tiempo...")
                    val result = fetchWeatherByAddress(address)
                    result.onSuccess { response ->
                        val iconCode = response.weather?.firstOrNull()?.icon
                        val constructedIconUrl = iconCode?.let { "https://openweathermap.org/img/wn/${it}@2x.png" }
                        weatherInfo = WeatherInfo(
                            temp = response.main?.temp,
                            description = response.weather?.firstOrNull()?.description?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                            iconUrl = constructedIconUrl,
                            locationName = response.name
                        )
                        weatherError = null // Limpiar error previo si la llamada fue exitosa
                        Log.d("HomeScreen", "Tiempo obtenido: $weatherInfo")
                    }.onFailure { error ->
                        // Mantener el error para mostrarlo, no limpiar weatherInfo si ya había datos
                        weatherError = "Tiempo: ${error.message?.take(100)}"
                        Log.e("HomeScreen", "Fallo al obtener el tiempo", error)
                    }
                } else {
                    weatherError = "No se encontró dirección para el piso."
                    Log.w("HomeScreen", "No se encontró dirección para $pisoId")
                }
            } catch (e: Exception) {
                weatherError = "Error buscando dirección del piso."
                Log.e("HomeScreen", "Error al obtener datos del piso $pisoId", e)
                isLoadingPisoData = false // Asegurar que la carga de piso termina
            } finally {
                weatherLoading = false // Carga del tiempo (intento) finalizada
            }
        } else {
            weatherError = "ID de piso inválido."
            isLoadingPisoData = false
            weatherLoading = false
            Log.w("HomeScreen", "pisoId está vacío.")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.roomielogonaranja),
                        contentDescription = "Roomie Logo Naranja",
                        modifier = Modifier.height(48.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = topAppBarColor),
                actions = {
                    Box {
                        IconButton(onClick = { showProfileMenu = true }) {
                            Icon( Icons.Filled.AccountCircle, "Menú perfil", tint = selectedIconColor)
                        }
                        DropdownMenu( expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false }) {
                            DropdownMenuItem( text = { Text("Cambiar nombre") }, onClick = { showProfileMenu = false; showChangeUsernameDialog = true }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                            DropdownMenuItem( text = { Text("Cerrar sesión") }, onClick = { showProfileMenu = false; onLogout() }, leadingIcon = { Icon(Icons.Filled.ExitToApp, null) })
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = navBarColor) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Image( painter = painterResource(id = item.iconResId), contentDescription = item.label, modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(if (selectedRoute == item.route) selectedIconColor else unselectedIconColor)) },
                        selected = selectedRoute == item.route,
                        onClick = {
                            if (selectedRoute != item.route) {
                                when (item) {
                                    BottomNavItem.Chat -> {
                                        if (pisoId.isNotBlank()) {
                                            navController.navigate("chat/$pisoId")
                                        } else {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("No se puede abrir el chat sin un piso.")
                                            }
                                        }
                                    }
                                    else -> selectedRoute = item.route
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = navBarColor)
                    )
                }
            }
        },
        containerColor = Color(0xFF222222)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (selectedRoute) {
                BottomNavItem.Home.route -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sección Tiempo - Restaurar la altura original si es necesario
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.3f) // Altura original que tenías
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            WeatherWidget(weatherInfo, weatherLoading, weatherError, coilImageLoader)
                        }
                        Divider(color = Color(0xFFF0B433), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        // Sección Resumen
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Mostrar carga del resumen solo si isLoadingPisoData es true Y weatherInfo es null (primera carga general)
                            if(isLoadingPisoData && weatherInfo == null) {
                                CircularProgressIndicator(color=selectedIconColor)
                            } else {
                                Text("Resumen del Piso", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("(Tareas, Gastos, etc.)", color = Color.Gray)
                            }
                        }
                    }
                }
                BottomNavItem.Tasks.route -> {
                    TaskListScreen( pisoId = pisoId, navController = navController, auth = auth )
                }
                BottomNavItem.Expenses.route -> {
                    ExpensesScreen(pisoId = pisoId, navController = navController, auth = auth)
                }
                BottomNavItem.Shopping.route -> CenteredText("Contenido Compras (Próximamente)")
            }
        }
    }

    if (showChangeUsernameDialog) {
        ChangeUsernameDialog(
            currentUsername = auth.currentUser?.displayName ?: "",
            onDismiss = { showChangeUsernameDialog = false },
            onConfirm = { newName -> showChangeUsernameDialog = false; updateUsername(newName) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeUsernameDialog(currentUsername: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newUsername by remember(currentUsername) { mutableStateOf(currentUsername) }
    var isLoadingDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoadingDialog) onDismiss() },
        title = { Text("Cambiar Nombre") },
        text = {
            OutlinedTextField(
                value = newUsername,
                onValueChange = { newUsername = it },
                label = { Text("Nuevo nombre") },
                singleLine = true,
                enabled = !isLoadingDialog,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    isLoadingDialog = true
                    onConfirm(newUsername)
                },
                enabled = newUsername.isNotBlank() && newUsername != currentUsername && !isLoadingDialog
            ) {
                if(isLoadingDialog) CircularProgressIndicator(modifier = Modifier.size(24.dp), color=MaterialTheme.colorScheme.onPrimary) else Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoadingDialog) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun WeatherWidget(weatherInfo: WeatherInfo?, isLoading: Boolean, error: String?, imageLoader: ImageLoader) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp), // Altura mínima como antes
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Lógica de visualización original del WeatherWidget
            when {
                isLoading && weatherInfo == null -> CircularProgressIndicator(color = Color(0xFFF0B90B), modifier = Modifier.size(40.dp)) // Muestra carga solo si no hay datos aún
                error != null -> Text(error, color = Color(0xFFF44336), textAlign = TextAlign.Center, fontSize = 13.sp)
                weatherInfo != null -> {
                    Row( verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth() ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(weatherInfo.iconUrl).crossfade(true).error(R.drawable.ic_broken_image).placeholder(R.drawable.loading_img).build(),
                            contentDescription = weatherInfo.description ?: "Icono tiempo",
                            imageLoader = imageLoader,
                            modifier = Modifier.size(60.dp).background(Color.Transparent, CircleShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text( weatherInfo.locationName ?: "-", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text( weatherInfo.temp?.let { "%.0f°C".format(it) } ?: "--°C", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text( weatherInfo.description ?: "-", fontSize = 13.sp, color = Color.LightGray)
                        }
                    }
                }
                else -> Text("Cargando datos...", color = Color.Gray, fontSize = 13.sp) // Texto por defecto si no está cargando, no hay error y no hay info
            }
        }
    }
}

@Composable
fun BoxScope.CenteredText(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(16.dp)
    )
}