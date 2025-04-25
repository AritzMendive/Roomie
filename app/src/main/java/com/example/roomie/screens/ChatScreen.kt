package com.example.roomie.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack // Icono para volver atrás
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController // Importar NavController
import com.example.roomie.data.ChatMessage //
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration // Importar para el listener
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope // Importar CoroutineScope
import kotlinx.coroutines.Dispatchers // Importar Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await // Asegúrate de tener esta importación
import kotlinx.coroutines.withContext // Importar withContext

@OptIn(ExperimentalMaterial3Api::class) // Necesario para TopAppBar experimental
@Composable
fun ChatScreen(
    pisoId: String,
    auth: FirebaseAuth,
    navController: NavController
) {
    val db = Firebase.firestore
    val currentUser = auth.currentUser
    val currentUid = currentUser?.uid
    val currentUsername = currentUser?.displayName ?: "Usuario desconocido"

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var newMessageText by remember { mutableStateOf("") }
    var isLoadingMessages by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pisoName by remember { mutableStateOf("Chat") }
    var isLoadingPisoName by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope() // Usaremos este scope

    // --- Efecto para cargar el nombre del piso (sin cambios) ---
    LaunchedEffect(pisoId) { //
        // ... (como estaba antes) ...
        isLoadingPisoName = true
        try {
            val pisoDoc = db.collection("pisos").document(pisoId).get().await()
            pisoName = pisoDoc.getString("nombre") ?: "Chat del Piso"
            Log.d("ChatScreen", "Piso name loaded: $pisoName")
        } catch (e: Exception) {
            Log.e("ChatScreen", "Error loading piso name", e)
            pisoName = "Error Nombre"
        } finally {
            isLoadingPisoName = false
        }
    }
    // --- Fin efecto nombre piso ---

    // --- Listener para mensajes ---
    DisposableEffect(pisoId, currentUid) { // Usar DisposableEffect para manejar la limpieza del listener
        var listenerRegistration: ListenerRegistration? = null // Variable para guardar el listener

        if (pisoId.isNotBlank() && currentUid != null) {
            isLoadingMessages = true // Marcar inicio de carga

            val chatMessagesRef = db.collection("pisos").document(pisoId)
                .collection("chatMessages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limitToLast(100)

            listenerRegistration = chatMessagesRef.addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("ChatScreen", "Listen failed.", e)
                    errorMessage = "Error al cargar mensajes: ${e.localizedMessage}"
                    isLoadingMessages = false
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val fetchedMessages = snapshots.documents.mapNotNull { doc ->
                        try {
                            doc.toObject<ChatMessage>()?.copy(id = doc.id) //
                        } catch (ex: Exception) {
                            Log.e("ChatScreen", "Error parsing chat message ${doc.id}", ex)
                            null
                        }
                    }
                    // Actualizar estado en el hilo principal si es necesario (aunque Compose suele manejarlo)
                    coroutineScope.launch(Dispatchers.Main) { // Asegurarse que la actualización de estado sea en Main
                        val previousSize = messages.size
                        messages = fetchedMessages
                        isLoadingMessages = false // Marcar fin de carga aquí
                        errorMessage = null // Limpiar error

                        // Scroll animado para nuevos mensajes (si ya estábamos al final)
                        if (fetchedMessages.size > previousSize && previousSize > 0) {
                            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                            if(lastVisibleItemIndex >= previousSize - 2 ) { // Si veíamos los últimos mensajes
                                // Usar animateScrollToItem para nuevos mensajes
                                listState.animateScrollToItem(fetchedMessages.lastIndex)
                            }
                        }
                    }
                    Log.d("ChatScreen", "${fetchedMessages.size} messages loaded for piso $pisoId")
                } else {
                    // Si snapshots es null, podría ser un estado intermedio o un error no capturado
                    coroutineScope.launch(Dispatchers.Main) {
                        messages = emptyList() // Limpiar por si acaso
                        isLoadingMessages = false
                        // Considerar si poner un mensaje de error aquí
                    }
                }
            }
        } else {
            // Manejar caso donde pisoId o currentUid son inválidos al inicio
            coroutineScope.launch(Dispatchers.Main) {
                errorMessage = "Error: No se pudo identificar el piso o el usuario."
                isLoadingMessages = false
            }
        }

        onDispose { // Se ejecuta cuando el Composable se elimina (navega fuera, etc.)
            Log.d("ChatScreen", "Removing Firestore listener")
            listenerRegistration?.remove() // Elimina el listener para evitar fugas
        }
    } // --- Fin DisposableEffect Listener ---


    // --- Efecto para SCROLL INICIAL ---
    LaunchedEffect(messages, isLoadingMessages) { // Se ejecuta cuando messages o isLoadingMessages cambian
        // Espera a que los mensajes NO estén vacíos Y la carga haya terminado
        if (messages.isNotEmpty() && !isLoadingMessages) {
            // Usa scrollToItem para ir directo al último mensaje la primera vez
            listState.scrollToItem(messages.lastIndex)
            Log.d("ChatScreen", "Initial scroll performed to index: ${messages.lastIndex}")
        }
    }
    // --- Fin Efecto Scroll Inicial ---


    // Función para enviar mensajes (sin cambios)
    val sendMessage: () -> Unit = sendMessage@{ //
        // ... (como estaba antes) ...
        if (newMessageText.isBlank() || currentUid == null) return@sendMessage

        val message = ChatMessage(
            pisoId = pisoId,
            senderUid = currentUid,
            senderName = currentUsername, // Guarda el nombre actual
            text = newMessageText.trim(),
            timestamp = null // Firestore se encargará con @ServerTimestamp
        ) //

        val textToSend = newMessageText // Guarda el texto antes de limpiar el campo
        newMessageText = "" // Limpia el campo de texto inmediatamente

        db.collection("pisos").document(pisoId).collection("chatMessages")
            .add(message)
            .addOnSuccessListener {
                Log.d("ChatScreen", "Message sent successfully!")
                // Scroll animado se maneja ahora en el listener al detectar cambio de tamaño
            }
            .addOnFailureListener { e ->
                Log.e("ChatScreen", "Error sending message", e)
                errorMessage = "Error al enviar mensaje."
                newMessageText = textToSend // Restaura el texto si falla
            }
    }

    // --- UI con Scaffold (sin cambios en la estructura del Scaffold) ---
    Scaffold( //
        topBar = {
            TopAppBar( //
                title = {
                    Text(
                        text = if (isLoadingPisoName) "Cargando..." else pisoName,
                        color = Color(0xFFF0B90B) // Color amarillo para el título
                    )
                },
                navigationIcon = { // Icono para volver atrás
                    IconButton(onClick = { navController.popBackStack() }) { // Acción de volver
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Color(0xFFF0B90B) // Color amarillo para el icono
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A) // Mismo color que HomeScreen
                )
            )
        },
        containerColor = Color(0xFF222222) // Color de fondo general
    ) { innerPadding -> // El contenido principal recibe el padding del Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Aplicar el padding del Scaffold aquí
        ) {

            // Indicador de carga o error general
            if (isLoadingMessages && messages.isEmpty()) { // Mostrar solo al inicio
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFF0B90B))
                }
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = Color.Red,
                    modifier = Modifier.fillMaxWidth().padding(horizontal=16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Lista de Mensajes
            LazyColumn( //
                state = listState,
                modifier = Modifier
                    .weight(1f) // Ocupa todo el espacio disponible menos el input
                    .padding(horizontal = 16.dp) // Padding horizontal para los mensajes
                    .padding(top = 8.dp), // Añadir padding superior si es necesario
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message, currentUid = currentUid) //
                }
                // Añadir un Spacer al final para que el último mensaje no quede pegado al input
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // Input y Botón de Enviar
            MessageInput( //
                value = newMessageText,
                onValueChange = { newMessageText = it },
                onSendClick = sendMessage
            )
        }
    } // Fin Scaffold
}

// Composable MessageBubble (sin cambios)
@Composable
fun MessageBubble(message: ChatMessage, currentUid: String?) { //
    // ... (como estaba antes) ...
    val isCurrentUser = message.senderUid == currentUid
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isCurrentUser) Color(0xFFF0B90B) else Color(0xFF4A4A4A) // Amarillo para mí, gris para otros
    val textColor = if (isCurrentUser) Color.Black else Color.White

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isCurrentUser) 40.dp else 0.dp, // Margen izquierdo si soy yo
                end = if (isCurrentUser) 0.dp else 40.dp    // Margen derecho si son otros
            )
    ) {
        Column(
            modifier = Modifier
                .align(alignment)
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Nombre del remitente (si no es el usuario actual)
            if (!isCurrentUser) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD0D0D0) // Un gris más claro para el nombre
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            // Texto del mensaje
            Text(
                text = message.text,
                color = textColor
            )
            // Hora (opcional, requiere formateo de Timestamp)
            message.timestamp?.toDate()?.let { date -> //
                val formattedTime = remember(date) { // Formatear solo si cambia
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(date)
                }
                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    color = if (isCurrentUser) Color.DarkGray else Color.LightGray,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }
    }
}

// Composable MessageInput (sin cambios)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageInput( //
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit
) {
    // ... (como estaba antes) ...
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A)) // Fondo oscuro para la barra de input
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Escribe un mensaje...", color = Color.Gray) },
            shape = RoundedCornerShape(20.dp), // Bordes redondeados
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color(0xFFF0B90B),
                focusedBorderColor = Color(0xFFF0B90B),
                unfocusedBorderColor = Color.Gray,
                focusedContainerColor = Color(0xFF333333), // Fondo del campo
                unfocusedContainerColor = Color(0xFF333333)
            ),
            maxLines = 4 // Permitir varias líneas
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSendClick,
            enabled = value.isNotBlank(), // Habilitado solo si hay texto
            modifier = Modifier
                .size(48.dp)
                .background(
                    if (value.isNotBlank()) Color(0xFFF0B90B) else Color.Gray, // Color del botón
                    CircleShape
                )
        ) {
            Icon(
                Icons.Filled.Send,
                contentDescription = "Enviar mensaje",
                tint = Color.Black // Icono negro
            )
        }
    }
}