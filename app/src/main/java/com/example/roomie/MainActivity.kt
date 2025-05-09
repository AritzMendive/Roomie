// MainActivity.kt
package com.example.roomie

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.roomie.screens.* // Importa todas tus pantallas
import com.example.roomie.ui.theme.RoomieTheme
import com.example.roomie.utils.ShakeDetector // IMPORTA TU CLASE ShakeDetector
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Objeto para mantener el pisoId actual de forma global
object GlobalPisoIdHolder {
    private val _currentPisoId = MutableStateFlow<String?>(null)
    val currentPisoId: StateFlow<String?> = _currentPisoId.asStateFlow()

    fun updatePisoId(pisoId: String?) {
        Log.d("GlobalPisoIdHolder", "Updating pisoId to: $pisoId")
        _currentPisoId.value = pisoId
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var shakeDetector: ShakeDetector
    private lateinit var navController: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = Firebase.auth

        shakeDetector = ShakeDetector(this)

        setContent {
            RoomieTheme {
                navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val currentPisoId by GlobalPisoIdHolder.currentPisoId.collectAsState()

                LaunchedEffect(key1 = Unit) {
                    shakeDetector.setOnShakeListener(object : ShakeDetector.OnShakeListener {
                        @RequiresPermission(Manifest.permission.VIBRATE)
                        override fun onShake() {
                            Log.d("MainActivity", "Shake detected! Current route: $currentRoute, PisoID: $currentPisoId")

                            val excludedRoutes = listOf("login", "profile", "register", "join_piso", "create_piso")
                            val isExcludedRoute = excludedRoutes.any { currentRoute?.startsWith(it) == true }

                            if (!isExcludedRoute && currentPisoId != null && currentPisoId!!.isNotBlank()) {
                                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                if (vibrator.hasVibrator()) {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(100)
                                }
                                Log.d("MainActivity", "Navigating to create_incident_screen/$currentPisoId due to shake.")
                                navController.navigate("create_incident_screen/$currentPisoId") {
                                    launchSingleTop = true // Evita apilar la misma pantalla múltiples veces
                                }
                            } else {
                                Log.d("MainActivity", "Shake ignored. Route: $currentRoute ($isExcludedRoute), PisoID: $currentPisoId")
                            }
                        }
                    })
                }

                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        LaunchedEffect(Unit) { GlobalPisoIdHolder.updatePisoId(null) }
                        if (auth.currentUser != null) {
                            LaunchedEffect(Unit) {
                                navController.navigate("profile") { popUpTo("login") { inclusive = true } }
                            }
                        } else {
                            LoginScreen(
                                auth = auth,
                                onNavigateToRegister = { navController.navigate("register") },
                                onLoginSuccess = {
                                    navController.navigate("profile") { popUpTo("login") { inclusive = true } }
                                }
                            )
                        }
                    }
                    composable("register") {
                        LaunchedEffect(Unit) { GlobalPisoIdHolder.updatePisoId(null) }
                        RegisterScreen(
                            auth = auth,
                            onNavigateToLogin = {
                                navController.navigate("login") { popUpTo("login") { inclusive = true } }
                            }
                        )
                    }

                    composable("profile") {
                        LaunchedEffect(Unit) { GlobalPisoIdHolder.updatePisoId(null) }
                        if (auth.currentUser == null) {
                            LaunchedEffect(Unit) {
                                navController.navigate("login") { popUpTo("profile") { inclusive = true } }
                            }
                        } else {
                            ProfileScreen(
                                auth = auth,
                                onLogout = {
                                    auth.signOut()
                                    GlobalPisoIdHolder.updatePisoId(null)
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToJoinPiso = { navController.navigate("join_piso") },
                                onNavigateToCreatePiso = { navController.navigate("create_piso") },
                                onNavigateToPisoHome = { pisoId ->
                                    GlobalPisoIdHolder.updatePisoId(pisoId)
                                    navController.navigate("piso_home/$pisoId")
                                }
                            )
                        }
                    }

                    composable("join_piso") {
                        LaunchedEffect(Unit) { GlobalPisoIdHolder.updatePisoId(null) }
                        if (auth.currentUser == null) { /* Redirigir a login */ }
                        else { JoinPisoScreen(auth = auth, navController = navController) }
                    }

                    composable("create_piso") {
                        LaunchedEffect(Unit) { GlobalPisoIdHolder.updatePisoId(null) }
                        if (auth.currentUser == null) { /* Redirigir a login */ }
                        else { CreatePisoScreen(auth = auth, navController = navController) }
                    }


                    composable(
                        route = "piso_home/{pisoId}",
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoIdArg = backStackEntry.arguments?.getString("pisoId")
                        LaunchedEffect(pisoIdArg) { if (pisoIdArg != null) GlobalPisoIdHolder.updatePisoId(pisoIdArg) }

                        if (pisoIdArg != null && auth.currentUser != null) {
                            HomeScreen(
                                navController = navController,
                                pisoId = pisoIdArg,
                                auth = auth,
                                onLogout = {
                                    auth.signOut()
                                    GlobalPisoIdHolder.updatePisoId(null)
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        } else {
                            Log.w("MainActivity", "PisoId is null or user not logged in for piso_home, redirecting to login")
                            LaunchedEffect(Unit) {
                                navController.navigate("login") {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            }
                        }
                    }

                    composable(
                        route = "create_task/{pisoId}",
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoIdArg = backStackEntry.arguments?.getString("pisoId")
                        val creatorUid = auth.currentUser?.uid
                        LaunchedEffect(pisoIdArg) { if(pisoIdArg != null) GlobalPisoIdHolder.updatePisoId(pisoIdArg) }
                        if (pisoIdArg != null && creatorUid != null) {
                            CreateTaskScreen(navController = navController, pisoId = pisoIdArg, creatorUid = creatorUid)
                        } else { LaunchedEffect(Unit) { navController.popBackStack() } }
                    }

                    composable(
                        route = "chat/{pisoId}",
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoIdArg = backStackEntry.arguments?.getString("pisoId")
                        LaunchedEffect(pisoIdArg) { if(pisoIdArg != null) GlobalPisoIdHolder.updatePisoId(pisoIdArg) }
                        if (pisoIdArg != null && auth.currentUser != null) {
                            ChatScreen(pisoId = pisoIdArg, auth = auth, navController = navController)
                        } else { LaunchedEffect(Unit) { navController.popBackStack() } }
                    }

                    composable(
                        route = "expenses_screen/{pisoId}", // Cambiado de expenses a expenses_screen si así lo tienes
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoIdArg = backStackEntry.arguments?.getString("pisoId")
                        LaunchedEffect(pisoIdArg) { if(pisoIdArg != null) GlobalPisoIdHolder.updatePisoId(pisoIdArg) }
                        if (pisoIdArg != null && auth.currentUser != null) {
                            ExpensesScreen(pisoId = pisoIdArg, navController = navController, auth = auth)
                        } else { LaunchedEffect(Unit) { navController.popBackStack() } }
                    }

                    composable(
                        route = "create_expense/{pisoId}",
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoIdArg = backStackEntry.arguments?.getString("pisoId")
                        LaunchedEffect(pisoIdArg) { if(pisoIdArg != null) GlobalPisoIdHolder.updatePisoId(pisoIdArg) }
                        if (pisoIdArg != null && auth.currentUser != null) {
                            CreateExpenseScreen(navController = navController, pisoId = pisoIdArg, auth = auth)
                        } else { LaunchedEffect(Unit) { navController.popBackStack() } }
                    }

                    composable(
                        route = "incidents_list_screen/{pisoId}",
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoIdArg = backStackEntry.arguments?.getString("pisoId")
                        LaunchedEffect(pisoIdArg) { if(pisoIdArg != null) GlobalPisoIdHolder.updatePisoId(pisoIdArg) }
                        if (pisoIdArg != null && auth.currentUser != null) {
                            IncidentsListScreen(navController = navController, pisoId = pisoIdArg, auth = auth)
                        } else { LaunchedEffect(Unit) { navController.popBackStack() } }
                    }

                    composable(
                        route = "create_incident_screen/{pisoId}",
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoIdArg = backStackEntry.arguments?.getString("pisoId")
                        LaunchedEffect(pisoIdArg) { if(pisoIdArg != null) GlobalPisoIdHolder.updatePisoId(pisoIdArg) }
                        if (pisoIdArg != null && auth.currentUser != null) {
                            CreateIncidentScreen(navController = navController, pisoId = pisoIdArg, auth = auth)
                        } else { LaunchedEffect(Unit) { navController.popBackStack() } }
                    }
                    // Aquí irían tus otras rutas como ShoppingListScreen si la tienes
                    /*
                   composable(
                       route = "shopping_list_screen/{pisoId}", // Asumiendo una ruta similar
                       arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                   ) { backStackEntry ->
                       val pisoIdArg = backStackEntry.arguments?.getString("pisoId")
                       LaunchedEffect(pisoIdArg) { if(pisoIdArg != null) GlobalPisoIdHolder.updatePisoId(pisoIdArg) }
                       if (pisoIdArg != null && auth.currentUser != null) {
                           // ShoppingListScreen(navController = navController, pisoId = pisoIdArg, auth = auth) // Descomentar cuando tengas la pantalla
                       } else { LaunchedEffect(Unit) { navController.popBackStack() } }
                   }
                   */
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            shakeDetector.start()
            Log.d("MainActivity", "ShakeDetector started onResume")
        } else {
            Log.w("MainActivity", "Accelerometer not available, ShakeDetector not started.")
        }
    }

    override fun onPause() {
        super.onPause()
        shakeDetector.stop()
        Log.d("MainActivity", "ShakeDetector stopped onPause")
    }
}