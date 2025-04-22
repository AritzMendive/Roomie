package com.example.roomie

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roomie.ui.theme.RoomieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoomieTheme {
                LoginScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen() {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1E1E1E)) { // Fondo oscuro
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp), // Mayor padding horizontal
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically) // Espacio entre elementos
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.roomie_logo), // Reemplaza con tu logo
                contentDescription = "Roomie Logo",
                modifier = Modifier.size(120.dp) // Ajusta el tamaño del logo
            )

            Text(
                text = "Inicia sesión",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp)
            )

            // Email TextField
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Loisbecket@gmail.com", color = Color.Gray) }, // Placeholder como label
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.MailOutline, contentDescription = "Email", tint = Color.Gray) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    focusedBorderColor = Color(0xFFF0B90B), // Amarillo
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFFF0B90B)
                ),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            // Password TextField
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("********", color = Color.Gray) }, // Placeholder como label
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Password", tint = Color.Gray) },
                trailingIcon = {
                    val icon = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(icon, contentDescription = "Toggle password visibility", tint = Color.Gray)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    textColor = Color.White,
                    focusedBorderColor = Color(0xFFF0B90B), // Amarillo
                    unfocusedBorderColor = Color.Gray,
                    cursorColor = Color(0xFFF0B90B)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // Forgot Password
            TextButton(onClick = {
                // Aquí iría la lógica para "Forgot Your Password?"
                Log.d("LoginScreen", "Forgot Your Password?")
            }) {
                Text("Forgot Your Password ?", color = Color.Gray)
            }

            // Login Button
            Button(
                onClick = {
                    // Aquí iría la lógica de autenticación
                    Log.d("LoginScreen", "Iniciar sesión con: $email, $password")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0B90B)) // Botón amarillo
            ) {
                Text("Iniciar sesión", color = Color.Black, fontSize = 16.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    RoomieTheme {
        LoginScreen()
    }
}