package com.example.gestorgastos.ui

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.gestorgastos.R
import com.example.gestorgastos.data.Gasto
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import java.time.Instant
import java.time.ZoneId

class ChartManager(
    private val context: Context,
    private val barChart: BarChart,
    private val pieChart: PieChart,
    // Callback para cuando se selecciona un quesito
    private val onCategorySelected: (String?) -> Unit
) {

    // --- BAR CHART ---
    fun setupBarChart() {
        barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setFitBars(true)
            animateY(1500)

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.textColor = ContextCompat.getColor(context, android.R.color.darker_gray)

            axisRight.isEnabled = false
            axisLeft.setDrawGridLines(true)
            axisLeft.gridColor = ContextCompat.getColor(context, R.color.gris_fondo)
            axisLeft.axisMinimum = 0f
        }
    }

    fun actualizarBarChart(listaGastos: List<Gasto>, limiteRojo: Double, limiteAmarillo: Double) {
        if (listaGastos.isEmpty()) {
            barChart.clear()
            return
        }

        val gastosPorDia = mutableMapOf<Int, Double>()
        for (gasto in listaGastos) {
            val fecha = Instant.ofEpochMilli(gasto.fecha).atZone(ZoneId.systemDefault()).toLocalDate()
            gastosPorDia[fecha.dayOfMonth] = (gastosPorDia[fecha.dayOfMonth] ?: 0.0) + gasto.cantidad
        }

        val entradas = ArrayList<BarEntry>()
        val colores = ArrayList<Int>()
        val colorVerde = ContextCompat.getColor(context, R.color.alerta_verde)
        val colorAmarillo = ContextCompat.getColor(context, R.color.alerta_amarillo)
        val colorRojo = ContextCompat.getColor(context, R.color.alerta_rojo)

        val umbralDiarioRojo = limiteRojo / 20.0
        val umbralDiarioAmarillo = limiteAmarillo / 20.0

        // Asumimos mes actual (o el máximo día registrado para simplificar lógica visual)
        val maxDia = gastosPorDia.keys.maxOrNull() ?: 30

        for (i in 1..maxDia) {
            val total = gastosPorDia[i]?.toFloat() ?: 0f
            entradas.add(BarEntry(i.toFloat(), total))
            when {
                total >= umbralDiarioRojo -> colores.add(colorRojo)
                total >= umbralDiarioAmarillo -> colores.add(colorAmarillo)
                else -> colores.add(colorVerde)
            }
        }

        val dataSet = BarDataSet(entradas, "Gastos").apply {
            colors = colores
            valueTextSize = 11f
            valueTextColor = ContextCompat.getColor(context, android.R.color.black)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    if (value > 0) Formato.formatearMoneda(value.toDouble()) else ""
            }
        }

        barChart.data = BarData(dataSet).apply { barWidth = 0.6f }
        barChart.invalidate()
    }

    // --- PIE CHART ---
    fun setupPieChart() {
        pieChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setHoleColor(Color.TRANSPARENT)
            isDrawHoleEnabled = true
            holeRadius = 40f
            transparentCircleRadius = 50f
            setCenterText("Gastos\nPor Categoría")
            setCenterTextSize(15f)
            setCenterTextColor(Color.GRAY)
            // Usamos dimens si estuviera en XML, aquí hardcodeamos para simplificar la lógica visual
            setExtraOffsets(25f, 10f, 25f, 10f)
            setDrawEntryLabels(true)
            setEntryLabelColor(Color.BLACK)
            setEntryLabelTextSize(11f)
            isRotationEnabled = true
            dragDecelerationFrictionCoef = 0.9f
            animateY(1400, Easing.EaseOutBounce)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    val pieEntry = e as? PieEntry ?: return
                    val cantidad = Formato.formatearMoneda(pieEntry.value.toDouble())
                    centerText = "${pieEntry.label}\n$cantidad"
                    setCenterTextSize(16f)
                    setCenterTextColor(Color.BLACK)
                    onCategorySelected(pieEntry.label)
                }

                override fun onNothingSelected() {
                    centerText = "Gastos\nPor Categoría"
                    setCenterTextSize(14f)
                    setCenterTextColor(Color.GRAY)
                    onCategorySelected(null)
                }
            })
        }
    }

    fun actualizarPieChart(listaGastos: List<Gasto>) {
        if (listaGastos.isEmpty()) {
            pieChart.clear()
            return
        }
        val mapaCategorias = listaGastos.groupBy { it.categoria }
            .mapValues { entry -> entry.value.sumOf { it.cantidad } }

        val entradas = ArrayList<PieEntry>()
        mapaCategorias.forEach { (nombre, total) ->
            entradas.add(PieEntry(total.toFloat(), nombre))
        }

        val dataSet = PieDataSet(entradas, "").apply {
            sliceSpace = 3f
            selectionShift = 5f
            colors = ColorTemplate.MATERIAL_COLORS.toList() +
                    ColorTemplate.JOYFUL_COLORS.toList() +
                    ColorTemplate.COLORFUL_COLORS.toList()
            yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
            valueLineColor = Color.BLACK
            valueLineWidth = 1f
            valueLinePart1Length = 0.4f
            valueLinePart2Length = 0.5f
            valueLinePart1OffsetPercentage = 80f
        }

        val data = PieData(dataSet).apply {
            setValueTextSize(11f)
            setValueTextColor(Color.BLACK)
            setValueFormatter(object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = Formato.formatearMoneda(value.toDouble())
            })
        }

        pieChart.data = data
        pieChart.highlightValues(null)
        pieChart.centerText = "Gastos\nPor Categoría"
        pieChart.invalidate()
    }
}