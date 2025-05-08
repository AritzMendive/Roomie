// ProfileScreen.kt

package com.example.roomie.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.ContentCopy // Icono para copiar
import androidx.compose.material.icons.filled.Share // Icono alternativo para compartir
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager // Para copiar al portapapeles
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString // Necesario para copiar
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast // Para mostrar mensaje de copiado
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.example.roomie.screens.ui.theme.InterFontFamily
import com.example.roomie.screens.ui.theme.SyneFontFamily
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

// import kotlinx.coroutines.launch // No se usa directamente aquí

@Composable
fun ProfileScreen(
    auth: FirebaseAuth,
    onLogout: () -> Unit,
    onNavigateToJoinPiso: () -> Unit,
    onNavigateToCreatePiso: () -> Unit,
    onNavigateToPisoHome: (pisoId: String) -> Unit
) {
    val currentUser = auth.currentUser
    val username = currentUser?.displayName
    val uid = currentUser?.uid
    val db = Firebase.firestore

    var pisoId by remember { mutableStateOf<String?>(null) }
    var pisoName by remember { mutableStateOf<String?>(null) }
    var isLoadingPisoName by remember { mutableStateOf(false) }

    // Para la funcionalidad de copiar
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val yellowColor = Color(0xFFF0B433)
    val lightYellowColor = Color(0xFFF4C561) // Un amarillo muy claro, casi blanco
    val gradientBrush = Brush.verticalGradient( // Gradiente vertical
        colors = listOf(lightYellowColor, yellowColor)
    )
    val disabledColor = Color.Gray

    LaunchedEffect(uid) {
        if (uid != null) {
            isLoadingPisoName = true
            pisoId = null
            pisoName = null
            try {
                Log.d("ProfileScreen", "Fetching user document for UID: $uid")
                val userDoc = db.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    val fetchedPisoId = userDoc.getString("piso")
                    pisoId = fetchedPisoId // Guardar el ID
                    Log.d("ProfileScreen", "User belongs to piso ID: $fetchedPisoId")

                    if (fetchedPisoId != null && fetchedPisoId.isNotBlank()) {
                        try {
                            Log.d("ProfileScreen", "Fetching piso document for ID: $fetchedPisoId")
                            val pisoDoc = db.collection("pisos").document(fetchedPisoId).get().await()
                            if (pisoDoc.exists()) {
                                pisoName = pisoDoc.getString("nombre")
                                Log.d("ProfileScreen", "Fetched piso name: $pisoName")
                            } else {
                                Log.w("ProfileScreen", "Piso document $fetchedPisoId does not exist.")
                                pisoName = null
                            }
                        } catch (e: Exception) {
                            Log.e("ProfileScreen", "Error fetching piso document $fetchedPisoId", e)
                            pisoName = null
                        }
                    } else {
                        pisoName = null
                    }
                } else {
                    Log.w("ProfileScreen", "User document $uid does not exist.")
                    pisoId = null; pisoName = null
                }
            } catch (e: Exception) {
                Log.e("ProfileScreen", "Error fetching user document $uid", e)
                pisoId = null; pisoName = null
            } finally {
                isLoadingPisoName = false
            }
        } else {
            pisoId = null; pisoName = null; isLoadingPisoName = false
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF242424)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Bienvenida ---
            Text(
                text = "Bienvenido/a",
                style = TextStyle(fontSize = 24.sp, fontFamily = SyneFontFamily, fontWeight = FontWeight.ExtraBold, color = Color.White),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = username ?: "Usuario",
                style = TextStyle(fontSize = 36.sp, color = Color(0xFFF0B90B), fontFamily = SyneFontFamily, fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // --- Sección Tus Pisos ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Apartment,
                    contentDescription = "Icono Piso",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tu piso actual:", // Cambiado para claridad
                    style = TextStyle(fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = SyneFontFamily),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- Botón/Indicador del piso actual ---
            val isEnabled = pisoId != null && !isLoadingPisoName // Determinar si está habilitado

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp)) // Aplicar forma redondeada al Box
                    .background(
                        brush = if (isEnabled) gradientBrush else SolidColor(disabledColor), // Aplicar gradiente o color gris
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = isEnabled) { // Hacer clicable el Box
                        pisoId?.let { onNavigateToPisoHome(it) }
                    },
                contentAlignment = Alignment.Center // Centrar el texto dentro del Box
            ) {
                val buttonText = when {
                    isLoadingPisoName -> "Cargando nombre..."
                    pisoName != null -> pisoName
                    pisoId != null -> "Ir al Piso"
                    else -> "No estás en ningún piso"
                }
                Text(
                    text = buttonText ?: "Error",
                    // fontFamily = InterFontFamily, // Aplica tu fuente si la tienes
                    fontWeight = FontWeight.Bold, // O SemiBold
                    fontFamily = InterFontFamily,
                    fontSize = 24.sp,
                    color = if (isEnabled) Color.White else Color.White // Color del texto (negro sobre amarillo, blanco sobre gris)
                )
            }

            // --- NUEVA SECCIÓN: MOSTRAR Y COPIAR ID DEL PISO ---
            if (pisoId != null && pisoId!!.isNotBlank() && !isLoadingPisoName) {
                Spacer(modifier = Modifier.height(24.dp)) // Más espacio antes de esta sección
                Text(
                    text = "Compartir ID:",
                    style = TextStyle(fontSize = 16.sp, color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold),
                    modifier = Modifier.align(Alignment.Start) // Alinear a la izquierda
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min) // Ajustar altura al contenido
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween // Espacio entre texto y botón
                ) {
                    // Mostrar el ID del piso
                    Text(
                        text = pisoId!!,
                        style = TextStyle(fontSize = 15.sp, color = Color.LightGray, fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f) // Permitir que ocupe espacio y se ajuste
                    )
                    // Botón para copiar el ID
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(pisoId!!))
                            // Mostrar mensaje de confirmación (Toast)
                            Toast.makeText(context, "ID del piso copiado", Toast.LENGTH_SHORT).show()
                            Log.d("ProfileScreen", "Piso ID copied: $pisoId")
                        },
                        modifier = Modifier.size(36.dp) // Tamaño del botón icono
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copiar ID del piso",
                            tint = Color(0xFFF0B90B) // Color del icono
                        )
                    }
                }
            }
            // --- FIN NUEVA SECCIÓN ---

            Spacer(modifier = Modifier.height(32.dp)) // Espacio antes de los botones de acción

            // --- Botones de Acción ---
            Button(
                onClick = { onNavigateToCreatePiso() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE4D21)) // Rojo
            ) {
                Text("+ Crear nuevo piso", color = Color.White, fontSize = 18.sp, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onNavigateToJoinPiso() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE7D21)) // Naranja
            ) {
                Text("Unirte a nuevo piso", color = Color.White, fontSize = 18.sp, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.weight(1f))

            // --- Botón Cerrar Sesión ---
            Button(
                onClick = { onLogout() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Cerrar Sesión", style = TextStyle(color = Color.White, fontSize = 16.sp))
            }
        }
    }
}