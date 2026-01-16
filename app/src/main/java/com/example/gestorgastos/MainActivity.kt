package com.example.gestorgastos

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gestorgastos.databinding.ActivityMainBinding
import com.example.gestorgastos.databinding.DialogAgregarGastoBinding
import com.example.gestorgastos.ui.GastoAdapter
import com.example.gestorgastos.ui.GastoViewModel
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: GastoViewModel
    private lateinit var adapter: GastoAdapter

    // Variables para manejar la foto
    private var uriFotoTemporal: android.net.Uri? = null
    private var uriFotoFinal: String? = null
    private var ivPreviewActual: android.widget.ImageView? = null // Referencia a la imagen del diálogo abierto

    // 1. LANZADOR CÁMARA
    private val takePictureLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { success ->
        if (success && uriFotoTemporal != null) {
            uriFotoFinal = uriFotoTemporal.toString()
            ivPreviewActual?.let { imageView ->
                com.bumptech.glide.Glide.with(this).load(uriFotoFinal).centerCrop().into(imageView)
            }
        }
    }

    // 2. LANZADOR GALERÍA
    private val pickGalleryLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uriFotoFinal = uri.toString()
            ivPreviewActual?.let { imageView ->
                com.bumptech.glide.Glide.with(this).load(uriFotoFinal).centerCrop().into(imageView)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Configurar ViewBinding (Para no usar findViewById)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Inicializar ViewModel
        viewModel = ViewModelProvider(this)[GastoViewModel::class.java]

        // 3. Configurar RecyclerView (La lista)
        setupRecyclerView()
        setupSwipeToDelete()

        // 4. Configurar Observadores (Reaccionan a cambios en la BD)
        setupObservers()

        // 5. Configurar Botones
        binding.fabAgregar.setOnClickListener {
            mostrarDialogoAgregarGasto()
        }
    }

    private fun setupRecyclerView() {
        // Inicializamos el adaptador.
        // El código entre llaves {} es lo que pasa cuando haces click en un gasto (por ahora, un Toast)
        adapter = GastoAdapter { gasto ->
            mostrarDialogoEditarGasto(gasto)
        }

        binding.rvGastos.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupObservers() {
        // A. Observar la lista de gastos
        viewModel.gastosDelMes.observe(this) { listaGastos ->
            adapter.submitList(listaGastos)

            // Mostrar texto de "Vacío" si no hay gastos
            if (listaGastos.isEmpty()) {
                binding.tvVacio.visibility = android.view.View.VISIBLE
            } else {
                binding.tvVacio.visibility = android.view.View.GONE
            }
        }

        // B. Observar el total de dinero gastado
        viewModel.sumaTotalDelMes.observe(this) { suma ->
            val total = suma ?: 0.0 // Si es null, es 0.0
            binding.tvTotalMes.text = String.format("%.2f €", total)

            // C. Cambiar el color del semáforo según el gasto
            val colorRes = viewModel.obtenerColorAlerta(total)
            binding.layoutAlerta.setBackgroundColor(ContextCompat.getColor(this, colorRes))
        }
    }

    private fun mostrarDialogoAgregarGasto() {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogAgregarGastoBinding.inflate(LayoutInflater.from(this))

        // Resetear variables
        uriFotoFinal = null
        ivPreviewActual = dialogBinding.ivPreviewFoto // Guardamos referencia para que el launcher la use

        // Botones Foto
        dialogBinding.btnCamara.setOnClickListener { abrirCamara() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }

        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Guardar") { _, _ ->
            val nombre = dialogBinding.etNombre.text.toString()
            val cantidadStr = dialogBinding.etCantidad.text.toString()
            val descripcion = dialogBinding.etDescripcion.text.toString()

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cantidad = cantidadStr.toDoubleOrNull() ?: 0.0
                // Pasamos la uriFotoFinal al ViewModel
                viewModel.agregarGasto(nombre, cantidad, descripcion, uriFotoFinal)
            } else {
                Toast.makeText(this, "Faltan datos", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun setupSwipeToDelete() {
        // Definimos el comportamiento del deslizamiento
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // No queremos mover ítems de arriba a abajo, solo deslizar
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                // Obtenemos el gasto que se ha deslizado
                val gastoABorrar = adapter.currentList[position]

                // Mostramos el diálogo de confirmación
                mostrarDialogoConfirmacionBorrado(gastoABorrar, position)
            }
        }

        // Conectamos este "detector" a nuestra lista (RecyclerView)
        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(binding.rvGastos)
    }

    private fun mostrarDialogoConfirmacionBorrado(gasto: com.example.gestorgastos.data.Gasto, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("¿Borrar Gasto?")
            .setMessage("¿Estás seguro de que quieres eliminar '${gasto.nombre}'? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                // Si dice SÍ: Llamamos al ViewModel para que lo borre de la BD
                viewModel.borrarGasto(gasto)
                Toast.makeText(this, "Gasto eliminado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                // Si dice NO: Importante -> Tenemos que avisar al adaptador para que
                // "pinte" la fila de nuevo, porque visualmente ya la habías deslizado.
                adapter.notifyItemChanged(position)
                dialog.dismiss()
            }
            .setCancelable(false) // Obligamos a elegir Sí o No
            .show()
    }

    private fun mostrarDialogoEditarGasto(gasto: com.example.gestorgastos.data.Gasto) {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogAgregarGastoBinding.inflate(LayoutInflater.from(this))

        // Cargar datos existentes
        dialogBinding.tvTituloDialogo.text = "Editar Gasto"
        dialogBinding.etNombre.setText(gasto.nombre)
        dialogBinding.etCantidad.setText(gasto.cantidad.toString())
        dialogBinding.etDescripcion.setText(gasto.descripcion)

        uriFotoFinal = gasto.uriFoto // Importante: Empezamos con la foto que ya tenía
        ivPreviewActual = dialogBinding.ivPreviewFoto

        // Si ya tenía foto, la mostramos
        if (uriFotoFinal != null) {
            com.bumptech.glide.Glide.with(this).load(uriFotoFinal).centerCrop().into(dialogBinding.ivPreviewFoto)
        }

        dialogBinding.btnCamara.setOnClickListener { abrirCamara() }
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
                    uriFoto = uriFotoFinal // Guardamos la nueva foto (o la vieja si no cambió)
                )
                viewModel.actualizarGasto(gastoEditado)
                Toast.makeText(this, "Actualizado", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    // --- FUNCIÓN AUXILIAR PARA CÁMARA ---
    private fun abrirCamara() {
        // Crear archivo temporal
        val tempFile = java.io.File.createTempFile("foto_", ".jpg", externalCacheDir)
        uriFotoTemporal = androidx.core.content.FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider", // Debe coincidir con el Manifest
            tempFile
        )
        takePictureLauncher.launch(uriFotoTemporal)
    }

}