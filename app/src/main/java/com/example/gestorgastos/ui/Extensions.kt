package com.example.gestorgastos.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.gestorgastos.R
import java.io.File

/**
 * ANIMACIONES
 * Hace parpadear un borde o vista con un color específico.
 */
fun View.flashEffect(colorResId: Int) {
    val color = ContextCompat.getColor(context, colorResId)
    this.setBackgroundColor(color)
    this.visibility = View.VISIBLE
    this.alpha = 0f

    val numFlashes = when (colorResId) {
        R.color.alerta_rojo -> 3
        R.color.alerta_amarillo -> 2
        else -> 1
    }

    fun ejecutarAnimacion(vecesRestantes: Int) {
        if (vecesRestantes <= 0) {
            this.visibility = View.GONE
            this.background = null
            return
        }
        this.animate().alpha(0.5f).setDuration(150).withEndAction {
            this.animate().alpha(0f).setDuration(150).withEndAction {
                ejecutarAnimacion(vecesRestantes - 1)
            }.start()
        }.start()
    }
    ejecutarAnimacion(numFlashes)
}

/**
 * RECYCLER VIEW - SWIPE
 * Configura el deslizamiento lateral para borrar de forma genérica.
 * @param onSwiped Lambda que devuelve la posición del elemento deslizado.
 */
fun RecyclerView.setupSwipeToDelete(onSwiped: (Int) -> Unit) {
    val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
        override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            onSwiped(viewHolder.adapterPosition)
        }
    }
    ItemTouchHelper(swipeHandler).attachToRecyclerView(this)
}

/**
 * PERMISOS
 * Comprueba si tiene permiso de cámara y ejecuta la acción correspondiente.
 */
fun Activity.ejecutarConPermisoCamara(
    onGranted: () -> Unit,
    onDenied: () -> Unit
) {
    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
        onGranted()
    } else {
        onDenied()
    }
}

fun Context.copiarImagenAInternalStorage(uriExterna: Uri): String {
    return try {
        // Creamos archivo destino
        val archivoDestino = File(filesDir, "img_${System.currentTimeMillis()}.jpg")

        // Abrimos el stream de la imagen original
        val inputStream = contentResolver.openInputStream(uriExterna)

        // --- MAGIA DE COMPRESIÓN ---
        // 1. Decodificamos a Bitmap
        val bitmapOriginal = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        // 2. Redimensionamos si es gigante (Opcional, pero recomendado)
        // Si es mayor a 1920px de ancho, lo bajamos. Ahorra mucha memoria RAM y disco.
        val bitmapFinal = if (bitmapOriginal.width > 1920) {
            val ratio = 1920.0 / bitmapOriginal.width
            val alto = (bitmapOriginal.height * ratio).toInt()
            Bitmap.createScaledBitmap(bitmapOriginal, 1920, alto, true)
        } else {
            bitmapOriginal
        }

        // 3. Guardamos comprimiendo a JPG calidad 70-80%
        val outputStream = java.io.FileOutputStream(archivoDestino)
        bitmapFinal.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, outputStream)

        outputStream.flush()
        outputStream.close()

        // 4. Devolvemos la URI del nuevo archivo optimizado
        androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivoDestino).toString()

    } catch (e: Exception) {
        e.printStackTrace()
        // Si falla la compresión, devolvemos la URI original por seguridad (o null)
        uriExterna.toString()
    }
}