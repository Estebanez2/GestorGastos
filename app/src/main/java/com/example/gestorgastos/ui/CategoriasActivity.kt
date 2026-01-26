package com.example.gestorgastos.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.gestorgastos.R
import com.example.gestorgastos.data.Categoria
import com.example.gestorgastos.databinding.ActivityCategoriasBinding
import com.example.gestorgastos.databinding.DialogAgregarCategoriaBinding
import com.example.gestorgastos.databinding.ItemCategoriaGestionBinding
import java.io.File

class CategoriasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriasBinding
    private lateinit var viewModel: GastoViewModel

    private var uriFotoTemporal: android.net.Uri? = null
    private var uriFotoFinal: String? = null
    private var ivPreviewActual: android.widget.ImageView? = null

    private var listaCategoriasActual: List<Categoria> = emptyList()

    // --- LANZADORES ---
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) abrirCamara() else Toast.makeText(this, "Permiso cámara necesario", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && uriFotoTemporal != null) {
            // La cámara guarda en fichero temporal, lo copiamos para hacerlo permanente y seguro
            uriFotoFinal = copiarImagenAInternalStorage(uriFotoTemporal!!)
            actualizarVistaPrevia()
        }
    }

    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            // LA CLAVE: Copiamos la imagen a un fichero nuestro y guardamos ESA ruta
            uriFotoFinal = copiarImagenAInternalStorage(uri)
            actualizarVistaPrevia()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoriasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Gestionar Categorías"

        viewModel = ViewModelProvider(this)[GastoViewModel::class.java]

        val adapter = CategoriasAdapter(
            onBorrar = { cat -> confirmarBorrado(cat) },
            onEditar = { cat -> mostrarDialogoEditar(cat) }
        )
        binding.rvCategorias.layoutManager = LinearLayoutManager(this)
        binding.rvCategorias.adapter = adapter

        viewModel.listaCategorias.observe(this) { lista ->
            listaCategoriasActual = lista
            adapter.submitList(lista)
        }

        binding.fabAgregarCategoria.setOnClickListener { mostrarDialogoNuevaCategoria() }
    }

    private fun actualizarVistaPrevia() {
        ivPreviewActual?.let { imageView ->
            Glide.with(this).load(uriFotoFinal).circleCrop().into(imageView)
            imageView.setPadding(0, 0, 0, 0)

            val parent = imageView.parent as? android.view.ViewGroup
            val btnBorrar = parent?.findViewById<android.view.View>(R.id.btnBorrarFotoCat)
            btnBorrar?.visibility = android.view.View.VISIBLE
        }
    }

    // --- FUNCIÓN DE COPIA "PRO" (Usando FileProvider) ---
    private fun copiarImagenAInternalStorage(uriExterna: android.net.Uri): String {
        return try {
            // 1. Crear archivo en memoria interna (permanente)
            val archivoDestino = File(filesDir, "img_${System.currentTimeMillis()}.jpg")

            // 2. Copiar
            val inputStream = contentResolver.openInputStream(uriExterna)
            val outputStream = java.io.FileOutputStream(archivoDestino)
            inputStream?.copyTo(outputStream)
            inputStream?.close()
            outputStream.close()

            // 3. GENERAR URI SEGURA (La forma correcta en Android moderno)
            // Gracias a que añadimos <files-path> en el XML, esto ahora funcionará perfecto.
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivoDestino).toString()

        } catch (e: Exception) {
            e.printStackTrace()
            uriExterna.toString() // Plan B si algo falla
        }
    }

    // --- DIÁLOGOS Y RESTO DE LÓGICA ---

    private fun mostrarDialogoNuevaCategoria() {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogAgregarCategoriaBinding.inflate(layoutInflater)

        uriFotoFinal = null
        ivPreviewActual = dialogBinding.ivPreviewFoto

        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }

        dialogBinding.btnBorrarFotoCat.setOnClickListener {
            uriFotoFinal = null
            dialogBinding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera)
            dialogBinding.ivPreviewFoto.setPadding(40, 40, 40, 40)
            dialogBinding.btnBorrarFotoCat.visibility = android.view.View.GONE
        }

        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Guardar") { _, _ ->
            val nombre = dialogBinding.etNombre.text.toString().trim()
            if (nombre.isEmpty()) {
                Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }
            if (listaCategoriasActual.any { it.nombre.equals(nombre, ignoreCase = true) }) {
                Toast.makeText(this, "¡Esa categoría ya existe!", Toast.LENGTH_LONG).show()
                return@setPositiveButton
            }

            viewModel.agregarNuevaCategoria(nombre, uriFotoFinal)
            Toast.makeText(this, "Creada", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun mostrarDialogoEditar(categoria: Categoria) {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogAgregarCategoriaBinding.inflate(layoutInflater)

        dialogBinding.etNombre.setText(categoria.nombre)
        uriFotoFinal = categoria.uriFoto
        ivPreviewActual = dialogBinding.ivPreviewFoto

        if (uriFotoFinal != null) {
            Glide.with(this).load(uriFotoFinal).circleCrop().into(dialogBinding.ivPreviewFoto)
            dialogBinding.ivPreviewFoto.setPadding(0, 0, 0, 0)
            dialogBinding.btnBorrarFotoCat.visibility = android.view.View.VISIBLE
        } else {
            dialogBinding.btnBorrarFotoCat.visibility = android.view.View.GONE
        }

        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }

        dialogBinding.btnBorrarFotoCat.setOnClickListener {
            uriFotoFinal = null
            dialogBinding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera)
            dialogBinding.ivPreviewFoto.setPadding(40, 40, 40, 40)
            dialogBinding.btnBorrarFotoCat.visibility = android.view.View.GONE
        }

        builder.setView(dialogBinding.root)
        builder.setTitle("Editar Categoría")
        builder.setPositiveButton("Actualizar") { _, _ ->
            val nuevoNombre = dialogBinding.etNombre.text.toString().trim()
            if (nuevoNombre.isEmpty()) return@setPositiveButton

            if (!nuevoNombre.equals(categoria.nombre, ignoreCase = true)) {
                if (listaCategoriasActual.any { it.nombre.equals(nuevoNombre, ignoreCase = true) }) {
                    Toast.makeText(this, "¡Ya existe!", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
            }
            viewModel.editarCategoria(categoria.nombre, nuevoNombre, uriFotoFinal)
            Toast.makeText(this, "Actualizada", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun confirmarBorrado(categoria: Categoria) {
        // 1. Consultamos al ViewModel (usando la función que creamos antes)
        viewModel.contarGastosDeCategoria(categoria.nombre) { cantidad ->
            // 2. Preparamos el mensaje según el resultado
            val mensaje = if (cantidad > 0) { "Se cambiarán a categoría 'Otros' los $cantidad gastos que ahora tienen esta categoría." } else { "No hay gastos asociados a esta categoría." }
            // 3. Mostramos el diálogo con el dato real
            AlertDialog.Builder(this)
                .setTitle("¿Borrar '${categoria.nombre}'?")
                .setMessage(mensaje)
                .setPositiveButton("Borrar") { _, _ ->
                    viewModel.borrarCategoria(categoria)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) abrirCamara()
        else requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    private fun abrirCamara() {
        try {
            val tempFile = File.createTempFile("cat_foto_", ".jpg", externalCacheDir)
            uriFotoTemporal = FileProvider.getUriForFile(this, "${packageName}.fileprovider", tempFile)
            takePictureLauncher.launch(uriFotoTemporal)
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- ADAPTER ---
    class CategoriasAdapter(
        val onBorrar: (Categoria) -> Unit,
        val onEditar: (Categoria) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<Categoria, CategoriasAdapter.Holder>(DiffCallback()) {

        class Holder(val b: ItemCategoriaGestionBinding) : RecyclerView.ViewHolder(b.root)
        class DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Categoria>() {
            override fun areItemsTheSame(a: Categoria, b: Categoria) = a.nombre == b.nombre
            override fun areContentsTheSame(a: Categoria, b: Categoria) = a == b
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            ItemCategoriaGestionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val cat = getItem(position)
            holder.b.tvNombreCat.text = cat.nombre

            if (cat.uriFoto != null) {
                // Aquí ya no hace falta comprobar seguridad porque las nuevas serán file://
                Glide.with(holder.itemView).load(cat.uriFoto).circleCrop().into(holder.b.ivIconoCat)
                holder.b.ivIconoCat.clearColorFilter()
                holder.b.ivIconoCat.setOnClickListener {
                    ImageZoomHelper.mostrarImagen(holder.itemView.context, cat.uriFoto)
                }
            } else {
                holder.b.ivIconoCat.setImageResource(CategoriasHelper.obtenerIcono(cat.nombre))
                holder.b.ivIconoCat.setOnClickListener(null)
            }

            holder.b.btnBorrarCat.setOnClickListener { onBorrar(cat) }
            holder.b.btnEditarCat.setOnClickListener { onEditar(cat) }
        }
    }
}