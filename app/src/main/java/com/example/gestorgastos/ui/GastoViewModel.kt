package com.example.gestorgastos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
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

    private val _mesSeleccionado = MutableStateFlow(YearMonth.now())
    private val _filtroActual = MutableStateFlow<FiltroBusqueda?>(null)

    // Exponemos el filtro actual para poder editarlo desde el Main
    val filtroActualValue: FiltroBusqueda? get() = _filtroActual.value

    val mesActual: LiveData<YearMonth> = _mesSeleccionado.asLiveData()
    val notificarCambioLimites = MutableLiveData<Boolean>()

    var limiteAmarillo: Double = prefs.obtenerAmarillo()
    var limiteRojo: Double = prefs.obtenerRojo()

    // --- LÓGICA CENTRAL ---
    val gastosVisibles: LiveData<List<Gasto>> = combine(_mesSeleccionado, _filtroActual) { mes, filtro ->
        Pair(mes, filtro)
    }.flatMapLatest { (mes, filtro) ->
        if (filtro != null) {
            // LÓGICA INTELIGENTE DE FECHAS
            val (inicio, fin) = when {
                // A. Usuario eligió fechas manuales
                filtro.fechaInicio != null && filtro.fechaFin != null -> Pair(filtro.fechaInicio, filtro.fechaFin)
                // B. Usuario marcó "Buscar en todo"
                filtro.buscarEnTodo -> Pair(0L, Long.MAX_VALUE)
                // C. Por defecto: buscamos dentro del mes que se ve en pantalla
                else -> obtenerRangoFechas(mes)
            }

            dao.buscarGastosAvanzado(
                filtro.nombre,
                filtro.descripcion, // Nuevo
                filtro.categoria,
                filtro.precioMin,
                filtro.precioMax,
                inicio,
                fin
            )
        } else {
            // SIN FILTRO: Mes normal
            val (inicio, fin) = obtenerRangoFechas(mes)
            dao.obtenerGastosPorMes(inicio, fin)
        }
    }.asLiveData()

    val listaCategorias: LiveData<List<Categoria>> = dao.obtenerCategorias().asLiveData()

    // --- ACCIONES ---
    fun aplicarFiltro(filtro: FiltroBusqueda) { _filtroActual.value = filtro }
    fun limpiarFiltro() { _filtroActual.value = null }
    fun estaBuscando(): Boolean = _filtroActual.value != null

    fun guardarNuevosLimites(nuevoAmarillo: Double, nuevoRojo: Double) {
        limiteAmarillo = nuevoAmarillo
        limiteRojo = nuevoRojo
        prefs.guardarLimites(nuevoAmarillo, nuevoRojo)
        notificarCambioLimites.value = true
    }

    fun agregarGasto(nombre: String, cantidad: Double, descripcion: String, uriFoto: String?, categoria: String, fecha: Long = System.currentTimeMillis()) {
        val nuevoGasto = Gasto(nombre = nombre, cantidad = cantidad, descripcion = descripcion, fecha = fecha, uriFoto = uriFoto, categoria = categoria)
        viewModelScope.launch(Dispatchers.IO) { dao.insertarGasto(nuevoGasto) }
    }

    fun borrarGasto(gasto: Gasto) { viewModelScope.launch(Dispatchers.IO) { dao.borrarGasto(gasto) } }
    fun actualizarGasto(gasto: Gasto) { viewModelScope.launch(Dispatchers.IO) { dao.actualizarGasto(gasto) } }

    fun cambiarMes(nuevoMes: YearMonth) { _mesSeleccionado.value = nuevoMes }
    fun mesAnterior() { _mesSeleccionado.value = _mesSeleccionado.value.minusMonths(1) }
    fun mesSiguiente() { _mesSeleccionado.value = _mesSeleccionado.value.plusMonths(1) }

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

    // Categorías
    fun inicializarCategoriasPorDefecto() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertarCategoria(Categoria("Comida", null))
            dao.insertarCategoria(Categoria("Transporte", null))
            dao.insertarCategoria(Categoria("Casa", null))
            dao.insertarCategoria(Categoria("Otros", null))
        }
    }
    fun agregarNuevaCategoria(nombre: String, uriFoto: String?) { viewModelScope.launch(Dispatchers.IO) { dao.insertarCategoria(Categoria(nombre, uriFoto)) } }
    fun editarCategoria(viejoNombre: String, nuevoNombre: String, nuevaUriFoto: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertarCategoria(Categoria(nuevoNombre, nuevaUriFoto))
            if (viejoNombre != nuevoNombre) {
                dao.actualizarCategoriaEnGastos(viejoNombre, nuevoNombre)
                dao.borrarCategoria(Categoria(viejoNombre, null))
            }
        }
    }
    fun borrarCategoria(categoria: Categoria) { viewModelScope.launch(Dispatchers.IO) { dao.borrarCategoria(categoria) } }
    fun obtenerGastosPorCategoria(lista: List<Gasto>): Map<String, Double> {
        return lista.groupBy { it.categoria }.mapValues { entry -> entry.value.sumOf { it.cantidad } }
    }
}