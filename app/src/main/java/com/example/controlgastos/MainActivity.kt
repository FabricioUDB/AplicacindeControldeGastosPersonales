package com.example.controlgastos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.controlgastos.ui.theme.ControlGastosTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val vm: ExpensesViewModel by viewModels()

    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) vm.signInWithGoogleIdToken(idToken)
            } catch (e: Exception) {
                // Error manejado por el ViewModel
            }
        }

    private fun startGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        googleLauncher.launch(client.signInIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ControlGastosTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val user by vm.user.collectAsState()
                    if (user == null) {
                        AuthScreen(
                            onLoginEmail = { e, p -> vm.loginWithEmail(e, p) },
                            onRegisterEmail = { e, p -> vm.registerWithEmail(e, p) },
                            onLoginGoogle = { startGoogleSignIn() },
                            vm = vm
                        )
                    } else {
                        HomeScreen(vm = vm, onLogout = { vm.signOut() })
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(
    onLoginEmail: (String, String) -> Unit,
    onRegisterEmail: (String, String) -> Unit,
    onLoginGoogle: () -> Unit,
    vm: ExpensesViewModel
) {
    var showRegisterScreen by remember { mutableStateOf(false) }
    val user by vm.user.collectAsState()

    // Si el usuario está autenticado, no mostramos ninguna pantalla de auth
    if (user != null) {
        return
    }

    if (showRegisterScreen) {
        RegisterScreen(
            onRegister = onRegisterEmail,
            onBackToLogin = { showRegisterScreen = false },
            vm = vm
        )
    } else {
        LoginScreen(
            onLogin = onLoginEmail,
            onNavigateToRegister = { showRegisterScreen = true },
            onLoginGoogle = onLoginGoogle,
            vm = vm
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(
    onLogin: (String, String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onLoginGoogle: () -> Unit,
    vm: ExpensesViewModel
) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val ui by vm.ui.collectAsState()

    // Limpiar errores cuando se entra a la pantalla
    LaunchedEffect(Unit) {
        vm.clearErrorState()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Control de Gastos",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Inicia sesión en tu cuenta",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("Contraseña") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        when (ui) {
            is UiState.Error -> {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        (ui as UiState.Error).message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is UiState.Info -> {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        (ui as UiState.Info).message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            else -> {}
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { onLogin(email, pass) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = ui != UiState.Loading
        ) {
            if (ui == UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("Iniciar sesión")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onNavigateToRegister,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = ui != UiState.Loading
        ) {
            Text("Crear cuenta nueva")
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                "  o  ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onLoginGoogle,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = ui != UiState.Loading
        ) {
            Icon(Icons.Default.AccountCircle, null)
            Spacer(Modifier.width(8.dp))
            Text("Continuar con Google")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterScreen(
    onRegister: (String, String) -> Unit,
    onBackToLogin: () -> Unit,
    vm: ExpensesViewModel
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val ui by vm.ui.collectAsState()
    val user by vm.user.collectAsState()

    // Limpiar errores cuando se entra a la pantalla
    LaunchedEffect(Unit) {
        vm.clearErrorState()
    }

    // Redirigir automáticamente cuando el registro sea exitoso
    LaunchedEffect(user) {
        if (user != null) {
            // El registro fue exitoso, no necesitamos hacer nada más
            // porque el AuthScreen detectará que user != null
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Header con botón de regreso
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToLogin) {
                Icon(Icons.Default.ArrowBack, "Volver al login")
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Crear Cuenta",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(60.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Regístrate para comenzar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Mínimo 6 caracteres") }
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirmar Contraseña") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Validación de contraseñas
        if (password.isNotEmpty() && confirmPassword.isNotEmpty() && password != confirmPassword) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Las contraseñas no coinciden",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        when (ui) {
            is UiState.Error -> {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        (ui as UiState.Error).message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            is UiState.Info -> {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        (ui as UiState.Info).message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            else -> {}
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (password == confirmPassword) {
                    onRegister(email, password)
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = ui != UiState.Loading &&
                    email.isNotBlank() &&
                    password.length >= 6 &&
                    password == confirmPassword
        ) {
            if (ui == UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("Crear cuenta")
        }

        Spacer(Modifier.height(12.dp))

        TextButton(onClick = onBackToLogin) {
            Text("¿Ya tienes cuenta? Inicia sesión")
        }
    }
}

// El resto del código se mantiene igual...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(vm: ExpensesViewModel, onLogout: () -> Unit) {
    val gastos by vm.gastos.collectAsState()
    val gastosFiltrados by vm.gastosFiltrados.collectAsState()
    val total by vm.totalMes.collectAsState()
    val ui by vm.ui.collectAsState()
    val categorias by vm.categorias.collectAsState()
    val categoryStats by vm.categoryStats.collectAsState()
    val filtroCategoria by vm.filtroCategoria.collectAsState()

    var mostrarFormulario by remember { mutableStateOf(false) }
    var mostrarEstadisticas by remember { mutableStateOf(false) }
    var gastoAEditar by remember { mutableStateOf<Gasto?>(null) }

    val cal = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(cal.get(Calendar.MONTH) + 1) }

    LaunchedEffect(Unit) {
        vm.loadGastosDelMes(year, month)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control de Gastos") },
                actions = {
                    IconButton(onClick = { mostrarEstadisticas = !mostrarEstadisticas }) {
                        Icon(Icons.Default.Info, "Estadísticas")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.Close, "Cerrar sesión")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { mostrarFormulario = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Agregar Gasto") }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Selector de mes
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (month == 1) {
                            month = 12
                            year -= 1
                        } else {
                            month -= 1
                        }
                        vm.loadGastosDelMes(year, month)
                    }) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Mes anterior")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            getNombreMes(month),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            year.toString(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    IconButton(onClick = {
                        if (month == 12) {
                            month = 1
                            year += 1
                        } else {
                            month += 1
                        }
                        vm.loadGastosDelMes(year, month)
                    }) {
                        Icon(Icons.Default.KeyboardArrowRight, "Mes siguiente")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Total del mes
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Total del Mes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "$${String.format("%.2f", total)}",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "${gastos.size} gastos registrados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Filtro por categoría
            if (categorias.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filtrar:", style = MaterialTheme.typography.bodyMedium)

                    FilterChip(
                        selected = filtroCategoria == null,
                        onClick = { vm.setFiltroCategoria(null) },
                        label = { Text("Todas") }
                    )

                    categorias.take(3).forEach { cat ->
                        FilterChip(
                            selected = filtroCategoria == cat,
                            onClick = {
                                vm.setFiltroCategoria(if (filtroCategoria == cat) null else cat)
                            },
                            label = { Text(cat) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Estadísticas por categoría
            if (mostrarEstadisticas && categoryStats.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Gastos por Categoría",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))

                        categoryStats.forEach { stat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stat.categoria,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${stat.cantidad} gasto${if (stat.cantidad != 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "$${String.format("%.2f", stat.total)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${String.format("%.1f", stat.porcentaje)}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { (stat.porcentaje / 100).toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                            )

                            if (stat != categoryStats.last()) {
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Lista de gastos
            when {
                ui is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                gastosFiltrados.isEmpty() -> {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (filtroCategoria != null)
                                    "No hay gastos en esta categoría"
                                else
                                    "No hay gastos registrados",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(gastosFiltrados, key = { it.id }) { gasto ->
                            GastoCard(
                                gasto = gasto,
                                onDelete = { vm.eliminarGasto(gasto.id) },
                                formatearFecha = { vm.formatearFecha(it) }
                            )
                        }
                    }
                }
            }

            // Mensajes de estado
            when (ui) {
                is UiState.Error -> {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            (ui as UiState.Error).message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                is UiState.Info -> {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            (ui as UiState.Info).message,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(2000)
                        vm.clearUiState()
                    }
                }
                else -> {}
            }
        }
    }

    // Diálogo para agregar gasto
    if (mostrarFormulario) {
        AgregarGastoDialog(
            vm = vm,
            onDismiss = { mostrarFormulario = false }
        )
    }
}

@Composable
fun GastoCard(
    gasto: Gasto,
    onDelete: () -> Unit,
    formatearFecha: (com.google.firebase.Timestamp) -> String
) {
    var mostrarConfirmacion by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    gasto.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            gasto.categoria,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        formatearFecha(gasto.fecha),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (gasto.nota.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        gasto.nota,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$${String.format("%.2f", gasto.monto)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = { mostrarConfirmacion = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (mostrarConfirmacion) {
        AlertDialog(
            onDismissRequest = { mostrarConfirmacion = false },
            title = { Text("Confirmar eliminación") },
            text = { Text("¿Estás seguro de que deseas eliminar este gasto?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        mostrarConfirmacion = false
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarConfirmacion = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgregarGastoDialog(
    vm: ExpensesViewModel,
    onDismiss: () -> Unit
) {
    var nombre by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }
    var mostrarMenuCategorias by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss
    ) {
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "Agregar Nuevo Gasto",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre del gasto") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                // Selector de categoría
                ExposedDropdownMenuBox(
                    expanded = mostrarMenuCategorias,
                    onExpandedChange = { mostrarMenuCategorias = it }
                ) {
                    OutlinedTextField(
                        value = categoria,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(mostrarMenuCategorias) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )

                    ExposedDropdownMenu(
                        expanded = mostrarMenuCategorias,
                        onDismissRequest = { mostrarMenuCategorias = false }
                    ) {
                        vm.categoriasComunes.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    categoria = cat
                                    mostrarMenuCategorias = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it },
                    label = { Text("Monto") },
                    leadingIcon = { Text("$") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = nota,
                    onValueChange = { nota = it },
                    label = { Text("Nota (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            vm.agregarGasto(nombre, categoria, monto, nota)
                            onDismiss()
                        }
                    ) {
                        Text("Agregar")
                    }
                }
            }
        }
    }
}

private fun getNombreMes(mes: Int): String {
    return when (mes) {
        1 -> "Enero"
        2 -> "Febrero"
        3 -> "Marzo"
        4 -> "Abril"
        5 -> "Mayo"
        6 -> "Junio"
        7 -> "Julio"
        8 -> "Agosto"
        9 -> "Septiembre"
        10 -> "Octubre"
        11 -> "Noviembre"
        12 -> "Diciembre"
        else -> ""
    }
}