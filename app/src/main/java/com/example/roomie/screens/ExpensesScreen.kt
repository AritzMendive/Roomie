package com.example.roomie.screens

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
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Data class para representar un Gasto
data class Expense(
    val id: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val description: String? = null,
    val date: Timestamp? = null,
    val paidByUserId: String = "",      // Quién adelantó el dinero
    var paidByUserName: String = "",    // Nombre de quien adelantó (para UI)
    val pisoId: String = "",
    val creatorUid: String = "",        // Quién creó el registro del gasto en la app
    val involvedUserIds: List<String> = emptyList(), // Todos los que participan
    val createdAt: Timestamp? = null,
    // NUEVO CAMPO: Rastrea quién ha pagado su parte al 'paidByUserId'
    // Key: userId del involucrado, Value: true si ha pagado, false si no
    val paymentsStatus: Map<String, Boolean> = emptyMap()
)

// Data class PisoMember (asegúrate que esté definida, puede ser en un archivo común)
// data class PisoMember(val uid: String, val name: String)


@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun ExpensesScreen(
    pisoId: String,
    navController: NavController,
    auth: FirebaseAuth
) {
    val db = Firebase.firestore
    val currentUid = auth.currentUser?.uid ?: ""

    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var isLoadingData by remember { mutableStateOf(true) } // Unificado para gastos y miembros
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var totalExpensesThisMonth by remember { mutableStateOf(0.0) }
    var youAreOwed by remember { mutableStateOf(0.0) }
    var youOwe by remember { mutableStateOf(0.0) }

    var pisoMemberNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPaymentSheet by remember { mutableStateOf(false) }
    var selectedExpenseForPayment by remember { mutableStateOf<Expense?>(null) }

    val primaryAppColor = Color(0xFFF0B90B)
    val topAppBarColor = Color(0xFF1A1A1A)
    val backgroundColor = Color(0xFF222222)
    val cardBackgroundColor = Color(0xFF333333)
    val textColorPrimary = Color.White
    val textColorSecondary = Color.LightGray // Ajustado para mejor contraste

    LaunchedEffect(pisoId, currentUid) {
        if (pisoId.isBlank()) {
            errorMessage = "ID de piso inválido."
            isLoadingData = false
            return@LaunchedEffect
        }
        isLoadingData = true
        errorMessage = null
        Log.d("ExpensesScreen", "Cargando datos para el piso: $pisoId, usuario: $currentUid")

        // Cargar nombres de los miembros del piso
        coroutineScope.launch {
            try {
                val pisoDoc = db.collection("pisos").document(pisoId).get().await()
                val memberUids = pisoDoc.get("members") as? List<String> ?: emptyList()
                if (memberUids.isNotEmpty()) {
                    val usersQuerySnapshot = db.collection("users").whereIn(FieldPath.documentId(), memberUids).get().await()
                    pisoMemberNames = usersQuerySnapshot.documents.associate { doc ->
                        doc.id to (doc.getString("username") ?: "Desconocido_${doc.id.take(4)}")
                    }
                }
                Log.d("ExpensesScreen", "Nombres de miembros cargados: $pisoMemberNames")
            } catch (e: Exception) {
                Log.e("ExpensesScreen", "Error cargando nombres de miembros: ${e.message}", e)
                // No establecer errorMessage aquí para priorizar errores de carga de gastos
            }
            // La carga de gastos establecerá isLoadingData = false al final
        }

        // Listener para gastos
        val expensesCollection = db.collection("pisos").document(pisoId)
            .collection("expenses")
            .orderBy("date", Query.Direction.DESCENDING)

        val listenerRegistration = expensesCollection.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("ExpensesScreen", "Error al escuchar gastos.", e)
                errorMessage = "Error al cargar gastos: ${e.localizedMessage}"
                isLoadingData = false
                return@addSnapshotListener
            }

            if (snapshots != null) {
                val fetchedExpenses = snapshots.documents.mapNotNull { doc ->
                    doc.toObject<Expense>()?.copy(id = doc.id)
                }
                expenses = fetchedExpenses

                var tempTotalOwedToCurrentUser = 0.0
                var tempTotalCurrentUserOwes = 0.0
                val cal = Calendar.getInstance()
                val currentMonth = cal.get(Calendar.MONTH)
                val currentYear = cal.get(Calendar.YEAR)
                var tempTotalMonth = 0.0

                fetchedExpenses.forEach { expense ->
                    // Cálculo de gastos del mes
                    expense.date?.toDate()?.let { date ->
                        cal.time = date
                        if (cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear) {
                            tempTotalMonth += expense.amount
                        }
                    }

                    // Cálculo de deudas
                    val numberOfParticipants = expense.involvedUserIds.size
                    if (numberOfParticipants == 0) return@forEach

                    val amountPerPerson = expense.amount / numberOfParticipants

                    if (expense.paidByUserId == currentUid) {
                        expense.involvedUserIds.forEach { involvedUserId ->
                            if (involvedUserId != currentUid) {
                                val hasPaid = expense.paymentsStatus[involvedUserId] ?: false
                                if (!hasPaid) {
                                    tempTotalOwedToCurrentUser += amountPerPerson
                                }
                            }
                        }
                    } else if (expense.involvedUserIds.contains(currentUid)) {
                        val hasPaid = expense.paymentsStatus[currentUid] ?: false
                        if (!hasPaid) {
                            tempTotalCurrentUserOwes += amountPerPerson
                        }
                    }
                }
                youAreOwed = tempTotalOwedToCurrentUser
                youOwe = tempTotalCurrentUserOwes
                totalExpensesThisMonth = tempTotalMonth
                Log.d("ExpensesScreen", "${fetchedExpenses.size} gastos cargados. Te deben: $youAreOwed, Debes: $youOwe")
            } else {
                expenses = emptyList()
                youAreOwed = 0.0
                youOwe = 0.0
                totalExpensesThisMonth = 0.0
            }
            isLoadingData = false
        }
        // No es necesario `awaitClose` para `addSnapshotListener` dentro de `LaunchedEffect`
        // ya que el listener se elimina automáticamente cuando el `LaunchedEffect` se cancela/relanza.
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Gastos", color = primaryAppColor, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = topAppBarColor)
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Aplicar padding del Scaffold
            // El padding horizontal y vertical para el contenido se maneja dentro
        ) {
            // Contenido principal (Card de resumen y lista de gastos)
            Column(
                modifier = Modifier
                    .weight(1f) // Permite que la lista ocupe el espacio disponible
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBackgroundColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Te deben: %.2f€".format(youAreOwed), color = Color(0xFF4CAF50), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Debes: %.2f€".format(youOwe), color = Color(0xFFF44336), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Gasto total del mes:",
                            color = textColorSecondary,
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            String.format(Locale.getDefault(), "%.2f€", totalExpensesThisMonth),
                            color = primaryAppColor,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }

                if (isLoadingData) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { // fillMaxSize en lugar de weight(1f) para el Box de carga
                        CircularProgressIndicator(color = primaryAppColor)
                    }
                } else if (errorMessage != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(errorMessage!!, color = Color.Red, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
                    }
                } else if (expenses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No hay gastos registrados todavía.", color = textColorSecondary, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        // No necesita modifier = Modifier.fillMaxSize() aquí si el Column padre ya tiene weight(1f)
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(expenses, key = { it.id }) { expense ->
                            val paidByActualName = pisoMemberNames[expense.paidByUserId] ?: expense.paidByUserName
                            ExpenseItem(
                                expense = expense.copy(paidByUserName = paidByActualName),
                                currentUserId = currentUid,
                                pisoMemberNames = pisoMemberNames,
                                onManagePaymentsClicked = {
                                    selectedExpenseForPayment = it
                                    showPaymentSheet = true
                                }
                            )
                        }
                        // No es necesario el Spacer aquí si el botón va fuera del LazyColumn
                    }
                }
            } // Fin Column con weight(1f)

            // --- NUEVO BOTÓN "AGREGAR GASTO" ---
            // Se coloca fuera del Column con weight(1f) para que esté al final
            // pero antes del ModalBottomSheet
            Button(
                onClick = { navController.navigate("create_expense/$pisoId") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp) // Padding para el botón
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000)) // Mismo color que en TaskListScreen
            ) {
                Text("Agregar gasto", color = Color.White, fontSize = 16.sp) // O 18.sp como en TaskListScreen
            }
        }

        if (showPaymentSheet && selectedExpenseForPayment != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    coroutineScope.launch {
                        sheetState.hide()
                        showPaymentSheet = false
                        selectedExpenseForPayment = null // Limpiar selección
                    }
                },
                sheetState = sheetState,
                containerColor = Color(0xFF2D2D2D) // Un poco más oscuro para el sheet
            ) {
                PaymentManagementSheetContent(
                    expense = selectedExpenseForPayment!!,
                    currentUserId = currentUid,
                    pisoMemberNames = pisoMemberNames,
                    onMarkAsPaid = { expenseId, debtorUid, hasPaid ->
                        coroutineScope.launch {
                            // Usar notación de punto para actualizar un campo dentro de un mapa en Firestore
                            val paymentPath = "paymentsStatus.$debtorUid"
                            db.collection("pisos").document(pisoId)
                                .collection("expenses").document(expenseId)
                                .update(paymentPath, hasPaid)
                                .addOnSuccessListener { Log.d("ExpensesScreen", "Pago de $debtorUid actualizado a $hasPaid para gasto $expenseId") }
                                .addOnFailureListener { e -> Log.e("ExpensesScreen", "Error al actualizar pago para $debtorUid en gasto $expenseId", e) }
                            // La UI se actualizará a través del listener de Firestore
                        }
                    },
                    onDismiss = {
                        coroutineScope.launch {
                            sheetState.hide()
                            showPaymentSheet = false
                            selectedExpenseForPayment = null // Limpiar selección
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ExpenseItem(
    expense: Expense,
    currentUserId: String,
    pisoMemberNames: Map<String, String>,
    onManagePaymentsClicked: (Expense) -> Unit,
    textColor: Color = Color.White,
    secondaryTextColor: Color = Color.LightGray
) {
    val isPaidByCurrentUser = expense.paidByUserId == currentUserId
    val isCurrentUserInvolved = expense.involvedUserIds.contains(currentUserId)
    val amountPerPerson = if (expense.involvedUserIds.isNotEmpty()) expense.amount / expense.involvedUserIds.size else 0.0

    val canManagePayments = isPaidByCurrentUser && expense.involvedUserIds.any { it != currentUserId }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canManagePayments) { // Solo el que pagó puede gestionar pagos de otros
                if (canManagePayments) onManagePaymentsClicked(expense)
            },
        shape = RoundedCornerShape(10.dp), // Bordes un poco más redondeados
        colors = CardDefaults.cardColors(containerColor = Color(0xFF424242)) // Gris oscuro para los items
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.title, color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(4.dp))
                val formattedDate = expense.date?.toDate()?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it) } ?: "Fecha desconocida"
                Text("$formattedDate - ${expense.paidByUserName}", color = secondaryTextColor, fontSize = 13.sp)

                if (!expense.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(expense.description, color = secondaryTextColor, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.copy(lineHeight = 16.sp))
                }

                // Mostrar estado de pago resumido
                var summaryText = ""
                var summaryColor = textColor
                if (isPaidByCurrentUser) {
                    val numOwedByOthers = expense.involvedUserIds.count { it != currentUserId && expense.paymentsStatus[it] == false }
                    if (numOwedByOthers > 0) {
                        summaryText = "Te deben ${numOwedByOthers} ${if(numOwedByOthers == 1) "persona" else "personas"}"
                        summaryColor = Color(0xFF4CAF50) // Verde
                    }
                } else if (isCurrentUserInvolved) {
                    val currentUserHasPaid = expense.paymentsStatus[currentUserId] ?: false
                    if (!currentUserHasPaid) {
                        summaryText = "Debes tu parte (%.2f€)".format(amountPerPerson)
                        summaryColor = Color(0xFFF44336) // Rojo
                    } else {
                        summaryText = "Tu parte está pagada"
                        summaryColor = Color.Gray
                    }
                }

                if (summaryText.isNotEmpty()) {
                    Text(summaryText, color = summaryColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 5.dp))
                }

                if (canManagePayments) {
                    Text("Gestionar pagos...", color = Color(0xFFF0B90B), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(String.format(Locale.getDefault(), "%.2f€", expense.amount), color = textColor, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PaymentManagementSheetContent(
    expense: Expense,
    currentUserId: String,
    pisoMemberNames: Map<String, String>,
    onMarkAsPaid: (expenseId: String, debtorUid: String, hasPaid: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val amountPerPerson = if (expense.involvedUserIds.isNotEmpty()) expense.amount / expense.involvedUserIds.size else 0.0
    val primaryAppColor = Color(0xFFF0B90B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top=16.dp, bottom = 24.dp) // Más padding inferior
            .navigationBarsPadding()
    ) {
        Text(
            "Gestionar Pagos: \"${expense.title}\"",
            fontSize = 18.sp, // Ligeramente más pequeño
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp).align(Alignment.CenterHorizontally)
        )
        Divider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(bottom=12.dp))

        Text(
            "Pagado por: ${pisoMemberNames[expense.paidByUserId] ?: expense.paidByUserName}",
            fontSize = 15.sp, color = Color.LightGray, modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            "Total: %.2f€  |  Por persona: %.2f€".format(expense.amount, amountPerPerson),
            fontSize = 15.sp, color = Color.LightGray, modifier = Modifier.padding(bottom = 16.dp)
        )

        // Solo mostrar la lista si el usuario actual es quien pagó
        if (expense.paidByUserId == currentUserId) {
            Text("Participantes:", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.padding(bottom=8.dp))
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp) // Altura máxima para la lista
            ) {
                items(expense.involvedUserIds.filter { it != currentUserId }, key = { it }) { debtorUid ->
                    val debtorName = pisoMemberNames[debtorUid] ?: "Usuario (${debtorUid.take(4)})"
                    val hasPaid = expense.paymentsStatus[debtorUid] ?: false
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(debtorName, color = Color.White, fontSize = 15.sp)
                            Text(if (hasPaid) "Pagado" else "Pendiente", color = if (hasPaid) Color(0xFF4CAF50) else Color(0xFFF44336), fontSize = 13.sp)
                        }
                        Switch(
                            checked = hasPaid,
                            onCheckedChange = { newCheckedState ->
                                onMarkAsPaid(expense.id, debtorUid, newCheckedState)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = primaryAppColor,
                                checkedTrackColor = primaryAppColor.copy(alpha = 0.5f),
                                uncheckedThumbColor = Color.LightGray,
                                uncheckedTrackColor = Color.DarkGray,
                                checkedBorderColor = primaryAppColor.copy(alpha = 0.7f),
                                uncheckedBorderColor = Color.Gray
                            )
                        )
                    }
                    Divider(color = Color.Gray.copy(alpha = 0.2f))
                }
            }
        } else {
            Text(
                "Solo ${pisoMemberNames[expense.paidByUserId] ?: "la persona que pagó"} puede gestionar los pagos de este gasto.",
                color = Color.Yellow,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryAppColor)
        ) {
            Text("Cerrar", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}