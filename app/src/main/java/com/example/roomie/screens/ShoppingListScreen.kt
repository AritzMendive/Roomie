package com.example.roomie.screens

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
// import androidx.compose.material.icons.filled.Add // No usado directamente si Send se usa para botón
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send // Reutilizado para añadir ítem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

// Data class para un ítem de la lista de la compra
data class ShoppingListItem(
    val id: String = "",
    val itemName: String = "",
    var isPurchased: Boolean = false, // Este campo se actualiza desde Firestore
    val addedByUid: String = "",
    val addedByName: String = "", // Nombre para mostrar
    val addedAt: Timestamp? = null,
    val pisoId: String = ""
    // Puedes añadir más campos como quantity, notes, category, purchasedByUid, purchasedAt
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun ShoppingListScreen(
    pisoId: String,
    navController: NavController, // Podría no ser necesaria si es una pestaña interna de HomeScreen
    auth: FirebaseAuth
) {
    val db = Firebase.firestore
    val currentUser = auth.currentUser
    val currentUid = currentUser?.uid ?: ""
    val currentUsername = currentUser?.displayName ?: "Usuario Desconocido"

    // Estado que contiene la lista de ítems obtenida del listener de Firestore
    var shoppingItems by remember { mutableStateOf<List<ShoppingListItem>>(emptyList()) }
    var newItemName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Colores
    val primaryAppColor = Color(0xFFF0B90B)
    val topAppBarColor = Color(0xFF1A1A1A)
    val backgroundColor = Color(0xFF222222)
    // val cardBackgroundColor = Color(0xFF333333) // No usado directamente aquí
    val textColorPrimary = Color.White
    val textColorSecondary = Color.LightGray
    val textFieldBackgroundColor = Color(0xFF333333)

    // Cargar ítems de la lista de la compra y escuchar cambios
    LaunchedEffect(pisoId) {
        if (pisoId.isBlank()) {
            errorMessage = "ID de piso inválido."
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null

        val shoppingItemsCollection = db.collection("pisos").document(pisoId)
            .collection("shoppingItems")
            .orderBy("isPurchased") // Mostrar no comprados primero
            .orderBy("addedAt", Query.Direction.DESCENDING) // Luego los más recientes

        // El listener se encargará de actualizar `shoppingItems` cada vez que haya un cambio en Firestore
        val listenerRegistration = shoppingItemsCollection.addSnapshotListener { snapshots, e ->
            if (e != null) {
                Log.w("ShoppingListScreen", "Error al escuchar ítems de compra.", e)
                // Mostrar el mensaje de error específico de Firestore si está disponible
                errorMessage = "Error al cargar la lista: ${e.localizedMessage ?: e.message}"
                isLoading = false
                return@addSnapshotListener
            }

            if (snapshots != null) {
                // Mapea los documentos a la data class y actualiza el estado
                shoppingItems = snapshots.documents.mapNotNull { doc ->
                    try {
                        doc.toObject<ShoppingListItem>()?.copy(id = doc.id)
                    } catch(ex: Exception) {
                        Log.e("ShoppingListScreen", "Error parseando item ${doc.id}", ex)
                        null // Ignorar documentos mal formados
                    }
                }
                Log.d("ShoppingListScreen", "${shoppingItems.size} ítems cargados/actualizados.")
                // Limpiar error si la carga fue exitosa
                errorMessage = null
            } else {
                shoppingItems = emptyList()
                Log.d("ShoppingListScreen", "Snapshot nulo recibido.")
            }
            isLoading = false
        }
        // No es necesario un cleanup explícito aquí, LaunchedEffect lo maneja.
    }

    // Función para añadir un nuevo ítem
    val addItemToList = {
        if (newItemName.isNotBlank() && currentUid.isNotBlank()) {
            val trimmedItemName = newItemName.trim()
            keyboardController?.hide()

            val shoppingItemData = hashMapOf(
                "itemName" to trimmedItemName,
                "isPurchased" to false,
                "addedByUid" to currentUid,
                "addedByName" to currentUsername,
                "addedAt" to FieldValue.serverTimestamp(),
                "pisoId" to pisoId
            )

            db.collection("pisos").document(pisoId).collection("shoppingItems")
                .add(shoppingItemData)
                .addOnSuccessListener {
                    Log.d("ShoppingListScreen", "Ítem '$trimmedItemName' añadido.")
                    newItemName = "" // Limpiar campo después de éxito
                }
                .addOnFailureListener { e ->
                    Log.e("ShoppingListScreen", "Error al añadir ítem '$trimmedItemName'", e)
                    errorMessage = "Error al añadir: ${e.message}"
                }
        }
    }

    // Función para actualizar el estado de compra de un ítem en Firestore
    val toggleItemPurchased = { item: ShoppingListItem ->
        // Solo actualiza Firestore. La UI se actualizará cuando el listener reciba el cambio.
        db.collection("pisos").document(pisoId)
            .collection("shoppingItems").document(item.id)
            .update("isPurchased", !item.isPurchased) // Actualiza al valor contrario
            .addOnSuccessListener {
                Log.d("ShoppingListScreen", "Solicitud de actualización enviada para '${item.itemName}' a ${!item.isPurchased}.")
                // NO actualices el estado local aquí si confías en el listener
            }
            .addOnFailureListener { e ->
                Log.e("ShoppingListScreen", "Error al actualizar estado de '${item.itemName}'", e)
                errorMessage = "Error al actualizar: ${e.message}"
                // Aquí podrías considerar mostrar un mensaje más específico al usuario
            }
    }

    // Función para eliminar un ítem de Firestore
    val deleteItem = { itemId: String ->
        db.collection("pisos").document(pisoId)
            .collection("shoppingItems").document(itemId)
            .delete()
            .addOnSuccessListener {
                Log.d("ShoppingListScreen", "Ítem $itemId eliminado.")
                // La UI se actualizará por el listener
            }
            .addOnFailureListener { e ->
                Log.e("ShoppingListScreen", "Error al eliminar ítem $itemId", e)
                errorMessage = "Error al eliminar: ${e.message}"
            }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Lista de la Compra", color = primaryAppColor, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = topAppBarColor)
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // Aplicar padding del Scaffold
        ) {
            // Input para añadir nuevos ítems
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(topAppBarColor.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Añadir a la lista...", color = Color.Gray) },
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColorPrimary,
                        unfocusedTextColor = textColorPrimary,
                        cursorColor = primaryAppColor,
                        focusedBorderColor = primaryAppColor,
                        unfocusedBorderColor = Color.Gray,
                        focusedContainerColor = textFieldBackgroundColor,
                        unfocusedContainerColor = textFieldBackgroundColor
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { addItemToList() }),
                    singleLine = true,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = addItemToList,
                    enabled = newItemName.isNotBlank(),
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (newItemName.isNotBlank()) primaryAppColor else Color.Gray,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Añadir ítem",
                        tint = Color.Black
                    )
                }
            }

            // Mostrar mensajes de error de forma prominente si existen
            if (errorMessage != null) {
                Surface( // Usar Surface para darle un fondo y destacarlo
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.Red.copy(alpha = 0.1f), // Fondo rojo claro
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }

            // Lista de ítems
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = primaryAppColor)
                }
            } else if (shoppingItems.isEmpty() && errorMessage == null) { // Mostrar solo si no hay error
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "La lista de la compra está vacía.\n¡Añade algo que necesitéis!",
                        color = textColorSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            } else {
                // Mostrar la lista solo si no está cargando y hay ítems (o si hay error, para que se vea el error arriba)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f) // Ocupa el espacio restante
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(shoppingItems, key = { it.id }) { item ->
                        ShoppingListItemRow(
                            item = item,
                            // Pasar la lambda directamente
                            onTogglePurchased = { toggleItemPurchased(item) },
                            onDeleteItem = { deleteItem(item.id) },
                            currentUserId = currentUid
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShoppingListItemRow(
    item: ShoppingListItem,
    onTogglePurchased: () -> Unit,
    onDeleteItem: () -> Unit,
    currentUserId: String
) {
    val textColor = if (item.isPurchased) Color.Gray else Color.White
    val textDecoration = if (item.isPurchased) TextDecoration.LineThrough else TextDecoration.None

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isPurchased) Color(0xFF2E2E2E) else Color(0xFF3A3A3A)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isPurchased, // El estado viene del item actual en la lista
                onCheckedChange = { _ -> // El nuevo estado no se usa directamente, solo se dispara la acción
                    onTogglePurchased()
                },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFFF0B90B),
                    uncheckedColor = Color.Gray,
                    checkmarkColor = Color.Black
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.itemName,
                    color = textColor,
                    textDecoration = textDecoration,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    // Mostrar nombre de quién añadió
                    text = "Añadido por: ${item.addedByName.ifBlank { "Desconocido" }}",
                    color = Color.DarkGray,
                    fontSize = 12.sp
                )
            }
            // Permitir eliminar solo al que añadió el ítem
            // O podrías permitir eliminar a cualquiera si no está comprado, etc.
            if (item.addedByUid == currentUserId) {
                IconButton(onClick = onDeleteItem) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Eliminar ítem",
                        tint = Color.Gray // Hacerlo siempre gris o cambiar según estado
                    )
                }
            } else {
                // Añadir un Spacer para mantener la alineación si el botón no está visible
                Spacer(modifier = Modifier.size(48.dp)) // Tamaño del IconButton
            }
        }
    }
}
