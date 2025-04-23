package com.example.roomie.screens

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.roomie.R // <-- IMPORTANTE: Importar R

// Define las rutas para cada sección de la NavBar
sealed class BottomNavItem(val route: String, val iconResId: Int, val label: String) {
    object Home : BottomNavItem("home_overview", R.drawable.home, "Inicio")
    object Tasks : BottomNavItem("tasks", R.drawable.checksquare, "Tareas") // Asumiendo que el archivo es checksquare.png
    object Expenses : BottomNavItem("expenses", R.drawable.dollarcircle, "Gastos") // Asumiendo dollarcircle.png
    object Chat : BottomNavItem("chat", R.drawable.message, "Chat")
    object Shopping : BottomNavItem("shopping", R.drawable.shoppingcart, "Compras") // Asumiendo shoppingcart.png
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController, // <-- Añadir NavController
    pisoId: String
) {
    // Estado para saber qué item está seleccionado
    var selectedRoute by remember { mutableStateOf(BottomNavItem.Home.route) }

    // Lista de items para la barra de navegación
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Tasks,
        BottomNavItem.Expenses,
        BottomNavItem.Chat,
        BottomNavItem.Shopping
    )

    // Colores personalizados (ajusta según tu tema)
    val navBarColor = Color(0xFF1A1A1A) // Un gris oscuro para la barra
    val selectedIconColor = Color(0xFFF0B90B) // Amarillo para seleccionado
    val unselectedIconColor = Color.Gray // Gris para no seleccionado
    val topAppBarColor = Color(0xFF1A1A1A) // Color para la barra superior
    val titleColor = Color(0xFFF0B90B) // Color para el título "ROOMIE"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar( // Barra centrada como en la imagen
                title = {
                    Text(
                        "ROOMIE",
                        color = titleColor,
                        fontWeight = FontWeight.Bold, // Puedes añadir más estilo
                        fontSize = 24.sp
                        // Asegúrate de tener una fuente adecuada si usas una específica como en la imagen
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = topAppBarColor
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = navBarColor // Color de fondo de la barra
            ) {
                items.forEach { item ->
                    NavigationBarItem(
                        icon = {
                            Image(
                                painter = painterResource(id = item.iconResId),
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp), // Ajusta tamaño si es necesario
                                colorFilter = ColorFilter.tint( // Aplicar color al icono
                                    if (selectedRoute == item.route) selectedIconColor else unselectedIconColor
                                )
                            )
                        },
                        // label = { Text(item.label) }, // Puedes añadir etiquetas si quieres
                        selected = selectedRoute == item.route,
                        onClick = {
                            selectedRoute = item.route
                            Log.d("HomeScreen", "Selected route: $selectedRoute")
                            // Aquí es donde normalmente navegarías o cambiarías el contenido principal
                        },
                        colors = NavigationBarItemDefaults.colors(
                            // Personaliza colores del item si es necesario
                            indicatorColor = navBarColor // Color del indicador (círculo/fondo detrás del icono)
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFF222222) // Color de fondo del contenido principal
    ) { innerPadding ->
        // --- CONTENIDO PRINCIPAL ---
        // Aquí mostrarás el contenido según la ruta seleccionada (`selectedRoute`)
        // Por ahora, solo un placeholder
        Box(
            modifier = Modifier
                .padding(innerPadding) // ¡IMPORTANTE! Aplica el padding del Scaffold
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Contenido para: $selectedRoute",
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
            when (selectedRoute) {
                BottomNavItem.Home.route -> Text("Contenido Home para $pisoId") // Placeholder

                BottomNavItem.Tasks.route -> TaskListScreen( // Mostrar la pantalla de tareas
                    pisoId = pisoId,           // <-- Pasa el pisoId recibido por HomeScreen
                    navController = navController // <-- Pasa el NavController recibido
                ) // <-- Sin comas extras

                BottomNavItem.Expenses.route -> Text("Contenido Gastos para $pisoId") // Placeholder

                BottomNavItem.Chat.route -> Text("Contenido Chat para $pisoId") // Placeholder

                BottomNavItem.Shopping.route -> Text("Contenido Compras para $pisoId") // Placeholder

                // Añade casos para las otras rutas si tienes pantallas para ellas
            }
        }
    }
}

// --- IMPORTANTE: Colocación de Recursos Drawable ---
// Tus imágenes (checksquare.png, dollarcircle.png, home.png, message.png, shoppingcart.png)
// deben estar directamente dentro de la carpeta `res/drawable` de tu proyecto Android,
// NO dentro de una subcarpeta `res/drawable/images`.
// Asegúrate de que los nombres de archivo NO contengan mayúsculas ni caracteres especiales
// (ej: renombra checksquare.png a checksquare.png, dollarcircle.png a dollarcircle.png, etc.)
// y que coincidan con los IDs usados en `R.drawable.<nombre_archivo>`.