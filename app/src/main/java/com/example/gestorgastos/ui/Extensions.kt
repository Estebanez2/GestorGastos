package com.example.gestorgastos.ui

import android.app.Activity
import android.content.pm.PackageManager
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

fun android.content.Context.copiarImagenAInternalStorage(uriExterna: Uri): String {
    return try {
        val archivoDestino = File(filesDir, "img_${System.currentTimeMillis()}.jpg")
        contentResolver.openInputStream(uriExterna)?.use { input ->
            java.io.FileOutputStream(archivoDestino).use { output ->
                input.copyTo(output)
            }
        }
        // Devuelve la URI segura generada por FileProvider
        FileProvider.getUriForFile(this, "${packageName}.fileprovider", archivoDestino).toString()
    } catch (e: Exception) {
        e.printStackTrace()
        uriExterna.toString()
    }
}