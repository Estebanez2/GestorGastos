package com.example.gestorgastos.data

data class FiltroBusqueda(
    val nombre: String? = null,
    val descripcion: String? = null,
    val categoria: String? = null,
    val precioMin: Double? = null,
    val precioMax: Double? = null,
    val fechaInicio: Long? = null,
    val fechaFin: Long? = null,
    val buscarEnTodo: Boolean = false
)