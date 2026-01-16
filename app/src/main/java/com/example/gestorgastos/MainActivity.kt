package com.example.gestorgastos

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.ActivityMainBinding
import com.example.gestorgastos.databinding.DialogAgregarGastoBinding
// CAMBIO: Importamos el binding de config y el Formato
import com.example.gestorgastos.databinding.DialogConfiguracionBinding
import com.example.gestorgastos.ui.Formato
import com.example.gestorgastos.ui.GastoAdapter
import com.example.gestorgastos.ui.GastoViewModel
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: GastoViewModel
    private lateinit var adapter: GastoAdapter

    // Variables para manejar la foto
    private var uriFotoTemporal: android.net.Uri? = null
    private var uriFotoFinal: String? = null
    private var ivPreviewActual: android.widget.ImageView? = null

    // LANZADORES (Se mantienen igual)
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

        setupRecyclerView()
        setupSwipeToDelete()
        setupObservers()

        binding.fabAgregar.setOnClickListener {
            mostrarDialogoAgregarGasto()
        }

        // Configurar botón de ajustes
        binding.btnConfig.setOnClickListener {
            mostrarDialogoConfiguracion()
        }

        // Navegación de meses
        binding.btnMesAnterior.setOnClickListener {
            viewModel.mesAnterior()
        }

        binding.btnMesSiguiente.setOnClickListener {
            viewModel.mesSiguiente()
        }

        // Botón EXPORTAR
        binding.btnExportar.setOnClickListener {
            mostrarMenuFormatoExportacion()
        }
    }

    private fun setupRecyclerView() {
        adapter = GastoAdapter { gasto ->
            mostrarDialogoEditarGasto(gasto)
        }
        binding.rvGastos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val gastoABorrar = adapter.currentList[position]
                mostrarDialogoConfirmacionBorrado(gastoABorrar, position)
            }
        }
        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.rvGastos)
    }

    private fun mostrarDialogoConfirmacionBorrado(gasto: Gasto, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("¿Borrar Gasto?")
            .setMessage("¿Eliminar '${gasto.nombre}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                viewModel.borrarGasto(gasto)
                Toast.makeText(this, "Eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                adapter.notifyItemChanged(position)
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun setupObservers() {
        // 1. Lista de Gastos
        viewModel.gastosDelMes.observe(this) { listaGastos ->
            adapter.submitList(listaGastos)
            binding.tvVacio.visibility = if (listaGastos.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        // 2. Total y Animaciones
        viewModel.sumaTotalDelMes.observe(this) { suma ->
            val total = suma ?: 0.0

            // A. Actualizar texto con formato
            binding.tvTotalMes.text = com.example.gestorgastos.ui.Formato.formatearMoneda(total)

            // B. Calcular el nuevo color destino
            val idColorDestino = viewModel.obtenerColorAlerta(total)
            val colorDestino = ContextCompat.getColor(this, idColorDestino)

            // C. Obtener el color que tiene AHORA mismo (para la transición)
            val background = binding.layoutAlerta.background
            val colorActual = if (background is android.graphics.drawable.ColorDrawable) {
                background.color
            } else {
                // Si es la primera vez, asumimos verde por defecto
                ContextCompat.getColor(this, com.example.gestorgastos.R.color.alerta_verde)
            }

            // D. ANIMACIÓN DE COLOR (Solo si cambia)
            if (colorActual != colorDestino) {
                val colorAnimation = android.animation.ValueAnimator.ofObject(
                    android.animation.ArgbEvaluator(),
                    colorActual,
                    colorDestino
                )
                colorAnimation.duration = 1000 // 1 segundo de transición suave
                colorAnimation.addUpdateListener { animator ->
                    binding.layoutAlerta.setBackgroundColor(animator.animatedValue as Int)
                }
                colorAnimation.start()
            } else {
                // Si es el mismo color, aseguramos que esté puesto (para el inicio de la app)
                binding.layoutAlerta.setBackgroundColor(colorDestino)
            }

            // E. ANIMACIÓN DE "LATIDO" (Pop) EN EL TEXTO
            // Hacemos que el texto crezca un poco y vuelva a su tamaño
            binding.tvTotalMes.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(200)
                .withEndAction {
                    // Cuando termine de crecer, vuelve al tamaño original
                    binding.tvTotalMes.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }

        // 3. Observer para cambio de límites (Forzar repintado)
        viewModel.notificarCambioLimites.observe(this) {
            // Obtenemos el valor actual del total
            val totalActual = viewModel.sumaTotalDelMes.value ?: 0.0

            // 1. IMPORTANTE: Actualizamos el TEXTO para que cambie el símbolo (€ -> $)
            binding.tvTotalMes.text = com.example.gestorgastos.ui.Formato.formatearMoneda(totalActual)

            // 2. Actualizamos el COLOR (por si al cambiar de moneda cambiaste también los límites)
            val nuevoColor = ContextCompat.getColor(this, viewModel.obtenerColorAlerta(totalActual))
            binding.layoutAlerta.setBackgroundColor(nuevoColor)
        }
    }

    private fun mostrarDialogoAgregarGasto() {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogAgregarGastoBinding.inflate(LayoutInflater.from(this))

        uriFotoFinal = null
        ivPreviewActual = dialogBinding.ivPreviewFoto

        // AÑADIR EL WATCHER AQUÍ
        dialogBinding.etCantidad.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etCantidad))

        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }

        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Guardar") { _, _ ->
            val nombre = dialogBinding.etNombre.text.toString()

            // LIMPIAR FORMATO ANTES DE GUARDAR (Quitar puntos, coma -> punto)
            val cantidadStr = dialogBinding.etCantidad.text.toString().replace(".", "").replace(",", ".")

            val descripcion = dialogBinding.etDescripcion.text.toString()

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cantidadNueva = cantidadStr.toDoubleOrNull() ?: 0.0

                // 1. Guardamos el gasto (esto va a la base de datos)
                viewModel.agregarGasto(nombre, cantidadNueva, descripcion, uriFotoFinal)

                // 2. CALCULAMOS EL FUTURO PARA EL FLASH
                // Cogemos lo que hay ahora en pantalla + lo que acabamos de meter
                val totalActual = viewModel.sumaTotalDelMes.value ?: 0.0
                val totalFuturo = totalActual + cantidadNueva

                // 3. Lanzamos el flash con el valor futuro
                hacerFlashBorde(totalFuturo)

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
        dialogBinding.etCantidad.setText(gasto.cantidad.toString())
        dialogBinding.etDescripcion.setText(gasto.descripcion)
        uriFotoFinal = gasto.uriFoto
        ivPreviewActual = dialogBinding.ivPreviewFoto

        if (uriFotoFinal != null) {
            Glide.with(this).load(uriFotoFinal).centerCrop().into(dialogBinding.ivPreviewFoto)
        }

        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }

        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Actualizar") { _, _ ->
            val nombre = dialogBinding.etNombre.text.toString()
            val cantidadStr = dialogBinding.etCantidad.text.toString()
            val descripcion = dialogBinding.etDescripcion.text.toString()

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cantidad = cantidadStr.toDoubleOrNull() ?: 0.0
                val gastoEditado = gasto.copy(
                    nombre = nombre,
                    cantidad = cantidad,
                    descripcion = descripcion,
                    uriFoto = uriFotoFinal
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
        val dialogBinding = com.example.gestorgastos.databinding.DialogConfiguracionBinding.inflate(LayoutInflater.from(this))

        dialogBinding.etAmarillo.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etAmarillo))
        dialogBinding.etRojo.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etRojo))

        dialogBinding.etAmarillo.setText(viewModel.limiteAmarillo.toString().replace(".", ","))
        dialogBinding.etRojo.setText(viewModel.limiteRojo.toString().replace(".", ","))

        builder.setView(dialogBinding.root)
        builder.setTitle("Configuración") // Título

        builder.setPositiveButton("Guardar") { _, _ ->
            val amarilloStr = dialogBinding.etAmarillo.text.toString().replace(".", "").replace(",", ".")
            val rojoStr = dialogBinding.etRojo.text.toString().replace(".", "").replace(",", ".")

            if (amarilloStr.isNotEmpty() && rojoStr.isNotEmpty()) {
                val amarillo = amarilloStr.toDoubleOrNull() ?: 0.0
                val rojo = rojoStr.toDoubleOrNull() ?: 0.0
                if (amarillo >= rojo) {
                    Toast.makeText(this, "El amarillo debe ser menor que el rojo", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                viewModel.guardarNuevosLimites(amarillo, rojo)
                Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
            }
        }

        // BOTÓN NUEVO: CAMBIAR MONEDA
        builder.setNeutralButton("Cambiar Moneda") { _, _ ->
            mostrarDialogoMoneda()
        }

        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    // Nueva función pequeña para elegir moneda
    private fun mostrarDialogoMoneda() {
        val monedas = arrayOf("Euro (€)", "Dólar ($)", "Libra (£)")
        AlertDialog.Builder(this)
            .setTitle("Elige tu divisa")
            .setItems(monedas) { _, which ->
                when(which) {
                    0 -> com.example.gestorgastos.ui.Formato.cambiarDivisa("EUR")
                    1 -> com.example.gestorgastos.ui.Formato.cambiarDivisa("USD")
                    2 -> com.example.gestorgastos.ui.Formato.cambiarDivisa("GBP")
                }
                // Forzamos actualizar la pantalla para ver el cambio
                adapter.notifyDataSetChanged() // Actualiza la lista
                viewModel.notificarCambioLimites.value = true // Actualiza el total de arriba
                Toast.makeText(this, "Moneda cambiada", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            abrirCamara()
        } else {
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun abrirCamara() {
        try {
            val tempFile = File.createTempFile("foto_", ".jpg", externalCacheDir)
            uriFotoTemporal = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
            takePictureLauncher.launch(uriFotoTemporal)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error cámara", Toast.LENGTH_SHORT).show()
        }
    }

    // PASO 1: Elegir Formato
    private fun mostrarMenuFormatoExportacion() {
        val opciones = arrayOf("Hoja de Cálculo (CSV)", "Imagen Larga (JPG)")

        AlertDialog.Builder(this)
            .setTitle("1. Elige el formato")
            .setItems(opciones) { _, which ->
                val esImagen = (which == 1)
                mostrarMenuAccionExportacion(esImagen)
            }
            .show()
    }

    // PASO 2: Elegir Acción (Guardar o Compartir)
    private fun mostrarMenuAccionExportacion(esImagen: Boolean) {
        val opciones = arrayOf("Guardar en Dispositivo", "Compartir (WhatsApp/Email...)")
        val titulo = if (esImagen) "Imagen Larga" else "Archivo CSV"

        AlertDialog.Builder(this)
            .setTitle("2. ¿Qué hacer con $titulo?")
            .setItems(opciones) { _, which ->
                procesarExportacion(esImagen, guardarEnDispositivo = (which == 0))
            }
            .show()
    }

    // LÓGICA FINAL
    private fun procesarExportacion(esImagen: Boolean, guardarEnDispositivo: Boolean) {
        val listaActual = viewModel.gastosDelMes.value ?: emptyList()
        if (listaActual.isEmpty()) {
            Toast.makeText(this, "No hay gastos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        // Preparamos los datos (Puede tardar un poco, idealmente en corrutina, pero aqui es rapido)
        var bitmapFinal: android.graphics.Bitmap? = null
        var csvContent: String? = null

        if (esImagen) {
            // Pasamos 'binding.cardResumen' para que pinte la cabecera verde
            // Y pasamos la lista para que pinte los items debajo
            bitmapFinal = com.example.gestorgastos.ui.ExportarHelper.generarImagenLarga(
                this,
                binding.cardResumen,
                listaActual
            )
        } else {
            csvContent = com.example.gestorgastos.ui.ExportarHelper.generarTextoCSV(listaActual)
        }

        // Ejecutamos la acción
        if (guardarEnDispositivo) {
            com.example.gestorgastos.ui.ExportarHelper.guardarEnDispositivo(this, bitmapFinal, csvContent, esImagen)
        } else {
            com.example.gestorgastos.ui.ExportarHelper.compartir(this, bitmapFinal, csvContent, esImagen)
        }
    }

    // --- FUNCIÓN PARA EL EFECTO DE FLASH ESCALONADO ---
    private fun hacerFlashBorde(totalSimulado: Double) {

        // Usamos el totalSimulado para calcular el color, no el actual
        val colorResId = viewModel.obtenerColorAlerta(totalSimulado)
        val color = ContextCompat.getColor(this, colorResId)

        // 1. Determinar cuántas veces parpadea según el color
        val numFlashes = when (colorResId) {
            com.example.gestorgastos.R.color.alerta_rojo -> 3 // Rojo = 3 veces
            com.example.gestorgastos.R.color.alerta_amarillo -> 2 // Amarillo = 2 veces
            else -> 1 // Verde = 1 vez
        }

        // 2. Configurar la vista de flash
        binding.viewFlashBorde.setBackgroundColor(color)
        binding.viewFlashBorde.visibility = android.view.View.VISIBLE
        binding.viewFlashBorde.alpha = 0f

        // 3. Función recursiva (Igual que antes)
        fun ejecutarAnimacionFlash(vecesRestantes: Int) {
            if (vecesRestantes <= 0) {
                binding.viewFlashBorde.visibility = android.view.View.GONE
                binding.viewFlashBorde.background = null
                return
            }

            // Encender
            binding.viewFlashBorde.animate()
                .alpha(0.5f) // Intensidad del flash
                .setDuration(150)
                .withEndAction {
                    // Apagar
                    binding.viewFlashBorde.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction {
                            // Repetir
                            ejecutarAnimacionFlash(vecesRestantes - 1)
                        }
                        .start()
                }
                .start()
        }

        // 4. ¡Arrancar!
        ejecutarAnimacionFlash(numFlashes)
    }
}