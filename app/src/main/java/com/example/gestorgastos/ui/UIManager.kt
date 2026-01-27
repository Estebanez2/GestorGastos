package com.example.gestorgastos.ui

import android.view.View
import com.example.gestorgastos.MainActivity
import com.example.gestorgastos.databinding.ActivityMainBinding
import com.github.mikephil.charting.animation.Easing
import com.example.gestorgastos.R
class UIManager(private val binding: ActivityMainBinding) {

    fun cambiarVista(vista: MainActivity.Vista, hayDatos: Boolean) {
        // 1. Ocultar todo
        binding.rvGastos.visibility = View.GONE
        binding.layoutVistaCalendario.visibility = View.GONE
        binding.layoutVistaGrafica.visibility = View.GONE
        binding.layoutVistaCategorias.visibility = View.GONE
        binding.tvVacio.visibility = View.GONE

        // 2. Mostrar lo que toca
        when (vista) {
            MainActivity.Vista.LISTA -> {
                binding.rvGastos.visibility = View.VISIBLE
                if (!hayDatos) binding.tvVacio.visibility = View.VISIBLE
            }
            MainActivity.Vista.CALENDARIO -> binding.layoutVistaCalendario.visibility = View.VISIBLE
            MainActivity.Vista.GRAFICA -> {
                binding.layoutVistaGrafica.visibility = View.VISIBLE
                binding.chartGastos.animateY(800)
            }
            MainActivity.Vista.QUESITOS -> {
                binding.layoutVistaCategorias.visibility = View.VISIBLE
                binding.chartCategorias.animateY(1200, Easing.EaseOutBounce)
            }
        }
    }

    fun actualizarTitulo(textoTitulo: String, modoBusqueda: Boolean) {
        binding.tvMesTitulo.text = textoTitulo
        if (modoBusqueda) {
            binding.btnBuscar.setImageResource(android.R.drawable.ic_menu_manage) // Engranaje
        } else {
            binding.btnBuscar.setImageResource(android.R.drawable.ic_menu_search) // Lupa
        }
    }

    fun gestionarBarraSeleccion(cantidad: Int, fab: View) {
        if (cantidad > 0) {
            binding.layoutBarraSeleccion.visibility = View.VISIBLE
            binding.tvContadorSeleccion.text = "$cantidad seleccionados"
            fab.visibility = View.GONE
        } else {
            binding.layoutBarraSeleccion.visibility = View.GONE
            fab.visibility = View.VISIBLE
        }
    }

    fun estaBarraSeleccionVisible(): Boolean {
        return binding.layoutBarraSeleccion.visibility == View.VISIBLE
    }

    fun ejecutarEfectoSemaforo(totalActual: Double, gastoNuevo: Double, limAmarillo: Double, limRojo: Double) {
        val nuevoTotal = totalActual + gastoNuevo
        val colorResId = when {
            nuevoTotal > limRojo -> R.color.alerta_rojo
            nuevoTotal > limAmarillo -> R.color.alerta_amarillo
            else -> R.color.alerta_verde
        }
        binding.viewFlashBorde.flashEffect(colorResId)
    }
}