package com.example.gestorgastos.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleCoroutineScope
import com.example.gestorgastos.MainActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.request.FutureTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.gestorgastos.data.Gasto
import java.io.File
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

class ExportManager(
    private val context: Context,
    private val scope: LifecycleCoroutineScope
) {
    enum class Accion { IMAGEN_VISTA, EXPORTAR_DATOS, IMPORTAR_DATOS }

    // --- MENÚ PRINCIPAL ---
    fun mostrarMenuPrincipal(onOpcionElegida: (Accion) -> Unit) {
        val opciones = arrayOf(
            "📷 Compartir Vista Actual (Imagen)",
            "💾 Copia de Seguridad / Exportar",
            "📥 Restaurar / Importar"
        )
        AlertDialog.Builder(context)
            .setTitle("Menú de Exportación")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> onOpcionElegida(Accion.IMAGEN_VISTA)
                    1 -> onOpcionElegida(Accion.EXPORTAR_DATOS)
                    2 -> onOpcionElegida(Accion.IMPORTAR_DATOS)
                }
            }
            .show()
    }

    // --- FLUJO DE EXPORTACIÓN (Rango -> Formato) ---

    fun iniciarProcesoExportacion(mesActual: YearMonth?, onConfiguracionLista: (inicio: Long, fin: Long, formatoIndex: Int) -> Unit) {
        mostrarDialogoSeleccionRango(mesActual) { inicio, fin ->
            mostrarDialogoFormato { formatoIndex ->
                onConfiguracionLista(inicio, fin, formatoIndex)
            }
        }
    }

    private fun mostrarDialogoSeleccionRango(mesActual: YearMonth?, onRangoConfirmado: (Long, Long) -> Unit) {
        val nombreMes = mesActual?.month?.getDisplayName(TextStyle.FULL, Locale("es", "ES")) ?: "Actual"
        val opciones = arrayOf(
            "📅 Mes Actual ($nombreMes)",
            "📆 Elegir Fechas (Personalizado)",
            "🗄️ Todo el Historial"
        )

        AlertDialog.Builder(context)
            .setTitle("1. Selecciona el Rango")
            .setItems(opciones) { _, which ->
                when (which) {
                    0 -> { // Mes Actual
                        val mes = mesActual ?: YearMonth.now()
                        val inicio = mes.atDay(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val fin = mes.atEndOfMonth().atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onRangoConfirmado(inicio, fin)
                    }
                    1 -> { // Personalizado
                        mostrarSelectorRangoFechas(onRangoConfirmado)
                    }
                    2 -> { // Todo
                        onRangoConfirmado(0L, Long.MAX_VALUE)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarSelectorRangoFechas(onFechaSeleccionada: (Long, Long) -> Unit) {
        val calendario = Calendar.getInstance()
        DatePickerDialog(context, { _, year1, month1, day1 ->
            val inicioCal = Calendar.getInstance().apply { set(year1, month1, day1, 0, 0, 0) }
            val inicioMs = inicioCal.timeInMillis

            DatePickerDialog(context, { _, year2, month2, day2 ->
                val finCal = Calendar.getInstance().apply { set(year2, month2, day2, 23, 59, 59) }
                val finMs = finCal.timeInMillis

                if (inicioMs > finMs) {
                    Toast.makeText(context, "Fecha inicio mayor que fin", Toast.LENGTH_SHORT).show()
                } else {
                    onFechaSeleccionada(inicioMs, finMs)
                }
            }, year1, month1, day1).apply { setTitle("Fecha Fin"); show() }
        }, calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH), calendario.get(Calendar.DAY_OF_MONTH)).apply { setTitle("Fecha Inicio"); show() }
    }

    private fun mostrarDialogoFormato(onFormatoElegido: (Int) -> Unit) {
        val opciones = arrayOf("📦 Completa con Fotos (ZIP)", "📄 Solo Datos (JSON)", "📊 Hoja de Cálculo (CSV)")
        AlertDialog.Builder(context)
            .setTitle("2. Elige el formato")
            .setItems(opciones) { _, which -> onFormatoElegido(which) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun mostrarDialogoPostExportacion(archivo: File?, mimeType: String, onGuardarEnDescargas: (File) -> Unit) {
        if (archivo != null) {
            AlertDialog.Builder(context)
                .setTitle("Archivo Generado")
                .setMessage("Nombre: ${archivo.name}")
                .setPositiveButton("Compartir") { _, _ ->
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Enviar a..."))
                }
                .setNegativeButton("Guardar en Descargas") { _, _ -> onGuardarEnDescargas(archivo) }
                .show()
        } else {
            Toast.makeText(context, "Error al generar archivo", Toast.LENGTH_SHORT).show()
        }
    }

    // --- LÓGICA DE CAPTURA DE PANTALLA (Ya la tenías) ---
    data class VistasCaptura(
        val cardResumen: View, val layoutNav: View, val chartGastos: View,
        val chartCategorias: View, val rvCalendario: View, val layoutQuesitos: View,
        val rvListaDetalles: View // El recycler de abajo
    )

    fun procesarCapturaImagen(
        vistaActual: MainActivity.Vista,
        listaCompleta: List<Gasto>,
        listaFiltradaDetalles: List<Gasto>, // <--- NUEVO PARÁMETRO: La lista específica del día/categoría
        vistas: VistasCaptura,
        mapaCategorias: Map<String, String?>,
        onBitmapListo: (Bitmap?) -> Unit
    ) {
        scope.launch(Dispatchers.Main) {
            val loading = AlertDialog.Builder(context).setMessage("Generando captura...").setCancelable(false).show()

            withContext(Dispatchers.IO) {
                // 1. Precargar bitmaps de las fotos (Tanto para lista completa como filtrada)
                val mapaBitmaps = mutableMapOf<Long, Bitmap>()
                // Cargamos fotos de la lista que vayamos a usar
                val listaAUsar = if (vistaActual == MainActivity.Vista.LISTA) listaCompleta else listaFiltradaDetalles

                for (gasto in listaAUsar) {
                    if (gasto.uriFoto != null) {
                        try {
                            val b = Glide.with(context).asBitmap().load(gasto.uriFoto).submit(100, 100).get()
                            mapaBitmaps[gasto.id] = b
                        } catch (e: Exception) {}
                    }
                }

                // 2. Generar el Bitmap final según la vista
                val bitmapFinal: Bitmap?

                if (vistaActual == MainActivity.Vista.LISTA) {
                    // MODO LISTA COMPLETA
                    // Usamos la cabecera + título + lista generada
                    val bmpLista = ExportarHelper.generarImagenListaItems(
                        context, listaCompleta, mapaBitmaps, mapaCategorias, vistas.cardResumen.width
                    )

                    bitmapFinal = if (bmpLista != null) {
                        ExportarHelper.unirVistasEnBitmap(vistas.cardResumen, vistas.layoutNav, bmpLista, context)
                    } else null

                } else {
                    // MODO CALENDARIO / GRÁFICA / QUESITOS

                    // A. Capturar la vista principal (Gráfica, Calendario...)
                    val viewPrincipal = when (vistaActual) {
                        MainActivity.Vista.CALENDARIO -> vistas.rvCalendario
                        MainActivity.Vista.GRAFICA -> vistas.chartGastos
                        MainActivity.Vista.QUESITOS -> vistas.chartCategorias
                        else -> vistas.chartGastos
                    }
                    val bmpPrincipal = ExportarHelper.capturarVista(viewPrincipal)

                    // B. Generar la sublista de detalles (SI HAY ELEMENTOS)
                    var bmpDetalles: Bitmap? = null
                    if (listaFiltradaDetalles.isNotEmpty()) {
                        // Generamos la imagen item por item para que salga completa (scroll infinito)
                        bmpDetalles = ExportarHelper.generarImagenListaItems(
                            context,
                            listaFiltradaDetalles,
                            mapaBitmaps,
                            mapaCategorias,
                            vistas.cardResumen.width // Usamos el ancho de la pantalla
                        )
                    }

                    // C. Unir Principal + Detalles
                    val bmpContenido = ExportarHelper.combinarBitmapsVerticalmente(bmpPrincipal, bmpDetalles)

                    // D. Unir con Cabecera y Título (con el fondo amarillo corregido)
                    bitmapFinal = ExportarHelper.unirVistasEnBitmap(
                        vistas.cardResumen,
                        vistas.layoutNav,
                        bmpContenido,
                        context
                    )
                }

                withContext(Dispatchers.Main) {
                    loading.dismiss()
                    onBitmapListo(bitmapFinal)
                }
            }
        }
    }
}