package com.example.controlgastos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.controlgastos.ui.theme.ControlGastosTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private val vm: ExpensesViewModel by viewModels()

    // Launcher para Google Sign-In
    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) vm.signInWithGoogleIdToken(idToken)
            } catch (e: Exception) {
                // El ViewModel mostrará error si falla el sign-in
            }
        }

    private fun startGoogleSignIn() {
        // Requiere que exista R.string.default_web_client_id (lo obtienes al añadir SHA-1)
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
                        LoginScreen(
                            onLoginEmail = { e, p -> vm.loginWithEmail(e, p) },
                            onRegisterEmail = { e, p -> vm.registerWithEmail(e, p) },
                            onLoginGoogle = { startGoogleSignIn() }
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
private fun LoginScreen(
    onLoginEmail: (String, String) -> Unit,
    onRegisterEmail: (String, String) -> Unit,
    onLoginGoogle: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) { 
        Text("Control de Gastos", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onLoginEmail(email, pass) }, modifier = Modifier.fillMaxWidth()) {
            Text("Iniciar sesión")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onRegisterEmail(email, pass) }, modifier = Modifier.fillMaxWidth()) {
            Text("Crear cuenta")
        }
        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(12.dp))
        Button(onClick = onLoginGoogle, modifier = Modifier.fillMaxWidth()) {
            Text("Continuar con Google")
        }
    }
}

@Composable
private fun HomeScreen(vm: ExpensesViewModel, onLogout: () -> Unit) {
    val gastos by vm.gastos.collectAsState()
    val total by vm.totalMes.collectAsState()
    val ui by vm.ui.collectAsState()

    var nombre by remember { mutableStateOf("") }
    var categoria by remember { mutableStateOf("") }
    var monto by remember { mutableStateOf("") }
    var nota by remember { mutableStateOf("") }

    val cal = remember { Calendar.getInstance() }
    var year by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(cal.get(Calendar.MONTH) + 1) }

    LaunchedEffect(Unit) {
        vm.loadGastosDelMes(year, month)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mes: $month/$year", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                if (month == 1) { month = 12; year -= 1 } else month -= 1
                vm.loadGastosDelMes(year, month)
            }) { Text("Anterior") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                if (month == 12) { month = 1; year += 1 } else month += 1
                vm.loadGastosDelMes(year, month)
            }) { Text("Siguiente") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onLogout) { Text("Cerrar sesión") }
        }

        Spacer(Modifier.height(8.dp))
        Text("Total del mes: $total", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        // Formulario rápido
        OutlinedTextField(nombre, { nombre = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(categoria, { categoria = it }, label = { Text("Categoría") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(monto, { monto = it }, label = { Text("Monto") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(nota, { nota = it }, label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            vm.agregarGasto(nombre, categoria, monto, nota)
            // limpiar inputs si quieres
            nombre = ""; categoria = ""; monto = ""; nota = ""
        }) { Text("Agregar gasto") }

        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(gastos) { g ->
                ListItem(
                    headlineContent = { Text("${g.nombre} - $${g.monto}") },
                    supportingContent = { Text("${g.categoria} • ${g.nota}") },
                    trailingContent = {
                        TextButton(onClick = { vm.eliminarGasto(g.id) }) { Text("Eliminar") }
                    }
                )
                Divider()
            }
        }

        when (ui) {
            is UiState.Error -> Text((ui as UiState.Error).message, color = MaterialTheme.colorScheme.error)
            is UiState.Info -> Text((ui as UiState.Info).message, color = MaterialTheme.colorScheme.primary)
            UiState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            UiState.Idle -> {}
        }
    }
}
