package com.example.roomie.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Incident(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val pisoId: String = "",
    val reportedByUid: String = "",
    val reportedByName: String = "",
    @ServerTimestamp
    val timestamp: Timestamp? = null,
    val status: String = "pendiente"
)