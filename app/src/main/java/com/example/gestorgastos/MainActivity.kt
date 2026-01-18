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

    private lateinit var adapterLista: GastoAdapter

    private var adapterCalendario: CalendarioAdapter? = null

    enum class Vista { LISTA, CALENDARIO, GRAFICA }
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

    private fun cambiarVista(nuevaVista: Vista) {
        vistaActual = nuevaVista
        binding.rvGastos.visibility = View.GONE
        binding.rvCalendario.visibility = View.GONE
        binding.chartGastos.visibility = View.GONE
        binding.tvVacio.visibility = View.GONE

        when(vistaActual) {
            Vista.LISTA -> {
                binding.rvGastos.visibility = View.VISIBLE
                if (adapterLista.currentList.isEmpty()) binding.tvVacio.visibility = View.VISIBLE
            }
            Vista.CALENDARIO -> binding.rvCalendario.visibility = View.VISIBLE
            Vista.GRAFICA -> binding.chartGastos.visibility = View.VISIBLE
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

                // 1. Actualizar Lista
                adapterLista.notifyDataSetChanged()

                // 2. CORRECCIÓN: Actualizar Calendario
                adapterCalendario?.notifyDataSetChanged()

                // 3. Actualizar Gráfica (Refrescamos los datos actuales para que cojan el nuevo formato)
                val listaActual = viewModel.gastosDelMes.value ?: emptyList()
                if (vistaActual == Vista.GRAFICA) actualizarDatosGrafica(listaActual)

                // 4. Actualizar Total
                viewModel.notificarCambioLimites.value = true

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
        dialogBinding.etCantidad.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etCantidad))
        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }
        dialogBinding.btnBorrarFoto.setOnClickListener {
            uriFotoFinal = null

            // Restaurar icono de cámara
            dialogBinding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera)
            dialogBinding.ivPreviewFoto.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE

            // Restaurar padding (usando dimens)
            val padding = resources.getDimensionPixelSize(com.example.gestorgastos.R.dimen.preview_padding_small)
            dialogBinding.ivPreviewFoto.setPadding(padding, padding, padding, padding)

            // Restaurar el tinte GRIS (Importante)
            dialogBinding.ivPreviewFoto.setColorFilter(android.graphics.Color.parseColor("#888888"))

            dialogBinding.btnBorrarFoto.visibility = android.view.View.GONE
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
        val adapterSpinner = android.widget.ArrayAdapter(
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
        dialogBinding.etCantidad.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etCantidad))
        dialogBinding.etDescripcion.setText(gasto.descripcion)
        dialogBinding.btnBorrarFoto.setOnClickListener {
            uriFotoFinal = null
            dialogBinding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera)
            dialogBinding.ivPreviewFoto.setPadding(60,60,60,60) // Restaurar padding visual
            dialogBinding.btnBorrarFoto.visibility = android.view.View.GONE
        }


        uriFotoFinal = gasto.uriFoto
        ivPreviewActual = dialogBinding.ivPreviewFoto

        // ... Cargar foto existente ...
        if (uriFotoFinal != null) {
            Glide.with(this).load(uriFotoFinal).centerCrop().into(dialogBinding.ivPreviewFoto)
            dialogBinding.ivPreviewFoto.setPadding(0,0,0,0)
            dialogBinding.btnBorrarFoto.visibility = android.view.View.VISIBLE
        } else {
            dialogBinding.btnBorrarFoto.visibility = android.view.View.GONE
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
                            val bitmapFinal = com.example.gestorgastos.ui.ExportarHelper.generarImagenLarga(
                                this@MainActivity,
                                binding.cardResumen,
                                binding.layoutNavegacion, // <--- Aquí va el título del mes
                                listaActual,
                                mapaBitmaps
                            )

                            if (guardarEnDispositivo) com.example.gestorgastos.ui.ExportarHelper.guardarEnDispositivo(this@MainActivity, bitmapFinal, null, true)
                            else com.example.gestorgastos.ui.ExportarHelper.compartir(this@MainActivity, bitmapFinal, null, true)
                        }
                    }
                }

                Vista.GRAFICA -> {
                    // MODO GRÁFICA
                    // 1. Capturamos solo la gráfica
                    val bitmapGrafica = binding.chartGastos.chartBitmap
                    // 2. Le pegamos encima la cabecera y el título
                    val bitmapFinal = com.example.gestorgastos.ui.ExportarHelper.unirVistasEnBitmap(
                        binding.cardResumen,
                        binding.layoutNavegacion,
                        bitmapGrafica
                    )

                    if (guardarEnDispositivo) com.example.gestorgastos.ui.ExportarHelper.guardarEnDispositivo(this, bitmapFinal, null, true)
                    else com.example.gestorgastos.ui.ExportarHelper.compartir(this, bitmapFinal, null, true)
                }

                Vista.CALENDARIO -> {
                    // MODO CALENDARIO
                    // 1. Capturamos la rejilla
                    val bitmapCalendario = com.example.gestorgastos.ui.ExportarHelper.capturarVista(binding.rvCalendario)
                    // 2. Le pegamos encima la cabecera y el título
                    val bitmapFinal = com.example.gestorgastos.ui.ExportarHelper.unirVistasEnBitmap(
                        binding.cardResumen,
                        binding.layoutNavegacion,
                        bitmapCalendario
                    )

                    if (guardarEnDispositivo) com.example.gestorgastos.ui.ExportarHelper.guardarEnDispositivo(this, bitmapFinal, null, true)
                    else com.example.gestorgastos.ui.ExportarHelper.compartir(this, bitmapFinal, null, true)
                }
            }
        } else {
            val csvContent = com.example.gestorgastos.ui.ExportarHelper.generarTextoCSV(listaActual)
            if (guardarEnDispositivo) com.example.gestorgastos.ui.ExportarHelper.guardarEnDispositivo(this, null, csvContent, false)
            else com.example.gestorgastos.ui.ExportarHelper.compartir(this, null, csvContent, false)
        }
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
}