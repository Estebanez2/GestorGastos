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

    fun iniciarProcesoExportacion(
        vistaActual: MainActivity.Vista,
        listaGastos: List<Gasto>,
        viewsParaCapturar: VistasCaptura
    ) {
        if (listaGastos.isEmpty()) {
            Toast.makeText(context, "No hay gastos para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        mostrarMenuFormato { esImagen ->
            mostrarMenuAccion { guardarEnDispositivo ->
                procesarExportacion(esImagen, guardarEnDispositivo, vistaActual, listaGastos, viewsParaCapturar)
            }
        }
    }

    // Clase de datos para pasar las vistas necesarias sin ensuciar los argumentos
    data class VistasCaptura(
        val cardResumen: View,
        val layoutNavegacion: View,
        val chartBarras: BarChart,
        val chartQuesitos: PieChart,
        val rvCalendario: View
    )

    private fun mostrarMenuFormato(onFormatoElegido: (Boolean) -> Unit) {
        val opciones = arrayOf("Imagen Larga (JPG)", "Hoja de Cálculo (CSV)")
        AlertDialog.Builder(context)
            .setTitle("1. Elige el formato")
            .setItems(opciones) { _, w -> onFormatoElegido(w == 0) }
            .show()
    }

    private fun mostrarMenuAccion(onAccionElegida: (Boolean) -> Unit) {
        val opciones = arrayOf("Guardar en Dispositivo", "Compartir")
        AlertDialog.Builder(context)
            .setTitle("2. ¿Qué hacer?")
            .setItems(opciones) { _, w -> onAccionElegida(w == 0) }
            .show()
    }

    private fun procesarExportacion(
        esImagen: Boolean,
        guardar: Boolean,
        vistaActual: MainActivity.Vista,
        lista: List<Gasto>,
        vistas: VistasCaptura
    ) {
        if (!esImagen) {
            // EXPORTAR CSV
            val csvContent = ExportarHelper.generarTextoCSV(lista)
            finalizar(null, csvContent, guardar)
            return
        }

        // EXPORTAR IMAGEN
        if (vistaActual == MainActivity.Vista.LISTA) {
            generarImagenListaAsync(lista, vistas, guardar)
        } else {
            // Capturas simples (Gráficas o Calendario)
            val bitmapCaptura = when (vistaActual) {
                MainActivity.Vista.GRAFICA -> vistas.chartBarras.chartBitmap
                MainActivity.Vista.QUESITOS -> vistas.chartQuesitos.chartBitmap
                MainActivity.Vista.CALENDARIO -> ExportarHelper.capturarVista(vistas.rvCalendario)
                else -> null
            }

            if (bitmapCaptura != null) {
                val bitmapFinal = ExportarHelper.unirVistasEnBitmap(
                    vistas.cardResumen,
                    vistas.layoutNavegacion,
                    bitmapCaptura
                )
                finalizar(bitmapFinal, null, guardar)
            }
        }
    }

    private fun generarImagenListaAsync(lista: List<Gasto>, vistas: VistasCaptura, guardar: Boolean) {
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
                finalizar(bitmapFinal, null, guardar)
            }
        }
    }

    private fun finalizar(bitmap: Bitmap?, texto: String?, guardar: Boolean) {
        if (guardar) ExportarHelper.guardarEnDispositivo(context, bitmap, texto, true)
        else ExportarHelper.compartir(context, bitmap, texto, true)
    }
}