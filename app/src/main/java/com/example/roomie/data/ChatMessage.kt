package com.example.roomie.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class ChatMessage(
    val id: String = "", // ID del documento de Firestore
    val pisoId: String = "",
    val senderUid: String = "",
    val senderName: String = "", // Guardar el nombre para no tener que buscarlo siempre
    val text: String = "",
    @ServerTimestamp // Firestore pondrá la hora del servidor automáticamente al crear
    val timestamp: Timestamp? = null
)