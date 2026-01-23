package com.example.gestorgastos

import android.Manifest
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import com.github.mikephil.charting.animation.Easing
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: GastoViewModel

    // Helpers y Managers
    private lateinit var chartManager: ChartManager
    private lateinit var dialogManager: DialogManager
    private lateinit var exportManager: ExportManager
    private lateinit var buscadorManager: BuscadorManager

    private lateinit var adapterLista: GastoAdapter
    private lateinit var adapterGastosCategoria: GastoAdapter
    private lateinit var dataTransferManager: DataTransferManager
    private lateinit var conflictosManager: ConflictosManager
    private var adapterCalendario: CalendarioAdapter? = null

    enum class Vista { LISTA, CALENDARIO, GRAFICA, QUESITOS }
    private var vistaActual = Vista.LISTA
    private var categoriaSeleccionada: String? = null

    // Variables para Fotos
    private var uriFotoTemporal: Uri? = null
    private var uriFotoFinal: String? = null
    private var ivPreviewActual: ImageView? = null

    // --- LANZADORES DE CÁMARA/GALERÍA ---
    private val requestCameraLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) abrirCamara() else Toast.makeText(this, "Sin permiso de cámara", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && uriFotoTemporal != null) {
            uriFotoFinal = copiarImagenAInternalStorage(uriFotoTemporal!!)
            actualizarVistaFotoDialogo()
        }
    }

    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uriFotoFinal = copiarImagenAInternalStorage(uri)
            actualizarVistaFotoDialogo()
        }
    }
    // Launcher para seleccionar el archivo al importar (ZIP o JSON)
    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            mostrarDialogoModoImportacion(uri)
        }
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
        chartManager = ChartManager(this, binding.chartGastos, binding.chartCategorias) { categoria ->
            categoriaSeleccionada = categoria
            filtrarListaCategorias()
            binding.tvTituloCategoriaSeleccionada.text = categoria?.let { "Detalles: $it" } ?: "Toca una categoría para ver detalles"
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
    }

    private fun setupVistas() {
        // Adaptador Lista Principal
        adapterLista = GastoAdapter { mostrarDialogoEditarGasto(it) }
        adapterLista.onSelectionChanged = { cantidad ->
            if (cantidad > 0) {
                // Mostrar barra
                binding.layoutBarraSeleccion.visibility = View.VISIBLE
                binding.tvContadorSeleccion.text = "$cantidad seleccionados"

                // Ocultar otras cosas si molestan (opcional)
                binding.fabAgregar.hide()
            } else {
                // Ocultar barra
                binding.layoutBarraSeleccion.visibility = View.GONE
                binding.fabAgregar.show()
            }
        }
        binding.rvGastos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adapterLista
            setupSwipeToDelete { pos ->
                // Truco: Si la barra está visible, no dejamos borrar deslizando
                if (binding.layoutBarraSeleccion.visibility == View.GONE) {
                    mostrarDialogoConfirmacionBorrado(adapterLista.currentList[pos], adapterLista, pos)
                } else {
                    adapterLista.notifyItemChanged(pos) // Restaurar swipe
                }
            }        }

        // Adaptador Categorías
        adapterGastosCategoria = GastoAdapter { mostrarDialogoEditarGasto(it) }
        binding.rvGastosCategoria.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adapterGastosCategoria
            setupSwipeToDelete { pos -> mostrarDialogoConfirmacionBorrado(adapterGastosCategoria.currentList[pos], adapterGastosCategoria, pos) }
        }

        binding.rvCalendario.layoutManager = GridLayoutManager(this, 7)
        chartManager.setupBarChart()
        chartManager.setupPieChart()
    }

    private fun setupBotones() {
        binding.fabAgregar.setOnClickListener { mostrarDialogoAgregarGasto() }

        // Ahora usamos DialogManager para la config
        binding.btnConfig.setOnClickListener {
            dialogManager.mostrarConfiguracion(
                viewModel.limiteAmarillo,
                viewModel.limiteRojo,
                onGuardar = { am, ro -> viewModel.guardarNuevosLimites(am, ro) },
                onCambiarMoneda = { dialogManager.mostrarSelectorMoneda { viewModel.notificarCambioLimites.value = true } }
            )
        }
        // Botón Cerrar (X)
        binding.btnCerrarSeleccion.setOnClickListener {
            adapterLista.salirModoSeleccion()
        }
        // Botón Borrar (Papelera)
        binding.btnBorrarSeleccionados.setOnClickListener {
            val seleccionados = adapterLista.obtenerGastosSeleccionados()
            if (seleccionados.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Borrar Gastos")
                    .setMessage("¿Estás seguro de borrar ${seleccionados.size} gastos?")
                    .setPositiveButton("Borrar") { _, _ ->

                        // A. Guardamos una copia temporal para poder deshacer
                        val copiaSeguridad = seleccionados.toList()

                        // B. Borramos
                        viewModel.borrarGastosSeleccionados(seleccionados)

                        // C. Salimos del modo selección
                        adapterLista.salirModoSeleccion()

                        // D. Mostramos Snackbar con botón DESHACER
                        var deshacerPulsado = false
                        Snackbar.make(
                            binding.root,
                            "${seleccionados.size} gastos eliminados",
                            5000 // 5 segundos de duración
                        )
                            .setAction("DESHACER") {
                                if (!deshacerPulsado) {
                                    deshacerPulsado = true
                                    // Restauramos los datos
                                    viewModel.restaurarGastos(copiaSeguridad)
                                }
                            }
                            .show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
        binding.btnExportar.setOnClickListener {
            exportManager.mostrarMenuPrincipal { accion ->
                when (accion) {
                    ExportManager.Accion.IMAGEN_VISTA -> manejarExportacionImagen()
                    ExportManager.Accion.EXPORTAR_DATOS -> mostrarDialogoSeleccionRango()
                    ExportManager.Accion.IMPORTAR_DATOS -> manejarImportacionDatos()
                }
            }
        }

        binding.btnMesAnterior.setOnClickListener { viewModel.mesAnterior() }
        binding.btnMesSiguiente.setOnClickListener { viewModel.mesSiguiente() }
        binding.btnCategorias.setOnClickListener { startActivity(Intent(this,CategoriasActivity::class.java)) }

        // Menú vistas
        binding.btnCambiarVista.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_vistas, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when(item.itemId) {
                    R.id.menu_vista_lista -> cambiarVista(Vista.LISTA)
                    R.id.menu_vista_calendario -> cambiarVista(Vista.CALENDARIO)
                    R.id.menu_vista_barras -> cambiarVista(Vista.GRAFICA)
                    R.id.menu_vista_categorias -> cambiarVista(Vista.QUESITOS)
                }
                true
            }
            popup.show()
        }

        // BOTÓN BUSCAR AVANZADO
        binding.btnBuscar.setOnClickListener { view ->
            if (viewModel.estaBuscando()) {
                mostrarMenuFiltro(view) // Función nueva abajo
            } else {
                abrirBuscador(null)
            }
        }
    }

    private fun setupObservers() {
        // 1. OBSERVER PRINCIPAL (Sincronizado)
        viewModel.gastosVisibles.observe(this) { lista ->
            // A. Actualizar datos en adaptadores y totales
            adapterLista.submitList(lista)

            val totalCalculado = lista.sumOf { it.cantidad }
            actualizarTotalUI(totalCalculado)

            filtrarListaCategorias()

            val mes = viewModel.mesActual.value ?: java.time.YearMonth.now()
            adapterCalendario = CalendarioAdapter(mes, lista)
            binding.rvCalendario.adapter = adapterCalendario

            chartManager.actualizarBarChart(lista, viewModel.limiteRojo, viewModel.limiteAmarillo)
            chartManager.actualizarPieChart(lista, categoriaSeleccionada)

            // B. Lógica del TÍTULO (Aquí integramos los 3 modos de fecha)

            // Preparamos el nombre del mes base (Ej: "Enero 2026")
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
            val nombreMes = viewModel.mesActual.value?.format(formatter)?.replaceFirstChar { it.uppercase() } ?: ""

            if (viewModel.estaBuscando()) {
                // HAY FILTRO ACTIVO
                val filtro = viewModel.filtroActualValue

                // Decidimos el título según el MODO de fecha elegido
                when (filtro?.modoFecha) {
                    ModoFiltroFecha.TODOS -> {
                        binding.tvMesTitulo.text = "Historial Completo (${lista.size})"
                    }
                    ModoFiltroFecha.RANGO_FECHAS -> {
                        // Formateamos las fechas: "01/02/25 - 15/03/25 (X)"
                        val sdf = java.text.SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                        val ini = filtro.fechaInicioAbs?.let { sdf.format(java.util.Date(it)) } ?: "?"
                        val fin = filtro.fechaFinAbs?.let { sdf.format(java.util.Date(it)) } ?: "?"
                        binding.tvMesTitulo.text = "$ini - $fin (${lista.size})"
                    }
                    else -> {
                        // MODO MES ACTUAL (con o sin días filtrados 1-31)
                        // Muestra "Enero 2026 (X)"
                        binding.tvMesTitulo.text = "$nombreMes (${lista.size})"
                    }
                }

                binding.tvVacio.text = "Sin resultados con este filtro"
                binding.btnBuscar.setImageResource(android.R.drawable.ic_menu_manage) // Icono engranaje

            } else {
                // MODO NORMAL (Sin buscar nada)
                binding.tvMesTitulo.text = nombreMes
                binding.tvVacio.text = "No hay gastos este mes"
                binding.btnBuscar.setImageResource(android.R.drawable.ic_menu_search) // Icono lupa
            }

            // Visibilidad del mensaje "Vacío"
            binding.tvVacio.visibility = if (lista.isEmpty() && vistaActual == Vista.LISTA) View.VISIBLE else View.GONE
        }

        // 2. TÍTULO MES (Solo visual)
        viewModel.mesActual.observe(this) { mes ->
            if (!viewModel.estaBuscando()) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
                binding.tvMesTitulo.text = mes.format(formatter).replaceFirstChar { it.uppercase() }
            }
        }

        // 3. CAMBIO DE LIMITES
        viewModel.notificarCambioLimites.observe(this) {
            val lista = viewModel.gastosVisibles.value ?: emptyList()
            actualizarTotalUI(lista.sumOf { it.cantidad })
            chartManager.actualizarBarChart(lista, viewModel.limiteRojo, viewModel.limiteAmarillo)
            adapterLista.notifyDataSetChanged()
        }

        // 4. CATEGORÍAS
        viewModel.listaCategorias.observe(this) { lista ->
            if (lista.isEmpty()) viewModel.inicializarCategoriasPorDefecto()
            val mapa = lista.associate { it.nombre to it.uriFoto }
            adapterLista.mapaCategorias = mapa
            adapterGastosCategoria.mapaCategorias = mapa
            adapterLista.notifyDataSetChanged()
        }
    }

    // --- FUNCIONES LÓGICA UI ---

    private fun cambiarVista(nuevaVista: Vista) {
        vistaActual = nuevaVista
        binding.rvGastos.visibility = View.GONE
        binding.rvCalendario.visibility = View.GONE
        binding.chartGastos.visibility = View.GONE
        binding.layoutVistaCategorias.visibility = View.GONE
        binding.tvVacio.visibility = View.GONE

        when (vistaActual) {
            Vista.LISTA -> {
                binding.rvGastos.visibility = View.VISIBLE
                if (viewModel.gastosVisibles.value.isNullOrEmpty()) binding.tvVacio.visibility = View.VISIBLE
            }
            Vista.CALENDARIO -> binding.rvCalendario.visibility = View.VISIBLE
            Vista.GRAFICA -> {
                binding.chartGastos.visibility = View.VISIBLE
                binding.chartGastos.animateY(800)
            }
            Vista.QUESITOS -> {
                binding.layoutVistaCategorias.visibility = View.VISIBLE
                binding.chartCategorias.animateY(1200, Easing.EaseOutBounce)
                adapterGastosCategoria.submitList(emptyList()) // Limpiamos selección
                binding.tvTituloCategoriaSeleccionada.text = "Toca una categoría para ver detalles"
                binding.chartCategorias.highlightValues(null)
            }
        }
    }

    // --- LÓGICA BUSCADOR NUEVA ---
    private fun abrirBuscador(filtroPreexistente: FiltroBusqueda?) {
        val categorias = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()
        buscadorManager.mostrarBuscador(categorias, filtroPreexistente) { filtro ->
            viewModel.aplicarFiltro(filtro)
        }
    }

    private fun mostrarMenuFiltro(view: View) {
        val popup = PopupMenu(this, view)
        popup.menu.add(0, 1, 0, "Modificar filtro")
        popup.menu.add(0, 2, 1, "Quitar filtro")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { // Modificar
                    val actual = viewModel.filtroActualValue // Obtenemos del ViewModel
                    abrirBuscador(actual)
                    true
                }
                2 -> { // Quitar
                    viewModel.limpiarFiltro()
                    Toast.makeText(this, "Filtro eliminado", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun filtrarListaCategorias() {
        val todos = viewModel.gastosVisibles.value ?: emptyList()
        if (categoriaSeleccionada != null) {
            adapterGastosCategoria.submitList(todos.filter { it.categoria == categoriaSeleccionada })
        } else {
            adapterGastosCategoria.submitList(emptyList())
        }
    }

    private fun actualizarTotalUI(total: Double) {
        binding.tvTotalMes.text = Formato.formatearMoneda(total)
        val colorRes = viewModel.obtenerColorAlerta(total)
        binding.layoutAlerta.setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }

    // --- DELEGACIÓN DE DIÁLOGOS AL MANAGER ---

    private fun mostrarDialogoAgregarGasto() {
        val categorias = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()
        uriFotoFinal = null

        val dialog = dialogManager.mostrarAgregarGasto(
            categorias, null,
            onGuardar = { nombre, cant, desc, cat ->
                viewModel.agregarGasto(nombre, cant, desc, uriFotoFinal, cat)
                // Efecto Flash (Opcional, si tienes Extensiones.kt)
                val totalNuevo = (viewModel.gastosVisibles.value?.sumOf { it.cantidad } ?: 0.0) + cant
                binding.viewFlashBorde.flashEffect(viewModel.obtenerColorAlerta(totalNuevo))
            },
            onBorrarFoto = { uriFotoFinal = null }
        )
        // Obtenemos la ref de la imagen para actualizarla luego
        ivPreviewActual = dialog.findViewById(R.id.ivPreviewFoto)
    }

    private fun mostrarDialogoEditarGasto(gasto: Gasto) {
        val categorias = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()
        uriFotoFinal = gasto.uriFoto

        val dialog = dialogManager.mostrarEditarGasto(
            gasto, categorias, uriFotoFinal,
            onActualizar = { editado ->
                viewModel.actualizarGasto(editado.copy(uriFoto = uriFotoFinal))
                Toast.makeText(this, "Actualizado", Toast.LENGTH_SHORT).show()
            },
            onBorrarFoto = { uriFotoFinal = null }
        )
        ivPreviewActual = dialog.findViewById(R.id.ivPreviewFoto)
    }

    private fun mostrarDialogoConfirmacionBorrado(gasto: Gasto, adapter: GastoAdapter, pos: Int) {
        dialogManager.mostrarConfirmacionBorrado(
            gasto,
            onConfirmar = {
                viewModel.borrarGasto(gasto)
                var deshacer = false
                Snackbar.make(binding.root, "Borrado", Snackbar.LENGTH_LONG).setAction("Deshacer") {
                    if (!deshacer) {
                        deshacer = true
                        viewModel.agregarGasto(gasto.nombre, gasto.cantidad, gasto.descripcion, gasto.uriFoto, gasto.categoria, gasto.fecha)
                    }
                }.show()
            },
            onCancelar = { adapter.notifyItemChanged(pos) } // Restaurar swipe
        )
    }

    // --- FOTOS (Se mantienen aquí porque necesitan los Launchers) ---
    private fun checkCameraPermissionAndOpen() {
        ejecutarConPermisoCamara(
            onGranted = { abrirCamara() },
            onDenied = { requestCameraLauncher.launch(Manifest.permission.CAMERA) }
        )
    }

    private fun abrirCamara() {
        try {
            val tempFile = File.createTempFile("foto_", ".jpg", externalCacheDir)
            uriFotoTemporal = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
            takePictureLauncher.launch(uriFotoTemporal)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun actualizarVistaFotoDialogo() {
        ivPreviewActual?.let { iv ->
            Glide.with(this).load(uriFotoFinal).centerCrop().into(iv)
            iv.setPadding(0, 0, 0, 0)
            iv.clearColorFilter()
            iv.setOnClickListener { ImageZoomHelper.mostrarImagen(this, uriFotoFinal) }
            (iv.parent as? View)?.findViewById<View>(R.id.btnBorrarFoto)?.visibility = View.VISIBLE
        }
    }

    // --- LÓGICA DE EXPORTAR IMAGEN (Antigua, adaptada) ---
    private fun manejarExportacionImagen() {
        val lista = viewModel.gastosVisibles.value ?: emptyList()
        if (lista.isEmpty()) {
            Toast.makeText(this, "No hay nada que capturar", Toast.LENGTH_SHORT).show()
            return
        }

        val vistas = ExportManager.VistasCaptura(binding.cardResumen, binding.layoutNavegacion, binding.chartGastos, binding.chartCategorias, binding.rvCalendario, binding.layoutVistaCategorias)

        exportManager.procesarCapturaImagen(vistaActual, lista, vistas) { bitmap ->
            if (bitmap != null) {
                // Preguntamos qué hacer con la imagen
                AlertDialog.Builder(this)
                    .setTitle("Imagen Generada")
                    .setItems(arrayOf("Guardar en Galería", "Compartir")) { _, which ->
                        if (which == 0) ExportarHelper.guardarEnDispositivo(this, bitmap, null, true)
                        else ExportarHelper.compartir(this, bitmap, null, true)
                    }
                    .show()
            }
        }
    }

    // --- LÓGICA DE EXPORTAR DATOS (BACKUP) ---
    // Antes se llamaba manejarExportacionDatos, ahora recibe el rango ya decidido
    private fun mostrarDialogoFormato(inicio: Long, fin: Long) {
        val opciones = arrayOf(
            "📦 Completa con Fotos (ZIP)",
            "📄 Solo Datos (JSON)",
            "📊 Hoja de Cálculo (CSV)"
        )

        AlertDialog.Builder(this)
            .setTitle("2. Elige el formato") // Título actualizado
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> realizarBackup(incluirFotos = true, inicio, fin)
                    1 -> realizarBackup(incluirFotos = false, inicio, fin)
                    2 -> realizarExportacionSoloCSV(inicio, fin)
                }
            }
            .setNegativeButton("Atrás") { _, _ -> mostrarDialogoSeleccionRango() } // Volver atrás
            .show()
    }

    private fun realizarBackup(incluirFotos: Boolean, inicio: Long, fin: Long) {
        val mensaje = if (incluirFotos) "Generando ZIP con fotos..." else "Generando JSON..."
        val progress = AlertDialog.Builder(this)
            .setMessage(mensaje)
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            val archivo = dataTransferManager.exportarDatos(incluirFotos, inicio, fin)
            progress.dismiss()
            // Llamamos al helper común
            mostrarDialogoPostExportacion(archivo, if (incluirFotos) "application/zip" else "application/json")
        }
    }

    private fun realizarExportacionSoloCSV(inicio: Long, fin: Long) {
        val progress = AlertDialog.Builder(this)
            .setMessage("Generando CSV...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            val archivo = dataTransferManager.exportarSoloCSV(inicio, fin)
            progress.dismiss()
            // Llamamos al helper común
            mostrarDialogoPostExportacion(archivo, "text/csv")
        }
    }

    private fun mostrarDialogoPostExportacion(archivo: java.io.File?, mimeType: String) {
        if (archivo != null) {
            AlertDialog.Builder(this)
                .setTitle("Archivo Generado")
                .setMessage("Nombre: ${archivo.name}")
                .setPositiveButton("Compartir / Enviar") { _, _ ->
                    val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivo)
                    val intent = Intent(android.content.Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(intent, "Enviar a..."))
                }
                .setNegativeButton("Guardar en Descargas") { _, _ ->
                    copiarADescargas(archivo, mimeType)
                }
                .show()
        } else {
            Toast.makeText(this, "Error al generar el archivo", Toast.LENGTH_SHORT).show()
        }
    }

    // Función rápida para mover el archivo de cache a Descargas
    private fun copiarADescargas(archivo: File, mime: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // --- OPCIÓN A: ANDROID 10+ (API 29+) ---
                // Usamos MediaStore como tenías, que no requiere permisos de escritura en el Manifest
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, archivo.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GestorGastos")
                }

                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { output ->
                        archivo.inputStream().use { input -> input.copyTo(output) }
                    }
                    Toast.makeText(this, "Guardado en Descargas (Carpeta GestorGastos)", Toast.LENGTH_LONG).show()
                }
            } else {
                // --- OPCIÓN B: ANDROID 9 Y MENOR (API < 29) ---
                // Usamos el sistema de archivos clásico java.io.File
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS)
                val carpetaApp = File(downloadsDir, "GestorGastos")

                if (!carpetaApp.exists()) carpetaApp.mkdirs()

                val archivoDestino = File(carpetaApp, archivo.name)

                // Copiamos los bytes
                archivo.inputStream().use { input ->
                    java.io.FileOutputStream(archivoDestino).use { output ->
                        input.copyTo(output)
                    }
                }

                // Avisamos al sistema para que el archivo aparezca si conectas el móvil al PC
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(archivoDestino.absolutePath),
                    arrayOf(mime),
                    null
                )

                Toast.makeText(this, "Guardado en Descargas/GestorGastos", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- LÓGICA DE IMPORTAR DATOS (RESTAURAR) ---
    private fun manejarImportacionDatos() {
        // Lanzamos el selector de archivos (Filtrar por * o zip/json)
        importFileLauncher.launch("*/*")
    }

    private fun mostrarDialogoModoImportacion(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("Modo de Importación")
            .setMessage("¿Cómo quieres importar estos datos?")
            .setPositiveButton("AÑADIR (Mezclar)") { _, _ ->
                confirmarImportacion(uri, sustituir = false)
            }
            .setNegativeButton("SUSTITUIR (Borrar todo)") { _, _ ->
                // Doble confirmación para sustituir
                AlertDialog.Builder(this)
                    .setTitle("⚠️ ¡Cuidado!")
                    .setMessage("Esta acción borrará TODOS tus gastos y categorías actuales antes de importar.\n\n¿Estás seguro?")
                    .setPositiveButton("SÍ, BORRAR TODO") { _, _ ->
                        confirmarImportacion(uri, sustituir = true)
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun confirmarImportacion(uri: Uri, sustituir: Boolean) {
        val progress = AlertDialog.Builder(this)
            .setMessage("Analizando archivo...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            // Importamos (esto guarda los NO conflictivos y devuelve los conflictivos)
            val resultado = dataTransferManager.importarDatos(uri, sustituir)
            progress.dismiss()

            if (resultado.exito) {

                // CASO A: Importación limpia o Sustitución total
                if (resultado.conflictos.isEmpty()) {
                    finalizarImportacionExito(resultado.gastosInsertados)
                }
                // CASO B: Hay duplicados para resolver
                else {
                    conflictosManager.mostrarDialogoResolucion(resultado.conflictos) { descartar, reemplazar, duplicar ->
                        // Cuando el usuario pulsa un botón en el diálogo, volvemos aquí
                        lifecycleScope.launch {
                            dataTransferManager.resolverConflictos(descartar, reemplazar, duplicar)
                            // Refrescamos UI (aunque sea parcialmente)
                            viewModel.limpiarFiltro()
                        }
                    }
                }
            } else {
                Toast.makeText(this@MainActivity, "Error al importar.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun finalizarImportacionExito(cantidad: Int) {
        viewModel.limpiarFiltro()
        viewModel.inicializarCategoriasPorDefecto()
        binding.viewFlashBorde.flashEffect(R.color.alerta_verde)

        val msg = if (cantidad > 0) "Importados $cantidad gastos nuevos." else "Importación completada."
        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
    }

    private fun mostrarDialogoSeleccionRango() {
        val opciones = arrayOf(
            "📅 Mes Actual (${viewModel.mesActual.value?.month?.getDisplayName(java.time.format.TextStyle.FULL, Locale("es", "ES"))})",
            "📆 Elegir Fechas (Personalizado)",
            "🗄️ Todo el Historial (Base de Datos completa)"
        )

        AlertDialog.Builder(this)
            .setTitle("1. Selecciona el Rango")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> {
                        // Opción: Mes Actual
                        val mes = viewModel.mesActual.value ?: java.time.YearMonth.now()
                        val inicio = mes.atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val fin = mes.atEndOfMonth().atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

                        mostrarDialogoFormato(inicio, fin) // Pasamos al siguiente paso
                    }
                    1 -> {
                        // Opción: Personalizado -> Abrimos DatePicker
                        mostrarSelectorRangoFechas()
                    }
                    2 -> {
                        // Opción: Todo
                        mostrarDialogoFormato(0L, Long.MAX_VALUE)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarSelectorRangoFechas() {
        val calendario = java.util.Calendar.getInstance()

        // 1. Elegir FECHA INICIO
        DatePickerDialog(
            this,
            { _, year1, month1, day1 ->
                val inicioCal = Calendar.getInstance()
                inicioCal.set(year1, month1, day1, 0, 0, 0)
                val inicioMs = inicioCal.timeInMillis

                // 2. Elegir FECHA FIN (Al aceptar la primera)
                DatePickerDialog(this, { _, year2, month2, day2 ->
                    val finCal = Calendar.getInstance()
                    finCal.set(year2, month2, day2, 23, 59, 59)
                    val finMs = finCal.timeInMillis

                    if (inicioMs > finMs) {
                        Toast.makeText(
                            this,
                            "La fecha inicio no puede ser mayor al fin",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        mostrarDialogoFormato(inicioMs, finMs) // Pasamos al siguiente paso
                    }

                }, year1, month1, day1).apply {
                    setTitle("Fecha Fin")
                    show()
                }

            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle("Fecha Inicio")
            show()
        }
    }
}