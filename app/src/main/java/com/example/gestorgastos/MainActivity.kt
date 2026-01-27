package com.example.gestorgastos

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.gestorgastos.data.FiltroBusqueda
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.data.ModoFiltroFecha
import com.example.gestorgastos.databinding.ActivityMainBinding
import com.example.gestorgastos.ui.*
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: GastoViewModel

    // --- MANAGERS ---
    private lateinit var chartManager: ChartManager
    private lateinit var dialogManager: DialogManager
    private lateinit var exportManager: ExportManager
    private lateinit var buscadorManager: BuscadorManager
    private lateinit var dataTransferManager: DataTransferManager
    private lateinit var conflictosManager: ConflictosManager
    private lateinit var catConflictosManager: CategoriaConflictosManager
    private lateinit var uiManager: UIManager
    private lateinit var viewConfigurator: MainViewConfigurator // ¡NUEVO!

    // --- ACCESO A ADAPTERS (Delegado al configurador) ---
    private val adapterLista get() = viewConfigurator.adapterLista
    private val adapterGastosCategoria get() = viewConfigurator.adapterGastosCategoria
    private var adapterCalendario: CalendarioAdapter? = null

    // --- ESTADO UI ---
    enum class Vista { LISTA, CALENDARIO, GRAFICA, QUESITOS }
    private var vistaActual = Vista.LISTA
    private var categoriaSeleccionada: String? = null
    private var diaGraficaSeleccionado: Int? = null
    private var fechaCalendarioSeleccionada: java.time.LocalDate? = null

    // Variables Foto
    private var uriFotoTemporal: Uri? = null
    private var uriFotoFinal: String? = null
    private var ivPreviewActual: ImageView? = null

    // --- LAUNCHERS ---
    private val requestCameraLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) abrirCamara() else Toast.makeText(this, "Sin permiso de cámara", Toast.LENGTH_SHORT).show()
    }
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && uriFotoTemporal != null) {
            uriFotoFinal = ExportarHelper.copiarImagenAInternalStorage(this, uriFotoTemporal!!)
            actualizarVistaFotoDialogo()
        }
    }
    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uriFotoFinal = ExportarHelper.copiarImagenAInternalStorage(this, uri)
            actualizarVistaFotoDialogo()
        }
    }
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) mostrarDialogoModoImportacion(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // --- RECUPERAR VISTA GUARDADA ---
        val prefs = getSharedPreferences("AppConfig", MODE_PRIVATE)
        val vistaGuardadaIndex = prefs.getInt("ULTIMA_VISTA", Vista.LISTA.ordinal)
        // Recuperamos la vista o usamos LISTA si falla algo
        vistaActual = Vista.values().getOrElse(vistaGuardadaIndex) { Vista.LISTA }
        viewModel = ViewModelProvider(this)[GastoViewModel::class.java]

        inicializarManagers()

        // Configuración delegada de Vistas y Swipe
        viewConfigurator.setupRecyclerViews(
            onEditGasto = { mostrarDialogoEditarGasto(it) },
            onSelectionChanged = { cantidad -> uiManager.gestionarBarraSeleccion(cantidad, binding.fabAgregar) },
            onSwipeDelete = { gasto, adapter, pos ->
                if (!uiManager.estaBarraSeleccionVisible()) {
                    mostrarDialogoConfirmacionBorrado(gasto, adapter, pos)
                } else {
                    adapter.notifyItemChanged(pos) // Restaurar si estaba bloqueado
                }
            }
        )

        // Gráficas
        chartManager.setupBarChart()
        chartManager.setupPieChart()

        setupBotones()
        setupObservers()
    }

    private fun inicializarManagers() {
        uiManager = UIManager(binding)
        viewConfigurator = MainViewConfigurator(binding, this, uiManager)

        chartManager = ChartManager(this, binding.chartGastos, binding.chartCategorias,
            onCategorySelected = { categoria ->
                categoriaSeleccionada = categoria
                filtrarListaCategorias()
                binding.tvTituloCategoriaSeleccionada.text = categoria?.let { "Detalles: $it" } ?: "Toca para ver detalles"
            },
            onBarSelected = { dia ->
                diaGraficaSeleccionado = dia
                val listaTotal = viewModel.gastosVisibles.value ?: emptyList()
                if (dia != null) {
                    val filtrados = listaTotal.filter {
                        val fecha = Instant.ofEpochMilli(it.fecha).atZone(ZoneId.systemDefault())
                        fecha.dayOfMonth == dia
                    }
                    viewConfigurator.adapterGastosGrafica.submitList(filtrados)
                    binding.tvTituloDetalleGrafica.text = "Gastos del día $dia"
                } else {
                    viewConfigurator.adapterGastosGrafica.submitList(emptyList())
                    binding.tvTituloDetalleGrafica.text = "Toca una barra para ver detalles"
                }
            }
        )
        dialogManager = DialogManager(this).apply {
            onCameraRequested = { checkCameraPermissionAndOpen() }
            onGalleryRequested = { pickGalleryLauncher.launch("image/*") }
            onImageClick = { uri -> ImageZoomHelper.mostrarImagen(this@MainActivity, uri) }
        }
        buscadorManager = BuscadorManager(this)
        exportManager = ExportManager(this, lifecycleScope)
        dataTransferManager = DataTransferManager(this)
        conflictosManager = ConflictosManager(this)
        catConflictosManager = CategoriaConflictosManager(this)
    }

    private fun setupBotones() {
        binding.fabAgregar.setOnClickListener { mostrarDialogoAgregarGasto() }

        binding.btnConfig.setOnClickListener {
            dialogManager.mostrarConfiguracion(viewModel.limiteAmarillo, viewModel.limiteRojo,
                onGuardar = { am, ro -> viewModel.guardarNuevosLimites(am, ro) },
                onCambiarMoneda = { dialogManager.mostrarSelectorMoneda { viewModel.notificarCambioLimites.value = true } }
            )
        }

        binding.btnExportar.setOnClickListener {
            exportManager.mostrarMenuPrincipal { accion ->
                when (accion) {
                    ExportManager.Accion.IMAGEN_VISTA -> manejarExportacionImagen()
                    ExportManager.Accion.IMPORTAR_DATOS -> importFileLauncher.launch("*/*")
                    ExportManager.Accion.EXPORTAR_DATOS -> {
                        exportManager.iniciarProcesoExportacion(viewModel.mesActual.value) { inicio, fin, formatoIndex ->
                            realizarBackup(inicio, fin, formatoIndex)
                        }
                    }
                }
            }
        }

        binding.btnMesAnterior.setOnClickListener { viewModel.mesAnterior() }
        binding.btnMesSiguiente.setOnClickListener { viewModel.mesSiguiente() }
        binding.btnCategorias.setOnClickListener { startActivity(Intent(this, CategoriasActivity::class.java)) }
        binding.btnCambiarVista.setOnClickListener { mostrarMenuVistas(it) }
        binding.btnBuscar.setOnClickListener { if (viewModel.estaBuscando()) mostrarMenuFiltro(it) else abrirBuscador(null) }

        binding.btnCerrarSeleccion.setOnClickListener { obtenerAdapterActivo().salirModoSeleccion() }
        binding.btnBorrarSeleccionados.setOnClickListener { procesarBorradoMultiple() }
    }

    private fun setupObservers() {
        viewModel.gastosVisibles.observe(this) { lista ->
            // 1. Actualizaciones generales (Lista principal, totales, colores...)
            viewConfigurator.adapterLista.submitList(lista)
            filtrarListaCategorias() // Mantiene filtro de quesitos

            val total = lista.sumOf { it.cantidad }
            binding.tvTotalMes.text = Formato.formatearMoneda(total)
            binding.layoutAlerta.setBackgroundColor(ContextCompat.getColor(this, viewModel.obtenerColorAlerta(total)))
            uiManager.cambiarVista(vistaActual, lista.isNotEmpty())
            gestionarTitulo(lista.size)
            // 2. Actualizar GRÁFICA y CALENDARIO (Visual)
            chartManager.actualizarBarChart(lista, viewModel.limiteRojo, viewModel.limiteAmarillo)
            chartManager.actualizarPieChart(lista, categoriaSeleccionada)
            val mes = viewModel.mesActual.value ?: java.time.YearMonth.now()
            adapterCalendario = CalendarioAdapter(mes, lista) { fecha ->
                fechaCalendarioSeleccionada = fecha
                val filtrados = lista.filter {
                    val f = java.time.Instant.ofEpochMilli(it.fecha).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    f == fecha
                }
                viewConfigurator.adapterGastosCalendario.submitList(filtrados)
                val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")
                binding.tvTituloDetalleCalendario.text = "Gastos del ${fecha.format(formatter)}"
            }
            binding.rvCalendario.adapter = adapterCalendario
            if (fechaCalendarioSeleccionada != null) {
                val filtrados = lista.filter {
                    val f = java.time.Instant.ofEpochMilli(it.fecha).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    f == fechaCalendarioSeleccionada
                }
                viewConfigurator.adapterGastosCalendario.submitList(filtrados)
            } else {
                viewConfigurator.adapterGastosCalendario.submitList(emptyList())
                binding.tvTituloDetalleCalendario.text = "Toca un día para ver detalles"
            }
            if (diaGraficaSeleccionado != null) {
                val filtrados = lista.filter {
                    val f = java.time.Instant.ofEpochMilli(it.fecha).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    f.dayOfMonth == diaGraficaSeleccionado
                }
                viewConfigurator.adapterGastosGrafica.submitList(filtrados)
            } else {
                viewConfigurator.adapterGastosGrafica.submitList(emptyList())
                binding.tvTituloDetalleGrafica.text = "Toca una barra para ver detalles"
            }
        }

        viewModel.mesActual.observe(this) {
            if (!viewModel.estaBuscando()) gestionarTitulo(viewModel.gastosVisibles.value?.size ?: 0)
        }
        viewModel.notificarCambioLimites.observe(this) {
            // 1. Recalcular datos actuales con los nuevos límites
            val lista = viewModel.gastosVisibles.value ?: emptyList()
            val total = lista.sumOf { it.cantidad }
            // 2. Actualizar la UI estática (Lista, Gráfica y Color de fondo fijo)
            adapterLista.notifyDataSetChanged()
            binding.layoutAlerta.setBackgroundColor(ContextCompat.getColor(this, viewModel.obtenerColorAlerta(total)))
            chartManager.actualizarBarChart(lista, viewModel.limiteRojo, viewModel.limiteAmarillo)
            // 3. Ejecutar el Flash para dar feedback inmediato
            uiManager.ejecutarEfectoSemaforo(totalActual = total, gastoNuevo = 0.0, limAmarillo = viewModel.limiteAmarillo, limRojo = viewModel.limiteRojo)
        }
        viewModel.listaCategorias.observe(this) { lista ->
            val mapa = lista.associate { it.nombre to it.uriFoto }
            // Pasamos el mapa a TODOS los adaptadores
            viewConfigurator.adapterLista.mapaCategorias = mapa
            viewConfigurator.adapterGastosCategoria.mapaCategorias = mapa

            // --- NUEVO: ARREGLO DE FOTOS EN CALENDARIO Y GRÁFICA ---
            viewConfigurator.adapterGastosCalendario.mapaCategorias = mapa
            viewConfigurator.adapterGastosGrafica.mapaCategorias = mapa

            // Notificamos cambios para que se repinten las fotos al instante
            viewConfigurator.adapterLista.notifyDataSetChanged()
            viewConfigurator.adapterGastosCategoria.notifyDataSetChanged()
            viewConfigurator.adapterGastosCalendario.notifyDataSetChanged()
            viewConfigurator.adapterGastosGrafica.notifyDataSetChanged()
        }
    }

    // --- LÓGICA DE UI DELEGADA ---
    private fun gestionarTitulo(cantidadGastos: Int) {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
        var titulo = viewModel.mesActual.value?.format(formatter)?.replaceFirstChar { it.uppercase() } ?: ""
        if (viewModel.estaBuscando()) {
            val filtro = viewModel.filtroActualValue
            titulo = when (filtro?.modoFecha) {
                ModoFiltroFecha.TODOS -> "Historial Completo ($cantidadGastos)"
                ModoFiltroFecha.RANGO_FECHAS -> "Rango Seleccionado ($cantidadGastos)"
                else -> "$titulo ($cantidadGastos)"
            }
        }
        uiManager.actualizarTitulo(titulo, viewModel.estaBuscando())
    }

    // --- FUNCIONES AUXILIARES ---
    private fun abrirCamara() {
        try {
            val tempFile = File.createTempFile("foto_", ".jpg", externalCacheDir)
            uriFotoTemporal = FileProvider.getUriForFile(this, "$packageName.fileprovider", tempFile)
            takePictureLauncher.launch(uriFotoTemporal)
        } catch (e: Exception) { e.printStackTrace() }
    }
    private fun checkCameraPermissionAndOpen() {
        ejecutarConPermisoCamara(
            onGranted = { abrirCamara() },
            onDenied = { requestCameraLauncher.launch(Manifest.permission.CAMERA) }
        )
    }

    // --- EXPORTAR / IMPORTAR ---
    private fun manejarExportacionImagen() {
        val lista = viewModel.gastosVisibles.value ?: emptyList()
        if (lista.isEmpty()) { Toast.makeText(this, "Nada que capturar", Toast.LENGTH_SHORT).show(); return }
        val vistas = ExportManager.VistasCaptura(binding.cardResumen, binding.layoutNavegacion, binding.chartGastos, binding.chartCategorias, binding.rvCalendario, binding.layoutVistaCategorias)
        exportManager.procesarCapturaImagen(vistaActual, lista, vistas) { bitmap ->
            if (bitmap != null) {
                AlertDialog.Builder(this).setTitle("Imagen lista").setItems(arrayOf("Guardar", "Compartir")) { _, i ->
                    if (i == 0) ExportarHelper.guardarEnDispositivo(this, bitmap, null, true)
                    else ExportarHelper.compartir(this, bitmap, null, true)
                }.show()
            }
        }
    }

    private fun realizarBackup(inicio: Long, fin: Long, formatoIndex: Int) {
        val incluirFotos = formatoIndex == 0
        val soloCsv = formatoIndex == 2
        val mensaje = if (incluirFotos) "Generando ZIP..." else "Generando archivo..."
        val progress = AlertDialog.Builder(this).setMessage(mensaje).setCancelable(false).show()
        lifecycleScope.launch {
            val archivo = if (soloCsv) dataTransferManager.exportarSoloCSV(inicio, fin) else dataTransferManager.exportarDatos(incluirFotos, inicio, fin)
            progress.dismiss()
            val mime = if (soloCsv) "text/csv" else if (incluirFotos) "application/zip" else "application/json"
            exportManager.mostrarDialogoPostExportacion(archivo, mime) { file ->
                ExportarHelper.guardarArchivoEnDescargas(this@MainActivity, file, mime)
            }
        }
    }

    private fun mostrarDialogoModoImportacion(uri: Uri) {
        AlertDialog.Builder(this).setTitle("Importar").setMessage("¿Cómo quieres proceder?")
            .setPositiveButton("MEZCLAR") { _, _ -> confirmarImportacion(uri, false) }
            .setNegativeButton("SUSTITUIR") { _, _ ->
                AlertDialog.Builder(this).setMessage("¿Borrar TODO antes?").setPositiveButton("SÍ") { _, _ -> confirmarImportacion(uri, true) }.setNegativeButton("No", null).show()
            }
            .setNeutralButton("Cancelar", null).show()
    }

    // --- LÓGICA IMPORTACIÓN INTELIGENTE (FILTRADO DE CATEGORÍAS) ---
    private fun confirmarImportacion(uri: Uri, sustituir: Boolean) {
        val progress = AlertDialog.Builder(this).setMessage("Procesando...").setCancelable(false).show()
        lifecycleScope.launch {
            val resultado = dataTransferManager.importarDatos(uri, sustituir)
            progress.dismiss()
            if (resultado.exito) {
                // PASO 1: Resolver Conflictos de GASTOS
                if (resultado.conflictosGastos.isNotEmpty()) {
                    conflictosManager.mostrarDialogoResolucion(resultado.conflictosGastos) { descartar, reemplazar, duplicar ->
                        lifecycleScope.launch {
                            // A. Ejecutamos la acción en la BD
                            dataTransferManager.resolverConflictos(descartar, reemplazar, duplicar)

                            // B. FILTRADO INTELIGENTE DE CATEGORÍAS
                            val categoriasAfectadas = mutableSetOf<String>()
                            // 1. Gastos directos
                            categoriasAfectadas.addAll(resultado.gastosInsertados.map { it.categoria })
                            // 2. Gastos reemplazados
                            categoriasAfectadas.addAll(reemplazar.map { it.nuevoImportado.categoria })
                            // 3. Gastos duplicados
                            categoriasAfectadas.addAll(duplicar.map { it.nuevoImportado.categoria })

                            // C. Filtramos conflictos relevantes
                            val conflictosRelevantes = resultado.conflictosCategorias.filter { conflicto ->
                                categoriasAfectadas.contains(conflicto.categoriaNombre)
                            }

                            // D. Procesamos
                            procesarConflictosCategorias(conflictosRelevantes, resultado.gastosInsertados.size + reemplazar.size + duplicar.size)
                        }
                    }
                } else {
                    // Si no hubo conflictos de gastos, preguntamos por las categorías de los insertados
                    val categoriasAfectadas = resultado.gastosInsertados.map { it.categoria }.toSet()
                    val conflictosRelevantes = resultado.conflictosCategorias.filter {
                        categoriasAfectadas.contains(it.categoriaNombre)
                    }
                    procesarConflictosCategorias(conflictosRelevantes, resultado.gastosInsertados.size)
                }
            } else {
                Toast.makeText(this@MainActivity, "Error al importar", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun procesarConflictosCategorias(conflictos: List<DataTransferManager.ConflictoCategoria>, nuevos: Int) {
        catConflictosManager.resolverConflictos(conflictos.toMutableList(),
            onDecisionTomada = { conf, usaNueva ->
                if (usaNueva) lifecycleScope.launch(Dispatchers.IO) { dataTransferManager.actualizarFotoCategoria(conf.categoriaNombre, conf.uriNueva) }
            },
            onTodosResueltos = {
                viewModel.limpiarFiltro()
                viewModel.inicializarCategoriasPorDefecto()
                binding.viewFlashBorde.flashEffect(R.color.alerta_verde)
                Toast.makeText(this, "Importación completada ($nuevos nuevos)", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- DIÁLOGOS WRAPPERS ---
    private fun mostrarDialogoAgregarGasto() {
        val cats = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()
        uriFotoFinal = null
        val d = dialogManager.mostrarAgregarGasto(cats, null,
            onGuardar = { n, c, de, ca ->
                // 1. Guardamos el gasto
                viewModel.agregarGasto(n, c, de, uriFotoFinal, ca)
                // 2. ACTIVAR SEMÁFORO
                uiManager.ejecutarEfectoSemaforo(totalActual = viewModel.gastosVisibles.value?.sumOf { it.cantidad } ?: 0.0, gastoNuevo = c, limAmarillo = viewModel.limiteAmarillo, limRojo = viewModel.limiteRojo)
            },
            onBorrarFoto = { borrarFotoDelDialogo() }
        )
        ivPreviewActual = d.findViewById(R.id.ivPreviewFoto)
    }

    private fun mostrarDialogoEditarGasto(g: Gasto) {
        val cats = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()
        uriFotoFinal = g.uriFoto
        val cantidadOriginal = g.cantidad
        val d = dialogManager.mostrarEditarGasto(g, cats, uriFotoFinal,
            onActualizar = { gastoEditado ->
                viewModel.actualizarGasto(gastoEditado.copy(uriFoto = uriFotoFinal))
                if (gastoEditado.cantidad != cantidadOriginal) {
                    val diferencia = gastoEditado.cantidad - cantidadOriginal
                    uiManager.ejecutarEfectoSemaforo(totalActual = viewModel.gastosVisibles.value?.sumOf { it.cantidad } ?: 0.0, gastoNuevo = diferencia, limAmarillo = viewModel.limiteAmarillo, limRojo = viewModel.limiteRojo)
                }
            },
            onBorrarFoto = { borrarFotoDelDialogo() }
        )
        ivPreviewActual = d.findViewById(R.id.ivPreviewFoto)
    }

    private fun mostrarDialogoConfirmacionBorrado(g: Gasto, adapter: GastoAdapter, pos: Int) {
        dialogManager.mostrarConfirmacionBorrado(g,
            onConfirmar = {
                viewModel.borrarGasto(g)
                var deshecho = false
                Snackbar.make(binding.root, "Borrado", Snackbar.LENGTH_LONG).setAction("Deshacer") {
                    if (!deshecho) { deshecho = true; viewModel.agregarGasto(g.nombre, g.cantidad, g.descripcion, g.uriFoto, g.categoria, g.fecha) }
                }.show()
            },
            onCancelar = { adapter.notifyItemChanged(pos) })
    }

    // Función auxiliar para saber qué adaptador manda ahora mismo
    private fun obtenerAdapterActivo(): GastoAdapter {
        return when (vistaActual) {
            Vista.LISTA -> viewConfigurator.adapterLista
            Vista.QUESITOS -> viewConfigurator.adapterGastosCategoria
            Vista.CALENDARIO -> viewConfigurator.adapterGastosCalendario
            Vista.GRAFICA -> viewConfigurator.adapterGastosGrafica
        }
    }

    private fun procesarBorradoMultiple() {
        val adapter = obtenerAdapterActivo()
        val seleccionados = adapter.obtenerGastosSeleccionados()
        if (seleccionados.isEmpty()) return

        AlertDialog.Builder(this).setTitle("Borrar ${seleccionados.size} gastos?")
            .setPositiveButton("Borrar") { _, _ ->
                val backup = seleccionados.toList()
                viewModel.borrarGastosSeleccionados(seleccionados)
                adapter.salirModoSeleccion() // Salimos del modo selección del adaptador activo

                var deshecho = false
                Snackbar.make(binding.root, "Eliminados ${seleccionados.size} gastos", 5000).setAction("DESHACER") {
                    if (!deshecho) { deshecho = true; viewModel.restaurarGastos(backup) }
                }.show()
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun mostrarMenuVistas(view: View) {
        PopupMenu(this, view).apply {
            inflate(R.menu.menu_vistas)
            setOnMenuItemClickListener {
                vistaActual = when(it.itemId) {
                    R.id.menu_vista_calendario -> Vista.CALENDARIO
                    R.id.menu_vista_barras -> Vista.GRAFICA
                    R.id.menu_vista_categorias -> Vista.QUESITOS
                    else -> Vista.LISTA
                }
                getSharedPreferences("AppConfig", MODE_PRIVATE).edit().putInt("ULTIMA_VISTA", vistaActual.ordinal).apply()
                viewModel.gastosVisibles.value?.let { lista -> uiManager.cambiarVista(vistaActual, lista.isNotEmpty()) }
                true
            }
            show()
        }
    }

    private fun abrirBuscador(filtro: FiltroBusqueda?) {
        val cats = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()
        buscadorManager.mostrarBuscador(cats, filtro) { viewModel.aplicarFiltro(it) }
    }

    private fun mostrarMenuFiltro(view: View) {
        PopupMenu(this, view).apply {
            menu.add("Modificar filtro").setOnMenuItemClickListener { abrirBuscador(viewModel.filtroActualValue); true }
            menu.add("Quitar filtro").setOnMenuItemClickListener { viewModel.limpiarFiltro(); true }
            show()
        }
    }

    private fun filtrarListaCategorias() {
        val todos = viewModel.gastosVisibles.value ?: emptyList()
        adapterGastosCategoria.submitList(if (categoriaSeleccionada != null) todos.filter { it.categoria == categoriaSeleccionada } else emptyList())
    }

    override fun onBackPressed() {
        if (uiManager.estaBarraSeleccionVisible()) obtenerAdapterActivo().salirModoSeleccion() else super.onBackPressed()
    }

    private fun actualizarVistaFotoDialogo() {
        ivPreviewActual?.let { iv ->
            Glide.with(this).load(uriFotoFinal).centerCrop().into(iv)
            iv.clearColorFilter()
            iv.setOnClickListener { ImageZoomHelper.mostrarImagen(this, uriFotoFinal) }
            (iv.parent as? View)?.findViewById<View>(R.id.btnBorrarFoto)?.visibility = View.VISIBLE
        }
    }

    private fun borrarFotoDelDialogo() {
        uriFotoFinal = null
        ivPreviewActual?.let { iv -> iv.setImageDrawable(null)
            (iv.parent as? View)?.findViewById<View>(R.id.btnBorrarFoto)?.visibility = View.GONE }
        Toast.makeText(this, "Foto eliminada", Toast.LENGTH_SHORT).show()
    }
}