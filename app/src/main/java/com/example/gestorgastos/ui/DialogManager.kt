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
import com.example.gestorgastos.databinding.DialogConfiguracionBinding // Asegúrate de tener este XML

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
        binding.etAmarillo.setText(actualAmarillo.toString().replace(".", ","))
        binding.etRojo.setText(actualRojo.toString().replace(".", ","))

        binding.etAmarillo.addTextChangedListener(EuroTextWatcher(binding.etAmarillo))
        binding.etRojo.addTextChangedListener(EuroTextWatcher(binding.etRojo))

        AlertDialog.Builder(context)
            .setTitle("Configurar Límites")
            .setView(binding.root)
            .setPositiveButton("Guardar") { _, _ ->
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

    // --- TUS FUNCIONES EXISTENTES (SIN CAMBIOS) ---
    fun mostrarAgregarGasto(
        listaCategorias: List<String>,
        uriFotoActual: String?,
        onGuardar: (nombre: String, cantidad: Double, desc: String, cat: String) -> Unit,
        onBorrarFoto: () -> Unit
    ): AlertDialog {
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
                onGuardar(nombre, cant, binding.etDescripcion.text.toString(), cat)
            } else {
                Toast.makeText(context, "Faltan datos", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        return builder.show()
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
                val gastoEditado = gasto.copy(
                    nombre = nombre,
                    cantidad = cant,
                    descripcion = binding.etDescripcion.text.toString(),
                    categoria = nuevaCat
                )
                onActualizar(gastoEditado)
            }
        }
        builder.setNegativeButton("Cancelar", null)
        return builder.show()
    }

    private fun configurarPreviewFoto(binding: DialogAgregarGastoBinding, uri: String?, onBorrar: () -> Unit) {

        // 1. CONFIGURACIÓN DEL CLICK (SIEMPRE ACTIVA)
        // Definimos qué hace el botón X, independientemente de si está visible o no ahora mismo.
        binding.btnBorrarFoto.setOnClickListener {
            onBorrar() // 1. Avisamos al Main para que limpie la variable

            // 2. Reseteamos la UI del diálogo visualmente
            binding.ivPreviewFoto.setImageResource(android.R.drawable.ic_menu_camera) // Icono por defecto
            binding.ivPreviewFoto.scaleType = ImageView.ScaleType.CENTER_INSIDE
            binding.ivPreviewFoto.clearColorFilter()

            // Calculamos padding (si tienes el dimen definido úsalo, si no, usa 20dp a ojo)
            // val padding = context.resources.getDimensionPixelSize(R.dimen.preview_padding_small)
            val padding = 20 // Valor seguro por si no tienes el dimens.xml a mano
            binding.ivPreviewFoto.setPadding(padding, padding, padding, padding)

            binding.ivPreviewFoto.setColorFilter(Color.parseColor("#888888"))
            binding.ivPreviewFoto.setOnClickListener(null) // Quitamos el zoom

            binding.btnBorrarFoto.visibility = View.GONE // Ocultamos la X
        }

        // 2. ESTADO INICIAL VISUAL
        if (uri != null) {
            // Si hay foto inicial (Editar), la cargamos
            Glide.with(context).load(uri).centerCrop().into(binding.ivPreviewFoto)
            binding.ivPreviewFoto.setPadding(0, 0, 0, 0)
            binding.ivPreviewFoto.clearColorFilter()
            binding.btnBorrarFoto.visibility = View.VISIBLE
            binding.ivPreviewFoto.setOnClickListener { onImageClick?.invoke(uri) }
        } else {
            // Si no hay foto inicial (Crear), ocultamos la X
            binding.btnBorrarFoto.visibility = View.GONE
        }
    }
}