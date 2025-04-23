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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

// Data class para representar una Tarea leída desde Firestore
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
    // Puedes añadir completedByUid, completedAt si los necesitas mostrar
)

@Composable
fun TaskListScreen(
    pisoId: String,
    navController: NavController
    // Opcional: podrías pasar el UID del usuario actual si lo necesitas para filtros "Mis Tareas"
    // currentUid: String
) {
    val db = Firebase.firestore
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // LaunchedEffect para escuchar cambios en las tareas de este piso
    LaunchedEffect(pisoId) {
        isLoading = true
        errorMessage = null
        val tasksCollection = db.collection("pisos").document(pisoId).collection("tasks")
            .orderBy("isCompleted", Query.Direction.ASCENDING) // Mostrar no completadas primero
            .orderBy("dueDate", Query.Direction.ASCENDING) // Luego ordenar por fecha

        val listenerRegistration = tasksCollection.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("TaskListScreen", "Listen failed.", e)
                errorMessage = "Error al cargar tareas: ${e.localizedMessage}"
                isLoading = false
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val taskList = snapshots.documents.mapNotNull { doc ->
                    try {
                        // Mapear el documento a la data class Task
                        // Nota: Asegúrate que los nombres de campo coincidan con Firestore
                        Task(
                            id = doc.id,
                            title = doc.getString("title") ?: "",
                            description = doc.getString("description") ?: "",
                            dueDate = doc.getTimestamp("dueDate"),
                            assignedUserIds = doc.get("assignedUserIds") as? List<String> ?: emptyList(),
                            creatorUid = doc.getString("creatorUid") ?: "",
                            createdAt = doc.getTimestamp("createdAt"),
                            isCompleted = doc.getBoolean("isCompleted") ?: false,
                            pisoId = doc.getString("pisoId") ?: pisoId // Tomar de doc o usar el pasado
                        )
                    } catch (ex: Exception) {
                        Log.e("TaskListScreen", "Error parsing task document ${doc.id}", ex)
                        null // Ignorar documentos con formato incorrecto
                    }
                }
                tasks = taskList
                Log.d("TaskListScreen", "Tasks updated: ${taskList.size} tasks found.")
            } else {
                Log.d("TaskListScreen", "Current data: null")
                tasks = emptyList()
            }
            isLoading = false
        }
        // Asegurarse de remover el listener cuando el Composable se destruye
        // No es estrictamente necesario con LaunchedEffect si pisoId no cambia a menudo,
        // pero es buena práctica si este Composable puede dejar de estar activo.
        // Considera usar 'produceState' o un ViewModel para manejar mejor el listener.
        // awaitClose { listenerRegistration.remove() } // Requeriría convertir a callbackFlow
    }

    // --- UI ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), // Padding alrededor de la columna
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
            Text("Error: $errorMessage", color = Color.Red)
        } else if (tasks.isEmpty()) {
            Text("No hay tareas pendientes.", color = Color.Gray, modifier = Modifier.padding(top = 20.dp))
        } else {
            // TODO: Implementar agrupación por día si se desea.
            // Por ahora, una lista simple.
            Text(
                "Tareas pendientes:", // Como en la imagen
                color = Color.White,
                modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
            )
            LazyColumn(modifier = Modifier.weight(1f)) { // Ocupa el espacio disponible
                items(tasks, key = { it.id }) { task ->
                    TaskItem(task = task, onTaskCheckedChanged = { taskId, isChecked ->
                        // Lógica para actualizar el estado 'isCompleted' en Firestore
                        db.collection("pisos").document(pisoId).collection("tasks").document(taskId)
                            .update(mapOf(
                                "isCompleted" to isChecked,
                                "completedAt" to if(isChecked) FieldValue.serverTimestamp() else null,
                                // Podrías querer guardar quién la completó también
                                // "completedByUid" to if(isChecked) currentUid else null
                            ))
                            .addOnSuccessListener { Log.d("TaskListScreen", "Task $taskId updated successfully.") }
                            .addOnFailureListener { e -> Log.w("TaskListScreen", "Error updating task $taskId", e) }
                    })
                    Divider(color = Color.Gray, thickness = 0.5.dp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Botón para agregar tarea (abajo)
        Button(
            onClick = { navController.navigate("create_task/$pisoId") },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000)) // Naranja
        ) {
            Text("Agregar tarea", color = Color.White, fontSize = 16.sp)
        }
    }
}

// Composable para mostrar un item individual de la lista de tareas
@Composable
fun TaskItem(task: Task, onTaskCheckedChanged: (String, Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { isChecked ->
                onTaskCheckedChanged(task.id, isChecked)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFFF0B90B), // Amarillo
                uncheckedColor = Color.Gray,
                checkmarkColor = Color.Black
            )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                color = if (task.isCompleted) Color.Gray else Color.White, // Tachar o cambiar color si está completada
                textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
            )
            // Mostrar fecha límite si existe
            task.dueDate?.let {
                val formattedDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it.toDate())
                Text("Fecha límite: $formattedDate", fontSize = 12.sp, color = Color.LightGray)
            }
            // Podrías mostrar a quién está asignada aquí si obtienes los nombres
            // Text("Asignada a: ${task.assignedUserIds.joinToString()}", fontSize = 12.sp, color = Color.LightGray)
        }
        // Podrías añadir un icono o botón para ver detalles/editar
    }
}