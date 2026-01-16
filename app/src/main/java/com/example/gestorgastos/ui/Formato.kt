package com.example.gestorgastos.ui

import java.text.NumberFormat
import java.util.Locale

object Formato {
    private val formatoEuro = NumberFormat.getCurrencyInstance(Locale("es", "ES"))

    fun formatearMoneda(cantidad: Double): String {
        return formatoEuro.format(cantidad)
    }
}