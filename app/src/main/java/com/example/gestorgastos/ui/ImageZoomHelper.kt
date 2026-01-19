package com.example.gestorgastos.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import com.bumptech.glide.Glide
import com.example.gestorgastos.databinding.DialogZoomImagenBinding

object ImageZoomHelper {

    fun mostrarImagen(context: Context, uri: String?) {
        if (uri == null) return

        // --- PROTECCIÓN ---
        // Intentamos leer el archivo antes de que Glide lo toque.
        // Si no tenemos permiso, salta la excepción aquí y no crashea la app.
        try {
            val androidUri = Uri.parse(uri)
            val stream = context.contentResolver.openInputStream(androidUri)
            stream?.close()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Imagen no accesible. Por favor, edite y seleccione la foto de nuevo.", Toast.LENGTH_LONG).show()
            return
        } catch (e: Exception) {
            // Otros errores
            return
        }

        // Si llegamos aquí, es seguro mostrarla
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val binding = DialogZoomImagenBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)

        Glide.with(context).load(uri).into(binding.ivZoom)

        binding.btnCerrarZoom.setOnClickListener { dialog.dismiss() }
        binding.containerZoom.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}