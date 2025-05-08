package com.example.roomie.screens

// --- Imports ---
import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apartment // Icono para volver a Profile
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
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.roomie.R // Asegúrate que tus recursos drawable estén aquí
import com.example.roomie.screens.ui.theme.SyneFontFamily
import com.google.firebase.Timestamp // Importar Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject // Importar para convertir documentos
import com.google.firebase.ktx.Firebase
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections

// --- Data Classes para Modelos (Asegúrate que estén definidas o impórtalas) ---
// Estas son definiciones básicas, usa las tuyas si son más completas

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
    @SerialName("temp") val temp: Double? = null
    // otros campos no se usan actualmente en la UI
)

// --- Cliente Ktor Global ---
object NetworkClient {
    val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) { // Usar OkHttp que ya tienes configurado para Coil
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                })
            }
            // Puedes añadir configuración del engine aquí si es necesario
        }
    }
}

// --- Función para obtener el tiempo ---
suspend fun fetchWeatherByAddress(address: String): Result<OpenWeatherMapResponse> {
    val apiKey = "9aca4a0cb3121a434c2d4ac9d5566890"
    if (apiKey.isBlank()) {
        return Result.failure(Exception("API Key no configurada"))
    }
    val formattedAddress = address.replace(" ", "+")
    val url = "https://api.openweathermap.org/data/2.5/weather"
    return try {
        Log.d("WeatherAPI", "Fetching weather for address: $formattedAddress")
        val response: OpenWeatherMapResponse = NetworkClient.httpClient.get(url) {
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
        Result.failure(Exception("Error de cliente API: ${e.response.status.value}"))
    } catch (e: Exception) {
        Log.e("WeatherAPI", "Error fetching weather", e)
        Result.failure(e)
    }
}

// --- Data Classes para Resúmenes ---
data class TaskSummary(
    val pendingTasksForUser: Int = 0,
    val totalPendingTasks: Int = 0,
    val nextDueTaskTitle: String? = null,
    val nextDueDate: Timestamp? = null
)

data class ExpenseSummary(
    val totalOwedToUser: Double = 0.0,
    val totalUserOwes: Double = 0.0
)

// --- Composable Principal HomeScreen ---
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
    val currentUid = auth.currentUser?.uid ?: ""

    var showProfileMenu by remember { mutableStateOf(false) }
    var showChangeUsernameDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
    var weatherError by remember { mutableStateOf<String?>(null) }
    var selectedRoute by remember { mutableStateOf(BottomNavItem.Home.route) }
    var isLoadingUser by remember { mutableStateOf(false) }
    var taskSummary by remember { mutableStateOf<TaskSummary?>(null) }
    var expenseSummary by remember { mutableStateOf<ExpenseSummary?>(null) }
    // Estado de carga unificado: true inicialmente, false después de la primera carga de todos los listeners
    var isLoadingContent by remember { mutableStateOf(true) }
    // Estados para saber si cada listener ha cargado datos por primera vez
    var initialWeatherLoaded by remember { mutableStateOf(false) }
    var initialTasksLoaded by remember { mutableStateOf(false) }
    var initialExpensesLoaded by remember { mutableStateOf(false) }
    var pisoDisplayName by remember { mutableStateOf<String?>(null) }


    val items = listOf(
        BottomNavItem.Home, BottomNavItem.Tasks, BottomNavItem.Expenses, BottomNavItem.Chat, BottomNavItem.Shopping
    )
    val navBarColor = Color(0xFF1A1A1A)
    val selectedIconColor = Color(0xFFF0B433)
    val unselectedIconColor = Color.Gray
    val topAppBarColor = Color(0xFF1A1A1A)

    // ImageLoader para Coil (usado en WeatherWidget)
    val coilImageLoader = remember {
        val spec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2).build()
        val okHttpClientForCoil = OkHttpClient.Builder()
            .connectionSpecs(Collections.singletonList(spec))
            .build()
        ImageLoader.Builder(context).okHttpClient(okHttpClientForCoil).build()
    }

    // Función para actualizar el nombre de usuario
    val updateUsername: (String) -> Unit = { newName ->
        if (currentUid.isNotBlank() && newName.isNotBlank()) {
            coroutineScope.launch {
                isLoadingUser = true
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(newName).build()
                    auth.currentUser?.updateProfile(profileUpdates)?.await()
                    db.collection("users").document(currentUid).update("username", newName).await()
                    snackbarHostState.showSnackbar("Nombre actualizado.")
                } catch (e: Exception) {
                    Log.e("HomeScreen", "Error updating username", e)
                    snackbarHostState.showSnackbar("Error al actualizar nombre: ${e.message}")
                } finally {
                    isLoadingUser = false
                }
            }
        } else { coroutineScope.launch { snackbarHostState.showSnackbar("Nombre inválido.") } }
    }

    // Efecto para cargar todo el contenido inicial (tiempo, resúmenes)
    LaunchedEffect(pisoId, currentUid) {
        if (pisoId.isBlank() || currentUid.isBlank()) {
            weatherError = "ID de piso o usuario inválido."
            isLoadingContent = false
            Log.w("HomeScreen", "pisoId o currentUid está vacío.")
            taskSummary = TaskSummary()
            expenseSummary = ExpenseSummary()
            pisoDisplayName = null // Limpiar nombre
            return@LaunchedEffect
        }

        isLoadingContent = true
        initialWeatherLoaded = false
        initialTasksLoaded = false
        initialExpensesLoaded = false
        taskSummary = null
        expenseSummary = null
        weatherInfo = null
        weatherError = null
        pisoDisplayName = null // Limpiar nombre al iniciar

        Log.d("HomeScreen", "LaunchedEffect: Iniciando carga y listeners para pisoId: $pisoId, UID: $currentUid")

        val pisoDataJob = launch {
            try {
                val pisoDoc = db.collection("pisos").document(pisoId).get().await()
                if (pisoDoc.exists()) {
                    // --- OBTENER NOMBRE DEL PISO ---
                    pisoDisplayName = pisoDoc.getString("nombre") ?: "Piso sin nombre"
                    Log.d("HomeScreen", "Nombre del piso obtenido: $pisoDisplayName")

                    // Obtener dirección para el tiempo
                    val address = pisoDoc.getString("direccion")
                    if (address != null && address.isNotBlank()) {
                        fetchWeatherByAddress(address).fold(
                            onSuccess = { response ->
                                val iconCode = response.weather?.firstOrNull()?.icon
                                val constructedIconUrl = iconCode?.let { "https://openweathermap.org/img/wn/${it}@2x.png" }
                                weatherInfo = WeatherInfo(
                                    temp = response.main?.temp,
                                    description = response.weather?.firstOrNull()?.description?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                    iconUrl = constructedIconUrl,
                                    locationName = response.name
                                )
                                weatherError = null
                            },
                            onFailure = { error ->
                                weatherError = "Tiempo: ${error.message?.take(100)}"
                            }
                        )
                    } else {
                        weatherError = "No se encontró dirección."
                    }
                } else {
                    weatherError = "No se encontró el documento del piso."
                    pisoDisplayName = "Piso no encontrado" // Indicar error
                    Log.w("HomeScreen", "Documento del piso $pisoId no existe.")
                }
            } catch (e: Exception) {
                weatherError = "Error con datos del piso/tiempo."
                pisoDisplayName = "Error al cargar" // Indicar error
                Log.e("HomeScreen", "Excepción en carga de datos del piso/tiempo", e)
            } finally {
                initialWeatherLoaded = true // Marcar carga de tiempo/piso como completada (o fallida)
                if (initialTasksLoaded && initialExpensesLoaded) {
                    isLoadingContent = false
                }
            }
        }

        val tasksCollection = db.collection("pisos").document(pisoId).collection("tasks")
            .whereEqualTo("isCompleted", false)
            .orderBy("dueDate", Query.Direction.ASCENDING)

        val tasksListener = tasksCollection.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e("HomeScreen", "Error en listener de tareas", e)
                taskSummary = TaskSummary() // Estado de error/defecto
                if (!initialTasksLoaded) { // Marcar como cargado incluso si hay error inicial
                    initialTasksLoaded = true
                    if (initialWeatherLoaded && initialExpensesLoaded) isLoadingContent = false
                }
                return@addSnapshotListener
            }
            if (snapshots != null) {
                val tasks = snapshots.documents.mapNotNull { it.toObject<Task>() }
                val pendingForUser = tasks.count { it.assignedUserIds.contains(currentUid) }
                val nextTaskForUser = tasks.firstOrNull { it.assignedUserIds.contains(currentUid) }
                val nextOverallTask = tasks.firstOrNull()
                // Actualizar el estado con el nuevo resumen
                taskSummary = TaskSummary(
                    pendingTasksForUser = pendingForUser,
                    totalPendingTasks = tasks.size,
                    nextDueTaskTitle = nextTaskForUser?.title ?: nextOverallTask?.title,
                    nextDueDate = nextTaskForUser?.dueDate ?: nextOverallTask?.dueDate
                )
                Log.d("HomeScreen", "Resumen de tareas actualizado.")
            }
            if (!initialTasksLoaded) { // Marcar como cargado después de la primera recepción de datos
                initialTasksLoaded = true
                if (initialWeatherLoaded && initialExpensesLoaded) isLoadingContent = false
            }
        }

        val expensesCollection = db.collection("pisos").document(pisoId).collection("expenses")

        val expensesListener = expensesCollection.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.e("HomeScreen", "Error en listener de gastos", e)
                expenseSummary = ExpenseSummary() // Estado de error/defecto
                if (!initialExpensesLoaded) {
                    initialExpensesLoaded = true
                    if (initialWeatherLoaded && initialTasksLoaded) isLoadingContent = false
                }
                return@addSnapshotListener
            }
            if (snapshots != null) {
                val expensesList = snapshots.documents.mapNotNull { it.toObject<Expense>() }
                var totalOwedToUser = 0.0
                var totalUserOwes = 0.0
                expensesList.forEach { expense ->
                    val numParticipants = expense.involvedUserIds.size
                    if (numParticipants > 0) {
                        val amountPerPerson = expense.amount / numParticipants
                        if (expense.paidByUserId == currentUid) {
                            expense.involvedUserIds.forEach { involvedId ->
                                if (involvedId != currentUid && expense.paymentsStatus[involvedId] == false) {
                                    totalOwedToUser += amountPerPerson
                                }
                            }
                        } else if (expense.involvedUserIds.contains(currentUid) && expense.paymentsStatus[currentUid] == false) {
                            totalUserOwes += amountPerPerson
                        }
                    }
                }
                // Actualizar el estado con el nuevo resumen
                expenseSummary = ExpenseSummary(totalOwedToUser, totalUserOwes)
                Log.d("HomeScreen", "Resumen de gastos actualizado.")
            }
            if (!initialExpensesLoaded) {
                initialExpensesLoaded = true
                if (initialWeatherLoaded && initialTasksLoaded) isLoadingContent = false
            }
        }
        }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Image(
                        painter = painterResource(id = R.drawable.roomielogonaranja),
                        contentDescription = "Roomie Logo",
                        modifier = Modifier.height(48.dp)
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = topAppBarColor),
                actions = {
                    // Botón para volver a la selección de piso
                    IconButton(onClick = {
                        Log.d("HomeScreen", "Navegando de vuelta a Profile")
                        navController.navigate("profile") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Apartment,
                            contentDescription = "Volver a Selección de Piso",
                            tint = selectedIconColor
                        )
                    }
                    // Menú de perfil
                    Box {
                        IconButton(onClick = { showProfileMenu = true }) {
                            Icon(Icons.Filled.AccountCircle, "Menú perfil", tint = selectedIconColor)
                        }
                        DropdownMenu(expanded = showProfileMenu, onDismissRequest = { showProfileMenu = false }) {
                            DropdownMenuItem(text = { Text("Cambiar nombre") }, onClick = { showProfileMenu = false; showChangeUsernameDialog = true }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                            DropdownMenuItem(text = { Text("Cerrar sesión") }, onClick = { showProfileMenu = false; onLogout() }, leadingIcon = { Icon(Icons.Filled.ExitToApp, null) })
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = navBarColor) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = { Image(painter = painterResource(id = item.iconResId), contentDescription = item.label, modifier = Modifier.size(34.dp), colorFilter = ColorFilter.tint(if (selectedRoute == item.route) selectedIconColor else unselectedIconColor)) },
                        selected = selectedRoute == item.route,
                        onClick = {
                            if (selectedRoute != item.route) {
                                when (item) {
                                    BottomNavItem.Chat -> {
                                        if (pisoId.isNotBlank()) {
                                            navController.navigate("chat/$pisoId")
                                        } else {
                                            coroutineScope.launch { snackbarHostState.showSnackbar("No se puede abrir el chat sin un piso.") }
                                        }
                                    }
                                    else -> selectedRoute = item.route // Cambia la pestaña interna
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = navBarColor) // Sin indicador visual
                    )
                }
            }
        },
        containerColor = Color(0xFF242424) // Fondo general
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (selectedRoute) {
                BottomNavItem.Home.route -> {
                    // Contenido de la pestaña Inicio
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Sección del Tiempo
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.28f) // Mantener altura original
                                .padding(horizontal = 16.dp, vertical = 8.dp), // Ajustar padding vertical
                            horizontalAlignment = Alignment.CenterHorizontally,
                            // Alinear contenido al centro verticalmente dentro de este espacio
                            verticalArrangement = Arrangement.Center
                        ) {
                            // --- MOSTRAR NOMBRE DEL PISO ---
                            if (!isLoadingContent || pisoDisplayName != null) { // Mostrar si no está cargando o si ya tenemos un nombre
                                Text(
                                    text = pisoDisplayName ?: "Cargando nombre...", // Mostrar placeholder si es null
                                    fontSize = 40.sp, // Tamaño un poco más grande para el nombre del piso
                                    fontFamily = SyneFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = selectedIconColor, // Usar color principal
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp) // Espacio antes del widget del tiempo
                                )
                            } else {
                                // Placeholder o espacio si aún está cargando el nombre
                                Spacer(modifier = Modifier.height(28.dp)) // Espacio equivalente al texto
                            }

                            // Widget del Tiempo
                            WeatherWidget(
                                weatherInfo,
                                isLoadingContent && weatherInfo == null, // Carga inicial del tiempo
                                weatherError,
                                coilImageLoader
                            )
                        }
                        Divider(color = Color(0xFFF0B433), thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                        // Sección de Resúmenes
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f) // Ocupa el espacio restante
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()), // Permite scroll si hay muchas tarjetas
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top) // Espacio entre tarjetas
                        ) {
                            Text("Resumen del Piso", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)

                            if (isLoadingContent) {
                                CircularProgressIndicator(color = selectedIconColor, modifier = Modifier.padding(top = 24.dp))
                            } else {
                                // Mostrar tarjeta de tareas si hay datos
                                taskSummary?.let { summary ->
                                    SummaryCard(title = "Tareas (${summary.totalPendingTasks} Pendientes)") {
                                        if (summary.totalPendingTasks == 0) {
                                            Text("¡Ninguna tarea pendiente! 🎉", color = Color.White)
                                        } else {
                                            Text("Tienes ${summary.pendingTasksForUser} tareas asignadas.", color = Color.White)
                                            summary.nextDueTaskTitle?.let { taskTitle ->
                                                val dueDateStr = summary.nextDueDate?.toDate()?.let {
                                                    SimpleDateFormat("dd MMM", Locale.getDefault()).format(it)
                                                } ?: "Pronto"
                                                Text("Próxima: $taskTitle ($dueDateStr)", color = Color.LightGray, fontSize = 14.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { selectedRoute = BottomNavItem.Tasks.route }, // Cambia a la pestaña de tareas
                                            colors = ButtonDefaults.buttonColors(containerColor = selectedIconColor.copy(alpha = 0.2f)),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                        ) { Text("Ver Tareas", color = selectedIconColor) }
                                    }
                                } ?: Text("No se pudo cargar el resumen de tareas.", color = Color.Gray) // Mensaje si taskSummary es null

                                // Mostrar tarjeta de gastos si hay datos
                                expenseSummary?.let { summary ->
                                    SummaryCard(title = "Control de Gastos") {
                                        Text("Te deben: %.2f€".format(summary.totalOwedToUser), color = if (summary.totalOwedToUser > 0) Color(0xFF4CAF50) else Color.White)
                                        Text("Debes: %.2f€".format(summary.totalUserOwes), color = if (summary.totalUserOwes > 0) Color(0xFFF44336) else Color.White)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { selectedRoute = BottomNavItem.Expenses.route }, // Cambia a la pestaña de gastos
                                            colors = ButtonDefaults.buttonColors(containerColor = selectedIconColor.copy(alpha = 0.2f)),
                                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                        ) { Text("Ver Gastos", color = selectedIconColor) }
                                    }
                                } ?: Text("No se pudo cargar el resumen de gastos.", color = Color.Gray) // Mensaje si expenseSummary es null

                                // Aquí podrías añadir más tarjetas de resumen (Compras, etc.)

                            }
                        }
                    }
                }
                // Otras pestañas llaman a sus respectivos Composables
                BottomNavItem.Tasks.route -> TaskListScreen(pisoId = pisoId, navController = navController, auth = auth)
                BottomNavItem.Expenses.route -> ExpensesScreen(pisoId = pisoId, navController = navController, auth = auth)
                BottomNavItem.Shopping.route -> ShoppingListScreen(pisoId = pisoId, navController = navController, auth = auth)
            }
        }
    }

    // Diálogo para cambiar nombre de usuario (sin cambios)
    if (showChangeUsernameDialog) {
        ChangeUsernameDialog(
            currentUsername = auth.currentUser?.displayName ?: "",
            onDismiss = { showChangeUsernameDialog = false },
            onConfirm = { newName -> showChangeUsernameDialog = false; updateUsername(newName) }
        )
    }
}

// --- Composable ChangeUsernameDialog (sin cambios) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeUsernameDialog(currentUsername: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newUsername by remember(currentUsername) { mutableStateOf(currentUsername) }
    var isLoadingDialog by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!isLoadingDialog) onDismiss() },
        title = { Text("Cambiar Nombre") },
        text = {
            OutlinedTextField(value = newUsername, onValueChange = { newUsername = it }, label = { Text("Nuevo nombre") }, singleLine = true, enabled = !isLoadingDialog, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            Button(
                onClick = { isLoadingDialog = true; onConfirm(newUsername) },
                enabled = newUsername.isNotBlank() && newUsername != currentUsername && !isLoadingDialog
            ) {
                if (isLoadingDialog) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary) else Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isLoadingDialog) { Text("Cancelar") } }
    )
}

// --- Composable WeatherWidget (sin cambios) ---
@Composable
fun WeatherWidget(weatherInfo: WeatherInfo?, isLoadingInitial: Boolean, error: String?, imageLoader: ImageLoader) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A))
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoadingInitial -> CircularProgressIndicator(color = Color(0xFFF0B90B), modifier = Modifier.size(40.dp))
                error != null -> Text(error, color = Color(0xFFF44336), textAlign = TextAlign.Center, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                weatherInfo != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(weatherInfo.iconUrl).crossfade(true).error(R.drawable.ic_broken_image).placeholder(R.drawable.loading_img).build(),
                            contentDescription = weatherInfo.description ?: "Icono tiempo",
                            imageLoader = imageLoader,
                            modifier = Modifier.size(60.dp).background(Color.Transparent, CircleShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(weatherInfo.locationName ?: "-", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(weatherInfo.temp?.let { "%.0f°C".format(it) } ?: "--°C", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(weatherInfo.description ?: "-", fontSize = 13.sp, color = Color.LightGray)
                        }
                    }
                }
                else -> Text("No se pudo cargar el tiempo.", color = Color.Gray, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

// --- Composable SummaryCard (sin cambios) ---
@Composable
fun SummaryCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF0B90B), // selectedIconColor
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .align(Alignment.CenterHorizontally)
            )
            content()
        }
    }
}
// --- Composable CenteredText (sin cambios) ---
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
