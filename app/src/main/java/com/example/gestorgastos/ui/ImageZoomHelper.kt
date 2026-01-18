package com.example.gestorgastos.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.view.WindowManager
import com.bumptech.glide.Glide
import com.example.gestorgastos.databinding.DialogZoomImagenBinding

object ImageZoomHelper {

    fun mostrarImagen(context: Context, uri: String?) {
        if (uri == null) return

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val binding = DialogZoomImagenBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )

        // Cargar imagen en el PhotoView
        Glide.with(context)
            .load(uri)
            .into(binding.ivZoom) // Glide es listo y sabe tratar con PhotoView

        // Cerrar con la X
        binding.btnCerrarZoom.setOnClickListener { dialog.dismiss() }

        // Cerrar pulsando fuera (en lo negro)
        binding.containerZoom.setOnClickListener { dialog.dismiss() }

        // OJO: PhotoView consume los toques para el zoom, así que el click directo
        // en la imagen para cerrar puede ser conflictivo con el doble tap para zoom.
        // Lo mejor es dejar que cierren con la X o pulsando en el área negra del borde.

        dialog.show()
    }
}