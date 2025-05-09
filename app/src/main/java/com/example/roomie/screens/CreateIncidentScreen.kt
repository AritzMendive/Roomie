package com.example.roomie.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.roomie.data.Incident
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIncidentScreen(
    navController: NavController,
    pisoId: String,
    auth: FirebaseAuth
) {
    val db = Firebase.firestore
    val currentUser = auth.currentUser
    val coroutineScope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val primaryAppColor = Color(0xFFF0B90B) // Amarillo
    val buttonColor = Color(0xFFF44336) // Rojo (como en tu maqueta de publicar)
    val textFieldBackgroundColor = Color(0xFFF0B90B) // Fondo amarillo para textfields
    val textFieldTextColor = Color.Black


    val publishIncident: () -> Unit = publishIncident@{
        if (currentUser == null) {
            errorMessage = "Debes estar autenticado para publicar una incidencia."
            return@publishIncident
        }
        if (title.isBlank()) {
            errorMessage = "El título no puede estar vacío."
            return@publishIncident
        }
        if (description.isBlank()) {
            errorMessage = "La descripción no puede estar vacía."
            return@publishIncident
        }

        isLoading = true
        errorMessage = null
        successMessage = null

        val incidentData = Incident(
            title = title.trim(),
            description = description.trim(),
            pisoId = pisoId,
            reportedByUid = currentUser.uid,
            reportedByName = currentUser.displayName ?: "Usuario desconocido",
            timestamp = null, // Firestore se encargará con @ServerTimestamp
            status = "pendiente"
        )

        db.collection("pisos").document(pisoId).collection("incidents")
            .add(incidentData)
            .addOnSuccessListener {
                Log.d("CreateIncidentScreen", "Incidencia publicada con ID: ${it.id}")
                isLoading = false
                successMessage = "¡Incidencia '${title}' publicada correctamente!"
                // Limpiar campos
                title = ""
                description = ""
                // Opcional: navegar hacia atrás después de un pequeño retraso
                coroutineScope.launch {
                    kotlinx.coroutines.delay(1500)
                    if(navController.currentDestination?.route?.startsWith("create_incident_screen") == true) {
                        navController.popBackStack()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("CreateIncidentScreen", "Error al publicar incidencia", e)
                isLoading = false
                errorMessage = "Error al publicar incidencia: ${e.localizedMessage}"
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Publicar Incidencia", color = primaryAppColor, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Volver", tint = primaryAppColor)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        },
        containerColor = Color(0xFF222222) // Fondo general
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Publicar incidencia", // Título dentro del contenido, como en tu maqueta
                color = primaryAppColor,
                fontSize = 20.sp, // Un poco más pequeño que el de la TopAppBar
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Campo Título de la incidencia
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Título de la incidencia", color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = textFieldBackgroundColor,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = textFieldTextColor,
                        focusedTextColor = textFieldTextColor,
                        unfocusedTextColor = textFieldTextColor,
                        focusedContainerColor = textFieldBackgroundColor,
                        unfocusedContainerColor = textFieldBackgroundColor,
                    ),
                    placeholder = { Text("Título", color = textFieldTextColor.copy(alpha = 0.7f)) },
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Campo Descripción de la incidencia
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Descripción de la incidencia", color = Color.White, modifier = Modifier.padding(bottom = 4.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = textFieldBackgroundColor,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = textFieldTextColor,
                        focusedTextColor = textFieldTextColor,
                        unfocusedTextColor = textFieldTextColor,
                        focusedContainerColor = textFieldBackgroundColor,
                        unfocusedContainerColor = textFieldBackgroundColor,
                    ),
                    placeholder = { Text("Descripción", color = textFieldTextColor.copy(alpha = 0.7f)) },
                    singleLine = false,
                    minLines = 3
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (errorMessage != null) {
                Text(errorMessage!!, color = Color.Red, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (successMessage != null) {
                Text(successMessage!!, color = Color(0xFF4CAF50), modifier = Modifier.padding(bottom = 8.dp))
            }

            if (isLoading) {
                CircularProgressIndicator(color = primaryAppColor, modifier = Modifier.padding(bottom = 16.dp))
            }

            Button(
                onClick = publishIncident,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor, // Rojo
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = !isLoading
            ) {
                Text("Publicar incidencia", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}