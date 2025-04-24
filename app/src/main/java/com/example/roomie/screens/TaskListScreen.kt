package com.example.roomie.screens

import android.util.Log
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue // Necesario para FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch // Importar launch
import java.text.SimpleDateFormat
import java.util.*

// Data class Task (sin cambios)
data class Task(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val dueDate: Timestamp? = null,
    val assignedUserIds: List<String> = emptyList(),
    val creatorUid: String = "",
    val createdAt: Timestamp? = null,
    val isCompleted: Boolean = false,
    val pisoId: String = ""
)

@OptIn(ExperimentalMaterial3Api::class) // Añadido OptIn para Scaffold
@Composable
fun TaskListScreen(
    pisoId: String,
    navController: NavController
    // currentUid: String // Descomenta si necesitas el UID para 'completedByUid'
) {
    val db = Firebase.firestore
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // --- Estados para el Snackbar ---
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // LaunchedEffect para escuchar cambios en las tareas NO COMPLETADAS de este piso
    LaunchedEffect(pisoId) {
        isLoading = true
        errorMessage = null
        Log.d("TaskListScreen", "Setting up listener for incomplete tasks in piso: $pisoId")

        // --- Consulta Modificada ---
        // Ahora filtra para obtener solo las tareas NO completadas
        // y ordena por fecha de entrega.
        val tasksCollection = db.collection("pisos").document(pisoId).collection("tasks")
            .whereEqualTo("isCompleted", false) // <-- FILTRO AÑADIDO
            .orderBy("dueDate", Query.Direction.ASCENDING) // Ordenar solo por fecha ahora

        val listenerRegistration = tasksCollection.addSnapshotListener { snapshots, e ->
            if (e != null) {
                // --- Posible Nuevo Error de Índice ---
                // Si Firestore necesita un índice para esta NUEVA consulta
                // (whereEqualTo isCompleted=false, orderBy dueDate),
                // el error aparecerá aquí. Sigue el enlace que te dé.
                Log.w("TaskListScreen", "Listen failed.", e)
                errorMessage = "Error al cargar tareas: ${e.localizedMessage}"
                // Comprueba si es error de índice y muestra mensaje útil
                if (e.message?.contains("index") == true) {
                    errorMessage = "Se necesita un índice en Firestore. Revisa los logs o crea el índice manualmente (isCompleted=false, dueDate ASC)."
                }
                isLoading = false
                return@addSnapshotListener
            }

            // ... (resto del mapeo de snapshots a taskList, igual que antes) ...
            if (snapshots != null) {
                val taskList = snapshots.documents.mapNotNull { doc ->
                    try {
                        Task(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            dueDate = doc.getTimestamp("dueDate"),
                            assignedUserIds = doc.get("assignedUserIds") as? List<String> ?: emptyList(),
                            creatorUid = doc.getString("creatorUid") ?: "",
                            createdAt = doc.getTimestamp("createdAt"),
                            isCompleted = doc.getBoolean("isCompleted") ?: false,
                            pisoId = doc.getString("pisoId") ?: pisoId
                        )
                    } catch (ex: Exception) {
                        Log.e("TaskListScreen", "Error parsing task document ${doc.id}", ex)
                        null
                    }
                }
                tasks = taskList
                Log.d("TaskListScreen", "Incomplete tasks updated: ${taskList.size} tasks found.")
            } else {
                Log.d("TaskListScreen", "Current data: null")
                tasks = emptyList()
            }
            isLoading = false
        }
        // awaitClose { listenerRegistration.remove() } // Considerar para gestión avanzada del listener
    }

    // --- UI con Scaffold para Snackbar ---
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color(0xFF222222) // Fondo oscuro para el Scaffold
    ) { innerPadding -> // Recibe el padding del Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // <-- Aplica el padding del Scaffold
                .padding(horizontal = 16.dp, vertical = 16.dp), // Padding adicional si quieres
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Tareas",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF0B90B), // Amarillo
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFF0B90B))
            } else if (errorMessage != null) {
                Text("Error: $errorMessage", color = Color.Red, textAlign = TextAlign.Center)
            } else if (tasks.isEmpty()) {
                Text(
                    "¡Todo listo! No hay tareas pendientes.",
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 20.dp)
                )
            } else {
                Text(
                    "Tareas pendientes:",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(tasks, key = { it.id }) { task ->
                        TaskItem(task = task, onTaskCheckedChanged = { taskId, isChecked ->
                            // --- Lógica de Checkbox Modificada ---
                            // Actualizar en Firestore
                            db.collection("pisos").document(pisoId).collection("tasks").document(taskId)
                                .update(mapOf(
                                    "isCompleted" to isChecked,
                                    "completedAt" to if(isChecked) FieldValue.serverTimestamp() else null,
                                    // "completedByUid" to if(isChecked) currentUid else null // Descomenta si pasas currentUid
                                ))
                                .addOnSuccessListener {
                                    Log.d("TaskListScreen", "Task $taskId updated successfully.")
                                    // Mostrar Snackbar SOLO si se MARCA como completada
                                    if (isChecked) {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = "¡Tarea '${task.title}' completada!",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    }
                                }
                                .addOnFailureListener { e -> Log.w("TaskListScreen", "Error updating task $taskId", e) }
                            // --- Fin Lógica Checkbox ---
                        })
                        Divider(color = Color.Gray, thickness = 0.5.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón para agregar tarea (sin cambios)
            Button(
                onClick = { navController.navigate("create_task/$pisoId") },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000)) // Naranja
            ) {
                Text("Agregar tarea", color = Color.White, fontSize = 16.sp)
            }
        } // Fin Column
    } // Fin Scaffold
}

// Composable TaskItem (sin cambios funcionales grandes,
// pero ahora no necesitará tachar el texto porque las completadas no se mostrarán)
@Composable
fun TaskItem(task: Task, onTaskCheckedChanged: (String, Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.isCompleted, // Aunque siempre será false si el filtro funciona
            onCheckedChange = { isChecked ->
                onTaskCheckedChanged(task.id, isChecked)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFFF0B90B),
                uncheckedColor = Color.Gray,
                checkmarkColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                color = Color.White // Ya no necesitamos cambiar color/tachar
            )
            task.dueDate?.let {
                val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it.toDate())
                Text("Fecha límite: $formattedDate", fontSize = 12.sp, color = Color.LightGray)
            }
            // Puedes mostrar otros detalles si quieres
        }
    }
}