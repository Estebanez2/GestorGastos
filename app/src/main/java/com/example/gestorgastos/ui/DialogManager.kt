package com.example.gestorgastos.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.DialogAgregarGastoBinding
import com.example.gestorgastos.databinding.DialogConfiguracionBinding

class DialogManager(private val context: Context) {

    var onCameraRequested: (() -> Unit)? = null
    var onGalleryRequested: (() -> Unit)? = null
    var onImageClick: ((String) -> Unit)? = null

    // --- NUEVO: CONFIGURACIÓN ---
    fun mostrarConfiguracion(
        actualAmarillo: Double,
        actualRojo: Double,
        onGuardar: (Double, Double) -> Unit,
        onCambiarMoneda: () -> Unit
    ) {
        val binding = DialogConfiguracionBinding.inflate(LayoutInflater.from(context))

        // --- CAMBIO AQUÍ: Usamos la función de formateo para cargar los datos con puntos y comas ---
        binding.etAmarillo.setText(Formato.formatearParaEditText(actualAmarillo))
        binding.etRojo.setText(Formato.formatearParaEditText(actualRojo))

        binding.etAmarillo.addTextChangedListener(EuroTextWatcher(binding.etAmarillo))
        binding.etRojo.addTextChangedListener(EuroTextWatcher(binding.etRojo))

        AlertDialog.Builder(context)
            .setTitle("Configurar Límites")
            .setView(binding.root)
            .setPositiveButton("Guardar") { _, _ ->
                // Al guardar, quitamos los puntos de miles para que Double.parse funcione
                val am = binding.etAmarillo.text.toString().replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0
                val ro = binding.etRojo.text.toString().replace(".", "").replace(",", ".").toDoubleOrNull() ?: 0.0

                if (am < ro) onGuardar(am, ro) else Toast.makeText(context, "El límite amarillo debe ser menor al rojo", Toast.LENGTH_LONG).show()
            }
            .setNeutralButton("Divisa") { _, _ -> onCambiarMoneda() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // --- NUEVO: SELECTOR MONEDA ---
    fun mostrarSelectorMoneda(onMonedaCambiada: () -> Unit) {
        val monedas = arrayOf("Euro (€)", "Dólar ($)", "Libra (£)")
        AlertDialog.Builder(context)
            .setTitle("Elige divisa")
            .setItems(monedas) { _, i ->
                val codigo = when(i) { 0 -> "EUR"; 1 -> "USD"; else -> "GBP" }
                Formato.cambiarDivisa(codigo)
                onMonedaCambiada()
                Toast.makeText(context, "Moneda cambiada", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // --- NUEVO: CONFIRMACIÓN BORRAR ---
    fun mostrarConfirmacionBorrado(gasto: Gasto, onConfirmar: () -> Unit, onCancelar: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Borrar Gasto")
            .setMessage("¿Estás seguro de borrar '${gasto.nombre}'?")
            .setPositiveButton("Borrar") { _, _ -> onConfirmar() }
            .setNegativeButton("Cancelar") { _, _ -> onCancelar() }
            .setOnCancelListener { onCancelar() }
            .show()
    }

    // --- TUS FUNCIONES EXISTENTES ---
    fun mostrarAgregarGasto(
        listaCategorias: List<String>,
        uriFotoActual: String?,
        onGuardar: (nombre: String, cantidad: Double, desc: String, cat: String) -> Unit,
        onBorrarFoto: () -> Unit
    ): AlertDialog {
        val builder = AlertDialog.Builder(context)
        val binding = DialogAgregarGastoBinding.inflate(LayoutInflater.from(context))

        // 1. Configurar el adaptador para el AutoCompleteTextView
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, listaCategorias)
        binding.actvCategoria.setAdapter(adapter)

        // Opcional: Si quieres que aparezca seleccionada la primera categoría por defecto si no hay nada en el XML
        if (listaCategorias.isNotEmpty() && binding.actvCategoria.text.isEmpty()) {
            binding.actvCategoria.setText(listaCategorias[0], false)
        }

        binding.etCantidad.addTextChangedListener(EuroTextWatcher(binding.etCantidad))
        configurarPreviewFoto(binding, uriFotoActual, onBorrarFoto)

        binding.btnCamara.setOnClickListener { onCameraRequested?.invoke() }
        binding.btnGaleria.setOnClickListener { onGalleryRequested?.invoke() }

        builder.setView(binding.root)
        builder.setPositiveButton("Guardar") { _, _ ->
            val nombre = binding.etNombre.text.toString()
            val cantidadStr = binding.etCantidad.text.toString().replace(".", "").replace(",", ".")

            // 2. Obtener el texto directamente del AutoCompleteTextView
            val cat = binding.actvCategoria.text.toString()

            // Validación simple: Si está vacío, asignar "Otros" o la primera categoría
            val categoriaFinal = if (cat.isNotEmpty()) cat else "Otros"

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cant = cantidadStr.toDoubleOrNull() ?: 0.0
                onGuardar(nombre, cant, binding.etDescripcion.text.toString(), categoriaFinal)
            } else {
                Toast.makeText(context, "Faltan datos", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        return dialog
    }

    fun mostrarEditarGasto(
        gasto: Gasto,
        listaCategorias: List<String>,
        uriFotoActual: String?,
        onActualizar: (Gasto) -> Unit,
        onBorrarFoto: () -> Unit
    ): AlertDialog {
        val builder = AlertDialog.Builder(context)
        val binding = DialogAgregarGastoBinding.inflate(LayoutInflater.from(context))
        binding.tvTituloDialogo.text = "Editar Gasto"

        // 1. Configurar el adaptador
        val adapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, listaCategorias)
        binding.actvCategoria.setAdapter(adapter)

        // 2. Pre-seleccionar la categoría existente
        // El 'false' es vital: evita que se despliegue la lista o se filtre al establecer el texto inicial
        binding.actvCategoria.setText(gasto.categoria, false)

        binding.etNombre.setText(gasto.nombre)
        binding.etCantidad.setText(Formato.formatearParaEditText(gasto.cantidad))
        binding.etCantidad.addTextChangedListener(EuroTextWatcher(binding.etCantidad))
        binding.etDescripcion.setText(gasto.descripcion)

        configurarPreviewFoto(binding, uriFotoActual, onBorrarFoto)

        binding.btnCamara.setOnClickListener { onCameraRequested?.invoke() }
        binding.btnGaleria.setOnClickListener { onGalleryRequested?.invoke() }

        builder.setView(binding.root)
        builder.setPositiveButton("Actualizar") { _, _ ->
            val nombre = binding.etNombre.text.toString()
            val cantidadStr = binding.etCantidad.text.toString().replace(".", "").replace(",", ".")

            // 3. Obtener la nueva categoría
            val nuevaCat = binding.actvCategoria.text.toString()

            if (nombre.isNotEmpty() && cantidadStr.isNotEmpty()) {
                val cant = cantidadStr.toDoubleOrNull() ?: 0.0
                val gastoEditado = gasto.copy(
                    nombre = nombre,
                    cantidad = cant,
                    descripcion = binding.etDescripcion.text.toString(),
                    categoria = if (nuevaCat.isNotEmpty()) nuevaCat else gasto.categoria
                )
                onActualizar(gastoEditado)
            }
        }
        builder.setNegativeButton("Cancelar", null)
        val dialog = builder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
        return dialog
    }

    private fun configurarPreviewFoto(binding: DialogAgregarGastoBinding, uri: String?, onBorrar: () -> Unit) {
        binding.btnBorrarFoto.setOnClickListener {
            onBorrar()
            binding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera)
            binding.ivPreviewFoto.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.ivPreviewFoto.clearColorFilter()
            val padding = 20
            binding.ivPreviewFoto.setPadding(padding, padding, padding, padding)
            binding.ivPreviewFoto.setColorFilter(Color.parseColor("#888888"))
            binding.ivPreviewFoto.setOnClickListener(null)
            binding.btnBorrarFoto.visibility = View.GONE
        }

        if (uri != null) {
            Glide.with(context).load(uri).centerCrop().into(binding.ivPreviewFoto)
            binding.ivPreviewFoto.setPadding(0, 0, 0, 0)
            binding.ivPreviewFoto.clearColorFilter()
            binding.btnBorrarFoto.visibility = View.VISIBLE
            binding.ivPreviewFoto.setOnClickListener { onImageClick?.invoke(uri) }
        } else {
            binding.btnBorrarFoto.visibility = View.GONE
        }
    }
}