// ProfileScreen.kt (Modificado para mostrar nombre del piso)
package com.example.roomie.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch // Importar CoroutineScope launch si no está

@Composable
fun ProfileScreen(
    auth: FirebaseAuth,
    onLogout: () -> Unit,
    onNavigateToJoinPiso: () -> Unit,
    onNavigateToCreatePiso: () -> Unit
) {
    val currentUser = auth.currentUser
    val username = currentUser?.displayName
    val uid = currentUser?.uid
    val db = Firebase.firestore

    // Estado para guardar el ID del piso del usuario
    var pisoId by remember { mutableStateOf<String?>(null) }
    // --- NUEVO: Estado para guardar el NOMBRE del piso ---
    var pisoName by remember { mutableStateOf<String?>(null) }
    // Estado para indicar si se está cargando el nombre del piso
    var isLoadingPisoName by remember { mutableStateOf(false) }


    // --- Fetch user data y luego piso data ---
    LaunchedEffect(uid) { // Se (re)ejecuta si uid cambia
        if (uid != null) {
            isLoadingPisoName = true // Empieza a cargar
            pisoId = null // Resetea estados al iniciar/cambiar uid
            pisoName = null
            try {
                Log.d("ProfileScreen", "Fetching user document for UID: $uid")
                val userDoc = db.collection("users").document(uid).get().await()

                if (userDoc.exists()) {
                    val fetchedPisoId = userDoc.getString("piso")
                    pisoId = fetchedPisoId // Guarda el ID
                    Log.d("ProfileScreen", "User belongs to piso ID: $fetchedPisoId")

                    // Si el usuario tiene un piso asignado, busca el nombre
                    if (fetchedPisoId != null && fetchedPisoId.isNotBlank()) {
                        try {
                            Log.d("ProfileScreen", "Fetching piso document for ID: $fetchedPisoId")
                            val pisoDoc = db.collection("pisos").document(fetchedPisoId).get().await()
                            if (pisoDoc.exists()) {
                                val fetchedPisoName = pisoDoc.getString("nombre")
                                pisoName = fetchedPisoName // Guarda el nombre
                                Log.d("ProfileScreen", "Fetched piso name: $fetchedPisoName")
                            } else {
                                Log.w("ProfileScreen", "Piso document $fetchedPisoId does not exist.")
                                pisoName = null // El documento del piso no existe
                            }
                        } catch (e: Exception) {
                            Log.e("ProfileScreen", "Error fetching piso document $fetchedPisoId", e)
                            pisoName = null // Error al buscar el piso
                        }
                    } else {
                        pisoName = null // El usuario no tiene pisoId asignado
                    }
                } else {
                    Log.w("ProfileScreen", "User document $uid does not exist.")
                    pisoId = null
                    pisoName = null
                }
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Error fetching user document $uid", e)
                pisoId = null
                pisoName = null
            } finally {
                isLoadingPisoName = false // Termina de cargar (éxito o error)
            }
        } else {
            // Si uid es null (usuario no logueado), resetea todo
            pisoId = null
            pisoName = null
            isLoadingPisoName = false
        }
    }
    // --- End Fetch ---

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF222222)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Bienvenida ---
            Text(
                text = "Bienvenido/a",
                style = TextStyle(fontSize = 24.sp, color = Color.White),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = username ?: "Usuario",
                style = TextStyle(fontSize = 36.sp, color = Color(0xFFF0B90B), fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // --- Sección Tus Pisos ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Apartment,
                    contentDescription = "Icono Piso",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tus pisos:",
                    style = TextStyle(fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.SemiBold),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Botón/Indicador del piso actual (AHORA MUESTRA EL NOMBRE) ---
            Button(
                onClick = { /* Acción al pulsar el nombre del piso (si la hay) */ },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    // Mantenemos la lógica del color basada en si tiene un piso asignado (pisoId)
                    containerColor = if (pisoId != null) Color(0xFFF0B90B) else Color.Gray,
                    contentColor = Color.Black
                ),
                enabled = pisoId != null // Quizás habilitado solo si tiene piso
            ) {
                // --- Lógica de Texto Modificada ---
                val buttonText = when {
                    isLoadingPisoName -> "Cargando nombre..." // Muestra cargando mientras busca el nombre
                    pisoName != null -> pisoName // Muestra el nombre si se encontró
                    pisoId != null -> "Piso sin nombre (ID: $pisoId)" // Si tiene ID pero no nombre
                    else -> "No estás en ningún piso" // Si no tiene ID
                }
                Text(
                    text = buttonText.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                // --- Fin Lógica de Texto ---
            }


            Spacer(modifier = Modifier.height(32.dp))

            // --- Botones de Acción ---
            Button(
                onClick = { onNavigateToCreatePiso() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)) // Rojo
            ) {
                Text("+ Crear nuevo piso", color = Color.White, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onNavigateToJoinPiso() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000)) // Naranja
            ) {
                Text("Unirte a nuevo piso", color = Color.White, fontSize = 16.sp)
            }


            Spacer(modifier = Modifier.weight(1f))

            // --- Botón Cerrar Sesión ---
            Button(
                onClick = {
                    // No es necesario llamar a auth.signOut() aquí si ya se hace en MainActivity
                    onLogout()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Cerrar Sesión", style = TextStyle(color = Color.White, fontSize = 16.sp))
            }
        }
    }
}