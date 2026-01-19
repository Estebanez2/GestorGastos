package com.example.gestorgastos

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.ActivityMainBinding
import com.example.gestorgastos.databinding.DialogConfiguracionBinding
import com.example.gestorgastos.ui.* import com.github.mikephil.charting.animation.Easing
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            val vistas = ExportManager.VistasCaptura(binding.cardResumen, binding.layoutNavegacion, binding.chartGastos, binding.chartCategorias, binding.rvCalendario)
            exportManager.iniciarProcesoExportacion(vistaActual, viewModel.gastosDelMes.value ?: emptyList(), vistas)
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
    }

    private fun setupObservers() {
        viewModel.mesActual.observe(this) { mes ->
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
            binding.tvMesTitulo.text = mes.format(formatter).replaceFirstChar { it.uppercase() }
        }

        viewModel.gastosDelMes.observe(this) { lista ->
            adapterLista.submitList(lista)
            filtrarListaCategorias()

            val mes = viewModel.mesActual.value ?: java.time.YearMonth.now()
            adapterCalendario = CalendarioAdapter(mes, lista)
            binding.rvCalendario.adapter = adapterCalendario

            chartManager.actualizarBarChart(lista, viewModel.limiteRojo, viewModel.limiteAmarillo)
            chartManager.actualizarPieChart(lista)

            binding.tvVacio.visibility = if (lista.isEmpty() && vistaActual == Vista.LISTA) View.VISIBLE else View.GONE
        }

        viewModel.sumaTotalDelMes.observe(this) { total -> actualizarTotalUI(total ?: 0.0) }
        viewModel.notificarCambioLimites.observe(this) {
            actualizarTotalUI(viewModel.sumaTotalDelMes.value ?: 0.0)
            adapterLista.notifyDataSetChanged()
            chartManager.actualizarPieChart(viewModel.gastosDelMes.value ?: emptyList())
        }

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
                if (viewModel.gastosDelMes.value.isNullOrEmpty()) binding.tvVacio.visibility = View.VISIBLE
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
        val todos = viewModel.gastosDelMes.value ?: emptyList()
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
            categorias, null,
            onGuardar = { nombre, cant, desc, cat ->
                // USAMOS uriFotoFinal LOCAL, que es la correcta tras hacer la foto
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
                        viewModel.agregarGasto(gasto.nombre, gasto.cantidad, gasto.descripcion, gasto.uriFoto, gasto.categoria)
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

    // --- COPIAR IMAGEN (USANDO FILEPROVIDER) ---
    private fun copiarImagenAInternalStorage(uriExterna: android.net.Uri): String {
        return try {
            val archivoDestino = File(filesDir, "img_${System.currentTimeMillis()}.jpg")
            val inputStream = contentResolver.openInputStream(uriExterna)
            val outputStream = java.io.FileOutputStream(archivoDestino)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            // Usamos FileProvider porque configuramos el XML correctamente
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivoDestino).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            uriExterna.toString()
        }
    }
}