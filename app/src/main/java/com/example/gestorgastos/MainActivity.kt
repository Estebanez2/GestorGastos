package com.example.gestorgastos

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.ActivityMainBinding
import com.example.gestorgastos.databinding.DialogConfiguracionBinding
import com.example.gestorgastos.ui.* import com.github.mikephil.charting.animation.Easing
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: GastoViewModel

    private lateinit var chartManager: ChartManager
    private lateinit var dialogManager: DialogManager
    private lateinit var exportManager: ExportManager

    private lateinit var adapterLista: GastoAdapter
    private lateinit var adapterGastosCategoria: GastoAdapter
    private lateinit var buscadorManager: BuscadorManager
    private var adapterCalendario: CalendarioAdapter? = null

    enum class Vista { LISTA, CALENDARIO, GRAFICA, QUESITOS }
    private var vistaActual = Vista.LISTA
    private var categoriaSeleccionada: String? = null

    private var uriFotoTemporal: android.net.Uri? = null
    private var uriFotoFinal: String? = null
    private var ivPreviewActual: ImageView? = null

    // --- LANZADORES ---
    private val requestCameraLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) abrirCamara() else Toast.makeText(this, "Permiso necesario", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && uriFotoTemporal != null) {
            // Convertimos la foto temporal de la cámara a una permanente nuestra
            uriFotoFinal = copiarImagenAInternalStorage(uriFotoTemporal!!)
            actualizarVistaFotoDialogo()
        }
    }

    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // IMPORTANTE: Copiamos la imagen para tener persistencia real
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
            binding.tvTituloCategoriaSeleccionada.text = if (categoria != null) "Detalles: $categoria" else "Toca una categoría para ver detalles"
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
        adapterLista = GastoAdapter { mostrarDialogoEditarGasto(it) }
        binding.rvGastos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adapterLista
            setupSwipeToDelete { pos -> mostrarDialogoConfirmacionBorrado(adapterLista.currentList[pos], adapterLista, pos) }
        }

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
        binding.btnConfig.setOnClickListener { mostrarDialogoConfiguracion() }

        binding.btnExportar.setOnClickListener {
            val vistas = ExportManager.VistasCaptura(binding.cardResumen, binding.layoutNavegacion, binding.chartGastos, binding.chartCategorias, binding.rvCalendario, binding.layoutVistaCategorias)
            exportManager.iniciarProcesoExportacion(vistaActual, viewModel.gastosVisibles.value ?: emptyList(), vistas)
        }

        binding.btnMesAnterior.setOnClickListener { viewModel.mesAnterior() }
        binding.btnMesSiguiente.setOnClickListener { viewModel.mesSiguiente() }
        binding.btnCategorias.setOnClickListener { startActivity(android.content.Intent(this, com.example.gestorgastos.ui.CategoriasActivity::class.java)) }

        binding.btnCambiarVista.setOnClickListener { view ->
            val popup = androidx.appcompat.widget.PopupMenu(this, view)
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
        binding.btnBuscar.setOnClickListener {
            if (viewModel.estaBuscando()) {
                // Si ya hay búsqueda, el botón sirve para LIMPIAR
                viewModel.limpiarFiltro()
                binding.btnBuscar.setImageResource(android.R.drawable.ic_menu_search) // Volver a icono lupa
                Toast.makeText(this, "Filtro eliminado", Toast.LENGTH_SHORT).show()
            } else {
                // Si no, ABRIMOS EL BUSCADOR
                val categorias = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()

                buscadorManager.mostrarBuscador(categorias) { filtro ->
                    viewModel.aplicarFiltro(filtro)
                    binding.btnBuscar.setImageResource(android.R.drawable.ic_menu_close_clear_cancel) // Cambiar a icono X
                }
            }
        }
    }

    private fun setupObservers() {

        // 1. EL OBSERVER PRINCIPAL (Hace todo el trabajo sucio)
        viewModel.gastosVisibles.observe(this) { lista ->

            // A. Actualizamos la lista visual
            adapterLista.submitList(lista)

            // B. CALCULO DEL TOTAL (AQUÍ ESTÁ LA CLAVE DEL ARREGLO)
            // Calculamos el total directamente de la lista que acaba de llegar (la nueva).
            // No usamos 'adapter.currentList' ni otro observer separado.
            val totalCalculado = lista.sumOf { it.cantidad }
            actualizarTotalUI(totalCalculado)

            // C. Filtros de Categorías y Gráficas
            filtrarListaCategorias()

            // Calendario
            val mes = viewModel.mesActual.value ?: java.time.YearMonth.now()
            adapterCalendario = CalendarioAdapter(mes, lista)
            binding.rvCalendario.adapter = adapterCalendario

            // Gráficos (ChartManager)
            chartManager.actualizarBarChart(lista, viewModel.limiteRojo, viewModel.limiteAmarillo)
            chartManager.actualizarPieChart(lista, categoriaSeleccionada)

            // D. Lógica visual (Título, Iconos, Vista vacía)
            if (viewModel.estaBuscando()) {
                binding.tvMesTitulo.text = "Resultados Búsqueda (${lista.size})"
                binding.tvVacio.text = "No se encontraron gastos con ese filtro"
                binding.btnBuscar.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                binding.tvVacio.visibility = if (lista.isEmpty() && vistaActual == Vista.LISTA) View.VISIBLE else View.GONE
            } else {
                // Modo Normal: Título del Mes
                val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
                val textoMes = viewModel.mesActual.value?.format(formatter)?.replaceFirstChar { it.uppercase() }
                binding.tvMesTitulo.text = textoMes

                binding.tvVacio.text = "No hay gastos este mes"
                binding.btnBuscar.setImageResource(android.R.drawable.ic_menu_search)

                // Solo mostramos 'Vacio' si la lista es vacía Y estamos en vista de lista
                binding.tvVacio.visibility = if (lista.isEmpty() && vistaActual == Vista.LISTA) View.VISIBLE else View.GONE
            }
        }

        // 2. SOLO OBSERVAMOS MES ACTUAL PARA ACTUALIZAR TÍTULO SI CAMBIA RÁPIDO
        // (Opcional, pero ayuda a que el título cambie instantáneamente al pulsar la flecha)
        viewModel.mesActual.observe(this) { mes ->
            if (!viewModel.estaBuscando()) {
                val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
                binding.tvMesTitulo.text = mes.format(formatter).replaceFirstChar { it.uppercase() }
            }
        }

        // 3. CAMBIO DE LÍMITES (Configuración)
        viewModel.notificarCambioLimites.observe(this) {
            // Aquí SÍ podemos usar gastosVisibles.value porque la lista no ha cambiado, solo los colores
            val listaActual = viewModel.gastosVisibles.value ?: emptyList()
            val total = listaActual.sumOf { it.cantidad }

            actualizarTotalUI(total) // Recalcula el color del semáforo

            // Refrescamos gráficas y listas para que pinten los nuevos colores
            chartManager.actualizarBarChart(listaActual, viewModel.limiteRojo, viewModel.limiteAmarillo)
            adapterLista.notifyDataSetChanged()
        }

        // 4. CATEGORÍAS (Fotos e iconos)
        viewModel.listaCategorias.observe(this) { lista ->
            if (lista.isEmpty()) viewModel.inicializarCategoriasPorDefecto()
            val mapa = lista.associate { it.nombre to it.uriFoto }
            adapterLista.mapaCategorias = mapa
            adapterGastosCategoria.mapaCategorias = mapa
            adapterLista.notifyDataSetChanged()
        }
    }

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
                if (viewModel.gastosVisibles.value.isNullOrEmpty()) {
                    binding.tvVacio.visibility = View.VISIBLE
                }
            }
            Vista.CALENDARIO -> binding.rvCalendario.visibility = View.VISIBLE
            Vista.GRAFICA -> {
                binding.chartGastos.visibility = View.VISIBLE
                binding.chartGastos.animateY(800)
            }
            Vista.QUESITOS -> {
                binding.layoutVistaCategorias.visibility = View.VISIBLE
                binding.chartCategorias.animateY(1200, Easing.EaseOutBounce)
                adapterGastosCategoria.submitList(emptyList())
                binding.tvTituloCategoriaSeleccionada.text = "Toca una categoría para ver detalles"
                binding.chartCategorias.highlightValues(null)
            }
        }
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
        binding.tvTotalMes.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
            binding.tvTotalMes.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }

    // --- DIALOGOS ---

    private fun mostrarDialogoAgregarGasto() {
        val categorias = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()
        uriFotoFinal = null // Reset

        val dialog = dialogManager.mostrarAgregarGasto(
            categorias, null, // Pasamos null porque la foto la gestiona el Main
            onGuardar = { nombre, cant, desc, cat ->
                // Usamos 'uriFotoFinal' que es la variable local del Main
                viewModel.agregarGasto(nombre, cant, desc, uriFotoFinal, cat)
                val total = (viewModel.sumaTotalDelMes.value ?: 0.0) + cant
                binding.viewFlashBorde.flashEffect(viewModel.obtenerColorAlerta(total))
            },
            onBorrarFoto = { uriFotoFinal = null }
        )
        // Recuperamos la vista de imagen del diálogo para actualizarla si volvemos de cámara
        ivPreviewActual = dialog.findViewById(R.id.ivPreviewFoto)
    }

    private fun mostrarDialogoEditarGasto(gasto: Gasto) {
        val categorias = viewModel.listaCategorias.value?.map { it.nombre } ?: emptyList()
        uriFotoFinal = gasto.uriFoto

        val dialog = dialogManager.mostrarEditarGasto(
            gasto, categorias, uriFotoFinal,
            onActualizar = { gastoEditado ->
                // Actualizamos usando uriFotoFinal local por si cambió en el proceso
                viewModel.actualizarGasto(gastoEditado.copy(uriFoto = uriFotoFinal))
                Toast.makeText(this, "Actualizado", Toast.LENGTH_SHORT).show()
            },
            onBorrarFoto = { uriFotoFinal = null }
        )
        // findViewById ahora funciona porque DialogManager retorna el AlertDialog
        ivPreviewActual = dialog.findViewById(R.id.ivPreviewFoto)
    }

    private fun mostrarDialogoConfiguracion() {
        val builder = AlertDialog.Builder(this)
        val dBinding = DialogConfiguracionBinding.inflate(layoutInflater)
        dBinding.etAmarillo.setText(viewModel.limiteAmarillo.toString().replace(".", ","))
        dBinding.etRojo.setText(viewModel.limiteRojo.toString().replace(".", ","))
        dBinding.etAmarillo.addTextChangedListener(EuroTextWatcher(dBinding.etAmarillo))
        dBinding.etRojo.addTextChangedListener(EuroTextWatcher(dBinding.etRojo))

        builder.setView(dBinding.root)
            .setPositiveButton("Guardar") { _, _ ->
                val am = dBinding.etAmarillo.text.toString().replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                val ro = dBinding.etRojo.text.toString().replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                if (am < ro) viewModel.guardarNuevosLimites(am, ro)
            }
            .setNeutralButton("Cambiar Moneda") { _, _ -> mostrarDialogoMoneda() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoMoneda() {
        val monedas = arrayOf("Euro (€)", "Dólar ($)", "Libra (£)")
        AlertDialog.Builder(this).setTitle("Elige divisa").setItems(monedas) { _, i ->
            when(i) { 0 -> "EUR"; 1 -> "USD"; else -> "GBP" }.let { Formato.cambiarDivisa(it) }
            viewModel.notificarCambioLimites.value = true
            Toast.makeText(this, "Moneda cambiada", Toast.LENGTH_SHORT).show()
        }.show()
    }

    private fun mostrarDialogoConfirmacionBorrado(gasto: Gasto, adapter: GastoAdapter, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Borrar Gasto")
            .setMessage("¿Estás seguro de borrar '${gasto.nombre}'?")
            .setPositiveButton("Borrar") { _, _ ->
                viewModel.borrarGasto(gasto)
                var deshacerPulsado = false
                Snackbar.make(binding.root, "Borrado", Snackbar.LENGTH_LONG).setAction("Deshacer") {
                    if (!deshacerPulsado) {
                        deshacerPulsado = true
                        viewModel.agregarGasto(gasto.nombre, gasto.cantidad, gasto.descripcion, gasto.uriFoto, gasto.categoria, gasto.fecha)
                    }
                }.show()
            }
            .setNegativeButton("Cancelar") { _, _ -> adapter.notifyItemChanged(position) }
            .setOnCancelListener { adapter.notifyItemChanged(position) }
            .show()
    }

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