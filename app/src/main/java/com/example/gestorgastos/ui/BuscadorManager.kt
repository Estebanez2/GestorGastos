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
import java.util.Locale

class BuscadorManager(private val context: Context) {

    private var fechaInicioSel: Long? = null
    private var fechaFinSel: Long? = null

    fun mostrarBuscador(
        listaCategorias: List<String>,
        onBuscar: (FiltroBusqueda) -> Unit
    ) {
        val builder = AlertDialog.Builder(context)
        val binding = DialogBuscadorBinding.inflate(LayoutInflater.from(context))

        // 1. Configurar Spinner Categorías (Añadimos opción "Todas")
        val categoriasConTodas = mutableListOf("Todas")
        categoriasConTodas.addAll(listaCategorias)

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, categoriasConTodas)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCatBusqueda.adapter = adapter

        // 2. Configurar Botones de Fecha
        binding.btnFechaInicio.setOnClickListener {
            pickDate { time ->
                fechaInicioSel = time
                binding.tvFechaInicio.text = formatearFecha(time)
            }
        }
        binding.btnFechaFin.setOnClickListener {
            pickDate { time ->
                // Ajustamos al final del día (23:59:59) para que incluya todo ese día
                val c = Calendar.getInstance()
                c.timeInMillis = time
                c.set(Calendar.HOUR_OF_DAY, 23)
                c.set(Calendar.MINUTE, 59)
                c.set(Calendar.SECOND, 59)
                fechaFinSel = c.timeInMillis
                binding.tvFechaFin.text = formatearFecha(time)
            }
        }

        builder.setView(binding.root)
        val dialog = builder.create()

        // 3. Acción al pulsar BUSCAR
        binding.btnRealizarBusqueda.setOnClickListener {
            // Recogemos datos
            val nombre = binding.etBuscarNombre.text.toString().ifEmpty { null }

            val catSeleccionada = binding.spinnerCatBusqueda.selectedItem.toString()
            val categoria = if (catSeleccionada == "Todas") null else catSeleccionada

            val minStr = binding.etPrecioMin.text.toString().replace(",", ".")
            val maxStr = binding.etPrecioMax.text.toString().replace(",", ".")
            val min = if (minStr.isNotEmpty()) minStr.toDoubleOrNull() else null
            val max = if (maxStr.isNotEmpty()) maxStr.toDoubleOrNull() else null

            // Empaquetamos todo en el objeto Filtro
            val filtro = FiltroBusqueda(
                nombre = nombre,
                categoria = categoria,
                precioMin = min,
                precioMax = max,
                fechaInicio = fechaInicioSel,
                fechaFin = fechaFinSel
            )

            onBuscar(filtro) // Se lo pasamos al Main
            dialog.dismiss()
        }

        dialog.show()
    }

    // Helper para abrir el calendario
    private fun pickDate(onDateSelected: (Long) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            c.set(y, m, d, 0, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            onDateSelected(c.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun formatearFecha(millis: Long): String {
        return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(millis)
    }
}