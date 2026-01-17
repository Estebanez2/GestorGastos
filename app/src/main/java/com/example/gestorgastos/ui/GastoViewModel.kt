package com.example.gestorgastos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.gestorgastos.R
import com.example.gestorgastos.data.AppDatabase
import com.example.gestorgastos.data.Gasto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

class GastoViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dao = db.gastoDao()

    // Inicializamos las preferencias
    private val prefs = com.example.gestorgastos.data.Preferencias(application)

    private val _mesSeleccionado = MutableStateFlow(YearMonth.now())

    // Exponemos el mes actual para que la Activity pueda ponerle título al texto
    val mesActual: LiveData<YearMonth> = _mesSeleccionado.asLiveData()

    // Variable para avisar a la UI que los límites han cambiado
    val notificarCambioLimites = androidx.lifecycle.MutableLiveData<Boolean>()

    // CAMBIO: Leemos los valores guardados. Si es la primera vez, usa 500 y 1000 por defecto.
    var limiteAmarillo: Double = prefs.obtenerAmarillo()
    var limiteRojo: Double = prefs.obtenerRojo()

    val gastosDelMes: LiveData<List<Gasto>> = _mesSeleccionado.flatMapLatest { mes ->
        val (inicio, fin) = obtenerRangoFechas(mes)
        dao.obtenerGastosPorMes(inicio, fin)
    }.asLiveData()

    val sumaTotalDelMes: LiveData<Double?> = _mesSeleccionado.flatMapLatest { mes ->
        val (inicio, fin) = obtenerRangoFechas(mes)
        dao.obtenerSumaGastos(inicio, fin)
    }.asLiveData()

    // --- FUNCIONES DE ACCIÓN ---

    fun guardarNuevosLimites(nuevoAmarillo: Double, nuevoRojo: Double) {
        limiteAmarillo = nuevoAmarillo
        limiteRojo = nuevoRojo
        prefs.guardarLimites(nuevoAmarillo, nuevoRojo)

        // Avisamos para que se recalcule el color del semáforo
        notificarCambioLimites.value = true
    }

       fun agregarGasto(nombre: String, cantidad: Double, descripcion: String, uriFoto: String?, categoria: String) {
        val nuevoGasto = Gasto(
            nombre = nombre,
            cantidad = cantidad,
            descripcion = descripcion,
            fecha = System.currentTimeMillis(),
            uriFoto = uriFoto,
            categoria = categoria
        )
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertarGasto(nuevoGasto)
        }
    }

    fun borrarGasto(gasto: Gasto) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.borrarGasto(gasto)
        }
    }

    fun actualizarGasto(gasto: Gasto) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.actualizarGasto(gasto)
        }
    }

    fun cambiarMes(nuevoMes: YearMonth) {
        _mesSeleccionado.value = nuevoMes
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
        val fin = mes.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Pair(inicio, fin)
    }

    fun mesAnterior() {
        _mesSeleccionado.value = _mesSeleccionado.value.minusMonths(1)
    }

    fun mesSiguiente() {
        _mesSeleccionado.value = _mesSeleccionado.value.plusMonths(1)
    }
}