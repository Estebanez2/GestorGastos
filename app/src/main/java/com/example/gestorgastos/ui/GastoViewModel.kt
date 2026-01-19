package com.example.gestorgastos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.example.gestorgastos.R
import com.example.gestorgastos.data.AppDatabase
import com.example.gestorgastos.data.Categoria
import com.example.gestorgastos.data.FiltroBusqueda
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.data.Preferencias
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

class GastoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.gastoDao()
    private val prefs = Preferencias(application)

    // --- ESTADO (Flows) ---
    // Usamos StateFlow para ambos para poder combinarlos fácilmente
    private val _mesSeleccionado = MutableStateFlow(YearMonth.now())
    private val _filtroActual = MutableStateFlow<FiltroBusqueda?>(null)

    // Exponemos el mes actual para la UI (Título del mes)
    val mesActual: LiveData<YearMonth> = _mesSeleccionado.asLiveData()

    // Notificación para cambio de límites (Semáforo)
    val notificarCambioLimites = MutableLiveData<Boolean>()

    // Límites
    var limiteAmarillo: Double = prefs.obtenerAmarillo()
    var limiteRojo: Double = prefs.obtenerRojo()

    val gastosVisibles: LiveData<List<Gasto>> = combine(_mesSeleccionado, _filtroActual) { mes, filtro ->
        Pair(mes, filtro)
    }.flatMapLatest { (mes, filtro) ->
        if (filtro != null) {
            // LÓGICA DE FECHAS INTELIGENTE
            val (inicio, fin) = when {
                // 1. Prioridad: Si el usuario puso fechas manuales en el filtro, usamos esas
                filtro.fechaInicio != null && filtro.fechaFin != null -> {
                    Pair(filtro.fechaInicio, filtro.fechaFin)
                }
                // 2. Si marcó "Buscar en todo", buscamos desde el principio de los tiempos
                filtro.buscarEnTodo -> {
                    Pair(0L, Long.MAX_VALUE)
                }
                // 3. Por defecto (sin fechas y sin check): Buscamos DENTRO del mes seleccionado
                else -> {
                    obtenerRangoFechas(mes)
                }
            }

            dao.buscarGastosAvanzado(filtro.nombre, filtro.descripcion, filtro.categoria, filtro.precioMin, filtro.precioMax, inicio, fin)
        } else {
            // SI NO HAY FILTRO: Buscamos por el mes seleccionado normal
            val (inicio, fin) = obtenerRangoFechas(mes)
            dao.obtenerGastosPorMes(inicio, fin)
        }
    }.asLiveData()

    val filtroActualValue: FiltroBusqueda? get() = _filtroActual.value

    // El total se calcula automáticamente derivado de gastosVisibles
    val sumaTotalDelMes: LiveData<Double> = gastosVisibles.map { lista ->
        lista.sumOf { it.cantidad }
    }

    // --- CATEGORÍAS ---
    val listaCategorias: LiveData<List<Categoria>> = dao.obtenerCategorias().asLiveData()

    // --- FUNCIONES DE ACCIÓN ---

    fun aplicarFiltro(filtro: FiltroBusqueda) {
        // Al actualizar este valor, el flujo 'gastosVisibles' de arriba se dispara solo
        _filtroActual.value = filtro
    }

    fun limpiarFiltro() {
        // Al poner null, el flujo vuelve automáticamente a mostrar el mes seleccionado
        _filtroActual.value = null
    }

    fun estaBuscando(): Boolean {
        return _filtroActual.value != null
    }

    fun guardarNuevosLimites(nuevoAmarillo: Double, nuevoRojo: Double) {
        limiteAmarillo = nuevoAmarillo
        limiteRojo = nuevoRojo
        prefs.guardarLimites(nuevoAmarillo, nuevoRojo)
        notificarCambioLimites.value = true
    }

    fun agregarGasto(nombre: String, cantidad: Double, descripcion: String, uriFoto: String?, categoria: String, fecha: Long = System.currentTimeMillis()) {
        val nuevoGasto = Gasto(
            nombre = nombre,
            cantidad = cantidad,
            descripcion = descripcion,
            fecha = fecha,
            uriFoto = uriFoto,
            categoria = categoria
        )
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertarGasto(nuevoGasto)
        }
    }

    fun borrarGasto(gasto: Gasto) {
        viewModelScope.launch(Dispatchers.IO) { dao.borrarGasto(gasto) }
    }

    fun actualizarGasto(gasto: Gasto) {
        viewModelScope.launch(Dispatchers.IO) { dao.actualizarGasto(gasto) }
    }

    fun cambiarMes(nuevoMes: YearMonth) {
        _filtroActual.value = null
        _mesSeleccionado.value = nuevoMes
    }


    fun mesAnterior() {
        _mesSeleccionado.value = _mesSeleccionado.value.minusMonths(1)
    }

    fun mesSiguiente() {
        _mesSeleccionado.value = _mesSeleccionado.value.plusMonths(1)
    }

    fun obtenerColorAlerta(gastoTotal: Double): Int {
        return when {
            gastoTotal >= limiteRojo -> R.color.alerta_rojo
            gastoTotal >= limiteAmarillo -> R.color.alerta_amarillo
            else -> R.color.alerta_verde
        }
    }

    private fun obtenerRangoFechas(mes: YearMonth): Pair<Long, Long> {
        val inicio = mes.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val fin = mes.atEndOfMonth().atTime(23, 59, 59, 999999999).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Pair(inicio, fin)
    }

    // --- GESTIÓN DE CATEGORÍAS ---

    fun inicializarCategoriasPorDefecto() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertarCategoria(Categoria("Comida", null))
            dao.insertarCategoria(Categoria("Transporte", null))
            dao.insertarCategoria(Categoria("Casa", null))
            dao.insertarCategoria(Categoria("Otros", null))
        }
    }

    fun agregarNuevaCategoria(nombre: String, uriFoto: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertarCategoria(Categoria(nombre, uriFoto))
        }
    }

    fun editarCategoria(viejoNombre: String, nuevoNombre: String, nuevaUriFoto: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertarCategoria(Categoria(nuevoNombre, nuevaUriFoto))
            if (viejoNombre != nuevoNombre) {
                dao.actualizarCategoriaEnGastos(viejoNombre, nuevoNombre)
                dao.borrarCategoria(Categoria(viejoNombre, null))
            }
        }
    }

    fun borrarCategoria(categoria: Categoria) {
        viewModelScope.launch(Dispatchers.IO) { dao.borrarCategoria(categoria) }
    }

    fun obtenerGastosPorCategoria(lista: List<Gasto>): Map<String, Double> {
        return lista.groupBy { it.categoria }
            .mapValues { entry -> entry.value.sumOf { it.cantidad } }
    }
}