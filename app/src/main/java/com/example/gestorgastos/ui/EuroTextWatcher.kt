package com.example.gestorgastos.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class EuroTextWatcher(private val editText: EditText) : TextWatcher {

    private val df: DecimalFormat
    private val dfnd: DecimalFormat

    init {
        val symbols = DecimalFormatSymbols(Locale("es", "ES"))
        symbols.decimalSeparator = ','
        symbols.groupingSeparator = '.'

        // Formato con decimales: 1.234,56
        df = DecimalFormat("#,###.##", symbols)
        df.isDecimalSeparatorAlwaysShown = true

        // Formato enteros: 1.234
        dfnd = DecimalFormat("#,###", symbols)
    }

    override fun afterTextChanged(s: Editable?) {
        // 1. Quitamos el listener para evitar bucle infinito al modificar el texto
        editText.removeTextChangedListener(this)

        try {
            val originalString = s.toString()

            // Guardamos la posición del cursor antes de tocar nada
            val cursorPosition = editText.selectionStart
            val inilen = originalString.length

            // Limpiamos la cadena: quitamos puntos y cambiamos coma por punto para calcular
            val cleanString = originalString.replace(".", "").replace(",", ".")

            if (cleanString.isNotEmpty() && cleanString != ".") {
                val parsed = cleanString.toDouble()
                var formattedString: String

                // Decidimos qué formato aplicar
                if (originalString.contains(",")) {
                    formattedString = df.format(parsed)

                    // Truco para mantener la coma si el usuario la acaba de escribir
                    // DecimalFormat tiende a borrar "12," y dejarlo en "12"
                    if (originalString.endsWith(",")) {
                        formattedString = dfnd.format(parsed) + ","
                    } else if (originalString.endsWith(",0")) {
                        // Caso especial para escribir decimales exactos
                        formattedString = dfnd.format(parsed) + ",0"
                    }
                } else {
                    formattedString = dfnd.format(parsed)
                }

                editText.setText(formattedString)

                // Recalcular posición del cursor
                val endlen = editText.text.length
                val selection = (cursorPosition + (endlen - inilen))

                // Validamos que el cursor no se salga de los límites
                if (selection > 0 && selection <= editText.text.length) {
                    editText.setSelection(selection)
                } else {
                    editText.setSelection(editText.text.length)
                }
            }

        } catch (nfe: NumberFormatException) {
            // Si hay error de formato, no hacemos nada
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Volvemos a activar el listener
        editText.addTextChangedListener(this)
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}