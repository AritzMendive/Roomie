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
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.roomie.R // <-- ASEGÚRATE QUE ESTA IMPORTACIÓN ES CORRECTA
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.*
import java.util.Collections // Import necesario para Collections.singletonList

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

// --- Cliente Ktor (Ejemplo Básico) ---
// Es mejor crear una instancia Singleton o inyectarla con Hilt/Koin
// pero para este ejemplo lo ponemos aquí.
val httpClient = HttpClient(OkHttp) { // Puedes cambiar CIO por OkHttp si prefieres
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = true
        })
    }
    // Puedes añadir más configuración: timeouts, logging, etc.
}

// --- Función para obtener el tiempo (Ejemplo) ---
suspend fun fetchWeatherByAddress(address: String): Result<OpenWeatherMapResponse> {
    // ¡IMPORTANTE! Guarda tu API Key de forma segura, no la pongas aquí.
    // Usa BuildConfig, Firebase Remote Config, o un backend.
    val apiKey = "9aca4a0cb3121a434c2d4ac9d5566890" // <-- REEMPLAZA ESTO DE FORMA SEGURA
    if (apiKey == "TU_API_KEY_DE_OPENWEATHERMAP" || apiKey.isBlank()) {
        return Result.failure(Exception("API Key no configurada"))
    }

    val formattedAddress = address.replace(" ", "+")
    val url = "https://api.openweathermap.org/data/2.5/weather"
    return try {
        Log.d("WeatherAPI", "Fetching weather for address: $formattedAddress")
        val response: OpenWeatherMapResponse = httpClient.get(url){
            parameter("q", formattedAddress)
            parameter("appid", apiKey)
            parameter("units", "metric")
            parameter("lang", "es")
        }.body()
        Log.d("WeatherAPI", "API Response: $response")
        if (response.cod == 200) {
            Result.success(response)
        } else {
            Result.failure(Exception(response.message ?: "Error API: ${response.cod}"))
        }
    } catch (e: Exception) {
        Log.e("WeatherAPI", "Error fetching weather", e)
        Result.failure(e)
    }
}


// --- Composable Principal HomeScreen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    pisoId: String,
    auth: FirebaseAuth,        // <-- Recibe FirebaseAuth
    onLogout: () -> Unit       // <-- Recibe Callback para cerrar sesión
) {
    val db = Firebase.firestore
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUid = auth.currentUser?.uid

    // --- Estados para el menú y diálogo ---
    var showProfileMenu by remember { mutableStateOf(false) }
    var showChangeUsernameDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Estados para el tiempo ---
    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
    var weatherLoading by remember { mutableStateOf(false) }
    var weatherError by remember { mutableStateOf<String?>(null) }

    // Estado para la ruta seleccionada
    var selectedRoute by remember { mutableStateOf(BottomNavItem.Home.route) }

    var isLoadingUser by remember { mutableStateOf(true) }
    // Lista de items para la barra de navegación
    val items = listOf(
        BottomNavItem.Home, BottomNavItem.Tasks, BottomNavItem.Expenses, BottomNavItem.Chat, BottomNavItem.Shopping
    )
    // Colores
    val navBarColor = Color(0xFF1A1A1A)
    val selectedIconColor = Color(0xFFF0B90B)
    val unselectedIconColor = Color.Gray
    val topAppBarColor = Color(0xFF1A1A1A)

    // --- Ktor/Coil OkHttpClient con TLS (Mantenido por si acaso) ---
    val customOkHttpClient = remember {
        val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2).build()
        OkHttpClient.Builder().connectionSpecs(Collections.singletonList(spec)).build()
    }
    // Reconfigurar Ktor y Coil para usarlo si es necesario (aunque con CIO puede no serlo)
    val ktorClient = remember { HttpClient(OkHttp) { // Usando OkHttp aquí
        engine { preconfigured = customOkHttpClient }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; isLenient = true }) }
    } }
    val imageLoader = remember { ImageLoader.Builder(context).okHttpClient(customOkHttpClient).build() }
    DisposableEffect(Unit) { onDispose { ktorClient.close() } }


    // --- Lógica para actualizar nombre de usuario ---
    val updateUsername: (String) -> Unit = { newName ->
        if (currentUid != null && newName.isNotBlank()) {
            coroutineScope.launch {
                isLoadingUser = true // Mostrar carga mientras se actualiza
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

    // Estado de carga general (para Firestore y API del tiempo)
    var isLoading by remember { mutableStateOf(true) } // Inicia en true para la carga inicial

    // --- Efecto para cargar la dirección y el tiempo ---
    LaunchedEffect(pisoId, ktorClient) {
        if (pisoId.isNotBlank()) {
            isLoading = true // Carga general
            weatherLoading = true
            weatherError = null
            weatherInfo = null
            Log.d("HomeScreen", "Fetching address for pisoId: $pisoId")
            try {
                val pisoDoc = db.collection("pisos").document(pisoId).get().await()
                val address = pisoDoc.getString("direccion")
                if (address != null && address.isNotBlank()) {
                    val result = fetchWeatherByAddress(address) // Llama a la función API
                    result.onSuccess { response ->
                        val iconCode = response.weather?.firstOrNull()?.icon
                        val constructedIconUrl = iconCode?.let { "https://openweathermap.org/img/wn/${it}@2x.png" }
                        weatherInfo = WeatherInfo(
                            temp = response.main?.temp,
                            description = response.weather?.firstOrNull()?.description,
                            iconUrl = constructedIconUrl,
                            locationName = response.name
                        )
                        Log.d("HomeScreen", "Weather fetched successfully: $weatherInfo")
                    }.onFailure { error ->
                        weatherError = "Tiempo: ${error.message?.take(50)}" // Mensaje más corto
                        Log.e("HomeScreen", "Weather fetch failed", error)
                    }
                } else {
                    weatherError = "No se encontró dirección."
                }
            } catch (e: Exception) {
                weatherError = "Error buscando dirección."
                Log.e("HomeScreen", "Error fetching piso address", e)
            } finally {
                weatherLoading = false
                isLoading = false // Termina la carga general
            }
        } else {
            weatherError = "ID de piso inválido."
            isLoading = false
            weatherLoading = false
        }
    }

    // --- UI ---
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
                actions = { // Acciones con Menú Perfil
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
        bottomBar = { // Barra de Navegación Inferior
            NavigationBar(containerColor = navBarColor) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Image( painter = painterResource(id = item.iconResId), contentDescription = item.label, modifier = Modifier.size(24.dp), colorFilter = ColorFilter.tint(if (selectedRoute == item.route) selectedIconColor else unselectedIconColor)) },
                        selected = selectedRoute == item.route,
                        onClick = {
                            if (selectedRoute != item.route) {
                                // --- CAMBIO AQUÍ ---
                                // Navega a la ruta correspondiente
                                when (item) {
                                    BottomNavItem.Home -> selectedRoute = item.route // Actualiza estado para la UI interna de HomeScreen
                                    BottomNavItem.Tasks -> selectedRoute = item.route // Usa el TaskListScreen interno
                                    BottomNavItem.Chat -> {
                                        // Navega a la pantalla externa ChatScreen
                                        if (pisoId.isNotBlank()) { // Asegúrate de tener pisoId
                                            navController.navigate("chat/$pisoId")
                                        } else {
                                            // Opcional: Mostrar un mensaje si no hay pisoId
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("No se puede abrir el chat sin un piso.")
                                            }
                                        }
                                        // No cambies 'selectedRoute' aquí si navegas fuera
                                        // O podrías querer mantener el estado visual, depende de tu lógica
                                        // selectedRoute = item.route
                                    }
                                    // Añade casos para Expenses, Shopping si navegan fuera
                                    else -> selectedRoute = item.route // Para los que se quedan en HomeScreen
                                }
                                // --- FIN CAMBIO ---
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = navBarColor)
                    )
                }
            }
        },
        containerColor = Color(0xFF222222) // Fondo
    ) { innerPadding ->
        Box( // Contenedor Principal del Contenido
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // --- Selector de Contenido ---
            when (selectedRoute) {
                BottomNavItem.Home.route -> { // Pestaña Inicio
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sección Tiempo
                        Column( modifier = Modifier.fillMaxWidth().fillMaxHeight(0.3f).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center ) {
                            WeatherWidget(weatherInfo, weatherLoading, weatherError, imageLoader)
                        }
                        Divider(color = Color(0xFFF0B433), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                        // Sección Resumen
                        Column( modifier = Modifier.fillMaxWidth().weight(1f).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally ) {
                            if(isLoading) { // Muestra carga si todavía está buscando datos
                                CircularProgressIndicator(color=selectedIconColor)
                            } else {
                                Text("Resumen del Piso", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("(Tareas, Gastos, etc.)", color = Color.Gray)
                                // Aquí iría el contenido real del resumen
                            }
                        }
                    }
                }
                BottomNavItem.Tasks.route -> { // Pestaña Tareas
                    val authInstance = Firebase.auth // Obtener instancia aquí o pasarla
                    TaskListScreen( pisoId = pisoId, navController = navController, auth = authInstance )
                }
                BottomNavItem.Expenses.route -> CenteredText("Contenido Gastos (Próximamente)")
                BottomNavItem.Chat.route -> CenteredText("Contenido Chat (Próximamente)")
                BottomNavItem.Shopping.route -> CenteredText("Contenido Compras (Próximamente)")
            }
        } // Fin Box Contenido Principal
    } // Fin Scaffold

    // --- Diálogo Cambio Nombre (fuera del Scaffold) ---
    if (showChangeUsernameDialog) {
        ChangeUsernameDialog(
            currentUsername = auth.currentUser?.displayName ?: "",
            onDismiss = { showChangeUsernameDialog = false },
            onConfirm = { newName -> showChangeUsernameDialog = false; updateUsername(newName) }
        )
    }
} // Fin HomeScreen

// --- Composable ChangeUsernameDialog ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeUsernameDialog(currentUsername: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newUsername by remember(currentUsername) { mutableStateOf(currentUsername) }
    var isLoadingDialog by remember { mutableStateOf(false) } // Estado de carga para el diálogo

    AlertDialog(
        onDismissRequest = { if (!isLoadingDialog) onDismiss() }, // No cerrar si está cargando
        title = { Text("Cambiar Nombre") },
        text = {
            OutlinedTextField(
                value = newUsername,
                onValueChange = { newUsername = it },
                label = { Text("Nuevo nombre") },
                singleLine = true,
                enabled = !isLoadingDialog, // Deshabilitar campo mientras carga
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    isLoadingDialog = true // Iniciar carga
                    onConfirm(newUsername)
                    // isLoadingDialog volverá a false implícitamente cuando se cierre el diálogo
                    // o si la función onConfirm gestionara el estado de carga.
                    // Para simplicidad, no lo manejamos aquí explícitamente.
                },
                enabled = newUsername.isNotBlank() && newUsername != currentUsername && !isLoadingDialog // Habilitar si es válido y no está cargando
            ) {
                if(isLoadingDialog) CircularProgressIndicator(modifier = Modifier.size(24.dp), color=MaterialTheme.colorScheme.onPrimary) else Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoadingDialog) { // Deshabilitar si está cargando
                Text("Cancelar")
            }
        }
    )
}


// --- Composable WeatherWidget ---
@Composable
fun WeatherWidget(weatherInfo: WeatherInfo?, isLoading: Boolean, error: String?, imageLoader: ImageLoader) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp), // Altura mínima
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)) // Un poco más oscuro
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp), // Padding reducido
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = Color(0xFFF0B90B), modifier = Modifier.size(40.dp))
                error != null -> Text(error, color = Color(0xFFF44336), textAlign = TextAlign.Center, fontSize = 13.sp)
                weatherInfo != null -> {
                    Row( verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth() ) {
                        AsyncImage( // Icono
                            model = ImageRequest.Builder(LocalContext.current).data(weatherInfo.iconUrl).crossfade(true).error(R.drawable.ic_broken_image).placeholder(R.drawable.loading_img).build(),
                            contentDescription = weatherInfo.description ?: "Icono tiempo",
                            imageLoader = imageLoader,
                            modifier = Modifier.size(60.dp).background(Color.Transparent, CircleShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { // Columna para textos
                            Text( weatherInfo.locationName ?: "-", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White) // Ubicación más pequeña
                            Spacer(modifier = Modifier.height(2.dp))
                            Text( weatherInfo.temp?.let { "%.0f°C".format(it) } ?: "--°C", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White) // Temp sin decimales
                            Text( weatherInfo.description?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: "-", fontSize = 13.sp, color = Color.LightGray) // Descripción
                        }
                    }
                }
                else -> Text("Cargando datos...", color = Color.Gray, fontSize = 13.sp)
            }
        }
    }
}

// --- Composable CenteredText ---
@Composable
fun BoxScope.CenteredText(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 18.sp, // Ligeramente más pequeño
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center).padding(16.dp)
    )
}