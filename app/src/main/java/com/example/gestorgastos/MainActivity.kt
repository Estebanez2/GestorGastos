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

        // CAMBIO: Configurar botón de ajustes
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
        viewModel.gastosDelMes.observe(this) { listaGastos ->
            adapter.submitList(listaGastos)
            binding.tvVacio.visibility = if (listaGastos.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        // Usamos Formato.formatearMoneda
        viewModel.sumaTotalDelMes.observe(this) { suma ->
            val total = suma ?: 0.0
            binding.tvTotalMes.text = Formato.formatearMoneda(total)

            val colorRes = viewModel.obtenerColorAlerta(total)
            binding.layoutAlerta.setBackgroundColor(ContextCompat.getColor(this, colorRes))
        }

        // Observamos cambio de límites para repintar el semáforo al instante
        viewModel.notificarCambioLimites.observe(this) {
            val totalActual = viewModel.sumaTotalDelMes.value ?: 0.0
            val colorRes = viewModel.obtenerColorAlerta(totalActual)
            binding.layoutAlerta.setBackgroundColor(ContextCompat.getColor(this, colorRes))
        }

        // Observar cambio de mes para actualizar el título
        viewModel.mesActual.observe(this) { mes ->
            // Formateamos la fecha a español (ej: "Enero 2026")
            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale("es", "ES"))
            // Capitalizamos la primera letra (enero -> Enero)
            val textoMes = mes.format(formatter).replaceFirstChar { it.uppercase() }
            binding.tvMesTitulo.text = textoMes
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
                val cantidad = cantidadStr.toDoubleOrNull() ?: 0.0
                viewModel.agregarGasto(nombre, cantidad, descripcion, uriFotoFinal)
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

    // CAMBIO: Nueva función para el diálogo de configuración
    private fun mostrarDialogoConfiguracion() {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = com.example.gestorgastos.databinding.DialogConfiguracionBinding.inflate(LayoutInflater.from(this))

        // Pre-llenar (usando formato bonito, pero quitando el símbolo € si lo tuviere para editar solo número)
        // Nota: viewModel.limiteAmarillo es Double (ej 500.0). Lo formateamos a String bonito para mostrarlo.
        // Pero para editar es mejor texto plano o usar nuestro watcher.
        // TRUCO: Simplemente lo ponemos y dejamos que el usuario edite.

        // Asignamos el Watcher a los campos para que ponga puntos mientras escribes
        dialogBinding.etAmarillo.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etAmarillo))
        dialogBinding.etRojo.addTextChangedListener(com.example.gestorgastos.ui.EuroTextWatcher(dialogBinding.etRojo))

        // Convertimos el Double a texto bonito para empezar (quitando puntos para no liar al watcher al inicio, o dejándolo)
        // Lo más fácil: setear texto plano y dejar que el watcher lo arregle al tocarlo, o formatearlo ya.
        // Vamos a ponerlo sin formato inicial para simplificar, el usuario escribirá.
        dialogBinding.etAmarillo.setText(viewModel.limiteAmarillo.toString().replace(".", ","))
        dialogBinding.etRojo.setText(viewModel.limiteRojo.toString().replace(".", ","))

        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Guardar") { _, _ ->
            // IMPORTANTE: Antes de convertir a número, tenemos que QUITAR los puntos y cambiar la COMA por punto
            // para que Kotlin lo entienda como Double.
            val amarilloStr = dialogBinding.etAmarillo.text.toString().replace(".", "").replace(",", ".")
            val rojoStr = dialogBinding.etRojo.text.toString().replace(".", "").replace(",", ".")

            if (amarilloStr.isNotEmpty() && rojoStr.isNotEmpty()) {
                val amarillo = amarilloStr.toDoubleOrNull() ?: 0.0
                val rojo = rojoStr.toDoubleOrNull() ?: 0.0

                // --- VALIDACIÓN SOLICITADA ---
                if (amarillo >= rojo) {
                    Toast.makeText(this, "El límite Amarillo debe ser MENOR que el Rojo", Toast.LENGTH_LONG).show()
                    // Reabrimos el diálogo porque se habrá cerrado (truco rápido) o simplemente mostramos error.
                    // Al ser un Dialog nativo, se cierra al dar al botón.
                    // Para evitar que se cierre habría que configurar el botón aparte,
                    // pero para no complicar el código, lanzamos el Toast y el usuario tendrá que volver a abrirlo.
                    return@setPositiveButton
                }
                // -----------------------------

                viewModel.guardarNuevosLimites(amarillo, rojo)
                Toast.makeText(this, "Límites actualizados", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
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
}