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
import com.example.gestorgastos.data.Categoria
import com.example.gestorgastos.databinding.ActivityCategoriasBinding
import com.example.gestorgastos.databinding.DialogAgregarCategoriaBinding
import com.example.gestorgastos.databinding.ItemCategoriaGestionBinding
import java.io.File
import com.example.gestorgastos.R

class CategoriasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCategoriasBinding
    private lateinit var viewModel: GastoViewModel

    private var uriFotoTemporal: android.net.Uri? = null
    private var uriFotoFinal: String? = null
    private var ivPreviewActual: android.widget.ImageView? = null

    // Lista local para comprobar duplicados rápidamente
    private var listaCategoriasActual: List<Categoria> = emptyList()

    // --- LANZADORES ---
    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) abrirCamara() else Toast.makeText(this, "Permiso cámara necesario", Toast.LENGTH_SHORT).show()
    }
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && uriFotoTemporal != null) {
            uriFotoFinal = uriFotoTemporal.toString()
            ivPreviewActual?.let {
                Glide.with(this).load(uriFotoFinal).circleCrop().into(it) // En categorias usamos circleCrop
                it.setPadding(0,0,0,0)

                // Mostrar botón borrar
                val parent = it.parent as? android.view.ViewGroup
                val btnBorrar = parent?.findViewById<android.view.View>(R.id.btnBorrarFotoCat)
                btnBorrar?.visibility = android.view.View.VISIBLE
            }
        }
    }

    private val pickGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uriFotoFinal = uri.toString()
            ivPreviewActual?.let {
                Glide.with(this).load(uriFotoFinal).circleCrop().into(it)
                it.setPadding(0,0,0,0)

                val parent = it.parent as? android.view.ViewGroup
                val btnBorrar = parent?.findViewById<android.view.View>(R.id.btnBorrarFotoCat)
                btnBorrar?.visibility = android.view.View.VISIBLE
            }
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
            listaCategoriasActual = lista // Guardamos referencia para comprobar duplicados
            adapter.submitList(lista)
        }

        binding.fabAgregarCategoria.setOnClickListener { mostrarDialogoNuevaCategoria() }
    }

    // --- DIÁLOGOS ---

    private fun mostrarDialogoNuevaCategoria() {
        val builder = AlertDialog.Builder(this)
        val dialogBinding = DialogAgregarCategoriaBinding.inflate(layoutInflater)

        uriFotoFinal = null
        ivPreviewActual = dialogBinding.ivPreviewFoto

        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }
        // Lógica del botón borrar
        dialogBinding.btnBorrarFotoCat.setOnClickListener {
            uriFotoFinal = null
            dialogBinding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera)
            dialogBinding.ivPreviewFoto.setPadding(40,40,40,40) // Restaurar padding
            dialogBinding.btnBorrarFotoCat.visibility = android.view.View.GONE
        }

        builder.setView(dialogBinding.root)
        builder.setPositiveButton("Guardar") { _, _ ->
            val nombre = dialogBinding.etNombre.text.toString().trim()

            // 1. VALIDACIÓN: Nombre vacío
            if (nombre.isEmpty()) {
                Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            // 2. VALIDACIÓN: Duplicado (Ignoramos mayúsculas/minúsculas)
            val existe = listaCategoriasActual.any { it.nombre.equals(nombre, ignoreCase = true) }
            if (existe) {
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

        // Rellenamos datos actuales
        dialogBinding.etNombre.setText(categoria.nombre)
        uriFotoFinal = categoria.uriFoto
        ivPreviewActual = dialogBinding.ivPreviewFoto

        if (uriFotoFinal != null) Glide.with(this).load(uriFotoFinal).circleCrop().into(dialogBinding.ivPreviewFoto)

        dialogBinding.btnCamara.setOnClickListener { checkCameraPermissionAndOpen() }
        dialogBinding.btnGaleria.setOnClickListener { pickGalleryLauncher.launch("image/*") }
        // Lógica del botón borrar
        dialogBinding.btnBorrarFotoCat.setOnClickListener {
            uriFotoFinal = null
            dialogBinding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera)
            dialogBinding.ivPreviewFoto.setPadding(40,40,40,40) // Restaurar padding
            dialogBinding.btnBorrarFotoCat.visibility = android.view.View.GONE
        }

        // En EDITAR, mostrar si ya existe foto:
        if (uriFotoFinal != null) {
            Glide.with(this).load(uriFotoFinal).circleCrop().into(dialogBinding.ivPreviewFoto)
            dialogBinding.ivPreviewFoto.setPadding(0,0,0,0)
            dialogBinding.btnBorrarFotoCat.visibility = android.view.View.VISIBLE
        } else {
            dialogBinding.btnBorrarFotoCat.visibility = android.view.View.GONE
        }

        builder.setView(dialogBinding.root)
        builder.setTitle("Editar Categoría")

        builder.setPositiveButton("Actualizar") { _, _ ->
            val nuevoNombre = dialogBinding.etNombre.text.toString().trim()

            if (nuevoNombre.isEmpty()) {
                Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                return@setPositiveButton
            }

            // Si cambia el nombre, verificamos que no exista YA otro igual (excluyéndose a sí misma)
            if (!nuevoNombre.equals(categoria.nombre, ignoreCase = true)) {
                val existe = listaCategoriasActual.any { it.nombre.equals(nuevoNombre, ignoreCase = true) }
                if (existe) {
                    Toast.makeText(this, "¡Ya existe otra categoría llamada así!", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
            }

            // Llamamos al método inteligente del ViewModel
            viewModel.editarCategoria(categoria.nombre, nuevoNombre, uriFotoFinal)
            Toast.makeText(this, "Actualizada", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun confirmarBorrado(categoria: Categoria) {
        AlertDialog.Builder(this)
            .setTitle("¿Borrar ${categoria.nombre}?")
            .setMessage("Los gastos de esta categoría perderán su icono (saldrá una interrogación).")
            .setPositiveButton("Borrar") { _, _ -> viewModel.borrarCategoria(categoria) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- CÁMARA ---
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

    // --- ADAPTADOR ---
    class CategoriasAdapter(
        val onBorrar: (Categoria) -> Unit,
        val onEditar: (Categoria) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<Categoria, CategoriasAdapter.Holder>(DiffCallback()) {

        class Holder(val b: ItemCategoriaGestionBinding) : RecyclerView.ViewHolder(b.root)

        class DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Categoria>() {
            override fun areItemsTheSame(a: Categoria, b: Categoria) = a.nombre == b.nombre
            override fun areContentsTheSame(a: Categoria, b: Categoria) = a == b
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(ItemCategoriaGestionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val cat = getItem(position)
            holder.b.tvNombreCat.text = cat.nombre

            if (cat.uriFoto != null) {
                // CASO A: Tiene foto personalizada (Galería/Cámara)
                Glide.with(holder.itemView).load(cat.uriFoto).circleCrop().into(holder.b.ivIconoCat)
                holder.b.ivIconoCat.clearColorFilter() // Por si acaso
            } else {
                // CASO B: Es una categoría por defecto (Comida, Casa...)
                // Usamos el Helper para recuperar su icono original
                val iconoRes = CategoriasHelper.obtenerIcono(cat.nombre)
                holder.b.ivIconoCat.setImageResource(iconoRes)

                // OPCIONAL: Si tus iconos se ven negros, descomenta esta línea para ponerlos negros o gris oscuro
                // holder.b.ivIconoCat.setColorFilter(android.graphics.Color.BLACK)
            }

            holder.b.btnBorrarCat.setOnClickListener { onBorrar(cat) }
            holder.b.btnEditarCat.setOnClickListener { onEditar(cat) }
        }
    }
}