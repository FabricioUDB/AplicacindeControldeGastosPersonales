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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

// Estadísticas por categoría
data class CategoryStats(
    val categoria: String,
    val total: Double,
    val cantidad: Int,
    val porcentaje: Double
)

class ExpensesViewModel : ViewModel() {

    // Firebase
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Estado expuesto a la UI
    private val _user = MutableStateFlow(auth.currentUser)
    val user: StateFlow<com.google.firebase.auth.FirebaseUser?> = _user.asStateFlow()

    private val _ui = MutableStateFlow<UiState>(UiState.Idle)
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _gastos = MutableStateFlow<List<Gasto>>(emptyList())
    val gastos: StateFlow<List<Gasto>> = _gastos.asStateFlow()

    private val _gastosFiltrados = MutableStateFlow<List<Gasto>>(emptyList())
    val gastosFiltrados: StateFlow<List<Gasto>> = _gastosFiltrados.asStateFlow()

    private val _totalMes = MutableStateFlow(0.0)
    val totalMes: StateFlow<Double> = _totalMes.asStateFlow()

    private val _categorias = MutableStateFlow<List<String>>(emptyList())
    val categorias: StateFlow<List<String>> = _categorias.asStateFlow()

    private val _categoryStats = MutableStateFlow<List<CategoryStats>>(emptyList())
    val categoryStats: StateFlow<List<CategoryStats>> = _categoryStats.asStateFlow()

    private val _filtroCategoria = MutableStateFlow<String?>(null)
    val filtroCategoria: StateFlow<String?> = _filtroCategoria.asStateFlow()

    // Categorías predefinidas
    val categoriasComunes = listOf(
        "Alimentación",
        "Transporte",
        "Entretenimiento",
        "Salud",
        "Educación",
        "Servicios",
        "Hogar",
        "Ropa",
        "Tecnología",
        "Otros"
    )

    // Formateador de fecha reutilizable
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    init {
        // Optimización: Habilitar persistencia offline de Firestore
        try {
            db.firestoreSettings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build()
        } catch (e: Exception) {
            // Ya está configurado
        }
    }

    // ---------- AUTH ----------
    fun registerWithEmail(email: String, password: String) = viewModelScope.launch {
        if (email.isBlank() || password.length < 6) {
            _ui.value = UiState.Error("Email inválido o contraseña muy corta (mín. 6 caracteres)")
            return@launch
        }
        _ui.value = UiState.Loading
        runCatching {
            auth.createUserWithEmailAndPassword(email.trim(), password).await()
        }.onSuccess {
            _user.value = auth.currentUser
            _ui.value = UiState.Info("Cuenta creada correctamente")
        }.onFailure {
            _ui.value = UiState.Error(it.message ?: "Error al registrarse")
        }
    }

    fun loginWithEmail(email: String, password: String) = viewModelScope.launch {
        if (email.isBlank() || password.isBlank()) {
            _ui.value = UiState.Error("Completa todos los campos")
            return@launch
        }
        _ui.value = UiState.Loading
        runCatching {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
        }.onSuccess {
            _user.value = auth.currentUser
            _ui.value = UiState.Info("Inicio de sesión exitoso")
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
                _ui.value = UiState.Info("Inicio de sesión con Google exitoso")
            }
            .onFailure {
                _ui.value = UiState.Error("Google Sign-In falló: ${it.message}")
            }
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
        _gastos.value = emptyList()
        _gastosFiltrados.value = emptyList()
        _totalMes.value = 0.0
        _categorias.value = emptyList()
        _categoryStats.value = emptyList()
        _filtroCategoria.value = null
        _ui.value = UiState.Idle
    }

    private fun gastosRef(uid: String) =
        db.collection("users").document(uid).collection("gastos")

    // ---------- FIRESTORE ----------
    fun agregarGasto(nombre: String, categoria: String, montoStr: String, nota: String) =
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val monto = montoStr.toDoubleOrNull()

            if (nombre.isBlank()) {
                _ui.value = UiState.Error("El nombre del gasto no puede estar vacío")
                return@launch
            }
            if (categoria.isBlank()) {
                _ui.value = UiState.Error("Selecciona una categoría")
                return@launch
            }
            if (monto == null || monto <= 0) {
                _ui.value = UiState.Error("Ingresa un monto válido mayor a 0")
                return@launch
            }

            _ui.value = UiState.Loading

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
                    // Agregar localmente para actualización instantánea
                    val nuevoGasto = Gasto(
                        id = it.id,
                        nombre = nombre.trim(),
                        categoria = categoria.trim(),
                        monto = monto,
                        fecha = Timestamp.now(),
                        nota = nota.trim(),
                        createdAt = Timestamp.now()
                    )

                    // Actualizar lista local inmediatamente
                    val cal = Calendar.getInstance()
                    if (esDelMesActual(nuevoGasto.fecha, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)) {
                        val nuevaLista = (_gastos.value + nuevoGasto).sortedByDescending { g -> g.fecha.seconds }
                        _gastos.value = nuevaLista
                        _totalMes.value = nuevaLista.sumOf { g -> g.monto }
                        calcularEstadisticasPorCategoria(nuevaLista)
                        aplicarFiltroCategoria()
                    }

                    _ui.value = UiState.Info("Gasto agregado correctamente")
                }
                .onFailure {
                    _ui.value = UiState.Error("Error al guardar: ${it.message}")
                }
        }

    fun editarGasto(
        gastoId: String,
        nombre: String,
        categoria: String,
        montoStr: String,
        nota: String
    ) = viewModelScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch
        val monto = montoStr.toDoubleOrNull()

        if (nombre.isBlank()) {
            _ui.value = UiState.Error("El nombre del gasto no puede estar vacío")
            return@launch
        }
        if (categoria.isBlank()) {
            _ui.value = UiState.Error("Selecciona una categoría")
            return@launch
        }
        if (monto == null || monto <= 0) {
            _ui.value = UiState.Error("Ingresa un monto válido mayor a 0")
            return@launch
        }

        _ui.value = UiState.Loading

        // Obtener el gasto original para preservar fecha y createdAt
        val gastoOriginal = _gastos.value.find { it.id == gastoId }

        val data = mapOf(
            "nombre" to nombre.trim(),
            "categoria" to categoria.trim(),
            "monto" to monto,
            "nota" to nota.trim(),
            // Preservar la fecha original
            "fecha" to (gastoOriginal?.fecha ?: Timestamp.now()),
            "createdAt" to (gastoOriginal?.createdAt ?: Timestamp.now())
        )

        runCatching {
            gastosRef(uid).document(gastoId).update(data).await()
        }.onSuccess {
            // Actualizar localmente
            val nuevaLista = _gastos.value.map { gasto ->
                if (gasto.id == gastoId) {
                    gasto.copy(
                        nombre = nombre.trim(),
                        categoria = categoria.trim(),
                        monto = monto,
                        nota = nota.trim()
                    )
                } else {
                    gasto
                }
            }

            _gastos.value = nuevaLista
            _totalMes.value = nuevaLista.sumOf { it.monto }

            // Recalcular categorías y estadísticas
            _categorias.value = nuevaLista.map { it.categoria }.distinct().sorted()
            calcularEstadisticasPorCategoria(nuevaLista)
            aplicarFiltroCategoria()

            _ui.value = UiState.Info("Gasto actualizado correctamente")
        }.onFailure { e ->
            _ui.value = UiState.Error("Error al actualizar: ${e.message}")
        }
    }

    fun eliminarGasto(gastoId: String) = viewModelScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch
        _ui.value = UiState.Loading

        runCatching { gastosRef(uid).document(gastoId).delete().await() }
            .onSuccess {
                // Eliminar localmente para actualización instantánea
                val nuevaLista = _gastos.value.filter { it.id != gastoId }
                _gastos.value = nuevaLista
                _totalMes.value = nuevaLista.sumOf { it.monto }
                calcularEstadisticasPorCategoria(nuevaLista)
                aplicarFiltroCategoria()

                _ui.value = UiState.Info("Gasto eliminado")
            }
            .onFailure {
                _ui.value = UiState.Error("No se pudo eliminar: ${it.message}")
            }
    }

    fun loadGastosDelMes(year: Int, month1to12: Int) = viewModelScope.launch {
        val uid = auth.currentUser?.uid ?: return@launch
        _ui.value = UiState.Loading

        val (start, end) = obtenerRangoFechas(year, month1to12)

        runCatching {
            val snap = gastosRef(uid)
                .whereGreaterThanOrEqualTo("fecha", Timestamp(start))
                .whereLessThanOrEqualTo("fecha", Timestamp(end))
                .orderBy("fecha", Query.Direction.DESCENDING)
                .get(com.google.firebase.firestore.Source.CACHE) // Intentar primero del cache
                .await()

            // Si el cache está vacío, obtener del servidor
            if (snap.isEmpty) {
                gastosRef(uid)
                    .whereGreaterThanOrEqualTo("fecha", Timestamp(start))
                    .whereLessThanOrEqualTo("fecha", Timestamp(end))
                    .orderBy("fecha", Query.Direction.DESCENDING)
                    .get(com.google.firebase.firestore.Source.SERVER)
                    .await()
                    .documents
                    .mapNotNull { d -> d.toObject(Gasto::class.java)?.copy(id = d.id) }
            } else {
                snap.documents.mapNotNull { d -> d.toObject(Gasto::class.java)?.copy(id = d.id) }
            }
        }.onSuccess { list ->
            _gastos.value = list
            _totalMes.value = list.sumOf { it.monto }

            // Extraer categorías únicas
            _categorias.value = list.map { it.categoria }.distinct().sorted()

            // Calcular estadísticas
            calcularEstadisticasPorCategoria(list)

            // Aplicar filtro
            aplicarFiltroCategoria()

            _ui.value = UiState.Idle
        }.onFailure {
            _ui.value = UiState.Error("No se pudo cargar: ${it.message}")
        }
    }

    // Funciones auxiliares optimizadas
    private fun obtenerRangoFechas(year: Int, month1to12: Int): Pair<java.util.Date, java.util.Date> {
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

        return Pair(start, end)
    }

    private fun esDelMesActual(fecha: Timestamp, year: Int, month1to12: Int): Boolean {
        val cal = Calendar.getInstance().apply { time = fecha.toDate() }
        return cal.get(Calendar.YEAR) == year && (cal.get(Calendar.MONTH) + 1) == month1to12
    }

    private fun calcularEstadisticasPorCategoria(gastos: List<Gasto>) {
        val total = gastos.sumOf { it.monto }
        if (total <= 0) {
            _categoryStats.value = emptyList()
            return
        }

        val stats = gastos
            .groupBy { it.categoria }
            .map { (categoria, gastosCategoria) ->
                val totalCategoria = gastosCategoria.sumOf { it.monto }
                CategoryStats(
                    categoria = categoria,
                    total = totalCategoria,
                    cantidad = gastosCategoria.size,
                    porcentaje = (totalCategoria / total) * 100
                )
            }
            .sortedByDescending { it.total }

        _categoryStats.value = stats
    }

    fun setFiltroCategoria(categoria: String?) {
        _filtroCategoria.value = categoria
        aplicarFiltroCategoria()
    }

    private fun aplicarFiltroCategoria() {
        _gastosFiltrados.value = if (_filtroCategoria.value == null) {
            _gastos.value
        } else {
            _gastos.value.filter { it.categoria == _filtroCategoria.value }
        }
    }

    fun clearUiState() {
        _ui.value = UiState.Idle
    }

    fun formatearFecha(timestamp: Timestamp): String {
        return dateFormatter.format(timestamp.toDate())
    }
}