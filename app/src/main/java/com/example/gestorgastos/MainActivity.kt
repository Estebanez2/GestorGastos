package com.example.gestorgastos

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import com.github.mikephil.charting.formatter.ValueFormatter
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
import com.example.gestorgastos.ui.EuroTextWatcher
import com.example.gestorgastos.ui.ExportarHelper
import com.example.gestorgastos.ui.Formato
import com.example.gestorgastos.ui.GastoAdapter
import com.example.gestorgastos.ui.GastoViewModel
import com.example.gestorgastos.ui.ImageZoomHelper
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: GastoViewModel

    private lateinit var adapterLista: GastoAdapter

    private var adapterCalendario: CalendarioAdapter? = null

    enum class Vista { LISTA, CALENDARIO, GRAFICA, QUESITOS }
    private var vistaActual = Vista.LISTA

    private var uriFotoTemporal: android.net.Uri? = null
    private var uriFotoFinal: String? = null
    private var ivPreviewActual: android.widget.ImageView? = null
    private var progressDialog: AlertDialog? = null
    // Variable para guardar las categorías actuales (Nombre -> Foto)
    private var mapaCategoriasActual: Map<String, String?> = emptyMap()
    // Lista solo de nombres para el Spinner
    private var listaNombresCategorias: List<String> = emptyList()

    // --- LANZADORES ---
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) abrirCamara() else Toast.makeText(this, "Permiso necesario", Toast.LENGTH_SHORT).show()
    }
    // LANZADOR CÁMARA
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && uriFotoTemporal != null) {
            uriFotoFinal = uriFotoTemporal.toString()
            ivPreviewActual?.let {
                // 1. Cargar la foto
                Glide.with(this).load(uriFotoFinal).centerCrop().into(it)

                // 2. AJUSTES VISUALES
                it.setPadding(0, 0, 0, 0)
                it.clearColorFilter()
                it.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                it.setOnClickListener {
                    ImageZoomHelper.mostrarImagen(this, uriFotoFinal)
                }

                // 3. Mostrar la X roja
                // Truco para buscar el botón hermano en el layout
                val parent = it.parent as? android.view.ViewGroup
                parent?.findViewById<View>(R.id.btnBorrarFoto)?.visibility = View.VISIBLE
            }
        }
    }

    // LANZADOR GALERÍA
    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uriFotoFinal = uri.toString()
            ivPreviewActual?.let {
                // 1. Cargar la foto
                Glide.with(this).load(uriFotoFinal).centerCrop().into(it)

                // 2. AJUSTES VISUALES
                it.setPadding(0, 0, 0, 0)
                it.clearColorFilter()
                it.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                it.setOnClickListener {
                    ImageZoomHelper.mostrarImagen(this, uriFotoFinal)
                }

                // 3. Mostrar la X roja
                val parent = it.parent as? android.view.ViewGroup
                parent?.findViewById<View>(R.id.btnBorrarFoto)?.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[GastoViewModel::class.java]

        setupVistas()
        setupBotones()
        setupObservers()
        setupPieChart()
    }

    private fun setupVistas() {
        // Lista
        adapterLista = GastoAdapter { gasto -> mostrarDialogoEditarGasto(gasto) }
        binding.rvGastos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = adapterLista
        }
        setupSwipeToDelete()

        // Calendario
        binding.rvCalendario.layoutManager = GridLayoutManager(this, 7)

        // Gráfica
        setupEstiloGrafica()
    }

    private fun setupBotones() {
        binding.fabAgregar.setOnClickListener { mostrarDialogoAgregarGasto() }
        binding.btnConfig.setOnClickListener { mostrarDialogoConfiguracion() }
        binding.btnExportar.setOnClickListener { mostrarMenuFormatoExportacion() }
        binding.btnMesAnterior.setOnClickListener { viewModel.mesAnterior() }
        binding.btnMesSiguiente.setOnClickListener { viewModel.mesSiguiente() }
        binding.btnCategorias.setOnClickListener {
            val intent = android.content.Intent(this, com.example.gestorgastos.ui.CategoriasActivity::class.java)
            startActivity(intent)
        }

        binding.btnCambiarVista.setOnClickListener { view ->
            // Usamos androidx.appcompat.widget.PopupMenu para mayor compatibilidad
            val popup = androidx.appcompat.widget.PopupMenu(this, view)

            // 1. INFLAMOS EL XML (Aquí está la magia)
            popup.menuInflater.inflate(R.menu.menu_vistas, popup.menu)

            // 2. GESTIONAMOS LOS CLICKS USANDO LOS IDs DEL XML
            popup.setOnMenuItemClickListener { item ->
                when(item.itemId) {
                    R.id.menu_vista_lista -> {
                        cambiarVista(Vista.LISTA)
                        true
                    }
                    R.id.menu_vista_calendario -> {
                        cambiarVista(Vista.CALENDARIO)
                        true
                    }
                    R.id.menu_vista_barras -> {
                        cambiarVista(Vista.GRAFICA)
                        true
                    }
                    R.id.menu_vista_categorias -> {
                        cambiarVista(Vista.QUESITOS)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun cambiarVista(nuevaVista: Vista) {
        vistaActual = nuevaVista

        // Ocultar todo
        binding.rvGastos.visibility = View.GONE
        binding.rvCalendario.visibility = View.GONE
        binding.chartGastos.visibility = View.GONE
        binding.chartCategorias.visibility = View.GONE
        binding.tvVacio.visibility = View.GONE

        when (vistaActual) {
            Vista.LISTA -> {
                if (viewModel.gastosDelMes.value.isNullOrEmpty()) binding.tvVacio.visibility = View.VISIBLE
                else binding.rvGastos.visibility = View.VISIBLE
            }
            Vista.CALENDARIO -> binding.rvCalendario.visibility = View.VISIBLE
            Vista.GRAFICA -> {
                binding.chartGastos.visibility = View.VISIBLE
                binding.chartGastos.animateY(800)
            }
            Vista.QUESITOS -> {
                binding.chartCategorias.visibility = View.VISIBLE
                binding.chartCategorias.animateY(800)
            }
        }
    }

    private fun setupObservers() {
        viewModel.mesActual.observe(this) { mes ->
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))
            binding.tvMesTitulo.text = mes.format(formatter).replaceFirstChar { it.uppercase() }
        }

        viewModel.gastosDelMes.observe(this) { listaGastos ->
            // A. Lista
            adapterLista.submitList(listaGastos)

            // B. Calendario (Guardamos la referencia en la variable global)
            val mesActual = viewModel.mesActual.value ?: java.time.YearMonth.now()
            adapterCalendario = CalendarioAdapter(mesActual, listaGastos)
            binding.rvCalendario.adapter = adapterCalendario

            // C. Gráfica
            actualizarDatosGrafica(listaGastos)
            actualizarPieChart(listaGastos)

            if (listaGastos.isEmpty() && vistaActual == Vista.LISTA) binding.tvVacio.visibility = View.VISIBLE
            else if (vistaActual == Vista.LISTA) binding.tvVacio.visibility = View.GONE
        }

        viewModel.sumaTotalDelMes.observe(this) { suma -> actualizarTotalYColor(suma ?: 0.0) }
        viewModel.notificarCambioLimites.observe(this) {
            val total = viewModel.sumaTotalDelMes.value ?: 0.0
            actualizarTotalYColor(total)
        }

        // OBSERVER CATEGORÍAS
        viewModel.listaCategorias.observe(this) { lista ->
            // 1. Si está vacía (primera vez), creamos las básicas
            if (lista.isEmpty()) {
                viewModel.inicializarCategoriasPorDefecto()
            }

            // 2. Preparamos los datos
            // Creamos un mapa: "Comida" -> "content://foto..."
            mapaCategoriasActual = lista.associate { it.nombre to it.uriFoto }
            // Creamos lista de nombres para los Spinners: ["Comida", "Casa", ...]
            listaNombresCategorias = lista.map { it.nombre }

            // 3. Actualizamos el adaptador de la lista principal
            adapterLista.mapaCategorias = mapaCategoriasActual
            adapterLista.notifyDataSetChanged()
        }
    }

    private fun actualizarTotalYColor(total: Double) {
        binding.tvTotalMes.text = Formato.formatearMoneda(total)
        val colorRes = viewModel.obtenerColorAlerta(total)
        binding.layoutAlerta.setBackgroundColor(ContextCompat.getColor(this, colorRes))
        binding.tvTotalMes.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
            binding.tvTotalMes.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }

    // --- GRÁFICA MEJORADA ---
    private fun setupEstiloGrafica() {
        binding.chartGastos.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)
            animateY(1500) // Animación más suave

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.textColor = ContextCompat.getColor(context, android.R.color.darker_gray)

            axisRight.isEnabled = false
            axisLeft.setDrawGridLines(true) // Líneas horizontales sutiles
            axisLeft.gridColor = ContextCompat.getColor(context, com.example.gestorgastos.R.color.gris_fondo) // Grid muy suave
            axisLeft.axisMinimum = 0f // Empezar siempre en 0
        }
    }

    private fun actualizarDatosGrafica(listaGastos: List<Gasto>) {
        if (listaGastos.isEmpty()) {
            binding.chartGastos.clear()
            return
        }

        val gastosPorDia = mutableMapOf<Int, Double>()
        for (gasto in listaGastos) {
            val fecha = java.time.Instant.ofEpochMilli(gasto.fecha).atZone(ZoneId.systemDefault()).toLocalDate()
            gastosPorDia[fecha.dayOfMonth] = (gastosPorDia[fecha.dayOfMonth] ?: 0.0) + gasto.cantidad
        }

        val entradas = ArrayList<BarEntry>()
        val colores = ArrayList<Int>() // Lista de colores dinámica

        // Colores de referencia
        val colorVerde = ContextCompat.getColor(this, com.example.gestorgastos.R.color.alerta_verde)
        val colorAmarillo = ContextCompat.getColor(this, com.example.gestorgastos.R.color.alerta_amarillo)
        val colorRojo = ContextCompat.getColor(this, com.example.gestorgastos.R.color.alerta_rojo)

        val mesActual = viewModel.mesActual.value ?: java.time.YearMonth.now()

        // Umbral diario aproximado (Ej: si límite mensual rojo es 1000, un día con >100 es "peligroso")
        // Esto es una estimación simple para colorear barras: LímiteMensual / 30 días * Factor
        val umbralDiarioRojo = viewModel.limiteRojo / 20.0
        val umbralDiarioAmarillo = viewModel.limiteAmarillo / 20.0

        for (i in 1..mesActual.lengthOfMonth()) {
            val total = gastosPorDia[i]?.toFloat() ?: 0f
            entradas.add(BarEntry(i.toFloat(), total))

            // Asignar color según la altura de la barra
            when {
                total >= umbralDiarioRojo -> colores.add(colorRojo)
                total >= umbralDiarioAmarillo -> colores.add(colorAmarillo)
                else -> colores.add(colorVerde)
            }
        }

        val dataSet = BarDataSet(entradas, "Gastos")
        dataSet.colors = colores // Aplicamos los colores dinámicos
        dataSet.valueTextSize = 11f
        dataSet.valueTextColor = ContextCompat.getColor(this, android.R.color.black)

        // Formateador mejorado: Muestra el símbolo de moneda en la gráfica
        dataSet.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value > 0) Formato.formatearMoneda(value.toDouble()) else ""
            }
        }

        val data = BarData(dataSet)
        data.barWidth = 0.6f

        binding.chartGastos.data = data
        binding.chartGastos.invalidate()
    }

    // --- DIÁLOGOS Y LÓGICA ---

    // Copia aquí las funciones privadas de diálogos (Agregar, Editar, Configurar, Flash, Exportar, etc.)
    // RECUERDA: La única que cambia un poco es la de mostrarDialogoMoneda para arreglar el bug.
    // Te pongo aquí la versión arreglada de mostrarDialogoMoneda:

    private fun mostrarDialogoMoneda() {
        val monedas = arrayOf("Euro (€)", "Dólar ($)", "Libra (£)")
        AlertDialog.Builder(this)
            .setTitle("Elige tu divisa")
            .setItems(monedas) { _, which ->
                when(which) {
                    0 -> Formato.cambiarDivisa("EUR")
                    1 -> Formato.cambiarDivisa("USD")
                    2 -> Formato.cambiarDivisa("GBP")
                }

                refrescarVistasPorCambioConfiguracion()


                Toast.makeText(this, "Moneda cambiada", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun mostrarDialogoAgregarGasto() {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogAgregarGastoBinding.inflate(LayoutInflater.from(this))
        val adapterSpinner = android.widget.ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listaNombresCategorias
        )
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerCategoria.adapter = adapterSpinner
        uriFotoFinal = null
        ivPreviewActual = dialogBinding.ivPreviewFoto
        dialogBinding.etCantidad.addTextChangedListener(EuroTextWatcher(dialogBinding.etCantidad))
        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }
        dialogBinding.btnBorrarFoto.setOnClickListener {
            uriFotoFinal = null

            // Restaurar icono de cámara
            dialogBinding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera)
            dialogBinding.ivPreviewFoto.scaleType = ImageView.ScaleType.CENTER_INSIDE

            // Restaurar padding (usando dimens)
            val padding = resources.getDimensionPixelSize(R.dimen.preview_padding_small)
            dialogBinding.ivPreviewFoto.setPadding(padding, padding, padding, padding)

            // Restaurar el tinte GRIS (Importante)
            dialogBinding.ivPreviewFoto.setColorFilter(Color.parseColor("#888888"))
            dialogBinding.ivPreviewFoto.setOnClickListener(null)
            dialogBinding.btnBorrarFoto.visibility = View.GONE
        }
        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Guardar") { _, _ ->
            val nombre = dialogBinding.etNombre.text.toString()
            val cantidadStr = dialogBinding.etCantidad.text.toString().replace(".", "").replace(",", ".")
            val descripcion = dialogBinding.etDescripcion.text.toString()
            val categoriaSeleccionada = if (listaNombresCategorias.isNotEmpty()) {
                dialogBinding.spinnerCategoria.selectedItem.toString()
            } else "Otros"

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cantidadNueva = cantidadStr.toDoubleOrNull() ?: 0.0
                viewModel.agregarGasto(nombre, cantidadNueva, descripcion, uriFotoFinal, categoriaSeleccionada)

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

        // 1. CONFIGURAR SPINNER (Igual que en agregar)
        val adapterSpinner = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listaNombresCategorias
        )
        adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerCategoria.adapter = adapterSpinner

        // 2. PRE-SELECCIONAR LA CATEGORÍA QUE YA TIENE EL GASTO
        // Buscamos en qué posición de la lista está (ej: "Casa" es la posición 2)
        val posicionActual = listaNombresCategorias.indexOf(gasto.categoria)
        if (posicionActual >= 0) {
            dialogBinding.spinnerCategoria.setSelection(posicionActual)
        }

        // Rellenar resto de datos
        dialogBinding.etNombre.setText(gasto.nombre)
        dialogBinding.etCantidad.setText(gasto.cantidad.toString().replace(".", ","))
        dialogBinding.etCantidad.addTextChangedListener(EuroTextWatcher(dialogBinding.etCantidad))
        dialogBinding.etDescripcion.setText(gasto.descripcion)
        dialogBinding.btnBorrarFoto.setOnClickListener {
            uriFotoFinal = null
            dialogBinding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera)
            dialogBinding.ivPreviewFoto.setPadding(60,60,60,60) // Restaurar padding visual
            dialogBinding.ivPreviewFoto.setOnClickListener(null)
            dialogBinding.btnBorrarFoto.visibility = android.view.View.GONE
        }


        uriFotoFinal = gasto.uriFoto
        ivPreviewActual = dialogBinding.ivPreviewFoto

        // ... Cargar foto existente ...
        if (uriFotoFinal != null) {
            Glide.with(this).load(uriFotoFinal).centerCrop().into(dialogBinding.ivPreviewFoto)
            dialogBinding.ivPreviewFoto.setPadding(0,0,0,0)
            dialogBinding.btnBorrarFoto.visibility = View.VISIBLE
            dialogBinding.ivPreviewFoto.setOnClickListener { ImageZoomHelper.mostrarImagen(this, uriFotoFinal) }
        } else {
            dialogBinding.btnBorrarFoto.visibility = View.GONE
        }

        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }

        builder.setView(dialogBinding.root)

        builder.setPositiveButton("Actualizar") { _, _ ->
            val nombre = dialogBinding.etNombre.text.toString()
            val cantidadStr = dialogBinding.etCantidad.text.toString().replace(".", "").replace(",", ".")
            val descripcion = dialogBinding.etDescripcion.text.toString()

            // 3. LEER LA CATEGORÍA SELECCIONADA (Puede haber cambiado)
            val nuevaCategoria = if (listaNombresCategorias.isNotEmpty()) {
                dialogBinding.spinnerCategoria.selectedItem.toString()
            } else gasto.categoria

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cantidad = cantidadStr.toDoubleOrNull() ?: 0.0

                // Creamos una copia del gasto con los datos nuevos
                val gastoEditado = gasto.copy(
                    nombre = nombre,
                    cantidad = cantidad,
                    descripcion = descripcion,
                    uriFoto = uriFotoFinal,
                    categoria = nuevaCategoria
                )

                viewModel.actualizarGasto(gastoEditado)
                Toast.makeText(this, "Actualizado", Toast.LENGTH_SHORT).show()
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
        builder.setNeutralButton("Cambiar Moneda") { _, _ -> mostrarDialogoMoneda() }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

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

    private fun mostrarMenuFormatoExportacion() {
        val opciones = arrayOf("Imagen Larga (JPG)", "Hoja de Cálculo (CSV)")
        AlertDialog.Builder(this).setTitle("1. Elige el formato").setItems(opciones) { _, w -> mostrarMenuAccionExportacion(w == 0) }.show()
    }
    private fun mostrarMenuAccionExportacion(esImagen: Boolean) {
        val opciones = arrayOf("Guardar en Dispositivo", "Compartir")
        AlertDialog.Builder(this).setTitle("2. ¿Qué hacer?").setItems(opciones) { _, w -> procesarExportacion(esImagen, w == 0) }.show()
    }
    private fun procesarExportacion(esImagen: Boolean, guardarEnDispositivo: Boolean) {
        val listaActual = viewModel.gastosDelMes.value ?: emptyList()
        if (listaActual.isEmpty()) {
            Toast.makeText(this, "No hay gastos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        if (esImagen) {
            when (vistaActual) {
                Vista.LISTA -> {
                    // MODO LISTA: Imagen Larga con Cabecera y Título
                    progressDialog = AlertDialog.Builder(this).setMessage("Generando imagen lista...").setCancelable(false).show()
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
                        withContext(Dispatchers.Main) {
                            progressDialog?.dismiss()

                            // CAMBIO: Ahora pasamos binding.layoutNavegacion también
                            val bitmapFinal = ExportarHelper.generarImagenLarga(
                                this@MainActivity,
                                binding.cardResumen,
                                binding.layoutNavegacion,
                                listaActual,
                                mapaBitmaps
                            )

                            if (guardarEnDispositivo) ExportarHelper.guardarEnDispositivo(this@MainActivity, bitmapFinal, null, true)
                            else ExportarHelper.compartir(this@MainActivity, bitmapFinal, null, true)
                        }
                    }
                }

                Vista.GRAFICA -> {
                    // MODO GRÁFICA
                    // 1. Capturamos solo la gráfica
                    val bitmapGrafica = binding.chartGastos.chartBitmap
                    // 2. Le pegamos encima la cabecera y el título
                    val bitmapFinal = ExportarHelper.unirVistasEnBitmap(
                        binding.cardResumen,
                        binding.layoutNavegacion,
                        bitmapGrafica
                    )

                    finalizarExportacion(bitmapFinal, guardarEnDispositivo)
                }

                Vista.CALENDARIO -> {
                    // MODO CALENDARIO
                    // 1. Capturamos la rejilla
                    val bitmapCalendario = ExportarHelper.capturarVista(binding.rvCalendario)
                    // 2. Le pegamos encima la cabecera y el título
                    val bitmapFinal = ExportarHelper.unirVistasEnBitmap(
                        binding.cardResumen,
                        binding.layoutNavegacion,
                        bitmapCalendario
                    )

                    finalizarExportacion(bitmapFinal, guardarEnDispositivo)
                }

                Vista.QUESITOS -> {
                    // MODO QUESITOS (PIE CHART)
                    // 1. Capturamos el gráfico de quesitos
                    val bitmapQuesito = binding.chartCategorias.chartBitmap

                    // 2. Lo unimos con la cabecera
                    val bitmapFinal = ExportarHelper.unirVistasEnBitmap(
                        binding.cardResumen,
                        binding.layoutNavegacion,
                        bitmapQuesito
                    )

                    // 3. Guardamos/Compartimos
                    finalizarExportacion(bitmapFinal, guardarEnDispositivo)
                }
            }
        } else {
            // EXPORTAR CSV (Texto)
            val csvContent = ExportarHelper.generarTextoCSV(listaActual)
            if (guardarEnDispositivo) ExportarHelper.guardarEnDispositivo(this, null, csvContent, false)
            else ExportarHelper.compartir(this, null, csvContent, false)
        }
    }

    private fun finalizarExportacion(bitmap: android.graphics.Bitmap, guardar: Boolean) {
        if (guardar) ExportarHelper.guardarEnDispositivo(this, bitmap, null, true)
        else ExportarHelper.compartir(this, bitmap, null, true)
    }

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
        AlertDialog.Builder(this).setTitle("¿Borrar?").setMessage("Eliminar '${gasto.nombre}'").setPositiveButton("Eliminar") { _, _ -> viewModel.borrarGasto(gasto) }.setNegativeButton("Cancelar") { d, _ -> adapterLista.notifyItemChanged(position); d.dismiss() }.setCancelable(false).show()
    }
    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) abrirCamara() else requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }
    private fun abrirCamara() {
        try {
            val tempFile = File.createTempFile("foto_", ".jpg", externalCacheDir)
            uriFotoTemporal = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
            takePictureLauncher.launch(uriFotoTemporal)
        } catch (e: Exception) { e.printStackTrace() }
    }

    // CONFIGURACIÓN VISUAL (ESTILO DONUT)
    private fun setupPieChart() {
        val chart = binding.chartCategorias

        // 1. Limpieza visual
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setHoleColor(Color.TRANSPARENT)

        // 2. Estilo Donut
        chart.isDrawHoleEnabled = true
        chart.holeRadius = 45f
        chart.transparentCircleRadius = 50f

        // 3. Texto del Centro
        chart.setCenterText("Gastos\nPor Categoría")
        chart.setCenterTextSize(15f)
        chart.setCenterTextColor(Color.GRAY)

        // 4. MÁRGENES GRANDES (Para que quepan las flechas)
        // Damos 50 de margen a los lados. El círculo se hará más pequeño automáticamente.
        chart.setExtraOffsets(40f, 10f, 40f, 10f)

        // 5. ACTIVAR ETIQUETAS DE TEXTO (Nombre Categoría)
        chart.setDrawEntryLabels(true)
        chart.setEntryLabelColor(Color.BLACK) // Importante: Negro para que se vea fuera
        chart.setEntryLabelTextSize(11f)

        chart.isRotationEnabled = true
        chart.dragDecelerationFrictionCoef = 0.9f

        chart.animateY(1400, Easing.EaseOutBounce)

        // LISTENER DE SELECCIÓN
        chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {

            // Cuando tocas un quesito
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                if (e == null) return

                // e.label es el nombre (ej. "Comida")
                // e.y es la cantidad (ej. 150.0)
                val nombre = (e as PieEntry).label
                val cantidad = Formato.formatearMoneda(e.y.toDouble())

                // Cambiamos el texto del agujero central con letra un poco más grande
                chart.centerText = "$nombre\n$cantidad"
                chart.setCenterTextSize(18f)
                chart.setCenterTextColor(Color.BLACK)
            }

            // Cuando tocas fuera (deseleccionar)
            override fun onNothingSelected() {
                // Volvemos al título original
                chart.centerText = "Gastos\nPor Categoría"
                chart.setCenterTextSize(16f)
                chart.setCenterTextColor(Color.GRAY)
            }
        })
    }

    // RELLENAR DATOS
    private fun actualizarPieChart(listaGastos: List<Gasto>) {
        if (listaGastos.isEmpty()) {
            binding.chartCategorias.clear()
            return
        }

        // 1. Agrupar datos
        val mapaCategorias = listaGastos.groupBy { it.categoria }
            .mapValues { entry -> entry.value.sumOf { it.cantidad } }

        val entradas = ArrayList<PieEntry>()
        for ((nombre, total) in mapaCategorias) {
            // Aquí pasamos el total y el NOMBRE de la categoría
            entradas.add(PieEntry(total.toFloat(), nombre))
        }

        // 2. Configurar Dataset
        val dataSet = PieDataSet(entradas, "")
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 5f

        // Colores
        val colores = ArrayList<Int>()
        colores.addAll(ColorTemplate.MATERIAL_COLORS.toList())
        colores.addAll(ColorTemplate.JOYFUL_COLORS.toList())
        colores.addAll(ColorTemplate.COLORFUL_COLORS.toList())
        dataSet.colors = colores

        // 3. SACAR TODO FUERA (Nombre y Cantidad)
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE // El número fuera
        dataSet.xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE // El nombre fuera

        // Estilo de la línea conectora
        dataSet.valueLineColor = Color.BLACK
        dataSet.valueLineWidth = 1f
        dataSet.valueLinePart1Length = 0.4f // Tramo 1 de la línea
        dataSet.valueLinePart2Length = 0.5f // Tramo 2 (más largo para separar el texto)
        dataSet.valueLinePart1OffsetPercentage = 80f // Dónde empieza la línea (desde el borde)

        // 4. Crear Datos
        val data = PieData(dataSet)
        data.setValueTextSize(11f)
        data.setValueTextColor(Color.BLACK) // Color del número

        // Formateador solo para el número (el nombre ya sale solo al activar EntryLabels)
        data.setValueFormatter(object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return Formato.formatearMoneda(value.toDouble())
            }
        })

        binding.chartCategorias.data = data

        // 1. Quitamos cualquier selección (el quesito que estaba resaltado baja)
        binding.chartCategorias.highlightValues(null)

        // 2. Volvemos a poner el texto original del centro
        binding.chartCategorias.centerText = "Gastos\nPor Categoría"
        binding.chartCategorias.setCenterTextSize(16f)
        binding.chartCategorias.setCenterTextColor(Color.GRAY)
        binding.chartCategorias.invalidate()
    }

    // Llama a esto cuando cambies la moneda o el símbolo
    private fun refrescarVistasPorCambioConfiguracion() {
        val gastosActuales = viewModel.gastosDelMes.value ?: emptyList()

        // 1. Refrescar la Lista (para cambiar el símbolo en cada fila)
        adapterLista.notifyDataSetChanged()

        // 2. Refrescar el Gráfico de Quesitos (Categorías)
        // Al llamarlo de nuevo, volverá a formatear los textos con la nueva moneda
        actualizarPieChart(gastosActuales)

        // 3. Refrescar el Gráfico de Barras (si lo usas)
        val listaActual = viewModel.gastosDelMes.value ?: emptyList()
        if (vistaActual == Vista.GRAFICA) actualizarDatosGrafica(listaActual)

        // 4. Refrescar Calendario (si lo usas)
        adapterCalendario?.notifyDataSetChanged()

        // 5. Cambiar moneda de TOTAL
        viewModel.notificarCambioLimites.value = true
    }
}