// MainActivity.kt
package com.example.roomie

import android.os.Bundle
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
import androidx.compose.material3.Text // <-- Asegúrate de importar Text si usas un placeholder

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
                                onNavigateToCreatePiso = { navController.navigate("create_piso") }
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
                }
            }
        }
    }
}