// JoinPisoScreen.kt
package com.example.roomie.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinPisoScreen(
    auth: FirebaseAuth,
    navController: NavController // Para poder navegar hacia atrás
) {
    val db = Firebase.firestore
    val uid = auth.currentUser?.uid

    var joinPisoId by remember { mutableStateOf("") }
    var joinPisoMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // --- Función para unirse a piso (Movida desde ProfileScreen) ---
    val joinPiso: () -> Unit = {
        if (uid != null && joinPisoId.isNotBlank()) {
            isLoading = true
            joinPisoMessage = null
            Log.d("JoinPisoScreen", "Attempting to join piso with ID: $joinPisoId by user $uid")

            val pisoRef = db.collection("pisos").document(joinPisoId) // Referencia al doc del piso
            val userRef = db.collection("users").document(uid)     // Referencia al doc del usuario

            pisoRef.get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // Paso 1: Actualizar el documento del usuario (como antes)
                        userRef.update("piso", joinPisoId)
                            .addOnSuccessListener {
                                Log.d("JoinPisoScreen", "User $uid document updated successfully.")

                                // --- NUEVO: Paso 2: Actualizar la lista de miembros del piso ---
                                pisoRef.update("members", FieldValue.arrayUnion(uid)) // Añade el UID a la lista
                                    .addOnSuccessListener {
                                        Log.d("JoinPisoScreen", "User $uid added to members array of piso $joinPisoId")
                                        isLoading = false
                                        joinPisoMessage = "Te has unido al piso correctamente."
                                        navController.popBackStack() // Vuelve atrás
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("JoinPisoScreen", "Error adding user to members array", e)
                                        isLoading = false
                                        // El usuario se unió (doc de usuario actualizado), pero no se añadió a la lista
                                        joinPisoMessage = "Te has unido, pero hubo un error al actualizar la lista de miembros del piso."
                                        // Considera si quieres revertir la actualización del usuario aquí o dejarlo así.
                                    }
                                // --- FIN NUEVO ---
                            }
                            .addOnFailureListener { e ->
                                Log.w("JoinPisoScreen", "Error updating user document", e)
                                isLoading = false
                                joinPisoMessage = "Error al guardar la unión al piso en tu perfil."
                            }
                    } else {
                        isLoading = false
                        joinPisoMessage = "El ID del piso no existe."
                    }
                }.addOnFailureListener { e ->
                    Log.w("JoinPisoScreen", "Error checking piso ID", e)
                    isLoading = false
                    joinPisoMessage = "Error al verificar el ID del piso."
                }
        } else {
            joinPisoMessage = "Por favor, introduce un ID de piso válido o asegúrate de estar logueado."
        }
    }
    // --- Fin joinPiso ---

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF222222)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top // Alineado arriba para el título
        ) {
            Spacer(modifier = Modifier.height(64.dp)) // Espacio superior

            Text(
                text = "Unirse a un piso",
                style = TextStyle(fontSize = 28.sp, color = Color(0xFFF0B90B)), // Color del título como en la imagen
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Ingresa el ID del piso en el campo:",
                style = TextStyle(fontSize = 16.sp, color = Color.White),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // --- Input para ID del Piso ---
            OutlinedTextField(
                value = joinPisoId,
                onValueChange = { joinPisoId = it },
                label = { Text("ID", color = Color.Black) }, // Label negro para contraste
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF0B90B), // Borde amarillo
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Color(0xFFF0B90B), // Cursor amarillo
                    focusedLabelColor = Color.Black, // Label negro
                    unfocusedLabelColor = Color.Black, // Label negro
                    focusedTextColor = Color.Black, // Texto negro
                    unfocusedTextColor = Color.Black, // Texto negro
                    focusedContainerColor = Color(0xFFF0B90B), // Fondo amarillo (como en la imagen)
                    unfocusedContainerColor = Color(0xFFF0B90B) // Fondo amarillo
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Mensaje de estado (éxito/error)
            if (joinPisoMessage != null) {
                Text(
                    text = joinPisoMessage!!,
                    color = if (joinPisoMessage!!.contains("correctamente")) Color.Green else Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Indicador de carga
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFF0B90B), modifier = Modifier.padding(bottom = 16.dp))
            }


            // --- Botón Unirse ---
            Button(
                onClick = { joinPiso() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)), // Botón rojo como en imagen
                shape = RoundedCornerShape(8.dp),
                enabled = !isLoading
            ) {
                Text("Unirse", color = Color.White, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.weight(1f)) // Empuja el botón de volver abajo si es necesario

            // Botón opcional para volver manualmente
            TextButton(onClick = { navController.popBackStack() }) {
                Text("Volver al perfil", color = Color.White)
            }
        }
    }
}