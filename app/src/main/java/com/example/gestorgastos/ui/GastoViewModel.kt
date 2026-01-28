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
import com.example.gestorgastos.data.ModoFiltroFecha
import kotlinx.coroutines.withContext

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
    val listaCategorias: LiveData<List<Categoria>> = dao.obtenerCategorias().asLiveData()

    // --- LÓGICA CENTRAL ---
    val gastosVisibles: LiveData<List<Gasto>> = combine(_mesSeleccionado, _filtroActual) { mes, filtro ->
        Pair(mes, filtro)
    }.flatMapLatest { (mes, filtro) ->

        if (filtro != null) {

            // DECIDIMOS EL RANGO SEGÚN EL MODO ELEGIDO
            val (inicio, fin) = when (filtro.modoFecha) {

                ModoFiltroFecha.TODOS -> {
                    // Modo Histórico: Desde 0 al infinito
                    Pair(0L, Long.MAX_VALUE)
                }

                ModoFiltroFecha.RANGO_FECHAS -> {
                    // Modo Absoluto: Usamos las fechas del DatePicker (si existen)
                    // (Ya están validadas en el Manager, pero por seguridad usamos 0L si es null)
                    val ini = filtro.fechaInicioAbs ?: 0L
                    val fin = filtro.fechaFinAbs ?: Long.MAX_VALUE
                    Pair(ini, fin)
                }

                ModoFiltroFecha.MES_ACTUAL -> {
                    // Modo Relativo: Usamos el mes actual + los días 1-31 elegidos
                    obtenerRangoFechasConDias(mes, filtro.diaInicio, filtro.diaFin)
                }
            }

            dao.buscarGastosAvanzado(
                filtro.nombre, filtro.descripcion, filtro.categoria,
                filtro.precioMin, filtro.precioMax,
                inicio, fin
            )

        } else {
            // Sin filtro -> Mes completo normal
            val (inicio, fin) = obtenerRangoFechas(mes)
            dao.obtenerGastosPorMes(inicio, fin)
        }
    }.asLiveData()

    // Helper para calcular fechas completas (Milisegundos) desde un día simple (1-31)
    private fun obtenerRangoFechasConDias(mes: YearMonth, diaInicio: Int?, diaFin: Int?): Pair<Long, Long> {
        // Si no puso días, cogemos el mes entero (1 al último día del mes)
        val dInicio = diaInicio ?: 1
        // Si puso día fin, usamos ese. Si no, usamos el último día del mes real
        val dFin = diaFin ?: mes.lengthOfMonth()

        // PROTECCIÓN: Si estamos en Febrero (28 días) y el filtro dice "Día 30",
        // ajustamos dFin a 28 para que no de error.
        val dInicioReales = dInicio.coerceAtMost(mes.lengthOfMonth())
        val dFinReales = dFin.coerceAtMost(mes.lengthOfMonth())

        val inicioMilis = mes.atDay(dInicioReales).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val finMilis = mes.atDay(dFinReales).atTime(23, 59, 59, 999999999).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        return Pair(inicioMilis, finMilis)
    }

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

    fun borrarGasto(gasto: Gasto) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Borramos el archivo físico primero
            borrarArchivoFisico(gasto.uriFoto)
            // 2. Borramos de la BD
            dao.borrarGasto(gasto)
        }
    }
    fun actualizarGasto(gastoNuevo: Gasto) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Obtenemos el gasto viejo para ver qué foto tenía
            val gastoViejo = dao.obtenerGastoPorId(gastoNuevo.id) // Necesitas esta query en DAO

            // 2. Si tenía foto, y es DIFERENTE a la nueva (o ahora es null), borramos la vieja
            if (gastoViejo != null && !gastoViejo.uriFoto.isNullOrEmpty()) {
                if (gastoViejo.uriFoto != gastoNuevo.uriFoto) {
                    borrarArchivoFisico(gastoViejo.uriFoto)
                }
            }

            // 3. Actualizamos en BD
            dao.actualizarGasto(gastoNuevo)
        }
    }

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
            val categoriasBasicas = listOf("Comida", "Transporte", "Casa", "Otros")
            for (nombre in categoriasBasicas) {
                // ANTES DE INSERTAR, VERIFICAMOS SI YA EXISTE
                val existe = dao.obtenerCategoriaPorNombre(nombre)
                if (existe == null) {
                    // Solo si NO existe, la creamos vacía (sin foto)
                    dao.insertarCategoria(Categoria(nombre, null))
                }
                // Si ya existe (aunque tenga foto personalizada), NO HACEMOS NADA.
            }
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

    fun borrarCategoria(categoria: Categoria) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Seguridad: Nos aseguramos de que la categoría destino "Otros" exista
            val catOtros = dao.obtenerCategoriaPorNombre("Otros")
            if (catOtros == null) {
                dao.insertarCategoria(Categoria("Otros", null))
            }
            // 2. Ejecutamos el borrado
            // Nunca permitimos borrar la categoría "Otros"
            if (categoria.nombre != "Otros") {
                // --- LIMPIEZA DE ARCHIVOS (NUEVO) ---
                // Si uriFoto NO es nulo, significa que es una foto TUYA guardada en el móvil.
                // Si es nulo (icono predeterminado), no entra aquí y no borra nada.
                if (!categoria.uriFoto.isNullOrEmpty()) {
                    try {
                        val uri = android.net.Uri.parse(categoria.uriFoto)
                        val path = uri.path
                        if (path != null) {
                            val file = java.io.File(path)
                            if (file.exists()) {
                                file.delete() // ¡Borrado físico para ahorrar espacio!
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace() // Si falla el borrado del archivo, no rompemos la app
                    }
                }
                // 3. Operación en Base de Datos (Mover gastos -> Borrar registro)
                dao.eliminarCategoriaDeFormaSegura(categoria)
            }
        }
    }

    fun obtenerGastosPorCategoria(lista: List<Gasto>): Map<String, Double> {
        return lista.groupBy { it.categoria }.mapValues { entry -> entry.value.sumOf { it.cantidad } }
    }

    fun borrarGastosSeleccionados(lista: List<Gasto>) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.borrarListaGastos(lista)
        }
    }

    // Función para deshacer el borrado (vuelve a insertar la lista)
    fun restaurarGastos(lista: List<Gasto>) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertarListaGastos(lista)
        }
    }

    fun contarGastosDeCategoria(nombre: String, alTerminar: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val cantidad = dao.contarGastosPorCategoria(nombre)
            withContext(Dispatchers.Main) {
                alTerminar(cantidad)
            }
        }
    }

    // 1. Herramienta auxiliar para borrar un archivo dada su URI string
    private fun borrarArchivoFisico(uriString: String?) {
        if (uriString.isNullOrEmpty()) return
        try {
            val uri = android.net.Uri.parse(uriString)
            // Solo borramos si el archivo está en nuestra carpeta privada (filesDir)
            // Esto evita intentar borrar fotos de la galería del sistema si usaras rutas externas
            if (uri.path?.contains(getApplication<Application>().filesDir.absolutePath) == true) {
                val file = java.io.File(uri.path!!)
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 2. FUNCIÓN MAESTRA: RECOLECTOR DE BASURA
    // Llámala al iniciar la App para limpiar residuos de sesiones anteriores (ej. diálogos cancelados)
    fun limpiarArchivosHuerfanos() {
        viewModelScope.launch(Dispatchers.IO) {
            // A. Obtenemos TODAS las URIs que la base de datos dice que estamos usando
            val gastos = dao.obtenerTodosLosGastosSincrono() // Necesitarás crear esta query simple o usar una existente
            val categorias = dao.obtenerTodasLasCategoriasDirecto()

            val urisEnUso = mutableSetOf<String>()

            gastos.forEach { g -> g.uriFoto?.let { urisEnUso.add(it) } }
            categorias.forEach { c -> c.uriFoto?.let { urisEnUso.add(it) } }

            // B. Listamos TODOS los archivos físicos reales en la carpeta de imágenes
            val carpetaArchivos = getApplication<Application>().filesDir
            val archivosReales = carpetaArchivos.listFiles() ?: return@launch

            var archivosBorrados = 0

            // C. Comparamos: Si existe el archivo FÍSICO pero NO está en la BD -> Es basura
            for (archivo in archivosReales) {
                // Filtramos solo nuestras imágenes (suelen empezar por img_ o el prefijo que uses)
                // Ojo: no borrar archivos de base de datos o preferencias
                if (archivo.name.startsWith("img_") || archivo.name.endsWith(".jpg")) {

                    // Reconstruimos la URI tal como la guardas en BD para comparar
                    // Usamos FileProvider o path absoluto según como lo guardes.
                    // Como tu 'copiarImagenAInternalStorage' devuelve la URI completa string, comparamos buscando el nombre

                    val estaEnUso = urisEnUso.any { uriDb -> uriDb.contains(archivo.name) }

                    if (!estaEnUso) {
                        archivo.delete()
                        archivosBorrados++
                    }
                }
            }
            if (archivosBorrados > 0) {
                println("LIMPIEZA: Se han eliminado $archivosBorrados imágenes huérfanas.")
            }
        }
    }
}