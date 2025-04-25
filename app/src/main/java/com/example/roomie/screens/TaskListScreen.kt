package com.example.roomie.screens

import android.util.Log
import androidx.compose.foundation.background // <-- Importar background
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
import com.google.firebase.auth.FirebaseAuth // <-- Importar FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FieldPath // <-- Importar FieldPath
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await // <-- Importar await
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

// Data class PisoMember (puede estar aquí o importada)
// data class PisoMember(val uid: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    pisoId: String,
    navController: NavController,
    auth: FirebaseAuth // <-- Recibir FirebaseAuth
) {
    val db = Firebase.firestore
    val currentUid = auth.currentUser?.uid // <-- Obtener UID del usuario logueado

    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    // --- Estado para mapear UID a Nombre de Miembro ---
    var memberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoadingTasks by remember { mutableStateOf(true) }
    var isLoadingMembers by remember { mutableStateOf(true) } // Estado separado para carga de miembros
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // LaunchedEffect para cargar Tareas Y Nombres de Miembros
    LaunchedEffect(pisoId) {
        isLoadingTasks = true
        isLoadingMembers = true // Iniciar ambas cargas
        errorMessage = null
        Log.d("TaskListScreen", "Setting up listener for tasks and members in piso: $pisoId")

        // --- Cargar Nombres de Miembros ---
        coroutineScope.launch {
            try {
                Log.d("TaskListScreen", "Fetching piso document for members...")
                val pisoDoc = db.collection("pisos").document(pisoId).get().await()
                val memberUids = pisoDoc.get("members") as? List<String> ?: emptyList()
                Log.d("TaskListScreen", "Fetched member UIDs: $memberUids")

                if (memberUids.isNotEmpty()) {
                    Log.d("TaskListScreen", "Fetching usernames for members...")
                    // Asegúrate que la colección 'users' usa el Auth UID como ID de documento
                    val usersQuery = db.collection("users").whereIn(FieldPath.documentId(), memberUids).get().await()
                    val namesMap = usersQuery.documents.associate { userDoc ->
                        // Asegúrate que el campo 'username' existe en tus documentos de usuario
                        userDoc.id to (userDoc.getString("username") ?: "Usuario ID: ${userDoc.id.take(5)}")
                    }
                    memberNames = namesMap
                    Log.d("TaskListScreen", "Loaded member names: $memberNames")
                } else {
                    Log.d("TaskListScreen", "No members found in piso.")
                    memberNames = emptyMap()
                }
            } catch (e: Exception) {
                Log.e("TaskListScreen", "Error loading member names", e)
                errorMessage = (errorMessage ?: "") + "\nError al cargar nombres de miembros." // Añadir al error existente
                memberNames = emptyMap()
            } finally {
                isLoadingMembers = false // Termina carga de miembros
            }
        } // Fin coroutine carga nombres

        // --- Listener para Tareas ---
        val tasksCollection = db.collection("pisos").document(pisoId).collection("tasks")
            .whereEqualTo("isCompleted", false)
            .orderBy("dueDate", Query.Direction.ASCENDING)

        val listenerRegistration = tasksCollection.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("TaskListScreen", "Task listener failed.", e)
                errorMessage = (errorMessage ?: "") + "\nError al cargar tareas: ${e.localizedMessage}"
                if (e.message?.contains("index") == true) {
                    errorMessage += "\nSe necesita un índice en Firestore (isCompleted=false, dueDate ASC)."
                }
                isLoadingTasks = false
                return@addSnapshotListener
            }
            // Mapeo de tareas
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
                        Log.e("TaskListScreen", "Error parsing task doc ${doc.id}", ex)
                        null
                    }
                }
                tasks = taskList
                Log.d("TaskListScreen", "Incomplete tasks updated: ${taskList.size} tasks found.")
            } else {
                tasks = emptyList()
            }
            isLoadingTasks = false // Termina carga de tareas
        }
        // Considera añadir awaitClose { listenerRegistration.remove() } si usas callbackFlow
    }

    // --- UI ---
    val isLoading = isLoadingTasks || isLoadingMembers // Considerar cargando si cualquiera está activo
    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color(0xFF222222)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text( /* ... Título ... */
                "Tareas",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF0B90B),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (isLoading) {
                CircularProgressIndicator(color = Color(0xFFF0B90B))
            } else if (errorMessage != null) {
                Text("Error: $errorMessage", color = Color.Red, textAlign = TextAlign.Center)
            } else if (tasks.isEmpty()) {
                Text( /* ... Sin tareas ... */
                    "¡Todo listo! No hay tareas pendientes.",
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 20.dp)
                )
            } else {
                Text( /* ... Tareas pendientes ... */
                    "Tareas pendientes:",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Start).padding(bottom = 8.dp)
                )
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(tasks, key = { it.id }) { task ->
                        // --- Pasar currentUid y memberNames a TaskItem ---
                        TaskItem(
                            task = task,
                            currentUid = currentUid, // Pasar UID actual
                            memberNames = memberNames, // Pasar mapa de nombres
                            onTaskCheckedChanged = { taskId, isChecked ->
                                // Lógica Checkbox (sin cambios)
                                db.collection("pisos").document(pisoId).collection("tasks").document(taskId)
                                    .update(mapOf(
                                        "isCompleted" to isChecked,
                                        "completedAt" to if(isChecked) FieldValue.serverTimestamp() else null,
                                        // "completedByUid" to if(isChecked) currentUid else null
                                    ))
                                    .addOnSuccessListener {
                                        Log.d("TaskListScreen", "Task $taskId updated successfully.")
                                        if (isChecked) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar( /* ... SnackBar ... */
                                                    message = "¡Tarea '${task.title}' completada!",
                                                    duration = SnackbarDuration.Short
                                                )
                                            }
                                        }
                                    }
                                    .addOnFailureListener { e -> Log.w("TaskListScreen", "Error updating task $taskId", e) }
                            })
                        Divider(color = Color.Gray, thickness = 0.5.dp)
                    }
                } // Fin LazyColumn
            } // Fin else

            Spacer(modifier = Modifier.height(16.dp))
            // Botón Agregar Tarea (sin cambios)
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

// --- TaskItem Modificado ---
@Composable
fun TaskItem(
    task: Task,
    currentUid: String?, // UID del usuario logueado (puede ser null si no está logueado)
    memberNames: Map<String, String>, // Mapa UID -> Nombre
    onTaskCheckedChanged: (String, Boolean) -> Unit
) {
    // Determinar si el usuario actual está asignado
    val isCurrentUserAssigned = currentUid != null && task.assignedUserIds.contains(currentUid)

    // Color de fondo condicional
    val backgroundColor = if (isCurrentUserAssigned) {
        Color(0xFFFFF9C4) // Un amarillo pálido para resaltar sin ser agresivo
        // O usa el que tenías: Color(0xFFF0B90B)
    } else {
        Color.Transparent // Sin fondo especial si no está asignado
    }
    // Color de texto condicional
    val textColor = if (isCurrentUserAssigned) Color.Black else Color.White
    val secondaryTextColor = if (isCurrentUserAssigned) Color.DarkGray else Color.LightGray
    val checkboxCheckedColor = if (isCurrentUserAssigned) Color.Black else Color(0xFFF0B90B)
    val checkboxUncheckedColor = if (isCurrentUserAssigned) Color.Black else Color.Gray
    val checkboxCheckmarkColor = if (isCurrentUserAssigned) Color(0xFFFFF9C4) else Color.Black


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(4.dp)) // <-- Aplicar fondo y redondear un poco
            .padding(vertical = 10.dp, horizontal = 8.dp), // Ajustar padding si se aplica fondo
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.isCompleted, // Siempre será false debido al filtro Firestore
            onCheckedChange = { isChecked ->
                onTaskCheckedChanged(task.id, isChecked)
            },
            colors = CheckboxDefaults.colors(
                checkedColor = checkboxCheckedColor,
                uncheckedColor = checkboxUncheckedColor,
                checkmarkColor = checkboxCheckmarkColor
            )
        )
        Spacer(modifier = Modifier.width(12.dp)) // Un poco más de espacio
        Column(modifier = Modifier.weight(1f)) {
            // Título de la tarea
            Text(
                text = task.title,
                color = textColor, // Color de texto condicional
                fontWeight = FontWeight.Medium // Un poco más de peso
            )
            // Fecha Límite
            task.dueDate?.let {
                val formattedDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(it.toDate())
                Text(
                    "Límite: $formattedDate",
                    fontSize = 12.sp,
                    color = secondaryTextColor,
                    modifier = Modifier.padding(top = 2.dp) // Pequeño espacio
                )
            }
            // --- Mostrar usuarios asignados ---
            if (task.assignedUserIds.isNotEmpty()) {
                // Buscar nombres en el mapa, si no, mostrar UID corto o "Cargando..."
                val assignedNames = task.assignedUserIds.map { uid ->
                    memberNames[uid] ?: "ID:...${uid.takeLast(4)}" // Muestra nombre o parte del ID
                }.joinToString(", ")

                // Mostrar solo si tenemos nombres o UIDs
                if (assignedNames.isNotBlank()) {
                    Text(
                        "Para: $assignedNames",
                        fontSize = 12.sp,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (memberNames.isEmpty() && task.assignedUserIds.isNotEmpty()){
                    // Indicador mientras cargan los nombres la primera vez
                    Text(
                        "Cargando asignados...",
                        fontSize = 12.sp,
                        color = secondaryTextColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            // --- FIN Mostrar usuarios ---
        }
    }
}