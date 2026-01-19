package com.example.gestorgastos

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.ActivityMainBinding
import com.example.gestorgastos.ui.* // Importamos todos los helpers
import com.github.mikephil.charting.animation.Easing
import com.google.android.material.snackbar.Snackbar
import java.io.File
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
    private var adapterCalendario: CalendarioAdapter? = null

    enum class Vista { LISTA, CALENDARIO, GRAFICA, QUESITOS }
    private var vistaActual = Vista.LISTA
    private var categoriaSeleccionada: String? = null

    // Variables para Fotos
    private var uriFotoTemporal: android.net.Uri? = null
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
    }

    private fun setupVistas() {
        // Adaptador Lista Principal
        adapterLista = GastoAdapter { mostrarDialogoEditarGasto(it) }
        binding.rvGastos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adapterLista
            setupSwipeToDelete { pos -> mostrarDialogoConfirmacionBorrado(adapterLista.currentList[pos], adapterLista, pos) }
        }

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

        binding.btnExportar.setOnClickListener {
            val vistas = ExportManager.VistasCaptura(binding.cardResumen, binding.layoutNavegacion, binding.chartGastos, binding.chartCategorias, binding.rvCalendario, binding.layoutVistaCategorias)
            exportManager.iniciarProcesoExportacion(vistaActual, viewModel.gastosVisibles.value ?: emptyList(), vistas)
        }

        binding.btnMesAnterior.setOnClickListener { viewModel.mesAnterior() }
        binding.btnMesSiguiente.setOnClickListener { viewModel.mesSiguiente() }
        binding.btnCategorias.setOnClickListener { startActivity(android.content.Intent(this, CategoriasActivity::class.java)) }

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
            adapterLista.submitList(lista)

            val totalCalculado = lista.sumOf { it.cantidad }
            actualizarTotalUI(totalCalculado)

            filtrarListaCategorias()

            val mes = viewModel.mesActual.value ?: java.time.YearMonth.now()
            adapterCalendario = CalendarioAdapter(mes, lista)
            binding.rvCalendario.adapter = adapterCalendario

            chartManager.actualizarBarChart(lista, viewModel.limiteRojo, viewModel.limiteAmarillo)
            chartManager.actualizarPieChart(lista, categoriaSeleccionada)
            
            // 1. Preparamos el nombre del mes siempre (Ej: "Enero 2026")
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
            val nombreMes = viewModel.mesActual.value?.format(formatter)?.replaceFirstChar { it.uppercase() } ?: ""

            if (viewModel.estaBuscando()) {
                // Hay filtro activo
                val filtro = viewModel.filtroActualValue

                if (filtro?.buscarEnTodo == true) {
                    // Si busca en todo el historial, el mes da igual
                    binding.tvMesTitulo.text = "Resultados Globales (${lista.size})"
                } else {
                    // Si busca DENTRO del mes, mostramos: "Enero 2026 (5)"
                    binding.tvMesTitulo.text = "$nombreMes (${lista.size})"
                }

                binding.tvVacio.text = "No hay resultados con este filtro"
                binding.btnBuscar.setImageResource(android.R.drawable.ic_menu_manage) // Engranaje/Filtro

            } else {
                // Modo normal, solo el mes
                binding.tvMesTitulo.text = nombreMes

                binding.tvVacio.text = "No hay gastos este mes"
                binding.btnBuscar.setImageResource(android.R.drawable.ic_menu_search) // Lupa
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
    private fun abrirBuscador(filtroPreexistente: com.example.gestorgastos.data.FiltroBusqueda?) {
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
            onDenied = { requestCameraLauncher.launch(android.Manifest.permission.CAMERA) }
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
}