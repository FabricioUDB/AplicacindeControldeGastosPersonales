package com.example.controlgastos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

// --- Modelo ---
data class Gasto(
    val id: String = "",
    val nombre: String = "",
    val categoria: String = "",
    val monto: Double = 0.0,
    val fecha: Timestamp = Timestamp.now(),
    val nota: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

// --- Estados de UI ---
sealed class UiState {
    data object Idle : UiState()
    data object Loading : UiState()
    data class Error(val message: String) : UiState()
    data class Info(val message: String) : UiState()
}

class ExpensesViewModel : ViewModel() {

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Estado expuesto a la UI
    private val _user = MutableStateFlow(auth.currentUser)
    val user: StateFlow<com.google.firebase.auth.FirebaseUser?> = _user

    private val _ui = MutableStateFlow<UiState>(UiState.Idle)
    val ui: StateFlow<UiState> = _ui

    private val _gastos = MutableStateFlow<List<Gasto>>(emptyList())
    val gastos: StateFlow<List<Gasto>> = _gastos

    private val _totalMes = MutableStateFlow(0.0)
    val totalMes: StateFlow<Double> = _totalMes

    // ---------- AUTH ----------
    fun registerWithEmail(email: String, password: String) = viewModelScope.launch {
        _ui.value = UiState.Loading
        runCatching {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
        }.onSuccess {
            _user.value = auth.currentUser
            _ui.value = UiState.Info("Cuenta creada e inicio de sesión correcto")
        }.onFailure {
            _ui.value = UiState.Error(it.message ?: "Error al registrarse")
        }
    }

    fun loginWithEmail(email: String, password: String) = viewModelScope.launch {
        _ui.value = UiState.Loading
        runCatching {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        }.onSuccess {
            _user.value = auth.currentUser
            _ui.value = UiState.Idle
        }.onFailure {
            _ui.value = UiState.Error(it.message ?: "Credenciales inválidas")
        }
    }

    fun signInWithGoogleIdToken(idToken: String) = viewModelScope.launch {
        _ui.value = UiState.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        runCatching { auth.signInWithCredential(credential).await() }
            .onSuccess {
                _user.value = auth.currentUser
                _ui.value = UiState.Idle
            }
            .onFailure {
                _ui.value = UiState.Error("Google Sign-In falló: ${it.message}")
            }
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
        _gastos.value = emptyList()
        _totalMes.value = 0.0
    }

    private fun gastosRef(uid: String) =
        db.collection("users").document(uid).collection("gastos")

    // ---------- FIRESTORE ----------
    fun agregarGasto(nombre: String, categoria: String, montoStr: String, nota: String) =
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val monto = montoStr.toDoubleOrNull()
            if (nombre.isBlank() || categoria.isBlank() || monto == null) {
                _ui.value = UiState.Error("Completa nombre, categoría y monto válido")
                return@launch
            }
            _ui.value = UiState.Loading

            // 👇 Enviar exactamente los campos que permiten tus reglas (sin "id")
            val data = mapOf(
                "nombre" to nombre.trim(),
                "categoria" to categoria.trim(),
                "monto" to monto,
                "fecha" to Timestamp.now(),
                "nota" to nota.trim(),
                "createdAt" to Timestamp.now()
            )

            runCatching { gastosRef(uid).add(data).await() }
                .onSuccess {
                    _ui.value = UiState.Info("Gasto agregado")
                    val cal = Calendar.getInstance()
                    loadGastosDelMes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
                }
                .onFailure { _ui.value = UiState.Error("Error al guardar: ${it.message}") }
        }


    fun eliminarGasto(gastoId: String) = viewModelScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch
        runCatching { gastosRef(uid).document(gastoId).delete().await() }
            .onSuccess {
                _ui.value = UiState.Info("Gasto eliminado")
                val cal = Calendar.getInstance()
                loadGastosDelMes(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
            }
            .onFailure { _ui.value = UiState.Error("No se pudo eliminar: ${it.message}") }
    }

    fun loadGastosDelMes(year: Int, month1to12: Int) = viewModelScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch
        _ui.value = UiState.Loading

        val start = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1to12 - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
        val end = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1to12 - 1)
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.time

        runCatching {
            val snap = gastosRef(uid)
                .whereGreaterThanOrEqualTo("fecha", Timestamp(start))
                .whereLessThanOrEqualTo("fecha", Timestamp(end))
                .orderBy("fecha", Query.Direction.DESCENDING)
                .get().await()
            snap.documents.map { d -> d.toObject(Gasto::class.java)!!.copy(id = d.id) }
        }.onSuccess { list ->
            _gastos.value = list
            _totalMes.value = list.sumOf { it.monto }
            _ui.value = UiState.Idle
        }.onFailure {
            _ui.value = UiState.Error("No se pudo cargar: ${it.message}")
        }
    }
}
