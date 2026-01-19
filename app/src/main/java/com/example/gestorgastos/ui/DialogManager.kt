package com.example.gestorgastos.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.example.gestorgastos.R
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.DialogAgregarGastoBinding

class DialogManager(private val context: Context) {

    var onCameraRequested: (() -> Unit)? = null
    var onGalleryRequested: (() -> Unit)? = null
    var onImageClick: ((String) -> Unit)? = null

    fun mostrarAgregarGasto(
        listaCategorias: List<String>,
        uriFotoActual: String?,
        // CORRECCIÓN: Quitamos 'uri' de aquí. Usaremos la variable del Main.
        onGuardar: (nombre: String, cantidad: Double, desc: String, cat: String) -> Unit,
        onBorrarFoto: () -> Unit
    ): AlertDialog { // Retorna AlertDialog

        val builder = AlertDialog.Builder(context)
        val binding = DialogAgregarGastoBinding.inflate(LayoutInflater.from(context))

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listaCategorias)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategoria.adapter = adapter

        binding.etCantidad.addTextChangedListener(EuroTextWatcher(binding.etCantidad))
        configurarPreviewFoto(binding, uriFotoActual, onBorrarFoto)

        binding.btnCamara.setOnClickListener { onCameraRequested?.invoke() }
        binding.btnGaleria.setOnClickListener { onGalleryRequested?.invoke() }

        builder.setView(binding.root)
        builder.setPositiveButton("Guardar") { _, _ ->
            val nombre = binding.etNombre.text.toString()
            val cantidadStr = binding.etCantidad.text.toString().replace(".", "").replace(",", ".")
            val cat = if (listaCategorias.isNotEmpty()) binding.spinnerCategoria.selectedItem.toString() else "Otros"

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cant = cantidadStr.toDoubleOrNull() ?: 0.0
                // Llamamos al callback SIN la foto (el Main ya la tiene)
                onGuardar(nombre, cant, binding.etDescripcion.text.toString(), cat)
            } else {
                Toast.makeText(context, "Faltan datos", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        return builder.show() // Devuelve el diálogo
    }

    fun mostrarEditarGasto(
        gasto: Gasto,
        listaCategorias: List<String>,
        uriFotoActual: String?,
        onActualizar: (Gasto) -> Unit,
        onBorrarFoto: () -> Unit
    ): AlertDialog { // CORRECCIÓN: Añadido tipo de retorno explícito

        val builder = AlertDialog.Builder(context)
        val binding = DialogAgregarGastoBinding.inflate(LayoutInflater.from(context))
        binding.tvTituloDialogo.text = "Editar Gasto"

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listaCategorias)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategoria.adapter = adapter

        val index = listaCategorias.indexOf(gasto.categoria)
        if (index >= 0) binding.spinnerCategoria.setSelection(index)

        binding.etNombre.setText(gasto.nombre)
        binding.etCantidad.setText(gasto.cantidad.toString().replace(".", ","))
        binding.etCantidad.addTextChangedListener(EuroTextWatcher(binding.etCantidad))
        binding.etDescripcion.setText(gasto.descripcion)

        configurarPreviewFoto(binding, uriFotoActual, onBorrarFoto)

        binding.btnCamara.setOnClickListener { onCameraRequested?.invoke() }
        binding.btnGaleria.setOnClickListener { onGalleryRequested?.invoke() }

        builder.setView(binding.root)
        builder.setPositiveButton("Actualizar") { _, _ ->
            val nombre = binding.etNombre.text.toString()
            val cantidadStr = binding.etCantidad.text.toString().replace(".", "").replace(",", ".")
            val nuevaCat = if (listaCategorias.isNotEmpty()) binding.spinnerCategoria.selectedItem.toString() else gasto.categoria

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cant = cantidadStr.toDoubleOrNull() ?: 0.0
                // Creamos copia solo con datos de texto, la foto la pone el Main
                val gastoEditado = gasto.copy(
                    nombre = nombre,
                    cantidad = cant,
                    descripcion = binding.etDescripcion.text.toString(),
                    categoria = nuevaCat
                    // No tocamos la URI aquí
                )
                onActualizar(gastoEditado)
            }
        }
        builder.setNegativeButton("Cancelar", null)

        return builder.show() // CORRECCIÓN: Añadido return para que findViewById funcione
    }

    private fun configurarPreviewFoto(binding: DialogAgregarGastoBinding, uri: String?, onBorrar: () -> Unit) {
        if (uri != null) {
            Glide.with(context).load(uri).centerCrop().into(binding.ivPreviewFoto)
            binding.ivPreviewFoto.setPadding(0, 0, 0, 0)
            binding.ivPreviewFoto.clearColorFilter()
            binding.btnBorrarFoto.visibility = View.VISIBLE
            binding.ivPreviewFoto.setOnClickListener { onImageClick?.invoke(uri) }

            binding.btnBorrarFoto.setOnClickListener {
                onBorrar()
                binding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera)
                binding.ivPreviewFoto.scaleType = ImageView.ScaleType.CENTER_INSIDE
                val padding = context.resources.getDimensionPixelSize(R.dimen.preview_padding_small)
                binding.ivPreviewFoto.setPadding(padding, padding, padding, padding)
                binding.ivPreviewFoto.setColorFilter(Color.parseColor("#888888"))
                binding.ivPreviewFoto.setOnClickListener(null)
                binding.btnBorrarFoto.visibility = View.GONE
            }
        } else {
            binding.btnBorrarFoto.visibility = View.GONE
        }
    }
}