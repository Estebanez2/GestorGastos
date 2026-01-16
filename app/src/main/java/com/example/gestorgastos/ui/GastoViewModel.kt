package com.example.gestorgastos.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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

    // 1. Conexión con la Base de Datos
    private val db = AppDatabase.getDatabase(application)
    private val dao = db.gastoDao()

    // 2. Control de Fechas (Por defecto, el mes actual)
    // "YearMonth" es una clase genial de Java para manejar "Enero 2024", "Febrero 2024", etc.
    private val _mesSeleccionado = MutableStateFlow(YearMonth.now())

    // 3. Límites de Presupuesto (Esto luego lo haremos editable, por ahora valores iniciales)
    var limiteAmarillo: Double = 500.0
    var limiteRojo: Double = 1000.0

    // 4. LISTA DE GASTOS (Se actualiza sola cuando cambia el mes)
    val gastosDelMes: LiveData<List<Gasto>> = _mesSeleccionado.flatMapLatest { mes ->
        val (inicio, fin) = obtenerRangoFechas(mes)
        dao.obtenerGastosPorMes(inicio, fin)
    }.asLiveData()

    // 5. SUMA TOTAL (Se actualiza sola)
    val sumaTotalDelMes: LiveData<Double?> = _mesSeleccionado.flatMapLatest { mes ->
        val (inicio, fin) = obtenerRangoFechas(mes)
        dao.obtenerSumaGastos(inicio, fin)
    }.asLiveData()

    // --- FUNCIONES DE ACCIÓN (Lo que la UI llama cuando tocas un botón) ---

    fun agregarGasto(nombre: String, cantidad: Double, descripcion: String, uriFoto: String?) {
        val nuevoGasto = Gasto(
            nombre = nombre,
            cantidad = cantidad,
            descripcion = descripcion,
            fecha = System.currentTimeMillis(), // Guarda fecha y hora actual
            uriFoto = uriFoto
        )
        // Lanzamos una "Corrutina" para no bloquear la pantalla mientras guarda
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

    // Cambiar de mes (para cuando hagas el calendario o flechas de navegación)
    fun cambiarMes(nuevoMes: YearMonth) {
        _mesSeleccionado.value = nuevoMes
    }

    // Función para obtener el estado de alerta (Color)
    fun obtenerColorAlerta(gastoTotal: Double): Int {
        return when {
            gastoTotal >= limiteRojo -> R.color.alerta_rojo
            gastoTotal >= limiteAmarillo -> R.color.alerta_amarillo
            else -> R.color.alerta_verde
        }
    }

    // --- AYUDANTE PRIVADO: Convierte Mes -> Milisegundos para la BD ---
    private fun obtenerRangoFechas(mes: YearMonth): Pair<Long, Long> {
        val inicio = mes.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val fin = mes.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Pair(inicio, fin)
    }
}