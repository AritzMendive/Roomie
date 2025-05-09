package com.example.roomie.screens

import android.R
import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.roomie.data.Incident // Asegúrate que esta importación sea correcta
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentsListScreen(
    navController: NavController,
    pisoId: String,
    auth: FirebaseAuth
) {
    val db = Firebase.firestore
    var incidentsList by remember { mutableStateOf<List<Incident>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Estados para el diálogo de cambio de estado
    var showStatusDialog by remember { mutableStateOf(false) }
    var selectedIncidentForStatusChange by remember { mutableStateOf<Incident?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val titleColor = Color(0xFFF0B90B) // Amarillo para el título "Incidencias"
    val buttonColor = Color(0xFFFE4D21) // Rojo para el botón "Publicar incidencia"
    val cardBackgroundColor = Color(0xFF333333)
    val textColorPrimary = Color.White
    val textColorSecondary = Color.LightGray

    LaunchedEffect(pisoId) {
        if (pisoId.isBlank()) {
            errorMessage = "ID de piso no válido."
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val incidentsRef = db.collection("pisos").document(pisoId)
            .collection("incidents")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val listenerRegistration = incidentsRef.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("IncidentsListScreen", "Error escuchando incidencias.", e)
                errorMessage = "Error al cargar incidencias: ${e.localizedMessage}"
                isLoading = false
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val fetchedIncidents = snapshots.documents.mapNotNull { doc ->
                    try {
                        doc.toObject<Incident>()?.copy(id = doc.id)
                    } catch (ex: Exception) {
                        Log.e("IncidentsListScreen", "Error convirtiendo incidencia ${doc.id}", ex)
                        null
                    }
                }
                incidentsList = fetchedIncidents
                errorMessage = null
            }
            isLoading = false
        }
        // No necesitas awaitClose si el listener se maneja dentro de LaunchedEffect
        // Se cancelará automáticamente cuando el Composable salga de la composición o pisoId cambie.
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Incidencias", color = titleColor, fontWeight = FontWeight.Bold, fontSize = 24.sp) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, "Volver", tint = titleColor)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1A1A1A)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("create_incident_screen/$pisoId") },
                containerColor = buttonColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, "Publicar nueva incidencia")
            }
        },
        containerColor = Color(0xFF222222)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (isLoading && incidentsList.isEmpty()) { // Mostrar solo si está cargando Y la lista está vacía
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = titleColor)
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(errorMessage!!, color = Color.Red, textAlign = TextAlign.Center)
                }
            } else if (incidentsList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No hay incidencias reportadas todavía.\n¡Sé el primero en publicar una!",
                        color = textColorSecondary,
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(incidentsList, key = { it.id }) { incident ->
                        IncidentItem(
                            incident = incident,
                            textColorPrimary = textColorPrimary,
                            textColorSecondary = textColorSecondary,
                            cardBackgroundColor = cardBackgroundColor,
                            onClick = {
                                selectedIncidentForStatusChange = incident
                                showStatusDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showStatusDialog && selectedIncidentForStatusChange != null) {
        ChangeIncidentStatusDialog(
            incident = selectedIncidentForStatusChange!!,
            onDismiss = {
                showStatusDialog = false
                selectedIncidentForStatusChange = null
            },
            onStatusChange = { incidentToUpdate, newStatus ->
                // Podrías usar un estado de carga específico para el diálogo si quieres
                // isLoadingDialog = true
                coroutineScope.launch {
                    db.collection("pisos").document(pisoId)
                        .collection("incidents").document(incidentToUpdate.id)
                        .update("status", newStatus)
                        .addOnSuccessListener {
                            Log.d("IncidentsListScreen", "Estado de incidencia ${incidentToUpdate.id} actualizado a $newStatus")
                        }
                        .addOnFailureListener { e ->
                            Log.e("IncidentsListScreen", "Error al actualizar estado de incidencia", e)
                            // Considera mostrar este error en un Snackbar o Toast
                            errorMessage = "Error al cambiar estado: ${e.localizedMessage}"
                        }
                        .addOnCompleteListener {
                            // isLoadingDialog = false
                            showStatusDialog = false
                            selectedIncidentForStatusChange = null
                        }
                }
            }
        )
    }
}

@Composable
fun IncidentItem(
    incident: Incident,
    textColorPrimary: Color,
    textColorSecondary: Color,
    cardBackgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = incident.title,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textColorPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = incident.description,
                fontSize = 14.sp,
                color = textColorSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Por: ${incident.reportedByName}", // Más corto
                    fontSize = 12.sp,
                    color = textColorSecondary
                )
                val formattedDate = incident.timestamp?.toDate()?.let {
                    SimpleDateFormat("dd MMM yy, HH:mm", Locale.getDefault()).format(it) // Formato un poco más corto
                } ?: "" // No mostrar "Fecha desconocida" si es null, simplemente nada
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = textColorSecondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Estado: ${incident.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }}",
                fontSize = 13.sp, // Ligeramente más grande para el estado
                color = when (incident.status) {
                    "pendiente" -> Color(0xFFFFA000) // Naranja
                    "en_progreso" -> Color(0xFF03A9F4) // Un azul claro
                    "resuelta" -> Color(0xFF4CAF50) // Verde
                    else -> textColorSecondary
                },
                fontWeight = FontWeight.Medium // Un poco más de énfasis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeIncidentStatusDialog(
    incident: Incident,
    onDismiss: () -> Unit,
    onStatusChange: (Incident, String) -> Unit
) {
    val possibleStatuses = listOf("pendiente", "en_progreso", "resuelta")
    var expanded by remember { mutableStateOf(false) }
    // Inicializar selectedStatus con el estado actual de la incidencia
    var selectedStatus by remember(incident.status) { mutableStateOf(incident.status) }


    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar Estado de Incidencia", fontWeight = FontWeight.Bold, color = Color(0xFFF0B90B) ) },
        text = {
            Column {
                Text(
                    "Incidencia: \"${incident.title}\"",
                    style = MaterialTheme.typography.titleSmall, // Usar un estilo de tema
                    modifier = Modifier.padding(bottom = 16.dp),
                    color = Color.White
                )
                // Text("Selecciona el nuevo estado:", style = MaterialTheme.typography.bodyMedium)
                // Spacer(modifier = Modifier.height(8.dp)) // No es necesario si el TextFiel tiene placeholder/label

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedStatus.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Nuevo Estado") }, // Añadir un label
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp), // Redondear esquinas
                        colors = OutlinedTextFieldDefaults.colors( // Estilo consistente
                            focusedBorderColor = Color(0xFFF0B90B),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color.Black, // Aunque sea readOnly, por consistencia
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedContainerColor = Color(0xFFF0B90B),
                            unfocusedContainerColor = Color(0xFFF0B90B),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(Color(0xFF333333)) // Fondo del menú desplegable
                    ) {
                        possibleStatuses.forEach { status ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                        color = Color.White // Texto blanco para el item del menú
                                    )
                                },
                                onClick = {
                                    selectedStatus = status
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onStatusChange(incident, selectedStatus)
                    onDismiss() // Cerrar diálogo después de confirmar
                },
                enabled = selectedStatus != incident.status,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) // Verde para confirmar
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
            ) {
                Text("Cancelar")
            }
        },
        containerColor = Color(0xFF2C2C2C) // Un color de fondo para el diálogo
    )
}