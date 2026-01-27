package com.example.gestorgastos.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class EuroTextWatcher(private val editText: EditText) : TextWatcher {

    private var isUpdating = false
    private val decimalFormat: DecimalFormat

    init {
        // Configuramos el formato para miles: 1.000, 10.000...
        val symbols = DecimalFormatSymbols(Locale("es", "ES"))
        symbols.groupingSeparator = '.'
        symbols.decimalSeparator = ','
        decimalFormat = DecimalFormat("#,###", symbols)
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        // Evitamos bucle infinito
        if (isUpdating) return

        val originalString = s.toString()
        if (originalString.isEmpty()) return

        isUpdating = true

        try {
            // 1. Limpieza: Quitamos los puntos de miles para analizar
            // NO quitamos la coma todavía
            val cleanString = originalString.replace(".", "")

            val formattedString: String
            var selectionIndex = editText.selectionStart

            // 2. Lógica de Separación
            if (cleanString.contains(",")) {
                // Si hay coma, separamos en Entero y Decimal
                val parts = cleanString.split(",")
                val integerPart = parts[0]
                var decimalPart = if (parts.size > 1) parts[1] else ""

                // Limitamos a 2 decimales
                if (decimalPart.length > 2) {
                    decimalPart = decimalPart.substring(0, 2)
                }

                // Formateamos solo la parte entera con puntos
                val formattedInt = if (integerPart.isNotEmpty()) {
                    try {
                        decimalFormat.format(integerPart.toDouble())
                    } catch (e: Exception) { integerPart }
                } else {
                    "" // Caso raro: empieza por , (,50)
                }

                // Reconstruimos: Entero + Coma + Decimales (tal cual los escribió el usuario)
                formattedString = "$formattedInt,$decimalPart"

            } else {
                // Si NO hay coma, es un entero normal. Formateamos con puntos.
                formattedString = try {
                    decimalFormat.format(cleanString.toDouble())
                } catch (e: Exception) {
                    cleanString
                }
            }

            // 3. Aplicar cambios solo si es necesario
            if (formattedString != originalString) {
                editText.setText(formattedString)

                // Intentamos mantener el cursor al final para evitar saltos raros al escribir rápido
                editText.setSelection(formattedString.length)
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        isUpdating = false
    }
}