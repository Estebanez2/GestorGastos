package com.example.gestorgastos.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.gestorgastos.data.FiltroBusqueda
import com.example.gestorgastos.databinding.DialogBuscadorBinding

class BuscadorManager(private val context: Context) {

    fun mostrarBuscador(
        listaCategorias: List<String>,
        filtroActual: FiltroBusqueda?,
        onBuscar: (FiltroBusqueda) -> Unit
    ) {
        val builder = AlertDialog.Builder(context)
        val binding = DialogBuscadorBinding.inflate(LayoutInflater.from(context))

        // Configurar Spinner
        val categoriasConTodas = mutableListOf("Todas")
        categoriasConTodas.addAll(listaCategorias)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, categoriasConTodas)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBusquedaCat.adapter = adapter

        // Variables para los DÍAS
        var diaInicioSel: Int? = filtroActual?.diaInicio
        var diaFinSel: Int? = filtroActual?.diaFin

        fun actualizarUIFechas() {
            val hayFechas = diaInicioSel != null || diaFinSel != null
            binding.tvBusquedaFechaInicio.text = if (diaInicioSel != null) "Día $diaInicioSel" else "Día inicio"
            binding.tvBusquedaFechaFin.text = if (diaFinSel != null) "Día $diaFinSel" else "Día fin"
            binding.ivBorrarFechas.visibility = if (hayFechas) View.VISIBLE else View.GONE
        }

        // --- RELLENAR DATOS ---
        if (filtroActual != null) {
            binding.etBusquedaNombre.setText(filtroActual.nombre)
            binding.etBusquedaDesc.setText(filtroActual.descripcion)
            binding.etBusquedaMin.setText(filtroActual.precioMin?.toString()?.replace(".", ",") ?: "")
            binding.etBusquedaMax.setText(filtroActual.precioMax?.toString()?.replace(".", ",") ?: "")

            if (filtroActual.categoria != null) {
                val index = categoriasConTodas.indexOf(filtroActual.categoria)
                if (index >= 0) binding.spinnerBusquedaCat.setSelection(index)
            }
            actualizarUIFechas()
            binding.cbBuscarEnTodo.isChecked = filtroActual.buscarEnTodo
        }

        // --- LÓGICA CHECKBOX ---
        binding.cbBuscarEnTodo.setOnCheckedChangeListener { _, isChecked ->
            val alpha = if (isChecked) 0.5f else 1.0f
            val enabled = !isChecked

            binding.tvBusquedaFechaInicio.isEnabled = enabled
            binding.tvBusquedaFechaInicio.alpha = alpha
            binding.tvBusquedaFechaFin.isEnabled = enabled
            binding.tvBusquedaFechaFin.alpha = alpha

            if (isChecked) binding.ivBorrarFechas.visibility = View.GONE
            else actualizarUIFechas()
        }

        if (binding.cbBuscarEnTodo.isChecked) {
            binding.tvBusquedaFechaInicio.isEnabled = false
            binding.tvBusquedaFechaInicio.alpha = 0.5f
            binding.tvBusquedaFechaFin.isEnabled = false
            binding.tvBusquedaFechaFin.alpha = 0.5f
            binding.ivBorrarFechas.visibility = View.GONE
        }

        // --- LISTENERS (CAMBIO AQUÍ: Pasamos el valor actual) ---
        binding.tvBusquedaFechaInicio.setOnClickListener {
            // Pasamos 'diaInicioSel' para que el selector se abra en ese número
            mostrarSelectorDia("Día Inicio", diaInicioSel) { dia ->
                diaInicioSel = dia
                actualizarUIFechas()
            }
        }
        binding.tvBusquedaFechaFin.setOnClickListener {
            // Pasamos 'diaFinSel'
            mostrarSelectorDia("Día Fin", diaFinSel) { dia ->
                diaFinSel = dia
                actualizarUIFechas()
            }
        }

        binding.ivBorrarFechas.setOnClickListener {
            diaInicioSel = null
            diaFinSel = null
            actualizarUIFechas()
        }

        builder.setView(binding.root)
        builder.setPositiveButton("Buscar", null)
        builder.setNegativeButton("Cancelar", null)

        val dialog = builder.create()
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val buscarEnTodo = binding.cbBuscarEnTodo.isChecked

            if (!buscarEnTodo) {
                if ((diaInicioSel != null && diaFinSel == null) || (diaInicioSel == null && diaFinSel != null)) {
                    Toast.makeText(context, "Por favor selecciona Día Inicio Y Día Fin", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (diaInicioSel != null && diaFinSel != null && diaInicioSel!! > diaFinSel!!) {
                    Toast.makeText(context, "El día de inicio no puede ser mayor al fin", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            val nombre = binding.etBusquedaNombre.text.toString().ifEmpty { null }
            val desc = binding.etBusquedaDesc.text.toString().ifEmpty { null }
            val catTexto = binding.spinnerBusquedaCat.selectedItem.toString()
            val categoria = if (catTexto == "Todas") null else catTexto
            val min = binding.etBusquedaMin.text.toString().replace(",", ".").toDoubleOrNull()
            val max = binding.etBusquedaMax.text.toString().replace(",", ".").toDoubleOrNull()

            val filtro = FiltroBusqueda(
                nombre = nombre,
                descripcion = desc,
                categoria = categoria,
                precioMin = min,
                precioMax = max,
                diaInicio = diaInicioSel,
                diaFin = diaFinSel,
                buscarEnTodo = buscarEnTodo
            )
            onBuscar(filtro)
            dialog.dismiss()
        }
    }

    // --- HELPER ACTUALIZADO ---
    // Ahora recibe 'diaPreseleccionado' (puede ser null)
    private fun mostrarSelectorDia(titulo: String, diaPreseleccionado: Int?, onDiaSelected: (Int) -> Unit) {
        val numberPicker = NumberPicker(context)
        numberPicker.minValue = 1
        numberPicker.maxValue = 31

        // Si ya había un día seleccionado, ponemos el picker en ese valor
        if (diaPreseleccionado != null) {
            numberPicker.value = diaPreseleccionado
        }

        AlertDialog.Builder(context)
            .setTitle(titulo)
            .setView(numberPicker)
            .setPositiveButton("Aceptar") { _, _ ->
                onDiaSelected(numberPicker.value)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}