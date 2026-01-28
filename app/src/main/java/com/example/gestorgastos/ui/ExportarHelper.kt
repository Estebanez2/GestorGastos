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
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ExportarHelper {

    fun capturarVista(view: View): Bitmap {
        // Aseguramos que la vista tenga dimensiones válidas
        if (view.width == 0 || view.height == 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) bgDrawable.draw(canvas) else canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return bitmap
    }

    fun unirVistasEnBitmap(
        viewCabecera: View,
        viewTitulo: View,
        bitmapContenido: Bitmap,
        context: Context
    ): Bitmap {
        // 1. Capturamos las vistas TAL CUAL están (sin modificarlas para no buguear la UI)
        val bmpCabecera = capturarVista(viewCabecera)
        val bmpTitulo = capturarVista(viewTitulo)

        // 2. Calculamos el ancho final (el máximo de los tres)
        val anchoFinal = maxOf(bmpCabecera.width, bmpTitulo.width, bitmapContenido.width)
        val altoTotal = bmpCabecera.height + bmpTitulo.height + bitmapContenido.height + 50

        val bitmapFinal = Bitmap.createBitmap(anchoFinal, altoTotal, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmapFinal)
        canvas.drawColor(Color.WHITE)

        // --- TRUCO DEL FONDO AMARILLO ---
        // En lugar de estirar la vista, tomamos el color del píxel (0,0) de la cabecera (el amarillo)
        // y pintamos un rectángulo que ocupe todo el ancho.
        val colorFondoCabecera = bmpCabecera.getPixel(10, 10) // Muestreamos el color
        val paintFondo = android.graphics.Paint().apply { color = colorFondoCabecera }

        // Pintamos el fondo amarillo ocupando todo el ancho de la zona de cabecera
        canvas.drawRect(0f, 0f, anchoFinal.toFloat(), bmpCabecera.height.toFloat(), paintFondo)

        // 3. DIBUJAR CABECERA (Centrada sobre el fondo amarillo extendido)
        val xCabecera = (anchoFinal - bmpCabecera.width) / 2f
        canvas.drawBitmap(bmpCabecera, xCabecera, 0f, null)

        // 4. DIBUJAR TÍTULO (Debajo)
        // Hacemos lo mismo para el título si tuviera fondo
        val yTitulo = bmpCabecera.height.toFloat()
        /* Si el título tuviera color de fondo, haríamos lo mismo que arriba aquí */
        val xTitulo = (anchoFinal - bmpTitulo.width) / 2f
        canvas.drawBitmap(bmpTitulo, xTitulo, yTitulo, null)

        // 5. DIBUJAR CONTENIDO (Gráfica/Calendario + Lista)
        val yContenido = yTitulo + bmpTitulo.height.toFloat() + 20f
        val xContenido = (anchoFinal - bitmapContenido.width) / 2f
        canvas.drawBitmap(bitmapContenido, xContenido, yContenido, null)

        return bitmapFinal
    }

    fun combinarBitmapsVerticalmente(arriba: Bitmap, abajo: Bitmap?): Bitmap {
        if (abajo == null) return arriba

        val ancho = maxOf(arriba.width, abajo.width)
        val alto = arriba.height + abajo.height

        val combinado = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinado)
        canvas.drawColor(Color.WHITE)

        val xOffsetArriba = (ancho - arriba.width) / 2f
        canvas.drawBitmap(arriba, xOffsetArriba, 0f, null)

        val xOffsetAbajo = (ancho - abajo.width) / 2f
        canvas.drawBitmap(abajo, xOffsetAbajo, arriba.height.toFloat(), null)

        return combinado
    }

    // --- GENERAR LISTA LARGA (Incluye tu corrección de categorías) ---
    fun generarImagenListaItems(
        context: Context,
        listaGastos: List<Gasto>,
        mapaBitmapsGastos: Map<Long, Bitmap>,
        mapaCategoriasUri: Map<String, String?>,
        anchoDeseado: Int
    ): Bitmap? {
        if (listaGastos.isEmpty()) return null

        val bindingItem = ItemGastoBinding.inflate(LayoutInflater.from(context))

        // Medir altura total
        var alturaTotal = 0
        val itemsHeights = mutableListOf<Int>()

        for (gasto in listaGastos) {
            bindingItem.tvNombre.text = gasto.nombre
            val tieneFoto = gasto.uriFoto != null || mapaBitmapsGastos.containsKey(gasto.id)
            bindingItem.cardThumb.visibility = if (tieneFoto) View.VISIBLE else View.GONE

            // Medimos con el ancho deseado
            bindingItem.root.measure(
                View.MeasureSpec.makeMeasureSpec(anchoDeseado, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            itemsHeights.add(bindingItem.root.measuredHeight)
            alturaTotal += bindingItem.root.measuredHeight
        }

        val bitmap = Bitmap.createBitmap(anchoDeseado, alturaTotal, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var currentY = 0f

        for ((index, gasto) in listaGastos.withIndex()) {
            // Rellenar datos
            bindingItem.tvNombre.text = gasto.nombre
            bindingItem.tvCantidad.text = Formato.formatearMoneda(gasto.cantidad)
            bindingItem.tvFecha.text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(gasto.fecha))

            // CATEGORÍA (Tu lógica corregida)
            val uriCat = mapaCategoriasUri[gasto.categoria]
            if (uriCat != null) {
                try {
                    val bitmapCat = com.bumptech.glide.Glide.with(context)
                        .asBitmap().load(uriCat).submit(48, 48).get()
                    bindingItem.ivIconoCategoria.setImageBitmap(bitmapCat)
                    bindingItem.ivIconoCategoria.setPadding(0,0,0,0)
                } catch (e: Exception) {
                    bindingItem.ivIconoCategoria.setImageResource(CategoriasHelper.obtenerIcono(gasto.categoria))
                }
            } else {
                bindingItem.ivIconoCategoria.setImageResource(CategoriasHelper.obtenerIcono(gasto.categoria))
            }

            // FOTO GASTO
            val bitmapPrecargado = mapaBitmapsGastos[gasto.id]
            if (bitmapPrecargado != null) {
                bindingItem.cardThumb.visibility = View.VISIBLE
                bindingItem.ivThumb.visibility = View.VISIBLE
                bindingItem.ivThumb.setImageBitmap(bitmapPrecargado)
                bindingItem.ivThumb.setPadding(0,0,0,0)
            } else if (gasto.uriFoto != null) {
                bindingItem.cardThumb.visibility = View.VISIBLE
                bindingItem.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery)
            } else {
                bindingItem.cardThumb.visibility = View.GONE
            }

            // Dibujar en posición
            bindingItem.root.measure(
                View.MeasureSpec.makeMeasureSpec(anchoDeseado, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            bindingItem.root.layout(0, 0, anchoDeseado, bindingItem.root.measuredHeight)

            canvas.save()
            canvas.translate(0f, currentY)
            bindingItem.root.draw(canvas)
            canvas.restore()

            currentY += itemsHeights[index]
        }
        return bitmap
    }
    // --- GENERAR IMAGEN LARGA (Corregido Iconos) ---
    fun generarImagenLarga(
        context: Context,
        viewCabecera: View,
        viewTitulo: View,
        listaGastos: List<Gasto>,
        mapaBitmapsGastos: Map<Long, Bitmap>,
        mapaCategoriasUri: Map<String, String?> // <--- NUEVO PARÁMETRO
    ): Bitmap? {
        if (listaGastos.isEmpty()) return null

        val ancho = viewCabecera.width
        val bindingItem = ItemGastoBinding.inflate(LayoutInflater.from(context))
        var alturaTotal = viewCabecera.height + viewTitulo.height + 50
        val itemsHeights = mutableListOf<Int>()
        for (gasto in listaGastos) {
            bindingItem.tvNombre.text = gasto.nombre
            val tieneFoto = gasto.uriFoto != null || mapaBitmapsGastos.containsKey(gasto.id)
            bindingItem.cardThumb.visibility = if (tieneFoto) View.VISIBLE else View.GONE
            bindingItem.root.measure(View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            itemsHeights.add(bindingItem.root.measuredHeight)
            alturaTotal += bindingItem.root.measuredHeight
        }

        val bitmap = Bitmap.createBitmap(ancho, alturaTotal, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // Dibujar Cabecera y Título con el "TRUCO" de medida también aquí
        val widthSpec = View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY)
        val heightSpecCabecera = View.MeasureSpec.makeMeasureSpec(viewCabecera.height, View.MeasureSpec.EXACTLY)
        viewCabecera.measure(widthSpec, heightSpecCabecera)
        viewCabecera.layout(0, 0, ancho, viewCabecera.measuredHeight)
        viewCabecera.draw(canvas)

        val heightSpecTitulo = View.MeasureSpec.makeMeasureSpec(viewTitulo.height, View.MeasureSpec.EXACTLY)
        viewTitulo.measure(widthSpec, heightSpecTitulo)
        viewTitulo.layout(0, 0, ancho, viewTitulo.measuredHeight)
        canvas.save()
        canvas.translate(0f, viewCabecera.height.toFloat())
        viewTitulo.draw(canvas)
        canvas.restore()

        var currentY = viewCabecera.height.toFloat() + viewTitulo.height.toFloat() + 20f

        for ((index, gasto) in listaGastos.withIndex()) {
            bindingItem.tvNombre.text = gasto.nombre
            bindingItem.tvCantidad.text = Formato.formatearMoneda(gasto.cantidad)
            bindingItem.tvFecha.text = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(gasto.fecha))

            // --- CORRECCIÓN CATEGORÍAS ---
            val uriCat = mapaCategoriasUri[gasto.categoria]
            if (uriCat != null) {
                // Si la categoría tiene foto personalizada, intentamos cargarla
                // NOTA: Como esto corre en hilo de fondo (desde ExportManager), podemos usar Glide síncrono
                try {
                    val bitmapCat = com.bumptech.glide.Glide.with(context)
                        .asBitmap()
                        .load(uriCat)
                        .submit(48, 48) // Tamaño pequeño para icono
                        .get()
                    bindingItem.ivIconoCategoria.setImageBitmap(bitmapCat)
                    bindingItem.ivIconoCategoria.setPadding(0,0,0,0) // Quitar padding si es foto completa
                } catch (e: Exception) {
                    // Si falla, icono por defecto
                    bindingItem.ivIconoCategoria.setImageResource(CategoriasHelper.obtenerIcono(gasto.categoria))
                }
            } else {
                // Icono estándar
                val iconoRes = CategoriasHelper.obtenerIcono(gasto.categoria)
                bindingItem.ivIconoCategoria.setImageResource(iconoRes)
            }

            // Gestión de Foto Gasto (Igual que tenías)
            val bitmapPrecargado = mapaBitmapsGastos[gasto.id]
            if (bitmapPrecargado != null) {
                bindingItem.cardThumb.visibility = View.VISIBLE
                bindingItem.ivThumb.visibility = View.VISIBLE
                bindingItem.ivThumb.setImageBitmap(bitmapPrecargado)
                bindingItem.ivThumb.setPadding(0,0,0,0)
            } else if (gasto.uriFoto != null) {
                bindingItem.cardThumb.visibility = View.VISIBLE
                bindingItem.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery)
            } else {
                bindingItem.cardThumb.visibility = View.GONE
            }

            bindingItem.root.measure(View.MeasureSpec.makeMeasureSpec(ancho, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
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

    fun copiarImagenAInternalStorage(context: Context, uriExterna: Uri): String? {
        return try {
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val file = File(context.filesDir, fileName)

            val inputStream = context.contentResolver.openInputStream(uriExterna) ?: return null

            // --- INICIO OPTIMIZACIÓN ---
            // 1. Decodificar
            val bitmapOriginal = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmapOriginal == null) return null // Por si el archivo no era una imagen válida

            // 2. Redimensionar si es gigante (ej. fotos de cámara de 4000px)
            val maxDimension = 1920 // Full HD es suficiente para móvil
            val bitmapFinal = if (bitmapOriginal.width > maxDimension || bitmapOriginal.height > maxDimension) {
                val ratio = maxDimension.toDouble() / maxOf(bitmapOriginal.width, bitmapOriginal.height)
                val anchoNuevo = (bitmapOriginal.width * ratio).toInt()
                val altoNuevo = (bitmapOriginal.height * ratio).toInt()
                android.graphics.Bitmap.createScaledBitmap(bitmapOriginal, anchoNuevo, altoNuevo, true)
            } else {
                bitmapOriginal
            }

            // 3. Guardar Comprimido (Calidad 75% reduce mucho el peso sin perder calidad visual)
            val outputStream = FileOutputStream(file)
            bitmapFinal.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)

            outputStream.flush()
            outputStream.close()
            // --- FIN OPTIMIZACIÓN ---

            // Devolvemos la URI string para guardar en BD
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun guardarArchivoEnDescargas(context: Context, archivoOrigen: File, mimeType: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, archivoOrigen.name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GestorGastos")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        archivoOrigen.inputStream().use { input -> input.copyTo(output) }
                    }
                    Toast.makeText(context, "Guardado en Descargas/GestorGastos", Toast.LENGTH_LONG).show()
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val carpetaApp = File(downloadsDir, "GestorGastos")
                if (!carpetaApp.exists()) carpetaApp.mkdirs()
                val archivoDestino = File(carpetaApp, archivoOrigen.name)
                archivoOrigen.copyTo(archivoDestino, overwrite = true)

                // Escanear para que aparezca en PC
                android.media.MediaScannerConnection.scanFile(context, arrayOf(archivoDestino.absolutePath), arrayOf(mimeType), null)
                Toast.makeText(context, "Guardado en Descargas/GestorGastos", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al guardar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}