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
import com.example.roomie.ui.theme.RoomieTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    auth: FirebaseAuth,
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit  //  <---  ADDED THIS CALLBACK
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoginSuccess by remember { mutableStateOf<Boolean?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF222222)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Inicia sesión",
                style = TextStyle(
                    fontSize = 28.sp,
                    color = Color.White
                ),
                modifier = Modifier.padding(bottom = 32.dp)
            )

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

                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,

                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
            )

            // Password TextField
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("contraseña", color = Color.Black) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    val icon = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = icon, contentDescription = "Toggle password visibility", tint = Color.White)
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFF0B90B),
                    unfocusedBorderColor = Color(0xFF555555),
                    cursorColor = Color(0xFFF0B90B),
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,

                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
            )

            TextButton(onClick = {
                // Aquí iría la lógica para "Forgot Your Password?"
                Log.d("LoginScreen", "Forgot Your Password?")
            }) {
                Text("Forgot Your Password ?", color = Color.White)
            }

            // State message
            if (isLoginSuccess != null) {
                if (isLoginSuccess == true) {
                    Text(text = "Login successful", color = Color(0xFF4CAF50), modifier = Modifier.padding(top = 8.dp))
                } else {
                    Text(text = "Login failed", color = Color(0xFFF44336), modifier = Modifier.padding(top = 8.dp))
                }
            }

            // Loading Indicator
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFF0B90B), modifier = Modifier.padding(16.dp))
            }

            // Login Button
            Button(
                onClick = {
                    isLoading = true
                    signInWithEmailAndPassword(auth, email, password) { success ->
                        isLoading = false
                        isLoginSuccess = success
                        if (success) {
                            onLoginSuccess()  //  <---  CALL THE CALLBACK ON SUCCESS
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0B90B))
            ) {
                Text("Iniciar sesión", style = TextStyle(color = Color.Black, fontSize = 16.sp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = { onNavigateToRegister() }) {
                Text("¿No tienes cuenta? Regístrate", color = Color.White)
            }
        }
    }
}

// Function to login with email and password
fun signInWithEmailAndPassword(
    auth: FirebaseAuth,
    email: String,
    password: String,
    callback: (Boolean) -> Unit
) {
    auth.signInWithEmailAndPassword(email, password)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("LoginScreen", "signInWithEmail:success")
                callback(true)
            } else {
                Log.w("LoginScreen", "signInWithEmail:failure", task.exception)
                callback(false)
            }
        }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    RoomieTheme {
        val mockAuth = Firebase.auth
        LoginScreen(mockAuth, {}, {}) // Added empty lambdas for preview
    }
}