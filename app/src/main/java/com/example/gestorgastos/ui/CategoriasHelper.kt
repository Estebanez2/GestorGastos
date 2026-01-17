package com.example.gestorgastos.ui

import com.example.gestorgastos.R

object CategoriasHelper {

    // Lista de nombres para el desplegable (Spinner)
    val listaCategorias = listOf("Comida", "Transporte", "Casa", "Compras", "Ocio", "Otros")

    // Función para obtener el icono según el nombre
    fun obtenerIcono(categoria: String): Int {
        return when (categoria) {
            "Comida" -> R.drawable.ic_comida
            "Transporte" -> R.drawable.ic_transporte
            "Casa" -> R.drawable.ic_casa
            "Compras" -> R.drawable.ic_compras
            "Ocio" -> R.drawable.ic_ocio
            else -> R.drawable.ic_otros
        }
    }
}