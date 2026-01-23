package com.example.gestorgastos.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.bumptech.glide.Glide
import com.example.gestorgastos.MainActivity
import com.example.gestorgastos.data.Gasto
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExportManager(
    private val context: Context,
    private val scope: LifecycleCoroutineScope
) {

    // Enum para saber qué ha elegido el usuario en el primer menú
    enum class Accion { IMAGEN_VISTA, EXPORTAR_DATOS, IMPORTAR_DATOS }

    // Clase de vistas para capturar (igual que antes)
    data class VistasCaptura(
        val cardResumen: View,
        val layoutNavegacion: View,
        val chartBarras: BarChart,
        val chartQuesitos: PieChart,
        val rvCalendario: View,
        val layoutVistaCategorias: View
    )

    fun mostrarMenuPrincipal(onOpcionElegida: (Accion) -> Unit) {
        val opciones = arrayOf(
            "📷 Compartir Vista Actual (Imagen)",
            "💾 Crear Copia de Seguridad / Exportar", // Aquí agrupamos todo
            "📥 Restaurar Copia de Seguridad"
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

    // --- LÓGICA DE IMAGEN (Se mantiene aquí porque es visual) ---

    fun procesarCapturaImagen(
        vistaActual: MainActivity.Vista,
        listaGastos: List<Gasto>,
        vistas: VistasCaptura,
        onTerminado: (Bitmap?) -> Unit
    ) {
        if (vistaActual == MainActivity.Vista.LISTA) {
            generarImagenListaAsync(listaGastos, vistas) { bitmap -> onTerminado(bitmap) }
        } else {
            // Capturas simples
            val bitmapCaptura = when (vistaActual) {
                MainActivity.Vista.GRAFICA -> vistas.chartBarras.chartBitmap
                MainActivity.Vista.QUESITOS -> ExportarHelper.capturarVista(vistas.layoutVistaCategorias)
                MainActivity.Vista.CALENDARIO -> ExportarHelper.capturarVista(vistas.rvCalendario)
                else -> null
            }
            // Unir con cabecera
            if (bitmapCaptura != null) {
                val final = ExportarHelper.unirVistasEnBitmap(vistas.cardResumen, vistas.layoutNavegacion, bitmapCaptura)
                onTerminado(final)
            } else {
                onTerminado(null)
            }
        }
    }

    private fun generarImagenListaAsync(lista: List<Gasto>, vistas: VistasCaptura, callback: (Bitmap?) -> Unit) {
        val progress = AlertDialog.Builder(context).setMessage("Generando imagen...").setCancelable(false).show()

        scope.launch(Dispatchers.IO) {
            val mapaBitmaps = mutableMapOf<Long, Bitmap>()
            for (gasto in lista) {
                gasto.uriFoto?.let {
                    try {
                        val bm = Glide.with(context).asBitmap().load(it).centerCrop().submit(100, 100).get()
                        mapaBitmaps[gasto.id] = bm
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            withContext(Dispatchers.Main) {
                progress.dismiss()
                val bitmapFinal = ExportarHelper.generarImagenLarga(
                    context,
                    vistas.cardResumen,
                    vistas.layoutNavegacion,
                    lista,
                    mapaBitmaps
                )
                callback(bitmapFinal)
            }
        }
    }
}