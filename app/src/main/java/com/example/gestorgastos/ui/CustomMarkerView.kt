package com.example.gestorgastos.ui

import android.content.Context
import android.widget.TextView
import com.example.gestorgastos.R
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CustomMarkerView(context: Context, layoutResource: Int) : MarkerView(context, layoutResource) {

    private val tvFecha: TextView = findViewById(R.id.tvMarkerFecha)
    private val tvCantidad: TextView = findViewById(R.id.tvMarkerCantidad)
    private val dateFormatter = DateTimeFormatter.ofPattern("dd MMM")

    // Se llama cada vez que se redibuja el marcador
    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        if (e == null) return

        val dia = e.x.toInt()
        val cantidad = e.y.toDouble()

        // Calculamos la fecha real basándonos en el día del mes actual
        val fechaActual = LocalDate.now().withDayOfMonth(dia) // Ojo: Asume mes actual, idealmente pasar mes

        tvFecha.text = fechaActual.format(dateFormatter)
        tvCantidad.text = Formato.formatearMoneda(cantidad)

        super.refreshContent(e, highlight)
    }

    // Ajusta la posición para que el globo salga centrado encima de la barra
    override fun getOffset(): MPPointF {
        return MPPointF(-(width / 2).toFloat(), -height.toFloat() - 20)
    }
}