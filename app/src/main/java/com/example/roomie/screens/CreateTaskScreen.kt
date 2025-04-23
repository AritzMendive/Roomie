package com.example.roomie.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

// Data class simple para representar a un miembro del piso seleccionable
data class PisoMember(
    val uid: String,
    val name: String // Podrías añadir más datos si los necesitas
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    navController: NavController,
    pisoId: String,
    creatorUid: String
) {
    val db = Firebase.firestore
    val coroutineScope = rememberCoroutineScope()

    // Estados para los campos del formulario
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<Date?>(null) } // Guarda la fecha seleccionada
    var assignedUserIds by remember { mutableStateOf<List<String>>(emptyList()) }

    // Estados para UI y datos auxiliares
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showUserSelectionDialog by remember { mutableStateOf(false) }
    var pisoMembers by remember { mutableStateOf<List<PisoMember>>(emptyList()) }
    var membersLoading by remember { mutableStateOf(false) }


    // --- Lógica para cargar miembros del piso ---
    // --- Lógica para cargar miembros del piso (CORREGIDA) ---
    LaunchedEffect(pisoId) {
        membersLoading = true
        pisoMembers = emptyList() // Limpiar antes de cargar
        errorMessage = null
        Log.d("CreateTaskScreen", "LaunchedEffect triggered for pisoId: $pisoId")

        try {
            // 1. Obtener la lista de UIDs del documento del piso
            val pisoDoc = db.collection("pisos").document(pisoId).get().await()
            if (!pisoDoc.exists()) {
                Log.w("CreateTaskScreen", "Piso document $pisoId does not exist.")
                throw Exception("Documento del piso no encontrado.")
            }
            val memberUids = pisoDoc.get("members") as? List<String> ?: emptyList()
            Log.d("CreateTaskScreen", "Fetched member UIDs: $memberUids")

            // 2. Obtener los detalles de cada usuario DIRECTAMENTE por su UID (que es el ID del documento)
            if (memberUids.isNotEmpty()) {
                val membersList = mutableListOf<PisoMember>()
                for (memberUid in memberUids) {
                    try {
                        val userDoc = db.collection("users").document(memberUid).get().await()
                        if (userDoc.exists()) {
                            val name = userDoc.getString("username") ?: "Usuario (ID: ${memberUid.take(5)}...)" // Nombre o placeholder
                            membersList.add(PisoMember(memberUid, name))
                            Log.d("CreateTaskScreen", "Found user: UID=$memberUid, Name=$name")
                        } else {
                            Log.w("CreateTaskScreen", "User document not found for member UID: $memberUid")
                            // Opcional: Añadir un miembro placeholder si el documento de usuario no se encuentra
                            // membersList.add(PisoMember(memberUid, "Usuario no encontrado"))
                        }
                    } catch (e: Exception) {
                        Log.e("CreateTaskScreen", "Error fetching individual user document: $memberUid", e)
                        // Opcional: Añadir un miembro placeholder en caso de error individual
                        // membersList.add(PisoMember(memberUid, "Error al cargar"))
                    }
                }
                pisoMembers = membersList
            } else {
                Log.d("CreateTaskScreen", "No member UIDs found in piso document.")
                pisoMembers = emptyList()
            }
            Log.d("CreateTaskScreen", "Final mapped pisoMembers: $pisoMembers")

        } catch (e: Exception) {
            Log.e("CreateTaskScreen", "Error loading piso members", e)
            errorMessage = "Error al cargar miembros: ${e.localizedMessage}"
            pisoMembers = emptyList()
        } finally {
            membersLoading = false
        }
    }
    // --- Fin lógica cargar miembros (CORREGIDA) ---
    // --- Fin lógica cargar miembros ---

    // --- Lógica para guardar la tarea ---
    val createTask: () -> Unit = createTask@{
        if (title.isBlank()) {
            errorMessage = "El título no puede estar vacío."
            return@createTask
        }
        if (dueDate == null) {
            errorMessage = "Debes seleccionar una fecha de cierre."
            return@createTask
        }
        if (assignedUserIds.isEmpty()) {
            errorMessage = "Debes asignar al menos un usuario."
            return@createTask
        }

        isLoading = true
        errorMessage = null

        val taskData = hashMapOf(
            "title" to title,
            "description" to description,
            "dueDate" to Timestamp(dueDate!!), // Convertir Date a Timestamp
            "assignedUserIds" to assignedUserIds,
            "creatorUid" to creatorUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "isCompleted" to false,
            "pisoId" to pisoId,
            "completedByUid" to null,
            "completedAt" to null
        )

        db.collection("pisos").document(pisoId).collection("tasks")
            .add(taskData)
            .addOnSuccessListener {
                Log.d("CreateTaskScreen", "Task created successfully with ID: ${it.id}")
                isLoading = false
                navController.popBackStack() // Volver a la lista de tareas
            }
            .addOnFailureListener { e ->
                Log.e("CreateTaskScreen", "Error creating task", e)
                isLoading = false
                errorMessage = "Error al crear la tarea: ${e.message}"
            }
    }
    // --- Fin lógica guardar tarea ---


    // --- UI ---
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF222222)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Crear nueva tarea", fontSize = 28.sp, color = Color(0xFFF0B90B), fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))

            // --- Campos del formulario ---
            FormTextField(label = "Título", value = title, onValueChange = { title = it })
            Spacer(modifier = Modifier.height(16.dp))
            FormTextField(label = "Descripción", value = description, onValueChange = { description = it }, singleLine = false, minLines = 3)
            Spacer(modifier = Modifier.height(16.dp))

            // --- Selector de Fecha ---
            Text("Fecha de cierre:", color = Color.White, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFFF0B90B), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .clickable { showDatePickerDialog = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val formattedDate = if (dueDate != null) {
                    SimpleDateFormat("dd / MM / yyyy", Locale.getDefault()).format(dueDate)
                } else {
                    "Seleccionar fecha"
                }
                Text(formattedDate, color = Color.Black)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- Selector de Usuarios ---
            Text("Asignar usuarios:", color = Color.White, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFFF0B90B), RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .clickable(enabled = !membersLoading) { showUserSelectionDialog = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val userText = when {
                    membersLoading -> "Cargando miembros..."
                    assignedUserIds.isEmpty() -> "Seleccionar usuarios"
                    assignedUserIds.size == 1 -> pisoMembers.find { it.uid == assignedUserIds.first() }?.name ?: "1 usuario"
                    else -> "${assignedUserIds.size} usuarios seleccionados"
                }
                Text(userText, color = Color.Black)
            }
            Spacer(modifier = Modifier.height(24.dp))


            // Mensaje de error
            if (errorMessage != null) {
                Text("Error: $errorMessage", color = Color.Red, modifier = Modifier.padding(bottom = 8.dp))
            }

            // Indicador de carga
            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFF0B90B), modifier = Modifier.padding(bottom = 16.dp))
            }

            // Botón Crear Tarea
            Button(
                onClick = { createTask() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000)), // Naranja
                shape = RoundedCornerShape(8.dp),
                enabled = !isLoading
            ) {
                Text("Agregar tarea", color = Color.White, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.weight(1f)) // Empuja el botón de volver abajo

            // Botón opcional para volver manualmente
            TextButton(onClick = { navController.popBackStack() }) {
                Text("Cancelar", color = Color.White)
            }
        }
    }

    // --- Diálogo Date Picker ---
    if (showDatePickerDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dueDate = Date(millis) // Guardar como java.util.Date
                    }
                    showDatePickerDialog = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // --- Diálogo Selección Usuarios ---
    if (showUserSelectionDialog) {
        UserSelectionDialog(
            members = pisoMembers,
            selectedUserIds = assignedUserIds,
            onDismiss = { showUserSelectionDialog = false },
            onConfirm = { selectedIds ->
                assignedUserIds = selectedIds
                showUserSelectionDialog = false
            }
        )
    }
}

// Composable auxiliar para los TextFields del formulario
@Composable
fun FormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label + ":", color = Color.White, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFF0B90B),
                unfocusedBorderColor = Color(0xFF555555),
                cursorColor = Color(0xFFF0B90B),
                focusedLabelColor = Color.Black, // No usamos label flotante aquí
                unfocusedLabelColor = Color.Black,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black,
                focusedContainerColor = Color(0xFFF0B90B), // Fondo amarillo
                unfocusedContainerColor = Color(0xFFF0B90B)
            ),
            placeholder = { Text(label, color = Color.Gray) }, // Placeholder dentro
            singleLine = singleLine,
            minLines = minLines
        )
    }
}

// Composable para el diálogo de selección de usuarios
@Composable
fun UserSelectionDialog(
    members: List<PisoMember>,
    selectedUserIds: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var currentSelection by remember { mutableStateOf(selectedUserIds.toSet()) } // Usar un Set para eficiencia

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp), // Altura máxima para que no sea gigante
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)) // Fondo oscuro para el diálogo
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Asignar a:", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(members, key = { it.uid }) { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentSelection = if (member.uid in currentSelection) {
                                        currentSelection - member.uid
                                    } else {
                                        currentSelection + member.uid
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = member.uid in currentSelection,
                                onCheckedChange = { isChecked ->
                                    currentSelection = if (isChecked) {
                                        currentSelection + member.uid
                                    } else {
                                        currentSelection - member.uid
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFFF0B90B),
                                    uncheckedColor = Color.Gray,
                                    checkmarkColor = Color.Black
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(member.name, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(currentSelection.toList()) }) {
                        Text("Confirmar")
                    }
                }
            }
        }
    }
}