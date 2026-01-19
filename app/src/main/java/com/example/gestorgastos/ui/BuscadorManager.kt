package com.example.gestorgastos.ui

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import com.example.gestorgastos.data.FiltroBusqueda
import com.example.gestorgastos.databinding.DialogBuscadorBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BuscadorManager(private val context: Context) {

    fun mostrarBuscador(
        listaCategorias: List<String>,
        filtroActual: FiltroBusqueda?, // Recibimos filtro para editar
        onBuscar: (FiltroBusqueda) -> Unit
    ) {
        val builder = AlertDialog.Builder(context)
        // Inflamos el XML nuevo
        val binding = DialogBuscadorBinding.inflate(LayoutInflater.from(context))

        // 1. Configurar Spinner
        val categoriasConTodas = mutableListOf("Todas")
        categoriasConTodas.addAll(listaCategorias)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, categoriasConTodas)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBusquedaCat.adapter = adapter

        // Variables temporales
        var fechaInicioSel: Long? = filtroActual?.fechaInicio
        var fechaFinSel: Long? = filtroActual?.fechaFin

        // 2. RELLENAR DATOS (Si es Modificar)
        if (filtroActual != null) {
            binding.etBusquedaNombre.setText(filtroActual.nombre)
            binding.etBusquedaDesc.setText(filtroActual.descripcion) // Campo nuevo
            binding.etBusquedaMin.setText(filtroActual.precioMin?.toString()?.replace(".", ",") ?: "")
            binding.etBusquedaMax.setText(filtroActual.precioMax?.toString()?.replace(".", ",") ?: "")
            binding.cbBuscarEnTodo.isChecked = filtroActual.buscarEnTodo // Checkbox nuevo

            // Categoría
            if (filtroActual.categoria != null) {
                val index = categoriasConTodas.indexOf(filtroActual.categoria)
                if (index >= 0) binding.spinnerBusquedaCat.setSelection(index)
            }

            // Fechas visuales
            if (fechaInicioSel != null) binding.tvBusquedaFechaInicio.text = formatearFecha(fechaInicioSel!!)
            if (fechaFinSel != null) binding.tvBusquedaFechaFin.text = formatearFecha(fechaFinSel!!)
        }

        // 3. Listeners de Fecha (IDs del nuevo XML)
        binding.tvBusquedaFechaInicio.setOnClickListener {
            pickDate { time ->
                fechaInicioSel = time
                binding.tvBusquedaFechaInicio.text = formatearFecha(time)
            }
        }
        binding.tvBusquedaFechaFin.setOnClickListener {
            pickDate { time ->
                // Final del día
                val c = Calendar.getInstance()
                c.timeInMillis = time
                c.set(Calendar.HOUR_OF_DAY, 23)
                c.set(Calendar.MINUTE, 59)
                c.set(Calendar.SECOND, 59)
                fechaFinSel = c.timeInMillis
                binding.tvBusquedaFechaFin.text = formatearFecha(time)
            }
        }

        builder.setView(binding.root)
        val dialog = builder.create()

        // 4. Botón Buscar (Generalmente es el positivo del builder, o un botón en el XML)
        // Si usas botón del AlertDialog:
        builder.setPositiveButton("Buscar") { _, _ ->
            val nombre = binding.etBusquedaNombre.text.toString().ifEmpty { null }
            val desc = binding.etBusquedaDesc.text.toString().ifEmpty { null }

            val catTexto = binding.spinnerBusquedaCat.selectedItem.toString()
            val categoria = if (catTexto == "Todas") null else catTexto

            val minStr = binding.etBusquedaMin.text.toString().replace(",", ".")
            val maxStr = binding.etBusquedaMax.text.toString().replace(",", ".")
            val min = minStr.toDoubleOrNull()
            val max = maxStr.toDoubleOrNull()

            val buscarEnTodo = binding.cbBuscarEnTodo.isChecked

            val filtro = FiltroBusqueda(
                nombre = nombre,
                descripcion = desc,
                categoria = categoria,
                precioMin = min,
                precioMax = max,
                fechaInicio = fechaInicioSel,
                fechaFin = fechaFinSel,
                buscarEnTodo = buscarEnTodo
            )
            onBuscar(filtro)
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun pickDate(onDateSelected: (Long) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            c.set(y, m, d, 0, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            onDateSelected(c.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun formatearFecha(millis: Long): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))
    }
}