package com.example.gestorgastos.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.gestorgastos.data.Gasto
import com.example.gestorgastos.databinding.ItemGastoBinding
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportarHelper {

    // --- GENERAR CONTENIDO CSV ---
    fun generarTextoCSV(listaGastos: List<Gasto>): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("Fecha,Concepto,Descripcion,Cantidad\n")
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        for (gasto in listaGastos) {
            val nombreLimpio = gasto.nombre.replace(",", " ")
            val descLimpia = gasto.descripcion.replace(",", " ")
            val fechaStr = dateFormat.format(Date(gasto.fecha))
            // Reemplazamos punto por coma para que Excel lo entienda bien en Europa, o viceversa según prefieras
            val cantidadStr = gasto.cantidad.toString().replace(".", ",")

            stringBuilder.append("$fechaStr,$nombreLimpio,$descLimpia,$cantidadStr\n")
        }
        return stringBuilder.toString()
    }

    // --- GENERAR IMAGEN LARGA (SCROLL COMPLETO) ---
    fun generarImagenLarga(context: Context, viewCabecera: View, listaGastos: List<Gasto>): Bitmap? {
        if (listaGastos.isEmpty()) return null

        // 1. Medir ancho y alto
        val ancho = viewCabecera.width

        // Preparamos una vista "falsa" para medir cuánto ocupa cada item
        val bindingItem = ItemGastoBinding.inflate(LayoutInflater.from(context))

        // Medimos un item de prueba para estimar altura (o podríamos medir uno a uno)
        // Para hacerlo perfecto, mediremos y dibujaremos uno a uno.

        // Altura total = Altura Cabecera + (Altura aproximada item * numero items) + margen
        // Vamos a ir dibujando y calculando sobre la marcha, pero necesitamos crear el Bitmap primero.
        // Haremos un cálculo previo.

        var alturaTotal = viewCabecera.height + 50 // 50 de margen
        val paint = Paint()
        paint.color = Color.WHITE

        // Calculamos altura real simulando el pintado
        val itemsHeights = mutableListOf<Int>()
        for (gasto in listaGastos) {
            // Rellenamos datos
            bindingItem.tvNombre.text = gasto.nombre
            bindingItem.tvCantidad.text = Formato.formatearMoneda(gasto.cantidad)
            bindingItem.tvFecha.text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(gasto.fecha))

            // Si hay foto, simulamos que ocupa espacio (aunque no cargue la imagen real en red, el hueco sí)
            bindingItem.ivThumb.visibility = if (gasto.uriFoto != null) View.VISIBLE else View.GONE

            // Forzamos medida
            bindingItem.root.measure(
                View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            itemsHeights.add(bindingItem.root.measuredHeight)
            alturaTotal += bindingItem.root.measuredHeight
        }

        // 2. Crear el lienzo gigante
        // OJO: Si la imagen es monstruosa (ej. 500 gastos), podría dar error de memoria.
        // Para una app personal (30-50 gastos) va bien.
        val bitmap = Bitmap.createBitmap(ancho, alturaTotal, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE) // Fondo blanco

        // 3. Dibujar Cabecera (Resumen verde)
        viewCabecera.draw(canvas)

        // 4. Dibujar la Lista item por item
        var currentY = viewCabecera.height.toFloat() + 20f

        for ((index, gasto) in listaGastos.withIndex()) {
            // Volvemos a bindear para dibujar de verdad
            bindingItem.tvNombre.text = gasto.nombre
            bindingItem.tvCantidad.text = Formato.formatearMoneda(gasto.cantidad)
            bindingItem.tvFecha.text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(gasto.fecha))
            bindingItem.ivThumb.visibility = if (gasto.uriFoto != null) View.VISIBLE else View.GONE

            // Cargar imagen pequeña (Miniatura) manualmente si existe
            // Nota: Glide es asíncrono y no pinta en Canvas síncronos fácilmente.
            // Para simplificar, dejaremos el hueco gris o el icono por defecto.
            // Si quisieras la foto real aquí, sería mucho más complejo.

            bindingItem.root.measure(
                View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            bindingItem.root.layout(0, 0, ancho, bindingItem.root.measuredHeight)

            canvas.save()
            canvas.translate(0f, currentY)
            bindingItem.root.draw(canvas)
            canvas.restore()

            currentY += itemsHeights[index]
        }

        return bitmap
    }

    // --- ACCIÓN: GUARDAR EN DISPOSITIVO (Descargas / Galería) ---
    fun guardarEnDispositivo(context: Context, bitmap: Bitmap?, csvContent: String?, esImagen: Boolean) {
        val timeStamp = System.currentTimeMillis()

        if (esImagen && bitmap != null) {
            val filename = "Gastos_$timeStamp.jpg"
            var fos: OutputStream? = null

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GestorGastos")
                    }
                    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    fos = imageUri?.let { resolver.openOutputStream(it) }
                } else {
                    // Para Android antiguo (menor a 10)
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val image = File(imagesDir, filename)
                    fos = FileOutputStream(image)
                }

                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos!!)
                fos.close()
                Toast.makeText(context, "Imagen guardada en Galería/Fotos", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error al guardar imagen", Toast.LENGTH_SHORT).show()
            }

        } else if (!esImagen && csvContent != null) {
            val filename = "Gastos_$timeStamp.csv"
            var fos: OutputStream? = null

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GestorGastos")
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    fos = uri?.let { resolver.openOutputStream(it) }
                } else {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, filename)
                    fos = FileOutputStream(file)
                }

                fos?.write(csvContent.toByteArray())
                fos?.close()
                Toast.makeText(context, "CSV guardado en Descargas", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error al guardar CSV", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- ACCIÓN: COMPARTIR (WhatsApp, Email...) ---
    fun compartir(context: Context, bitmap: Bitmap?, csvContent: String?, esImagen: Boolean) {
        val timeStamp = System.currentTimeMillis()

        if (esImagen && bitmap != null) {
            // Guardar temporalmente en caché para compartir
            val file = File(context.externalCacheDir, "share_gastos.jpg")
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            lanzarIntentCompartir(context, uri, "image/jpeg")

        } else if (!esImagen && csvContent != null) {
            val file = File(context.externalCacheDir, "share_gastos.csv")
            val fos = FileOutputStream(file)
            fos.write(csvContent.toByteArray())
            fos.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            lanzarIntentCompartir(context, uri, "text/csv")
        }
    }

    private fun lanzarIntentCompartir(context: Context, uri: Uri, mime: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir con..."))
    }
}