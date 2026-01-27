package com.example.gestorgastos.ui

import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import com.example.gestorgastos.R
import com.example.gestorgastos.data.Gasto
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.ChartTouchListener
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.listener.OnChartGestureListener
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

class ChartManager(
    private val context: Context,
    private val barChart: BarChart,
    private val pieChart: PieChart,
    private val onCategorySelected: (String?) -> Unit,
    private val onBarSelected: (Int?) -> Unit
) {

    // --- BAR CHART ---
    fun setupBarChart() {
        barChart.apply {
            description.isEnabled = false
            legend.isEnabled = false

            // --- 1. CONFIGURACIÓN DE ZOOM Y GESTOS ---
            setTouchEnabled(true)
            isDragEnabled = true
            setScaleEnabled(true)
            setPinchZoom(false) // false es mejor para gráficas de tiempo (solo zoom X)

            // Desactivamos el zoom in automático con doble toque
            isDoubleTapToZoomEnabled = false

            // Agregamos el listener para que el doble toque RESETEE la vista
            onChartGestureListener = object : OnChartGestureListener {
                override fun onChartDoubleTapped(me: MotionEvent?) {
                    fitScreen() // Vuelve a ver el mes completo
                }
                override fun onChartGestureStart(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                override fun onChartGestureEnd(me: MotionEvent?, lastPerformedGesture: ChartTouchListener.ChartGesture?) {}
                override fun onChartLongPressed(me: MotionEvent?) {}
                override fun onChartSingleTapped(me: MotionEvent?) {}
                override fun onChartFling(me1: MotionEvent?, me2: MotionEvent?, velocityX: Float, velocityY: Float) {}
                override fun onChartScale(me: MotionEvent?, scaleX: Float, scaleY: Float) {}
                override fun onChartTranslate(me: MotionEvent?, dX: Float, dY: Float) {}
            }

            // --- 2. LIMPIEZA VISUAL ---
            setDrawGridBackground(false)
            setDrawBorders(false)
            setFitBars(true)
            animateY(1200, Easing.EaseOutCubic)
            extraBottomOffset = 10f
            extraTopOffset = 0f
            minOffset = 0f

            // --- 3. CONFIGURACIÓN EJE X (DÍAS) ---
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLimitLinesBehindData(false)
                granularity = 1f
                textColor = ContextCompat.getColor(context, R.color.purple_200) // O tu color gris oscuro
                textSize = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String = value.toInt().toString()
                }
            }

            axisRight.isEnabled = false

            // --- 4. CONFIGURACIÓN EJE Y (DINERO) ---
            axisLeft.apply {
                setDrawGridLines(true)
                enableGridDashedLine(10f, 10f, 0f) // Rejilla discontinua
                gridColor = Color.parseColor("#E0E0E0")
                setDrawAxisLine(false)
                textColor = ContextCompat.getColor(context, R.color.purple_200)
                axisMinimum = 0f
            }

            // --- 5. AÑADIR MARKER VIEW (GLOBO) ---
            val marker = CustomMarkerView(context, R.layout.marker_view)
            marker.chartView = this
            this.marker = marker

            // Listener de selección (Click en barra)
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    val barEntry = e as? BarEntry ?: return
                    onBarSelected(barEntry.x.toInt())
                }
                override fun onNothingSelected() {
                    onBarSelected(null)
                }
            })
        }
    }

    fun actualizarBarChart(listaGastos: List<Gasto>, limiteRojo: Double, limiteAmarillo: Double, mes: YearMonth, mostrarLimite: Boolean) {
        if (listaGastos.isEmpty()) {
            barChart.clear()
            return
        }

        // 1. Agrupar gastos por día
        val gastosPorDia = mutableMapOf<Int, Double>()
        for (gasto in listaGastos) {
            val fecha = Instant.ofEpochMilli(gasto.fecha).atZone(ZoneId.systemDefault()).toLocalDate()
            gastosPorDia[fecha.dayOfMonth] = (gastosPorDia[fecha.dayOfMonth] ?: 0.0) + gasto.cantidad
        }

        // 2. CÁLCULO REALISTA DE LÍMITES (Basado en días del mes EXACTOS)
        val diasEnElMes = mes.lengthOfMonth()
        val diasActivosEstimados = 12.0
        // El límite diario es el tope mensual dividido entre los días REALES de ese mes
        val umbralDiarioRojo = limiteRojo / diasActivosEstimados
        val umbralDiarioAmarillo = limiteAmarillo / diasActivosEstimados

        // 3. Crear las barras
        val entradas = ArrayList<BarEntry>()
        val colores = ArrayList<Int>()

        val colorVerde = Color.parseColor("#66BB6A")
        val colorAmarillo = Color.parseColor("#FFA726")
        val colorRojo = Color.parseColor("#EF5350")

        // Iteramos hasta el último día que tenga gastos registrados
        val maxDia = gastosPorDia.keys.maxOrNull() ?: diasEnElMes

        for (i in 1..maxDia) {
            val total = gastosPorDia[i]?.toFloat() ?: 0f
            entradas.add(BarEntry(i.toFloat(), total))

            // Colorear según los umbrales diarios calculados
            when {
                total >= umbralDiarioRojo -> colores.add(colorRojo)
                total >= umbralDiarioAmarillo -> colores.add(colorAmarillo)
                else -> colores.add(colorVerde)
            }
        }

        val dataSet = BarDataSet(entradas, "Gastos").apply {
            colors = colores
            setDrawValues(false) // Ocultamos valores (se ven con el Marker)
            highLightAlpha = 50
        }

        // 4. LÍNEAS DE LÍMITE (LimitLines)
        val axisLeft = barChart.axisLeft
        axisLeft.removeAllLimitLines()

        if (mostrarLimite) {
            // Dibujamos la línea del tope diario
            val textoLimite = "Máx Diario ${Formato.formatearMoneda(umbralDiarioRojo)}"
            val lineaRoja = LimitLine(umbralDiarioRojo.toFloat(), textoLimite).apply {
                lineColor = Color.BLACK
                lineWidth = 1f
                enableDashedLine(10f, 10f, 0f)
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                textSize = 15f
                textColor = Color.BLACK
            }
            axisLeft.addLimitLine(lineaRoja)
        }
        axisLeft.setDrawLimitLinesBehindData(false)

        // 5. Asignar datos y refrescar
        barChart.data = BarData(dataSet).apply { barWidth = 0.65f }
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

    fun actualizarPieChart(listaGastos: List<Gasto>, categoriaSeleccionada: String?) {
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
            colors = ColorTemplate.MATERIAL_COLORS.toList() + ColorTemplate.JOYFUL_COLORS.toList() + ColorTemplate.COLORFUL_COLORS.toList()
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

        // LÓGICA DE PERSISTENCIA (Movida desde el Main)
        if (categoriaSeleccionada != null) {
            val index = entradas.indexOfFirst { it.label == categoriaSeleccionada }
            if (index != -1) {
                pieChart.highlightValue(index.toFloat(), 0, false)
                val total = entradas[index].value.toDouble()
                pieChart.centerText = "$categoriaSeleccionada\n${Formato.formatearMoneda(total)}"
                pieChart.setCenterTextSize(16f)
                pieChart.setCenterTextColor(Color.BLACK)
            } else {
                resetearTextoCentro()
                onCategorySelected(null) // Avisamos que se ha perdido la selección
            }
        } else {
            resetearTextoCentro()
        }

        pieChart.invalidate()
    }

    private fun resetearTextoCentro() {
        pieChart.highlightValues(null)
        pieChart.centerText = "Gastos\nPor Categoría"
        pieChart.setCenterTextSize(14f)
        pieChart.setCenterTextColor(Color.GRAY)
    }
}