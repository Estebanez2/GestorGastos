package com.example.gestorgastos.ui

import android.app.DatePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.gestorgastos.data.FiltroBusqueda
import com.example.gestorgastos.data.ModoFiltroFecha
import com.example.gestorgastos.databinding.DialogBuscadorBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BuscadorManager(private val context: Context) {

    fun mostrarBuscador(
        listaCategorias: List<String>,
        filtroActual: FiltroBusqueda?,
        onBuscar: (FiltroBusqueda) -> Unit
    ) {
        val binding = DialogBuscadorBinding.inflate(LayoutInflater.from(context))

        // --- 1. CONFIGURACIÓN SPINNER ---
        val categoriasConTodas = mutableListOf("Todas")
        categoriasConTodas.addAll(listaCategorias)
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, categoriasConTodas)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerBusquedaCat.adapter = adapter

        // --- 2. VARIABLES DE ESTADO ---
        // Opción A: Días (1-31)
        var diaInicioSel: Int? = filtroActual?.diaInicio
        var diaFinSel: Int? = filtroActual?.diaFin

        // Opción B: Fechas Completas (Long)
        var fechaAbsInicio: Long? = filtroActual?.fechaInicioAbs
        var fechaAbsFin: Long? = filtroActual?.fechaFinAbs

        // --- 3. FUNCIONES DE ACTUALIZACIÓN UI ---
        fun actualizarVistas() {
            // A. Días
            binding.tvDiaInicio.text = diaInicioSel?.let { "Día $it" } ?: "Día Inicio"
            binding.tvDiaFin.text = diaFinSel?.let { "Día $it" } ?: "Día Fin"
            binding.ivBorrarDias.visibility = if (diaInicioSel != null || diaFinSel != null) View.VISIBLE else View.GONE

            // B. Fechas Absolutas
            binding.tvFechaAbsInicio.text = fechaAbsInicio?.let { formatearFecha(it) } ?: "dd/mm/aaaa"
            binding.tvFechaAbsFin.text = fechaAbsFin?.let { formatearFecha(it) } ?: "dd/mm/aaaa"
            binding.ivBorrarFechasAbs.visibility = if (fechaAbsInicio != null || fechaAbsFin != null) View.VISIBLE else View.GONE
        }

        // --- 4. CARGAR DATOS EXISTENTES ---
        if (filtroActual != null) {
            binding.etBusquedaNombre.setText(filtroActual.nombre)
            binding.etBusquedaDesc.setText(filtroActual.descripcion)
            binding.etBusquedaMin.setText(filtroActual.precioMin?.toString()?.replace(".", ",") ?: "")
            binding.etBusquedaMax.setText(filtroActual.precioMax?.toString()?.replace(".", ",") ?: "")
            if (filtroActual.categoria != null) {
                val idx = categoriasConTodas.indexOf(filtroActual.categoria)
                if (idx >= 0) binding.spinnerBusquedaCat.setSelection(idx)
            }

            // Seleccionar RadioButton correcto
            when (filtroActual.modoFecha) {
                ModoFiltroFecha.MES_ACTUAL -> binding.rbMesActual.isChecked = true
                ModoFiltroFecha.RANGO_FECHAS -> binding.rbRangoAbsoluto.isChecked = true
                ModoFiltroFecha.TODOS -> binding.rbTodos.isChecked = true
            }
        }
        actualizarVistas()

        // --- 5. LÓGICA RADIO GROUP (VISIBILIDAD) ---
        binding.rgModoFecha.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.rbMesActual.id -> {
                    binding.layoutFechasDias.visibility = View.VISIBLE
                    binding.layoutFechasAbsolutas.visibility = View.GONE
                }
                binding.rbRangoAbsoluto.id -> {
                    binding.layoutFechasDias.visibility = View.GONE
                    binding.layoutFechasAbsolutas.visibility = View.VISIBLE
                }
                binding.rbTodos.id -> {
                    binding.layoutFechasDias.visibility = View.GONE
                    binding.layoutFechasAbsolutas.visibility = View.GONE
                }
            }
        }
        // Disparar manualmente la primera vez para setear visibilidad
        binding.rgModoFecha.check(binding.rgModoFecha.checkedRadioButtonId)


        // --- 6. LISTENERS (MODO DÍAS 1-31) ---
        binding.tvDiaInicio.setOnClickListener {
            mostrarSelectorDia("Día Inicio", diaInicioSel) { d -> diaInicioSel = d; actualizarVistas() }
        }
        binding.tvDiaFin.setOnClickListener {
            mostrarSelectorDia("Día Fin", diaFinSel) { d -> diaFinSel = d; actualizarVistas() }
        }
        binding.ivBorrarDias.setOnClickListener { diaInicioSel = null; diaFinSel = null; actualizarVistas() }


        // --- 7. LISTENERS (MODO FECHAS COMPLETAS) ---
        binding.tvFechaAbsInicio.setOnClickListener {
            pickDate { time -> fechaAbsInicio = time; actualizarVistas() }
        }
        binding.tvFechaAbsFin.setOnClickListener {
            pickDate { time ->
                // Ajustamos a última hora del día
                val c = Calendar.getInstance()
                c.timeInMillis = time
                c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59)
                fechaAbsFin = c.timeInMillis
                actualizarVistas()
            }
        }
        binding.ivBorrarFechasAbs.setOnClickListener { fechaAbsInicio = null; fechaAbsFin = null; actualizarVistas() }


        // --- 8. CREAR DIÁLOGO Y VALIDAR ---
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setPositiveButton("Buscar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            // Validaciones según el modo activo
            val modoSeleccionado = when (binding.rgModoFecha.checkedRadioButtonId) {
                binding.rbMesActual.id -> ModoFiltroFecha.MES_ACTUAL
                binding.rbRangoAbsoluto.id -> ModoFiltroFecha.RANGO_FECHAS
                else -> ModoFiltroFecha.TODOS
            }

            if (modoSeleccionado == ModoFiltroFecha.MES_ACTUAL) {
                // Validación de días (parejas obligatorias)
                if ((diaInicioSel != null && diaFinSel == null) || (diaInicioSel == null && diaFinSel != null)) {
                    Toast.makeText(context, "En modo Mes Actual, selecciona ambos días o ninguno", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (diaInicioSel != null && diaFinSel != null && diaInicioSel!! > diaFinSel!!) {
                    Toast.makeText(context, "El día inicio debe ser menor al fin", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } else if (modoSeleccionado == ModoFiltroFecha.RANGO_FECHAS) {
                // Validación de fechas completas
                if (fechaAbsInicio == null || fechaAbsFin == null) {
                    Toast.makeText(context, "En modo Rango, debes seleccionar Fecha Desde y Hasta", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                if (fechaAbsInicio!! > fechaAbsFin!!) {
                    Toast.makeText(context, "La fecha de inicio debe ser anterior al fin", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }

            // Recoger datos comunes
            val nombre = binding.etBusquedaNombre.text.toString().ifEmpty { null }
            val desc = binding.etBusquedaDesc.text.toString().ifEmpty { null }
            val catTxt = binding.spinnerBusquedaCat.selectedItem.toString()
            val categoria = if (catTxt == "Todas") null else catTxt
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
                fechaInicioAbs = fechaAbsInicio,
                fechaFinAbs = fechaAbsFin,
                modoFecha = modoSeleccionado // IMPORTANTE: Pasamos el modo
            )
            onBuscar(filtro)
            dialog.dismiss()
        }
    }

    // Helpers
    private fun mostrarSelectorDia(titulo: String, preSelected: Int?, onSelect: (Int) -> Unit) {
        val picker = NumberPicker(context).apply { minValue = 1; maxValue = 31 }
        if(preSelected != null) picker.value = preSelected
        AlertDialog.Builder(context).setTitle(titulo).setView(picker)
            .setPositiveButton("OK") { _, _ -> onSelect(picker.value) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun pickDate(onDate: (Long) -> Unit) {
        val c = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            c.set(y, m, d, 0, 0, 0)
            onDate(c.timeInMillis)
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun formatearFecha(milis: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(milis))
}