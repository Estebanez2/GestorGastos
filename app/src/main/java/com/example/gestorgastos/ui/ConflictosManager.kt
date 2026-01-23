package com.example.gestorgastos.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.gestorgastos.databinding.DialogConflictosBinding

class ConflictosManager(private val context: Context) {

    fun mostrarDialogoResolucion(
        listaConflictos: List<DataTransferManager.ConflictoGasto>,
        onResolver: (descartar: List<DataTransferManager.ConflictoGasto>, reemplazar: List<DataTransferManager.ConflictoGasto>, duplicar: List<DataTransferManager.ConflictoGasto>) -> Unit
    ) {
        val builder = AlertDialog.Builder(context)
        val binding = DialogConflictosBinding.inflate(LayoutInflater.from(context))

        val adapter = ConflictosAdapter()
        binding.rvConflictos.layoutManager = LinearLayoutManager(context)
        binding.rvConflictos.adapter = adapter

        // Cargar datos
        adapter.submitList(listaConflictos)
        binding.tvTituloConflictos.text = "${listaConflictos.size} Conflictos Detectados"

        // Checkbox "Seleccionar Todo"
        binding.cbSeleccionarTodo.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) adapter.seleccionarTodo() else adapter.deseleccionarTodo()
        }

        val dialog = builder.setView(binding.root).setCancelable(false).create()

        // --- ACCIONES DE LOS BOTONES ---

        // Función auxiliar para procesar selección y cerrar si acabamos
        fun procesarAccion(accion: (List<DataTransferManager.ConflictoGasto>) -> Unit) {
            val seleccionados = adapter.obtenerSeleccionados()
            if (seleccionados.isEmpty()) {
                Toast.makeText(context, "Selecciona al menos uno", Toast.LENGTH_SHORT).show()
                return
            }

            // Ejecutamos la acción elegida
            accion(seleccionados)

            // Quitamos los procesados de la lista visible
            val listaRestante = adapter.currentList.toMutableList()
            listaRestante.removeAll(seleccionados)

            if (listaRestante.isEmpty()) {
                dialog.dismiss() // Si no queda ninguno, cerramos
                Toast.makeText(context, "Todos los conflictos resueltos", Toast.LENGTH_SHORT).show()
            } else {
                // Actualizamos la lista para seguir trabajando con los que quedan
                adapter.submitList(listaRestante)
                adapter.deseleccionarTodo()
                binding.cbSeleccionarTodo.isChecked = false
                binding.tvTituloConflictos.text = "${listaRestante.size} Conflictos Restantes"
            }
        }

        binding.btnDescartar.setOnClickListener {
            procesarAccion { selec ->
                onResolver(selec, emptyList(), emptyList()) // Lista descartar llena
            }
        }

        binding.btnReemplazar.setOnClickListener {
            procesarAccion { selec ->
                onResolver(emptyList(), selec, emptyList()) // Lista reemplazar llena
            }
        }

        binding.btnDuplicar.setOnClickListener {
            procesarAccion { selec ->
                onResolver(emptyList(), emptyList(), selec) // Lista duplicar llena
            }
        }

        binding.btnCerrar.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}