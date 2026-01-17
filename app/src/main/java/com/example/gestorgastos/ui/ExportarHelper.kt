package com.example.gestorgastos.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

    fun capturarVista(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return bitmap
    }

    fun unirVistasEnBitmap(viewCabecera: View, viewTitulo: View, bitmapContenido: Bitmap): Bitmap {
        val ancho = maxOf(viewCabecera.width, viewTitulo.width, bitmapContenido.width)
        val altoTotal = viewCabecera.height + viewTitulo.height + bitmapContenido.height + 50

        val bitmapFinal = Bitmap.createBitmap(ancho, altoTotal, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmapFinal)
        canvas.drawColor(Color.WHITE)

        // 1. Cabecera
        viewCabecera.draw(canvas)

        // 2. Título
        canvas.save()
        canvas.translate(0f, viewCabecera.height.toFloat())
        viewTitulo.draw(canvas)
        canvas.restore()

        // 3. Contenido
        canvas.save()
        val yContenido = viewCabecera.height.toFloat() + viewTitulo.height.toFloat() + 20f
        canvas.translate(0f, yContenido)
        val xOffset = (ancho - bitmapContenido.width) / 2f
        canvas.drawBitmap(bitmapContenido, xOffset, 0f, null)
        canvas.restore()

        return bitmapFinal
    }

    fun generarTextoCSV(listaGastos: List<Gasto>): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("Fecha,Categoria,Concepto,Descripcion,Cantidad\n") // He añadido Categoria al CSV también
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        for (gasto in listaGastos) {
            val nombreLimpio = gasto.nombre.replace(",", " ")
            val descLimpia = gasto.descripcion.replace(",", " ")
            val fechaStr = dateFormat.format(Date(gasto.fecha))
            val cantidadStr = gasto.cantidad.toString().replace(".", ",")
            // CSV ahora incluye la categoría
            stringBuilder.append("$fechaStr,${gasto.categoria},$nombreLimpio,$descLimpia,$cantidadStr\n")
        }
        return stringBuilder.toString()
    }

    // --- GENERAR IMAGEN LARGA (Corregido Iconos) ---
    fun generarImagenLarga(
        context: Context,
        viewCabecera: View,
        viewTitulo: View,
        listaGastos: List<Gasto>,
        mapaBitmaps: Map<Long, Bitmap>
    ): Bitmap? {
        if (listaGastos.isEmpty()) return null

        val ancho = viewCabecera.width
        val bindingItem = ItemGastoBinding.inflate(LayoutInflater.from(context))

        // 1. Calcular altura total
        var alturaTotal = viewCabecera.height + viewTitulo.height + 50
        val itemsHeights = mutableListOf<Int>()

        for (gasto in listaGastos) {
            bindingItem.tvNombre.text = gasto.nombre

            // Medimos visibilidad de foto para la altura correcta
            val tieneFoto = gasto.uriFoto != null || mapaBitmaps.containsKey(gasto.id)
            bindingItem.cardThumb.visibility = if (tieneFoto) View.VISIBLE else View.GONE
            bindingItem.ivThumb.visibility = if (tieneFoto) View.VISIBLE else View.GONE

            bindingItem.root.measure(
                View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            itemsHeights.add(bindingItem.root.measuredHeight)
            alturaTotal += bindingItem.root.measuredHeight
        }

        // 2. Crear Lienzo
        val bitmap = Bitmap.createBitmap(ancho, alturaTotal, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // 3. Dibujar Cabecera y Título
        viewCabecera.draw(canvas)
        canvas.save()
        canvas.translate(0f, viewCabecera.height.toFloat())
        viewTitulo.draw(canvas)
        canvas.restore()

        // 4. Dibujar Lista
        var currentY = viewCabecera.height.toFloat() + viewTitulo.height.toFloat() + 20f

        for ((index, gasto) in listaGastos.withIndex()) {
            // Datos de texto
            bindingItem.tvNombre.text = gasto.nombre
            bindingItem.tvCantidad.text = Formato.formatearMoneda(gasto.cantidad)
            bindingItem.tvFecha.text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(gasto.fecha))

            // --- NUEVO: PINTAR CATEGORÍA CORRECTA ---
            val iconoRes = CategoriasHelper.obtenerIcono(gasto.categoria)
            bindingItem.ivIconoCategoria.setImageResource(iconoRes)

            // Gestión de Foto
            val bitmapPrecargado = mapaBitmaps[gasto.id]
            if (bitmapPrecargado != null) {
                bindingItem.cardThumb.visibility = View.VISIBLE
                bindingItem.ivThumb.visibility = View.VISIBLE
                bindingItem.ivThumb.setImageBitmap(bitmapPrecargado)
                bindingItem.ivThumb.setPadding(0,0,0,0)
            } else if (gasto.uriFoto != null) {
                bindingItem.cardThumb.visibility = View.VISIBLE
                bindingItem.ivThumb.visibility = View.VISIBLE
                bindingItem.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery)
                bindingItem.ivThumb.setPadding(20,20,20,20)
            } else {
                bindingItem.cardThumb.visibility = View.GONE
                bindingItem.ivThumb.visibility = View.GONE
            }

            // Dibujar
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

    // Funciones guardar y compartir se mantienen igual...
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

    fun compartir(context: Context, bitmap: Bitmap?, csvContent: String?, esImagen: Boolean) {
        if (esImagen && bitmap != null) {
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