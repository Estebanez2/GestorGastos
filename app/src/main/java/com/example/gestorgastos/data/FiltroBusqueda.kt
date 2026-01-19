package com.example.gestorgastos.data

data class FiltroBusqueda(
    val nombre: String? = null,
    val descripcion: String? = null,
    val categoria: String? = null,
    val precioMin: Double? = null,
    val precioMax: Double? = null,
    val diaInicio: Int? = null,
    val diaFin: Int? = null,
    val buscarEnTodo: Boolean = false
)