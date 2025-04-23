// CreatePisoScreen.kt
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
fun CreatePisoScreen(
    auth: FirebaseAuth,
    navController: NavController
) {
    val db = Firebase.firestore
    val currentUser = auth.currentUser
    val uid = currentUser?.uid

    var pisoName by remember { mutableStateOf("") }
    var pisoAddress by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var createPisoMessage by remember { mutableStateOf<String?>(null) }

    // --- Función para crear el piso ---
    val createPiso: () -> Unit = createPiso@{
        if (uid == null) {
            createPisoMessage = "Error: Usuario no autenticado."
            return@createPiso
        }
        if (pisoName.isBlank() || pisoAddress.isBlank()) {
            createPisoMessage = "Por favor, rellena todos los campos."
            return@createPiso
        }

        isLoading = true
        createPisoMessage = null

        // Datos del nuevo piso
        val pisoData = hashMapOf(
            "nombre" to pisoName,
            "direccion" to pisoAddress,
            "creatorUid" to uid,
            "members" to listOf(uid), // Añade al creador como primer miembro
            "createdAt" to FieldValue.serverTimestamp() // Fecha de creación
        )

        // Añadir el nuevo piso a la colección 'pisos'
        db.collection("pisos")
            .add(pisoData) // Firestore genera un ID automático
            .addOnSuccessListener { documentReference ->
                val newPisoId = documentReference.id // Obtiene el ID del piso recién creado
                Log.d("CreatePisoScreen", "Piso created successfully with ID: $newPisoId")

                // Ahora, actualiza el documento del usuario para unirlo a este piso
                db.collection("users").document(uid)
                    .update("piso", newPisoId)
                    .addOnSuccessListener {
                        Log.d("CreatePisoScreen", "User $uid successfully joined the new piso $newPisoId")
                        isLoading = false
                        createPisoMessage = "Piso '$pisoName' creado y te has unido correctamente."
                        // Navegar de vuelta al perfil después de crear y unirse
                        navController.popBackStack()
                    }
                    .addOnFailureListener { e ->
                        Log.e("CreatePisoScreen", "Error joining user to the new piso", e)
                        isLoading = false
                        // El piso se creó, pero el usuario no pudo unirse. Requiere manejo manual?
                        createPisoMessage = "Piso creado, pero hubo un error al unirte automáticamente."
                    }
            }
            .addOnFailureListener { e ->
                Log.e("CreatePisoScreen", "Error creating piso", e)
                isLoading = false
                createPisoMessage = "Error al crear el piso: ${e.message}"
            }
    }
    // --- Fin función crear piso ---


    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF222222)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(64.dp)) // Espacio superior

            Text(
                text = "Crear piso",
                style = TextStyle(fontSize = 32.sp, color = Color(0xFFF0B90B), fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Rellena todos los campos a continuación:",
                style = TextStyle(fontSize = 16.sp, color = Color.White),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // --- Campo Nombre ---
            OutlinedTextField(
                value = pisoName,
                onValueChange = { pisoName = it },
                label = { Text("Nombre", color = Color.Black) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next // Va al siguiente campo
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF0B90B),
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Color(0xFFF0B90B),
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color(0xFFF0B90B), // Fondo amarillo
                    unfocusedContainerColor = Color(0xFFF0B90B)
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // --- Campo Dirección ---
            OutlinedTextField(
                value = pisoAddress,
                onValueChange = { pisoAddress = it },
                label = { Text("Dirección", color = Color.Black) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done // Acción final
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF0B90B),
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Color(0xFFF0B90B),
                    focusedLabelColor = Color.Black,
                    unfocusedLabelColor = Color.Black,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color(0xFFF0B90B), // Fondo amarillo
                    unfocusedContainerColor = Color(0xFFF0B90B)
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Mensaje de estado
            if (createPisoMessage != null) {
                Text(
                    text = createPisoMessage!!,
                    color = if (createPisoMessage!!.contains("correctamente")) Color.Green else Color.Red,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Indicador de carga
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFF0B90B), modifier = Modifier.padding(bottom = 16.dp))
            }

            // --- Botón Crear ---
            Button(
                onClick = { createPiso() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)), // Rojo
                shape = RoundedCornerShape(8.dp),
                enabled = !isLoading
            ) {
                Text("Crear", color = Color.White, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.weight(1f)) // Empuja el botón de volver abajo

            // Botón opcional para volver manualmente
            TextButton(onClick = { navController.popBackStack() }) {
                Text("Volver al perfil", color = Color.White)
            }
        }
    }
}