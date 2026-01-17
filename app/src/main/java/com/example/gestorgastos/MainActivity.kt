package com.example.gestorgastos

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupMenu
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
import com.example.gestorgastos.databinding.DialogAgregarGastoBinding
import com.example.gestorgastos.databinding.DialogConfiguracionBinding
import com.example.gestorgastos.ui.CalendarioAdapter
import com.example.gestorgastos.ui.Formato
import com.example.gestorgastos.ui.GastoAdapter
import com.example.gestorgastos.ui.GastoViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: GastoViewModel

    // CAMBIO: Renombramos 'adapter' a 'adapterLista' para no confundir con el del calendario
    private lateinit var adapterLista: GastoAdapter

    // CAMBIO: Enumeración para controlar qué vista estamos viendo (Lista, Calendario o Gráfica)
    enum class Vista { LISTA, CALENDARIO, GRAFICA }
    private var vistaActual = Vista.LISTA

    // Variables para manejar la foto
    private var uriFotoTemporal: android.net.Uri? = null
    private var uriFotoFinal: String? = null
    private var ivPreviewActual: android.widget.ImageView? = null

    // Variable para el diálogo de carga al exportar
    private var progressDialog: AlertDialog? = null

    // --- LANZADORES DE PERMISOS Y FOTOS ---
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) abrirCamara()
        else Toast.makeText(this, "Permiso necesario para fotos", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && uriFotoTemporal != null) {
            uriFotoFinal = uriFotoTemporal.toString()
            ivPreviewActual?.let { Glide.with(this).load(uriFotoFinal).centerCrop().into(it) }
        }
    }

    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uriFotoFinal = uri.toString()
            ivPreviewActual?.let { Glide.with(this).load(uriFotoFinal).centerCrop().into(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[GastoViewModel::class.java]

        // 1. Configurar las 3 vistas (Lista, Calendario, Gráfica)
        setupVistas()

        // 2. Configurar todos los botones
        setupBotones()

        // 3. Configurar los observadores de datos (LiveData)
        setupObservers()
    }

    // --- CONFIGURACIÓN INICIAL DE VISTAS ---
    private fun setupVistas() {
        // A. Configurar RecyclerView de la LISTA
        adapterLista = GastoAdapter { gasto -> mostrarDialogoEditarGasto(gasto) }
        binding.rvGastos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adapterLista
        }
        setupSwipeToDelete() // Activar deslizar para borrar

        // B. Configurar RecyclerView del CALENDARIO (Rejilla de 7 columnas)
        binding.rvCalendario.layoutManager = GridLayoutManager(this, 7)

        // C. Configurar GRÁFICA (Estilo visual)
        setupEstiloGrafica()
    }

    // --- CONFIGURACIÓN DE BOTONES ---
    private fun setupBotones() {
        // Agregar Gasto
        binding.fabAgregar.setOnClickListener { mostrarDialogoAgregarGasto() }

        // Configuración (Engranaje)
        binding.btnConfig.setOnClickListener { mostrarDialogoConfiguracion() }

        // Exportar (Compartir)
        binding.btnExportar.setOnClickListener { mostrarMenuFormatoExportacion() }

        // Navegación de meses (Flechas)
        binding.btnMesAnterior.setOnClickListener { viewModel.mesAnterior() }
        binding.btnMesSiguiente.setOnClickListener { viewModel.mesSiguiente() }

        // CAMBIO: BOTÓN "OJO" PARA CAMBIAR VISTA (Lista / Calendario / Gráfica)
        binding.btnCambiarVista.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 0, 0, "Ver Lista")
            popup.menu.add(0, 1, 0, "Ver Calendario")
            popup.menu.add(0, 2, 0, "Ver Gráfica")

            popup.setOnMenuItemClickListener { item ->
                when(item.itemId) {
                    0 -> cambiarVista(Vista.LISTA)
                    1 -> cambiarVista(Vista.CALENDARIO)
                    2 -> cambiarVista(Vista.GRAFICA)
                }
                true
            }
            popup.show()
        }
    }

    // --- LÓGICA PARA CAMBIAR ENTRE VISTAS ---
    private fun cambiarVista(nuevaVista: Vista) {
        vistaActual = nuevaVista
        // Primero ocultamos todo
        binding.rvGastos.visibility = View.GONE
        binding.rvCalendario.visibility = View.GONE
        binding.chartGastos.visibility = View.GONE
        binding.tvVacio.visibility = View.GONE

        // Mostramos solo la elegida
        when(vistaActual) {
            Vista.LISTA -> {
                binding.rvGastos.visibility = View.VISIBLE
                // Si la lista está vacía, mostramos el aviso
                if (adapterLista.currentList.isEmpty()) binding.tvVacio.visibility = View.VISIBLE
            }
            Vista.CALENDARIO -> binding.rvCalendario.visibility = View.VISIBLE
            Vista.GRAFICA -> binding.chartGastos.visibility = View.VISIBLE
        }
    }

    // --- OBSERVADORES (REACTIVIDAD) ---
    private fun setupObservers() {

        // 1. OBSERVAR CAMBIO DE MES (Arreglado el bug del título)
        viewModel.mesActual.observe(this) { mes ->
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
            // Ponemos la primera letra en mayúscula (enero -> Enero)
            binding.tvMesTitulo.text = mes.format(formatter).replaceFirstChar { it.uppercase() }
        }

        // 2. OBSERVAR LISTA DE GASTOS (Actualiza las 3 vistas a la vez)
        viewModel.gastosDelMes.observe(this) { listaGastos ->
            // A. Actualizar Lista normal
            adapterLista.submitList(listaGastos)

            // B. Actualizar Calendario
            val mesActual = viewModel.mesActual.value ?: java.time.YearMonth.now()
            binding.rvCalendario.adapter = CalendarioAdapter(mesActual, listaGastos)

            // C. Actualizar Gráfica
            actualizarDatosGrafica(listaGastos)

            // D. Gestionar mensaje "Vacío" (Solo si estamos en modo lista)
            if (listaGastos.isEmpty() && vistaActual == Vista.LISTA) {
                binding.tvVacio.visibility = View.VISIBLE
            } else if (vistaActual == Vista.LISTA) {
                binding.tvVacio.visibility = View.GONE
            }
        }

        // 3. OBSERVAR TOTAL DEL MES
        viewModel.sumaTotalDelMes.observe(this) { suma ->
            actualizarTotalYColor(suma ?: 0.0)
        }

        // 4. OBSERVAR CAMBIO DE LÍMITES O MONEDA
        viewModel.notificarCambioLimites.observe(this) {
            val total = viewModel.sumaTotalDelMes.value ?: 0.0
            actualizarTotalYColor(total)
        }
    }

    private fun actualizarTotalYColor(total: Double) {
        binding.tvTotalMes.text = Formato.formatearMoneda(total)
        val colorRes = viewModel.obtenerColorAlerta(total)
        binding.layoutAlerta.setBackgroundColor(ContextCompat.getColor(this, colorRes))

        // Animación latido del texto
        binding.tvTotalMes.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
            binding.tvTotalMes.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }

    // --- LÓGICA GRÁFICA (MPAndroidChart) ---
    private fun setupEstiloGrafica() {
        binding.chartGastos.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)
            animateY(1000)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            axisRight.isEnabled = false
        }
    }

    private fun actualizarDatosGrafica(listaGastos: List<Gasto>) {
        if (listaGastos.isEmpty()) {
            binding.chartGastos.clear()
            return
        }

        // Agrupar gastos por día
        val gastosPorDia = mutableMapOf<Int, Double>()
        for (gasto in listaGastos) {
            val fecha = java.time.Instant.ofEpochMilli(gasto.fecha).atZone(ZoneId.systemDefault()).toLocalDate()
            gastosPorDia[fecha.dayOfMonth] = (gastosPorDia[fecha.dayOfMonth] ?: 0.0) + gasto.cantidad
        }

        // Crear barras para todos los días del mes
        val entradas = ArrayList<BarEntry>()
        val mesActual = viewModel.mesActual.value ?: java.time.YearMonth.now()
        for (i in 1..mesActual.lengthOfMonth()) {
            entradas.add(BarEntry(i.toFloat(), gastosPorDia[i]?.toFloat() ?: 0f))
        }

        val dataSet = BarDataSet(entradas, "Gastos")
        dataSet.color = ContextCompat.getColor(this, com.example.gestorgastos.R.color.alerta_verde)
        dataSet.valueTextSize = 10f
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String = if (value > 0) value.toInt().toString() else ""
        }

        binding.chartGastos.data = BarData(dataSet)
        binding.chartGastos.invalidate()
    }

    // --- DIÁLOGOS (Agregar, Editar, Configurar) ---

    private fun mostrarDialogoAgregarGasto() {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogAgregarGastoBinding.inflate(LayoutInflater.from(this))

        uriFotoFinal = null
        ivPreviewActual = dialogBinding.ivPreviewFoto

        // Watcher para formato europeo mientras escribes
        dialogBinding.etCantidad.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etCantidad))

        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }

        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Guardar") { _, _ ->
            val nombre = dialogBinding.etNombre.text.toString()
            // Limpiamos formato para guardar numero limpio (1.000,00 -> 1000.00)
            val cantidadStr = dialogBinding.etCantidad.text.toString().replace(".", "").replace(",", ".")
            val descripcion = dialogBinding.etDescripcion.text.toString()

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cantidadNueva = cantidadStr.toDoubleOrNull() ?: 0.0
                viewModel.agregarGasto(nombre, cantidadNueva, descripcion, uriFotoFinal)

                // CAMBIO: Calculamos el total FUTURO para que el flash sea del color correcto
                val totalActual = viewModel.sumaTotalDelMes.value ?: 0.0
                hacerFlashBorde(totalActual + cantidadNueva)

            } else {
                Toast.makeText(this, "Faltan datos", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun mostrarDialogoEditarGasto(gasto: Gasto) {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogAgregarGastoBinding.inflate(LayoutInflater.from(this))

        dialogBinding.tvTituloDialogo.text = "Editar Gasto"
        dialogBinding.etNombre.setText(gasto.nombre)
        dialogBinding.etCantidad.setText(gasto.cantidad.toString().replace(".", ","))
        dialogBinding.etCantidad.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etCantidad))
        dialogBinding.etDescripcion.setText(gasto.descripcion)
        uriFotoFinal = gasto.uriFoto
        ivPreviewActual = dialogBinding.ivPreviewFoto

        if (uriFotoFinal != null) Glide.with(this).load(uriFotoFinal).centerCrop().into(dialogBinding.ivPreviewFoto)

        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }

        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Actualizar") { _, _ ->
            val nombre = dialogBinding.etNombre.text.toString()
            val cantidadStr = dialogBinding.etCantidad.text.toString().replace(".", "").replace(",", ".")
            val descripcion = dialogBinding.etDescripcion.text.toString()

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cantidad = cantidadStr.toDoubleOrNull() ?: 0.0
                viewModel.actualizarGasto(gasto.copy(nombre = nombre, cantidad = cantidad, descripcion = descripcion, uriFoto = uriFotoFinal))
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun mostrarDialogoConfiguracion() {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogConfiguracionBinding.inflate(LayoutInflater.from(this))

        dialogBinding.etAmarillo.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etAmarillo))
        dialogBinding.etRojo.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etRojo))

        dialogBinding.etAmarillo.setText(viewModel.limiteAmarillo.toString().replace(".", ","))
        dialogBinding.etRojo.setText(viewModel.limiteRojo.toString().replace(".", ","))

        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Guardar") { _, _ ->
            val amarilloStr = dialogBinding.etAmarillo.text.toString().replace(".", "").replace(",", ".")
            val rojoStr = dialogBinding.etRojo.text.toString().replace(".", "").replace(",", ".")

            if (amarilloStr.isNotEmpty() && rojoStr.isNotEmpty()) {
                val amarillo = amarilloStr.toDoubleOrNull() ?: 0.0
                val rojo = rojoStr.toDoubleOrNull() ?: 0.0
                if (amarillo >= rojo) return@setPositiveButton
                viewModel.guardarNuevosLimites(amarillo, rojo)
            }
        }
        builder.setNeutralButton("Cambiar Moneda") { _, _ ->
            val monedas = arrayOf("Euro (€)", "Dólar ($)", "Libra (£)")
            AlertDialog.Builder(this).setTitle("Elige divisa").setItems(monedas) { _, w ->
                when(w) {
                    0 -> Formato.cambiarDivisa("EUR")
                    1 -> Formato.cambiarDivisa("USD")
                    2 -> Formato.cambiarDivisa("GBP")
                }
                adapterLista.notifyDataSetChanged()
                viewModel.notificarCambioLimites.value = true
            }.show()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    // --- EFECTOS VISUALES (Flash) ---
    private fun hacerFlashBorde(totalSimulado: Double) {
        val colorResId = viewModel.obtenerColorAlerta(totalSimulado)
        val color = ContextCompat.getColor(this, colorResId)
        val numFlashes = when (colorResId) {
            com.example.gestorgastos.R.color.alerta_rojo -> 3
            com.example.gestorgastos.R.color.alerta_amarillo -> 2
            else -> 1
        }

        binding.viewFlashBorde.setBackgroundColor(color)
        binding.viewFlashBorde.visibility = View.VISIBLE
        binding.viewFlashBorde.alpha = 0f

        fun ejecutarAnimacionFlash(vecesRestantes: Int) {
            if (vecesRestantes <= 0) {
                binding.viewFlashBorde.visibility = View.GONE
                binding.viewFlashBorde.background = null
                return
            }
            binding.viewFlashBorde.animate().alpha(0.5f).setDuration(150).withEndAction {
                binding.viewFlashBorde.animate().alpha(0f).setDuration(150).withEndAction {
                    ejecutarAnimacionFlash(vecesRestantes - 1)
                }.start()
            }.start()
        }
        ejecutarAnimacionFlash(numFlashes)
    }

    // --- EXPORTAR (Con corrutinas para las fotos) ---
    private fun mostrarMenuFormatoExportacion() {
        val opciones = arrayOf("Hoja de Cálculo (CSV)", "Imagen Larga (JPG)")
        AlertDialog.Builder(this).setTitle("Formato").setItems(opciones) { _, w -> mostrarMenuAccionExportacion(w == 1) }.show()
    }

    private fun mostrarMenuAccionExportacion(esImagen: Boolean) {
        val opciones = arrayOf("Guardar en Dispositivo", "Compartir")
        AlertDialog.Builder(this).setTitle("Acción").setItems(opciones) { _, w -> procesarExportacion(esImagen, w == 0) }.show()
    }

    private fun procesarExportacion(esImagen: Boolean, guardarEnDispositivo: Boolean) {
        val listaActual = viewModel.gastosDelMes.value ?: emptyList()
        if (listaActual.isEmpty()) { Toast.makeText(this, "Nada que exportar", Toast.LENGTH_SHORT).show(); return }

        if (esImagen) {
            progressDialog = AlertDialog.Builder(this).setMessage("Generando imagen...").setCancelable(false).show()

            // Corrutina para cargar fotos en segundo plano
            lifecycleScope.launch(Dispatchers.IO) {
                val mapaBitmaps = mutableMapOf<Long, android.graphics.Bitmap>()
                for (gasto in listaActual) {
                    if (gasto.uriFoto != null) {
                        try {
                            val bitmap = Glide.with(this@MainActivity).asBitmap().load(gasto.uriFoto).centerCrop().submit(100, 100).get()
                            mapaBitmaps[gasto.id] = bitmap
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                }
                // Volver al hilo principal para dibujar
                withContext(Dispatchers.Main) {
                    progressDialog?.dismiss()
                    val bitmapFinal = com.example.gestorgastos.ui.ExportarHelper.generarImagenLarga(this@MainActivity, binding.cardResumen, listaActual, mapaBitmaps)
                    if (guardarEnDispositivo) com.example.gestorgastos.ui.ExportarHelper.guardarEnDispositivo(this@MainActivity, bitmapFinal, null, true)
                    else com.example.gestorgastos.ui.ExportarHelper.compartir(this@MainActivity, bitmapFinal, null, true)
                }
            }
        } else {
            val csvContent = com.example.gestorgastos.ui.ExportarHelper.generarTextoCSV(listaActual)
            if (guardarEnDispositivo) com.example.gestorgastos.ui.ExportarHelper.guardarEnDispositivo(this, null, csvContent, false)
            else com.example.gestorgastos.ui.ExportarHelper.compartir(this, null, csvContent, false)
        }
    }

    // --- UTILS (Swipe, Cámara) ---
    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val gastoABorrar = adapterLista.currentList[position]
                mostrarDialogoConfirmacionBorrado(gastoABorrar, position)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvGastos)
    }

    private fun mostrarDialogoConfirmacionBorrado(gasto: Gasto, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("¿Borrar?")
            .setMessage("Se eliminará '${gasto.nombre}'")
            .setPositiveButton("Eliminar") { _, _ -> viewModel.borrarGasto(gasto) }
            .setNegativeButton("Cancelar") { d, _ -> adapterLista.notifyItemChanged(position); d.dismiss() }
            .setCancelable(false).show()
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) abrirCamara()
        else requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    private fun abrirCamara() {
        try {
            val tempFile = File.createTempFile("foto_", ".jpg", externalCacheDir)
            uriFotoTemporal = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
            takePictureLauncher.launch(uriFotoTemporal)
        } catch (e: Exception) { e.printStackTrace() }
    }
}