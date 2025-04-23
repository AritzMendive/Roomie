package com.example.roomie.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roomie.screens.ui.theme.RoomieTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest // Importar para actualizar perfil
import com.google.firebase.auth.ktx.auth // Necesario si usas Firebase.auth en la Preview
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase // Necesario si usas Firebase.auth en la Preview


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(auth: FirebaseAuth, onNavigateToLogin: () -> Unit) {
    var username by remember { mutableStateOf("") } // Estado para el nombre de usuario
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isRegisterSuccess by remember { mutableStateOf<Boolean?>(null) }
    var registrationMessage by remember { mutableStateOf<String?>(null) } // Para mensajes de éxito/error


    // --- Función para crear cuenta y guardar nombre de usuario ---
    fun createAccountWithEmailAndUsername(
        auth: FirebaseAuth,
        email: String,
        password: String,
        username: String,
        callback: (Boolean, String?) -> Unit // Modificado para devolver mensaje de error
    ) {
        if (username.isBlank()) {
            callback(false, "El nombre de usuario no puede estar vacío.")
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("RegisterScreen", "createUserWithEmail:success")
                    val user = task.result?.user
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(username)
                        .build()

                    user?.updateProfile(profileUpdates)
                        ?.addOnCompleteListener { profileTask ->
                            if (profileTask.isSuccessful) {
                                Log.d("RegisterScreen", "User profile updated with username.")

                                // -------- NUEVO: Crear documento en Firestore --------
                                val db = Firebase.firestore
                                val uid = user.uid // Obtener el UID del usuario recién creado
                                // Crear un mapa con los datos iniciales del usuario
                                // Puedes añadir más campos como email, fecha de creación, etc.
                                val userData = hashMapOf(
                                    "username" to username,
                                    "email" to email,
                                    "piso" to null // Inicialmente no pertenece a ningún piso
                                    // Agrega cualquier otro campo inicial que necesites
                                )

                                db.collection("users").document(uid)
                                    .set(userData) // Usa set() para crear el documento
                                    .addOnSuccessListener {
                                        Log.d(
                                            "RegisterScreen",
                                            "Firestore user document created successfully for UID: $uid"
                                        )
                                        callback(true, "Registro exitoso y documento creado.")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.w(
                                            "RegisterScreen",
                                            "Error creating Firestore user document",
                                            e
                                        )
                                        // El Auth se creó, pero Firestore falló. Decide cómo manejarlo.
                                        // Podrías intentar eliminar el usuario de Auth o informar al usuario.
                                        callback(
                                            false,
                                            "Registro fallido al crear datos de usuario."
                                        )
                                        // IMPORTANTE: Considera una lógica para manejar este fallo (ej. eliminar el usuario de Auth)
                                    }
                                // -------- FIN NUEVO --------

                            } else {
                                Log.w(
                                    "RegisterScreen",
                                    "Failed to update profile.",
                                    profileTask.exception
                                )
                                callback(
                                    true,
                                    "Registro exitoso, pero hubo un problema al guardar el nombre de usuario."
                                )
                            }
                        }
                } else {
                    Log.w("RegisterScreen", "createUserWithEmail:failure", task.exception)
                    callback(false, task.exception?.message ?: "Error desconocido al registrar.")
                }
            }
    }
    // --- Fin de la función ---


    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF222222)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Crea tu cuenta",
                style = TextStyle(fontSize = 28.sp, color = Color.White),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // --- Campo de Nombre de Usuario ---
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("nombre de usuario", color = Color.Black) }, // Cambiado a minúscula
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), // Añadido padding inferior
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text, // Teclado de texto normal
                    imeAction = ImeAction.Next // Ir al siguiente campo (Email)
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF0B90B),
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Color(0xFFF0B90B),
                    focusedLabelColor = Color.Black, // Cambiado color label
                    unfocusedLabelColor = Color.Black, // Cambiado color label
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp) // Esquinas redondeadas
            )
            // --- Fin Campo de Nombre de Usuario ---


            // Email TextField
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("e-mail", color = Color.Black) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF0B90B),
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Color(0xFFF0B90B),
                    focusedLabelColor = Color.Black, // Cambiado color label
                    unfocusedLabelColor = Color.Black, // Cambiado color label
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                // Mantén la forma original si quieres que se junten visualmente
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
            )

            // Password TextField
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("contraseña" , color = Color.Black) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done // Acción final
                ),
                trailingIcon = {
                    val icon = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Toggle password visibility",
                            // Asegúrate de que el icono sea visible sobre el fondo blanco
                            tint = Color.Gray // O Color.Black si prefieres
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF0B90B),
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Color(0xFFF0B90B),
                    focusedLabelColor = Color.Black, // Cambiado color label
                    unfocusedLabelColor = Color.Black, // Cambiado color label
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                // Mantén la forma original si quieres que se junten visualmente
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            )

            // --- Mensaje de Estado de Registro ---
            if (registrationMessage != null) {
                val messageColor = if (isRegisterSuccess == true) Color(0xFF4CAF50) else Color(0xFFF44336)
                Text(
                    text = registrationMessage!!,
                    color = messageColor,
                    modifier = Modifier.padding(bottom = 8.dp) // Añadido padding inferior
                )
            }
            // --- Fin Mensaje de Estado ---

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFF0B90B), modifier = Modifier.padding(16.dp))
            }

            Button(
                onClick = {
                    isLoading = true
                    registrationMessage = null // Limpiar mensaje anterior
                    // --- Llamar a la nueva función ---
                    createAccountWithEmailAndUsername(auth, email, password, username) { success, message ->
                        isLoading = false
                        isRegisterSuccess = success
                        registrationMessage = message // Mostrar mensaje de éxito/error
                        if (success && message?.contains("Registro exitoso") == true) {
                            // Opcional: Navegar a login o a la pantalla principal tras éxito
                            // onNavigateToLogin()
                        }
                    }
                    // --- Fin llamada ---
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp), // Ajustado padding superior
                shape = RoundedCornerShape(8.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0B90B))
            ) {
                Text("Registrarse", style = TextStyle(color = Color.Black, fontSize = 16.sp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { onNavigateToLogin() }) {
                Text("¿Ya tienes cuenta? Inicia sesión", color = Color.White)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    RoomieTheme { // Asegúrate de tener tu tema aquí
         // Crea una instancia mock o real de FirebaseAuth para la preview
        val mockAuth = Firebase.auth // Necesita inicialización de Firebase en la app
        RegisterScreen(mockAuth) {}
    }
}
