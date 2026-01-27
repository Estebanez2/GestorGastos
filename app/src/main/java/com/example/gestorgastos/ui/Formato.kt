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

    // Formatea el número para mostrarlo en un EditText (Ej: 1.000,50)
    // Sin símbolo de moneda, pero con separadores
    fun formatearParaEditText(cantidad: Double): String {
        // Usamos Locale.GERMANY o es_ES para asegurar puntos en miles y comas en decimales
        val formato = java.text.DecimalFormat("#,##0.##")
        val simbolos = java.text.DecimalFormatSymbols(java.util.Locale("es", "ES"))
        simbolos.groupingSeparator = '.'
        simbolos.decimalSeparator = ','
        formato.decimalFormatSymbols = simbolos
        return formato.format(cantidad)
    }
}