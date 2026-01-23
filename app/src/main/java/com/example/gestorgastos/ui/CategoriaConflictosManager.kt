package com.example.gestorgastos.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import com.bumptech.glide.Glide
import com.example.gestorgastos.databinding.DialogConflictoCategoriaBinding

class CategoriaConflictosManager(private val context: Context) {

    // Función recursiva para mostrar conflictos uno a uno
    fun resolverConflictos(
        listaConflictos: MutableList<DataTransferManager.ConflictoCategoria>,
        onDecisionTomada: (DataTransferManager.ConflictoCategoria, usarNueva: Boolean) -> Unit,
        onTodosResueltos: () -> Unit
    ) {
        if (listaConflictos.isEmpty()) {
            onTodosResueltos()
            return
        }

        // Sacamos el primer conflicto de la lista
        val conflicto = listaConflictos[0]

        val builder = AlertDialog.Builder(context)
        val binding = DialogConflictoCategoriaBinding.inflate(LayoutInflater.from(context))

        binding.tvMensajeCategoria.text = "La categoría '${conflicto.categoriaNombre}' ya existe. ¿Qué imagen prefieres?"

        // --- CARGAR IMAGEN ACTUAL (IZQUIERDA) ---
        if (conflicto.uriActual != null) {
            // Es Foto
            binding.ivFotoActual.clearColorFilter()
            binding.ivFotoActual.setPadding(0,0,0,0)
            Glide.with(context).load(conflicto.uriActual).into(binding.ivFotoActual)
        } else {
            // Es Icono por defecto
            binding.ivFotoActual.setColorFilter(Color.BLACK)
            binding.ivFotoActual.setPadding(20,20,20,20) // Un poco de margen al icono
            val iconoRes = CategoriasHelper.obtenerIcono(conflicto.categoriaNombre)
            binding.ivFotoActual.setImageResource(iconoRes)
        }

        // --- CARGAR IMAGEN NUEVA (DERECHA) ---
        if (conflicto.uriNueva != null) {
            binding.ivFotoNueva.clearColorFilter()
            binding.ivFotoNueva.setPadding(0,0,0,0)
            Glide.with(context).load(conflicto.uriNueva).into(binding.ivFotoNueva)
        } else {
            // Si lo nuevo es volver al icono por defecto
            binding.ivFotoNueva.setColorFilter(Color.BLACK)
            binding.ivFotoNueva.setPadding(20,20,20,20)
            val iconoRes = CategoriasHelper.obtenerIcono(conflicto.categoriaNombre)
            binding.ivFotoNueva.setImageResource(iconoRes)
        }

        val dialog = builder.setView(binding.root)
            .setCancelable(false) // OBLIGATORIO ELEGIR
            .create()

        // Fondo transparente para que se vean las esquinas redondas del CardView
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // --- BOTONES ---
        binding.btnMantener.setOnClickListener {
            dialog.dismiss()
            onDecisionTomada(conflicto, false) // False = Mantener vieja
            listaConflictos.removeAt(0) // Quitamos de la lista
            resolverConflictos(listaConflictos, onDecisionTomada, onTodosResueltos) // SIGUIENTE
        }

        binding.btnCambiar.setOnClickListener {
            dialog.dismiss()
            onDecisionTomada(conflicto, true) // True = Usar nueva
            listaConflictos.removeAt(0)
            resolverConflictos(listaConflictos, onDecisionTomada, onTodosResueltos) // SIGUIENTE
        }

        dialog.show()
    }
}