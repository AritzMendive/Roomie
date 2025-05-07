package com.example.roomie.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
// import androidx.compose.foundation.lazy.LazyColumn // No se usa directamente aquí
// import androidx.compose.foundation.lazy.items // No se usa directamente aquí
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// import androidx.compose.ui.window.Dialog // UserSelectionDialog podría estar en otro lado
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Asumiendo que PisoMember está definido en otro archivo o lo defines aquí
// data class PisoMember(val uid: String, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExpenseScreen(
    navController: NavController,
    pisoId: String,
    auth: FirebaseAuth
) {
    val db = Firebase.firestore
    val coroutineScope = rememberCoroutineScope()
    val currentUser = auth.currentUser
    val currentUid = currentUser?.uid
    val currentUsername = currentUser?.displayName ?: "Usuario Desconocido"

    var title by remember { mutableStateOf("") }
    var amountString by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") } // <-- NUEVO CAMPO PARA DESCRIPCIÓN
    var expenseDate by remember { mutableStateOf<Date?>(Date()) }
    var assignedUserIds by remember { mutableStateOf<List<String>>(emptyList()) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showUserSelectionDialog by remember { mutableStateOf(false) }
    var pisoMembers by remember { mutableStateOf<List<PisoMember>>(emptyList()) }
    var membersLoading by remember { mutableStateOf(true) }

    val primaryAppColor = Color(0xFFF0B90B)
    val backgroundColor = Color(0xFF222222)
    val textFieldBackgroundColor = Color(0xFFF0B90B)
    val textFieldTextColor = Color.Black
    val buttonTextColor = Color.White
    val errorColor = Color.Red
    val successColor = Color(0xFF4CAF50)
    val scrollState = rememberScrollState() // Para Column con scroll

    LaunchedEffect(pisoId) {
        if (pisoId.isBlank()) {
            errorMessage = "ID de piso no válido."
            membersLoading = false
            return@LaunchedEffect
        }
        membersLoading = true
        pisoMembers = emptyList()
        Log.d("CreateExpenseScreen", "Cargando miembros para el piso: $pisoId")
        try {
            val pisoDoc = db.collection("pisos").document(pisoId).get().await()
            if (!pisoDoc.exists()) {
                throw Exception("Documento del piso no encontrado.")
            }
            val memberUids = pisoDoc.get("members") as? List<String> ?: emptyList()
            if (memberUids.isNotEmpty()) {
                val membersList = mutableListOf<PisoMember>()
                for (memberUid in memberUids) {
                    val userDoc = db.collection("users").document(memberUid).get().await()
                    if (userDoc.exists()) {
                        val name = userDoc.getString("username") ?: "Usuario (ID: ${memberUid.take(5)})"
                        membersList.add(PisoMember(memberUid, name))
                    }
                }
                pisoMembers = membersList
                if (currentUid != null) { // Asignar solo al creador por defecto
                    assignedUserIds = listOf(currentUid)
                } else {
                    assignedUserIds = membersList.map { it.uid } // O todos si no hay UID actual (raro)
                }

            }
        } catch (e: Exception) {
            Log.e("CreateExpenseScreen", "Error cargando miembros del piso", e)
            errorMessage = "Error al cargar miembros: ${e.localizedMessage}"
        } finally {
            membersLoading = false
        }
    }

    val createExpense: () -> Unit = createExpense@{
        if (currentUid == null) {
            errorMessage = "Usuario no autenticado."
            return@createExpense
        }
        if (title.isBlank()) {
            errorMessage = "El título no puede estar vacío."
            return@createExpense
        }
        val amount = amountString.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            errorMessage = "Introduce un importe válido."
            return@createExpense
        }
        if (expenseDate == null) {
            errorMessage = "Debes seleccionar una fecha para el gasto."
            return@createExpense
        }
        if (assignedUserIds.isEmpty()) {
            errorMessage = "Debes asignar al menos un usuario al gasto."
            return@createExpense
        }
        // La descripción puede estar vacía, no es obligatoria aquí
        // if (description.isBlank()) {
        //     errorMessage = "La descripción no puede estar vacía."
        //     return@createExpense
        // }


        isLoading = true
        errorMessage = null
        successMessage = null

        val initialPaymentsStatus = mutableMapOf<String, Boolean>()
        assignedUserIds.forEach { userId ->
            if (userId != currentUid) { // El que paga (currentUid) no se debe a sí mismo
                initialPaymentsStatus[userId] = false // Por defecto, nadie ha pagado
            }
        }

        val expenseData = hashMapOf(
            "title" to title,
            "amount" to amount,
            "description" to description.ifBlank { null }, // <-- GUARDAR DESCRIPCIÓN REAL, null si está vacía
            "date" to Timestamp(expenseDate!!),
            "pisoId" to pisoId,
            "creatorUid" to currentUid,
            "paidByUserId" to currentUid,
            "paidByUserName" to currentUsername,
            "involvedUserIds" to assignedUserIds,
            "createdAt" to FieldValue.serverTimestamp(),
            "paymentsStatus" to initialPaymentsStatus
        )

        db.collection("pisos").document(pisoId).collection("expenses")
            .add(expenseData)
            .addOnSuccessListener {
                Log.d("CreateExpenseScreen", "Gasto creado con ID: ${it.id}")
                isLoading = false
                successMessage = "¡Gasto '$title' agregado correctamente!"
                title = ""
                amountString = ""
                description = "" // Limpiar descripción
                // expenseDate = Date() // Opcional
                // assignedUserIds = if (currentUid != null) listOf(currentUid) else pisoMembers.map { it.uid } // Opcional

                // Considerar navegar hacia atrás después de un pequeño retraso para que el usuario vea el mensaje
                coroutineScope.launch {
                    kotlinx.coroutines.delay(1500) // Espera 1.5 segundos
                    if(navController.currentDestination?.route?.startsWith("create_expense") == true) { // Evita pop si ya no está en esta pantalla
                        navController.popBackStack()
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("CreateExpenseScreen", "Error al crear gasto", e)
                isLoading = false
                errorMessage = "Error al agregar gasto: ${e.message}"
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Agregar Gasto", color = primaryAppColor, fontWeight = FontWeight.Bold) },
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
        containerColor = backgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .verticalScroll(scrollState), // Hacer la columna scrolleable
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            FormTextFieldExpense(
                label = "Título del gasto:",
                value = title,
                onValueChange = { title = it },
                labelColor = Color.White,
                backgroundColor = textFieldBackgroundColor,
                textColor = textFieldTextColor
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- CAMPO IMPORTE ---
            FormTextFieldExpense(
                label = "Importe del gasto (€):",
                value = amountString,
                onValueChange = { amountString = it },
                keyboardType = KeyboardType.Number,
                labelColor = Color.White,
                backgroundColor = textFieldBackgroundColor,
                textColor = textFieldTextColor
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- NUEVO CAMPO DESCRIPCIÓN ---
            FormTextFieldExpense(
                label = "Descripción del gasto (opcional):",
                value = description,
                onValueChange = { description = it },
                labelColor = Color.White,
                backgroundColor = textFieldBackgroundColor,
                textColor = textFieldTextColor,
                singleLine = false, // Permitir múltiples líneas
                minLines = 2 // Mostrar al menos 2 líneas
            )
            Spacer(modifier = Modifier.height(16.dp))
            // --- FIN NUEVO CAMPO DESCRIPCIÓN ---


            Text("Fecha del gasto:", color = Color.White, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(textFieldBackgroundColor, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .clickable { showDatePickerDialog = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val formattedDate = expenseDate?.let {
                    SimpleDateFormat("dd / MM / yyyy", Locale.getDefault()).format(it)
                } ?: "Seleccionar fecha"
                Text(formattedDate, color = textFieldTextColor)
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text("Asignar usuarios (participantes):", color = Color.White, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(textFieldBackgroundColor, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .clickable(enabled = !membersLoading && pisoMembers.isNotEmpty()) {
                        if (pisoMembers.isNotEmpty()) showUserSelectionDialog = true
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val userText = when {
                    membersLoading -> "Cargando miembros..."
                    pisoMembers.isEmpty() -> "No hay miembros en el piso."
                    assignedUserIds.isEmpty() -> "Seleccionar usuarios"
                    else -> {
                        assignedUserIds
                            .mapNotNull { uid -> pisoMembers.find { it.uid == uid }?.name }
                            .joinToString(", ")
                            .ifEmpty { "Seleccionar usuarios" }
                    }
                }
                Text(userText, color = textFieldTextColor, maxLines = 3)
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (errorMessage != null) {
                Text(errorMessage!!, color = errorColor, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
            }
            if (successMessage != null) {
                Text(successMessage!!, color = successColor, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 8.dp))
            }

            if (isLoading) {
                CircularProgressIndicator(color = primaryAppColor, modifier = Modifier.padding(bottom = 16.dp))
            }

            Button(
                onClick = { createExpense() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFA000),
                    contentColor = buttonTextColor
                ),
                shape = RoundedCornerShape(8.dp),
                enabled = !isLoading
            ) {
                Text("Agregar gasto", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.height(16.dp)) // Espacio al final por si el teclado aparece
        }
    }

    if (showDatePickerDialog) {
        val calendar = Calendar.getInstance()
        expenseDate?.let { calendar.time = it }

        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = calendar.timeInMillis,
            yearRange = (Calendar.getInstance().get(Calendar.YEAR) - 10)..(Calendar.getInstance().get(Calendar.YEAR) + 10)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        expenseDate = Date(millis)
                    }
                    showDatePickerDialog = false
                }) { Text("Aceptar", color=primaryAppColor) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerDialog = false }) { Text("Cancelar", color=primaryAppColor) }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContentColor = Color.Black,
                    selectedDayContainerColor = primaryAppColor,
                    todayContentColor = primaryAppColor,
                    todayDateBorderColor = primaryAppColor,
                )
            )
        }
    }

    if (showUserSelectionDialog && pisoMembers.isNotEmpty()) {
        UserSelectionDialog( // Asegúrate que UserSelectionDialog esté definido y sea accesible
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

// Composable auxiliar para los TextFields del formulario (similar al de CreateTaskScreen)
@Composable
fun FormTextFieldExpense(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    labelColor: Color,
    backgroundColor: Color,
    textColor: Color,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = labelColor, modifier = Modifier.align(Alignment.Start).padding(bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = backgroundColor, // Borde del mismo color que el fondo para que no se vea doble
                unfocusedBorderColor = Color.Gray, // Un borde sutil cuando no está enfocado
                cursorColor = textColor,
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedContainerColor = backgroundColor,
                unfocusedContainerColor = backgroundColor,
                focusedLabelColor = textColor, // Aunque no usamos label flotante aquí
                unfocusedLabelColor = textColor
            ),
            placeholder = { Text(label.removeSuffix(":"), color = textColor.copy(alpha = 0.6f)) },
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}