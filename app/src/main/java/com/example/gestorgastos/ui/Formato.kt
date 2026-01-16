package com.example.gestorgastos.ui

import java.text.NumberFormat
import java.util.Locale

object Formato {
    // Variable para guardar la moneda actual (Por defecto España)
    var localeActual: Locale = Locale("es", "ES")

    fun formatearMoneda(cantidad: Double): String {
        val formato = NumberFormat.getCurrencyInstance(localeActual)
        return formato.format(cantidad)
    }

    fun cambiarDivisa(tipo: String) {
        localeActual = when(tipo) {
            "USD" -> Locale("en", "US") // Dólares (formato 1,000.00)
            "GBP" -> Locale("en", "GB") // Libras
            else -> Locale("es", "ES")  // Euro (formato 1.000,00)
        }
    }
}