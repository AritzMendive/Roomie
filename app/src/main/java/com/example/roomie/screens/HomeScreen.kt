package com.example.roomie.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.navigation.NavController
import coil.ImageLoader // <- Import para Coil ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.roomie.R
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import io.ktor.client.*
import io.ktor.client.call.*
// import io.ktor.client.engine.android.* // <- Ya no usamos este
import io.ktor.client.engine.okhttp.* // <- Import para el motor OkHttp
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec // <- Imports para OkHttp y TLS
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.util.Collections
import java.util.Locale


// Define las rutas para cada sección de la NavBar (sin cambios)
sealed class BottomNavItem(val route: String, val iconResId: Int, val label: String) {
    object Home : BottomNavItem("home_overview", R.drawable.home, "Inicio")
    object Tasks : BottomNavItem("tasks", R.drawable.checksquare, "Tareas")
    object Expenses : BottomNavItem("expenses", R.drawable.dollarcircle, "Gastos")
    object Chat : BottomNavItem("chat", R.drawable.message, "Chat")
    object Shopping : BottomNavItem("shopping", R.drawable.shoppingcart, "Compras")
}

// --- Data class para el estado del tiempo simplificado ---
data class WeatherInfo(
    val temp: Double? = null,
    val description: String? = null,
    val iconUrl: String? = null, // URL completa del icono
    val locationName: String? = null
)

// --- Data classes para parsear la respuesta COMPLETA de OpenWeatherMap ---
@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class OpenWeatherMapResponse(
    @SerialName("weather") val weather: List<WeatherItem>? = null,
    @SerialName("main") val main: MainInfo? = null,
    @SerialName("name") val name: String? = null, // Nombre de la ciudad/localización
    @SerialName("cod") val cod: Int? = null, // Código de respuesta (200 = OK)
    @SerialName("message") val message: String? = null // Mensaje de error de la API
)

@SuppressLint("UnsafeOptInUsageError")
@Serializable
data class WeatherItem(
    @SerialName("description") val description: String? = null,
    @SerialName("icon") val icon: String? = null // ej: "04d"
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    pisoId: String
) {
    val db = Firebase.firestore
    val context = LocalContext.current // <- Obtener contexto para ImageLoader

    // --- Estados para el tiempo ---
    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
    var weatherLoading by remember { mutableStateOf(false) }
    var weatherError by remember { mutableStateOf<String?>(null) }

    // Estado para la ruta seleccionada
    var selectedRoute by remember { mutableStateOf(BottomNavItem.Home.route) }

    // Lista de items para la barra de navegación
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Tasks,
        BottomNavItem.Expenses,
        BottomNavItem.Chat,
        BottomNavItem.Shopping
    )

    // Colores
    val navBarColor = Color(0xFF1A1A1A)
    val selectedIconColor = Color(0xFFF0B90B)
    val unselectedIconColor = Color.Gray
    val topAppBarColor = Color(0xFF1A1A1A)


    // --- INICIO: Código añadido para forzar TLS 1.2/1.3 ---
    val customOkHttpClient = remember {
        // Especificar que solo se usen TLS 1.3 y 1.2
        val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2) // Forzar versiones
            .build()

        OkHttpClient.Builder()
            .connectionSpecs(Collections.singletonList(spec)) // Aplicar la especificación
            .build()
    }

    val ktorClient = remember {
        HttpClient(OkHttp) { // Usar el motor OkHttp
            engine {
                preconfigured = customOkHttpClient // Pasar el cliente OkHttp
            }
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
        }
    }

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .okHttpClient(customOkHttpClient) // Usar el mismo OkHttpClient
            .build()
    }
    // --- FIN: Código añadido para forzar TLS 1.2/1.3 ---


    // Asegúrate de cerrar el cliente Ktor cuando el Composable se destruya
    DisposableEffect(Unit) {
        onDispose {
            Log.d("HomeScreen", "Closing Ktor client")
            ktorClient.close() // Solo cerramos Ktor, OkHttp lo gestiona Coil/Ktor
        }
    }


    // --- Efecto para cargar la dirección y el tiempo ---
    // Usa el ktorClient creado arriba
    LaunchedEffect(pisoId, ktorClient) {
        if (pisoId.isNotBlank()) {
            weatherLoading = true
            weatherError = null
            weatherInfo = null // Resetear
            Log.d("HomeScreen", "Fetching address for pisoId: $pisoId")

            try {
                val pisoDoc = db.collection("pisos").document(pisoId).get().await()
                val address = pisoDoc.getString("direccion") // Obtiene la dirección de Firestore

                if (address != null && address.isNotBlank()) {
                    Log.d("HomeScreen", "Address found: $address. Fetching weather...")
                    val apiKey = "9aca4a0cb3121a434c2d4ac9d5566890"

                    if (apiKey.isBlank()) {
                        weatherError = "API Key de OpenWeatherMap no configurada."
                        weatherLoading = false
                        return@LaunchedEffect
                    }

                    try {
                        // 2. Llamar a OpenWeatherMap usando el ktorClient configurado
                        val url = "https://api.openweathermap.org/data/2.5/weather"
                        Log.d("HomeScreen", "3. Llamando a URL: $url con q=$address")
                        val response: OpenWeatherMapResponse = ktorClient.get(url) {
                            parameter("q", address)
                            parameter("appid", apiKey)
                            parameter("units", "metric")
                            parameter("lang", "es")
                        }.body()

                        Log.d("HomeScreen", "4. Respuesta JSON recibida (primeros 500 chars): ${response.toString().take(500)}")
                        Log.d("HomeScreen", "API Response Code: ${response.cod}, Message: ${response.message}")

                        if (response.cod == 200 && response.main != null && response.weather != null) {
                            val iconCode = response.weather.firstOrNull()?.icon
                            val constructedIconUrl = iconCode?.let { "https://openweathermap.org/img/wn/${it}@2x.png" }
                            Log.d("HomeScreen", "Icon Code: '$iconCode', Constructed Icon URL: '$constructedIconUrl'") // Log URL construida

                            weatherInfo = WeatherInfo(
                                temp = response.main.temp,
                                description = response.weather.firstOrNull()?.description,
                                iconUrl = constructedIconUrl, // Guardar URL construida
                                locationName = response.name
                            )
                            Log.d("HomeScreen", "Weather fetched successfully: $weatherInfo")
                        } else {
                            Log.e("HomeScreen", "API Error: Code=${response.cod}, Message=${response.message ?: "Unknown error"}")
                            weatherError = response.message ?: "Error de la API del tiempo (código ${response.cod})"
                        }

                    } catch (networkError: Exception) {
                        Log.e("HomeScreen", "Error fetching/parsing weather from API", networkError)
                        weatherError = "No se pudo obtener el tiempo: ${networkError.message}"
                    }

                } else {
                    Log.w("HomeScreen", "Address not found or empty for pisoId: $pisoId")
                    weatherError = "No se encontró la dirección del piso."
                }
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error fetching piso address from Firestore", e)
                weatherError = "Error al buscar dirección: ${e.message}"
            } finally {
                weatherLoading = false
            }
        } else {
            weatherError = "ID de piso inválido."
            weatherLoading = false
        }
    }


    Scaffold(
        topBar = { /* ... (igual que antes) ... */
            CenterAlignedTopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.roomielogonaranja), // Logo
                        contentDescription = "Roomie Logo Naranja",
                        modifier = Modifier.height(48.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = topAppBarColor
                )
            )
        },
        bottomBar = { /* ... (igual que antes) ... */
            NavigationBar(containerColor = navBarColor) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = {
                            Image(
                                painter = painterResource(id = item.iconResId),
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp),
                                colorFilter = ColorFilter.tint(
                                    if (selectedRoute == item.route) selectedIconColor else unselectedIconColor
                                )
                            )
                        },
                        selected = selectedRoute == item.route,
                        onClick = {
                            if (selectedRoute != item.route) { // Evita recargar si ya está seleccionada
                                selectedRoute = item.route
                                Log.d("HomeScreen", "Navigating to: $selectedRoute")
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = navBarColor // O color deseado para el indicador
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFF222222) // Color de fondo general
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding) // Aplicar padding del Scaffold
                .fillMaxSize()
        ) {
            // --- CONTENIDO PRINCIPAL ---
            // --- Mostrar contenido según la ruta seleccionada ---
            when (selectedRoute) {
                BottomNavItem.Home.route -> {
                    // --- Contenido de la Pestaña Home ---
                    Column( // Columna principal para organizar toda la pestaña Home
                        modifier = Modifier
                            .fillMaxSize()
                        // Quitar el padding general de aquí si lo aplicas por secciones
                    ) {
                        // --- Sección Superior: Tiempo ---
                        Column( // Contenedor para el tiempo
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.2f) // <-- Ocupa el 40% de la altura disponible (ajusta < 0.5f)
                                .padding(16.dp), // Padding para la sección del tiempo
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center // Centrar el widget verticalmente si hay espacio extra
                        ) {


                            // Widget del Tiempo
                            WeatherWidget(
                                weatherInfo = weatherInfo,
                                isLoading = weatherLoading,
                                error = weatherError,
                                imageLoader = imageLoader
                            )
                        } // --- Fin Sección Superior ---

                        // --- Separador Opcional ---
                        Divider(color = Color(0xFFF0B433), thickness = 2.dp, modifier = Modifier.padding(horizontal = 16.dp))

                        // --- Sección Inferior: Resto del Contenido ---
                        Column( // Contenedor para el resto del resumen
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f) // Ocupa el espacio restante
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Resumen del Piso",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Aquí podrías mostrar un resumen de tareas, gastos, etc.", color = Color.Gray)
                            // ... (LazyColumn o más contenido aquí) ...
                        } // --- Fin Sección Inferior ---

                    } // --- Fin Columna Principal ---
                } // Fin caso Home

                BottomNavItem.Tasks.route -> TaskListScreen(
                    pisoId = pisoId,
                    navController = navController
                )

                // ... (otros casos: Expenses, Chat, Shopping) ...
                BottomNavItem.Expenses.route -> CenteredText("Contenido Gastos (Próximamente)")
                BottomNavItem.Chat.route -> CenteredText("Contenido Chat (Próximamente)")
                BottomNavItem.Shopping.route -> CenteredText("Contenido Compras (Próximamente)")

            } // Fin when
        }
    }
}

// --- Composable auxiliar para texto centrado ---
@Composable
fun BoxScope.CenteredText(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.align(Alignment.Center)
    )
}


// --- MODIFICADO: Composable WeatherWidget ahora acepta ImageLoader ---
@Composable
fun WeatherWidget(
    weatherInfo: WeatherInfo?,
    isLoading: Boolean,
    error: String?,
    imageLoader: ImageLoader // <-- ACEPTA EL PARÁMETRO
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF303030))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(color = Color(0xFFF0B90B))
                }
                error != null -> {
                    Text(
                        text = "Tiempo no disponible:\n$error",
                        color = Color(0xFFF44336),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
                weatherInfo != null -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Icono del tiempo (usando Coil y el imageLoader personalizado)
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(weatherInfo.iconUrl)
                                .crossfade(true)
                                .error(R.drawable.ic_broken_image)
                                .placeholder(R.drawable.loading_img)
                                .listener( // Listener para depurar
                                    onStart = { request ->
                                        Log.d("Coil", "Iniciando carga de imagen: ${request.data}")
                                    },
                                    onSuccess = { request, result ->
                                        Log.d("Coil", "Imagen cargada con éxito: ${request.data} (DataSource: ${result.dataSource})")
                                    },
                                    onError = { request, result ->
                                        Log.e("Coil", "Error cargando imagen: ${request.data}", result.throwable)
                                    }
                                )
                                .build(),
                            contentDescription = weatherInfo.description ?: "Icono del tiempo",
                            imageLoader = imageLoader, // <-- USA EL LOADER PERSONALIZADO
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.Transparent, CircleShape),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = weatherInfo.locationName ?: "Ubicación",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = weatherInfo.temp?.let { "%.1f°C".format(it) } ?: "--°C",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Light,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = weatherInfo.description?.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                                } ?: "Desconocido",
                                fontSize = 14.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }
                else -> {
                    Text("Obteniendo tiempo...", color = Color.Gray)
                }
            }
        }
    }
}