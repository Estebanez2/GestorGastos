package com.example.gestorgastos.data

// Esta clase es una "caja" para transportar los filtros
data class FiltroBusqueda(
    val nombre: String? = null,
    val categoria: String? = null,
    val precioMin: Double? = null,
    val precioMax: Double? = null,
    val fechaInicio: Long? = null, // Timestamp (milisegundos)
    val fechaFin: Long? = null
)