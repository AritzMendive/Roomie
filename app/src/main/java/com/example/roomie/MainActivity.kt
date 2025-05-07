// MainActivity.kt
package com.example.roomie

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.roomie.screens.LoginScreen
import com.example.roomie.screens.ProfileScreen
import com.example.roomie.screens.RegisterScreen
import com.example.roomie.screens.JoinPisoScreen // <-- Importa la nueva pantalla
import com.example.roomie.screens.CreatePisoScreen // <-- Descomenta si creas esta pantalla
import com.example.roomie.ui.theme.RoomieTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.example.roomie.screens.HomeScreen
import com.example.roomie.screens.CreateTaskScreen
import com.example.roomie.screens.ChatScreen // <-- AÑADE ESTA LÍNEA
import androidx.compose.material3.Text // <-- Asegúrate de importar Text si usas un placeholder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.roomie.screens.ExpensesScreen
import com.example.roomie.screens.CreateExpenseScreen

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = Firebase.auth // Inicializa FirebaseAuth

        setContent {
            RoomieTheme {
                val navController = rememberNavController() // Inicializa NavController

                // Comprueba el estado de autenticación inicial (opcional pero recomendado)
                // Podrías querer navegar directamente a "profile" si el usuario ya está logueado.
                // val startDestination = if (auth.currentUser != null) "profile" else "login"
                val startDestination = "login" // O mantener login como inicio siempre

                NavHost(navController = navController, startDestination = startDestination) {

                    composable("login") {
                        // Si el usuario ya está logueado, redirige a profile inmediatamente
                        // Esto evita ver la pantalla de login brevemente si ya hay sesión.
                        if (auth.currentUser != null) {
                            LaunchedEffect(Unit) { // Evita llamadas múltiples
                                navController.navigate("profile") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        } else {
                            LoginScreen(
                                auth = auth,
                                onNavigateToRegister = { navController.navigate("register") },
                                onLoginSuccess = {
                                    // Navega a profile limpiando el stack hasta login
                                    navController.navigate("profile") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }
                    }

                    composable("register") {
                        RegisterScreen(
                            auth = auth,
                            onNavigateToLogin = {
                                // Vuelve a login, limpiando el stack para no volver a register
                                navController.navigate("login") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = "piso_home/{pisoId}",
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoId = backStackEntry.arguments?.getString("pisoId")
                        if (pisoId != null && auth.currentUser != null) {
                            Log.d("MainActivity", "Displaying HomeScreen for pisoId: $pisoId")
                            // --- Pasa navController y pisoId a HomeScreen ---
                            HomeScreen(
                                navController = navController, // Pasar el NavController
                                pisoId = pisoId,               // Pasar el pisoId extraído
                                auth = auth,                   // <-- AÑADIR: Pasar la instancia auth de MainActivity
                                onLogout = {                   // <-- AÑADIR: Definir la acción de logout
                                    auth.signOut()
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                            // --- Fin Pasar ---
                        } else {
                            Log.w("MainActivity", "PisoId is null or user not logged in, redirecting to login")
                            LaunchedEffect(Unit) {
                                navController.navigate("login") {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                }
                            }
                        }
                    }

                    composable("profile") {
                        // Asegúrate de que el usuario esté autenticado para ver el perfil
                        if (auth.currentUser == null) {
                            // Si por alguna razón llega aquí sin estar logueado, vuelve a login
                            LaunchedEffect(Unit) {
                                navController.navigate("login") {
                                    popUpTo("profile") { inclusive = true }
                                }
                            }
                        } else {
                            ProfileScreen(
                                auth = auth,
                                onLogout = {
                                    // Cierra sesión y vuelve a login, limpiando el stack
                                    auth.signOut() // Asegúrate que signOut se llama antes de navegar
                                    navController.navigate("login") {
                                        popUpTo("profile") { inclusive = true }
                                    }
                                },
                                onNavigateToJoinPiso = { navController.navigate("join_piso") },
                                onNavigateToCreatePiso = { navController.navigate("create_piso") },
                                onNavigateToPisoHome = { pisoId ->
                                    navController.navigate("piso_home/$pisoId")
                                }
                            )
                        }
                    }

                    composable("join_piso") {
                        // Solo accesible si está logueado
                        if (auth.currentUser == null) {
                            LaunchedEffect(Unit) {
                                navController.navigate("login") {
                                    popUpTo("join_piso") { inclusive = true }
                                }
                            }
                        } else {
                            JoinPisoScreen(
                                auth = auth,
                                navController = navController
                            )
                        }
                    }

                    composable(
                        route = "create_task/{pisoId}", // Ruta que espera el ID del piso
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoId = backStackEntry.arguments?.getString("pisoId")
                        val creatorUid = auth.currentUser?.uid // Obtener UID del usuario actual
                        if (pisoId != null && creatorUid != null) {
                            CreateTaskScreen(
                                navController = navController,
                                pisoId = pisoId,
                                creatorUid = creatorUid
                            )
                        } else {
                            // Redirigir o mostrar error si falta pisoId o el usuario no está logueado
                            Log.e("Nav", "Cannot navigate to CreateTaskScreen: pisoId=$pisoId, creatorUid=$creatorUid")
                            // Quizás navController.popBackStack() o navegar a login
                        }
                    }

                    // Ruta placeholder para la pantalla de crear piso
                    composable("create_piso") {
                        if (auth.currentUser == null) {
                            LaunchedEffect(Unit) {
                                navController.navigate("login") {
                                    popUpTo("create_piso") { inclusive = true }
                                }
                            }
                        } else {
                            // Reemplaza esto con tu pantalla real cuando la tengas
                            // CreatePisoScreen(auth = auth, navController = navController)
                            CreatePisoScreen(auth = auth, navController = navController) // Placeholder
                        }
                    }

                    composable(
                        route = "expenses_screen/{pisoId}",
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoIdArg = backStackEntry.arguments?.getString("pisoId")
                        if (pisoIdArg != null && auth.currentUser != null) {
                            ExpensesScreen(
                                pisoId = pisoIdArg,
                                navController = navController,
                                auth = auth
                            )
                        } else {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        }
                    }


                    composable(
                        route = "create_expense/{pisoId}",
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoIdArg = backStackEntry.arguments?.getString("pisoId")
                        if (pisoIdArg != null && auth.currentUser != null) {
                            CreateExpenseScreen(
                                navController = navController,
                                pisoId = pisoIdArg,
                                auth = auth
                            )
                        } else {
                            // Redirigir si no hay pisoId o usuario
                            Log.e("Nav", "No se puede navegar a CreateExpenseScreen: pisoId=$pisoIdArg, user=${auth.currentUser}")
                            LaunchedEffect(Unit) {
                                navController.popBackStack() // O navegar a login
                            }
                        }
                    }
                    // Dentro del NavHost en MainActivity.kt

// ... otras rutas como login, register, profile, piso_home, etc.

                    composable(
                        route = "chat/{pisoId}",
                        arguments = listOf(navArgument("pisoId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val pisoId = backStackEntry.arguments?.getString("pisoId")
                        if (pisoId != null && auth.currentUser != null) {
                            Log.d("MainActivity", "Navigating to ChatScreen for pisoId: $pisoId")
                            ChatScreen(
                                pisoId = pisoId,
                                auth = auth,
                                navController = navController // <-- ASEGÚRATE DE PASARLO AQUÍ
                            )
                        }  else {
                            // Si falta el pisoId o el usuario no está logueado, volver
                            Log.w("MainActivity", "Cannot open ChatScreen: Missing pisoId or user not logged in. Redirecting.")
                            LaunchedEffect(Unit) {
                                // Decide a dónde redirigir, por ejemplo, a 'profile' o 'login'
                                navController.popBackStack() // Simplemente vuelve atrás
                                // o navController.navigate("login") { popUpTo(...) }
                            }
                        }
                    }

// ... resto de las rutas
                }
            }
        }
    }
}