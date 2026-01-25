package com.example.gestorgastos

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
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
    private lateinit var uiManager: UIManager // Nuevo

    // --- ADAPTERS ---
    private lateinit var adapterLista: GastoAdapter
    private lateinit var adapterGastosCategoria: GastoAdapter
    private var adapterCalendario: CalendarioAdapter? = null

    // --- ESTADO UI ---
    enum class Vista { LISTA, CALENDARIO, GRAFICA, QUESITOS }
    private var vistaActual = Vista.LISTA
    private var categoriaSeleccionada: String? = null

    // Variables para la cámara (Necesarias aquí)
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

        viewModel = ViewModelProvider(this)[GastoViewModel::class.java]

        inicializarManagers()
        setupVistas()
        setupBotones()
        setupObservers()
    }

    private fun inicializarManagers() {
        uiManager = UIManager(binding)
        chartManager = ChartManager(this, binding.chartGastos, binding.chartCategorias) { categoria ->
            categoriaSeleccionada = categoria
            filtrarListaCategorias()
            binding.tvTituloCategoriaSeleccionada.text = categoria?.let { "Detalles: $it" } ?: "Toca para ver detalles"
        }
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

    private fun setupVistas() {
        // Lista Principal
        adapterLista = GastoAdapter { mostrarDialogoEditarGasto(it) }
        adapterLista.onSelectionChanged = { cantidad ->
            uiManager.gestionarBarraSeleccion(cantidad, binding.fabAgregar)
        }

        binding.rvGastos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adapterLista
            setupSwipeToDelete { pos ->
                if (!uiManager.estaBarraSeleccionVisible()) {
                    mostrarDialogoConfirmacionBorrado(adapterLista.currentList[pos], adapterLista, pos)
                } else {
                    adapterLista.notifyItemChanged(pos) // Bloqueado si seleccionando
                }
            }
        }

        // Lista Categorías y Calendario
        adapterGastosCategoria = GastoAdapter { mostrarDialogoEditarGasto(it) }
        binding.rvGastosCategoria.layoutManager = LinearLayoutManager(this)
        binding.rvGastosCategoria.adapter = adapterGastosCategoria

        binding.rvCalendario.layoutManager = GridLayoutManager(this, 7)

        chartManager.setupBarChart()
        chartManager.setupPieChart()
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
                        // Delegamos el flujo complejo al ExportManager
                        exportManager.iniciarProcesoExportacion(viewModel.mesActual.value) { inicio, fin, formatoIndex ->
                            realizarBackup(inicio, fin, formatoIndex)
                        }
                    }
                }
            }
        }

        // Navegación y Vistas
        binding.btnMesAnterior.setOnClickListener { viewModel.mesAnterior() }
        binding.btnMesSiguiente.setOnClickListener { viewModel.mesSiguiente() }
        binding.btnCategorias.setOnClickListener { startActivity(Intent(this, CategoriasActivity::class.java)) }
        binding.btnCambiarVista.setOnClickListener { mostrarMenuVistas(it) }
        binding.btnBuscar.setOnClickListener { if (viewModel.estaBuscando()) mostrarMenuFiltro(it) else abrirBuscador(null) }

        // Multiselección
        binding.btnCerrarSeleccion.setOnClickListener { adapterLista.salirModoSeleccion() }
        binding.btnBorrarSeleccionados.setOnClickListener { procesarBorradoMultiple() }
    }

    private fun setupObservers() {
        viewModel.gastosVisibles.observe(this) { lista ->
            // Actualizar Adaptadores
            adapterLista.submitList(lista)
            filtrarListaCategorias()

            // Actualizar Calendario y Gráficas
            val mes = viewModel.mesActual.value ?: java.time.YearMonth.now()
            adapterCalendario = CalendarioAdapter(mes, lista)
            binding.rvCalendario.adapter = adapterCalendario
            chartManager.actualizarBarChart(lista, viewModel.limiteRojo, viewModel.limiteAmarillo)
            chartManager.actualizarPieChart(lista, categoriaSeleccionada)

            // Actualizar UI General (Totales y Títulos)
            val total = lista.sumOf { it.cantidad }
            binding.tvTotalMes.text = Formato.formatearMoneda(total)
            binding.layoutAlerta.setBackgroundColor(ContextCompat.getColor(this, viewModel.obtenerColorAlerta(total)))

            gestionarTitulo(lista.size)
            uiManager.cambiarVista(vistaActual, lista.isNotEmpty())
        }

        viewModel.mesActual.observe(this) {
            if (!viewModel.estaBuscando()) gestionarTitulo(viewModel.gastosVisibles.value?.size ?: 0)
        }

        viewModel.notificarCambioLimites.observe(this) { adapterLista.notifyDataSetChanged() }

        viewModel.listaCategorias.observe(this) { lista ->
            val mapa = lista.associate { it.nombre to it.uriFoto }
            adapterLista.mapaCategorias = mapa
            adapterGastosCategoria.mapaCategorias = mapa
            adapterLista.notifyDataSetChanged()
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

    // --- FUNCIONALIDADES (Cámara, Exportar, Importar) ---

    // CORRECCIÓN: Esta función faltaba y es necesaria para la cámara
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

    private fun manejarExportacionImagen() {
        val lista = viewModel.gastosVisibles.value ?: emptyList()
        if (lista.isEmpty()) { Toast.makeText(this, "Nada que capturar", Toast.LENGTH_SHORT).show(); return }

        val vistas = ExportManager.VistasCaptura(binding.cardResumen, binding.layoutNavegacion, binding.chartGastos, binding.chartCategorias, binding.rvCalendario, binding.layoutVistaCategorias)

        exportManager.procesarCapturaImagen(vistaActual, lista, vistas) { bitmap ->
            if (bitmap != null) {
                // Aquí usamos el helper antiguo para imágenes, que funciona bien
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
            val archivo = if (soloCsv) dataTransferManager.exportarSoloCSV(inicio, fin)
            else dataTransferManager.exportarDatos(incluirFotos, inicio, fin)
            progress.dismiss()

            val mime = if (soloCsv) "text/csv" else if (incluirFotos) "application/zip" else "application/json"

            // CORRECCIÓN: Usamos el helper nuevo para guardar en descargas
            exportManager.mostrarDialogoPostExportacion(archivo, mime) { file ->
                ExportarHelper.guardarArchivoEnDescargas(this@MainActivity, file, mime)
            }
        }
    }

    // --- IMPORTAR ---
    private fun mostrarDialogoModoImportacion(uri: Uri) {
        AlertDialog.Builder(this).setTitle("Importar").setMessage("¿Cómo quieres proceder?")
            .setPositiveButton("MEZCLAR") { _, _ -> confirmarImportacion(uri, false) }
            .setNegativeButton("SUSTITUIR") { _, _ ->
                AlertDialog.Builder(this).setMessage("¿Borrar TODO antes?").setPositiveButton("SÍ") { _, _ -> confirmarImportacion(uri, true) }.setNegativeButton("No", null).show()
            }
            .setNeutralButton("Cancelar", null).show()
    }

    private fun confirmarImportacion(uri: Uri, sustituir: Boolean) {
        val progress = AlertDialog.Builder(this).setMessage("Procesando...").setCancelable(false).show()
        lifecycleScope.launch {
            val res = dataTransferManager.importarDatos(uri, sustituir)
            progress.dismiss()
            if (res.exito) {
                // Cadena de resolución de conflictos: Gastos -> Categorías -> Fin
                if (res.conflictosGastos.isNotEmpty()) {
                    conflictosManager.mostrarDialogoResolucion(res.conflictosGastos) { d, r, du ->
                        lifecycleScope.launch {
                            dataTransferManager.resolverConflictos(d, r, du)
                            procesarConflictosCategorias(res.conflictosCategorias, res.gastosInsertados)
                        }
                    }
                } else {
                    procesarConflictosCategorias(res.conflictosCategorias, res.gastosInsertados)
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

    // --- DIÁLOGOS WRAPPERS (Para mantener el código corto) ---
    private fun mostrarDialogoAgregarGasto() {
        val cats = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()
        uriFotoFinal = null
        val d = dialogManager.mostrarAgregarGasto(cats, null,
            onGuardar = { n, c, de, ca -> viewModel.agregarGasto(n, c, de, uriFotoFinal, ca) },
            onBorrarFoto = { borrarFotoDelDialogo() })
        ivPreviewActual = d.findViewById(R.id.ivPreviewFoto)
    }

    private fun mostrarDialogoEditarGasto(g: Gasto) {
        val cats = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()
        uriFotoFinal = g.uriFoto
        val d = dialogManager.mostrarEditarGasto(g, cats, uriFotoFinal,
            onActualizar = { viewModel.actualizarGasto(it.copy(uriFoto = uriFotoFinal)) },
            onBorrarFoto = { borrarFotoDelDialogo() })
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

    private fun procesarBorradoMultiple() {
        val seleccionados = adapterLista.obtenerGastosSeleccionados()
        if (seleccionados.isEmpty()) return
        AlertDialog.Builder(this).setTitle("Borrar ${seleccionados.size} gastos?")
            .setPositiveButton("Borrar") { _, _ ->
                val backup = seleccionados.toList()
                viewModel.borrarGastosSeleccionados(seleccionados)
                adapterLista.salirModoSeleccion()
                var deshecho = false
                Snackbar.make(binding.root, "Eliminados", 5000).setAction("DESHACER") {
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
                // Forzar refresco
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

    // Helper para listas
    private fun filtrarListaCategorias() {
        val todos = viewModel.gastosVisibles.value ?: emptyList()
        adapterGastosCategoria.submitList(if (categoriaSeleccionada != null) todos.filter { it.categoria == categoriaSeleccionada } else emptyList())
    }

    override fun onBackPressed() {
        if (uiManager.estaBarraSeleccionVisible()) adapterLista.salirModoSeleccion() else super.onBackPressed()
    }

    private fun actualizarVistaFotoDialogo() {
        // ivPreviewActual es una variable que guardamos cuando abrimos el diálogo
        ivPreviewActual?.let { iv ->
            // Cargamos la imagen seleccionada (uriFotoFinal)
            Glide.with(this).load(uriFotoFinal).centerCrop().into(iv)
            // Quitamos el tinte negro por si era un icono
            iv.clearColorFilter()
            // Habilitamos el zoom al pulsar la miniatura
            iv.setOnClickListener { ImageZoomHelper.mostrarImagen(this, uriFotoFinal) }
            // Mostramos el botón de borrar foto (la X roja pequeña)
            (iv.parent as? View)?.findViewById<View>(R.id.btnBorrarFoto)?.visibility = View.VISIBLE
        }
    }

    private fun borrarFotoDelDialogo() {
        // 1. Borramos la variable de datos
        uriFotoFinal = null
        // 2. Actualizamos la UI (importante)
        ivPreviewActual?.let { iv -> iv.setImageDrawable(null) // Quitamos la imagen
            // Ocultamos el botón de borrar (X)
            (iv.parent as? View)?.findViewById<View>(R.id.btnBorrarFoto)?.visibility = View.GONE }
        Toast.makeText(this, "Foto eliminada", Toast.LENGTH_SHORT).show()
    }
}