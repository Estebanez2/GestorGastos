package com.example.gestorgastos.data

import android.content.Context

class Preferencias(context: Context) {
    private val storage = context.getSharedPreferences("mis_configuraciones", Context.MODE_PRIVATE)

    // Guardar límites
    fun guardarLimites(amarillo: Double, rojo: Double) {
        storage.edit().apply {
            putFloat("limite_amarillo", amarillo.toFloat())
            putFloat("limite_rojo", rojo.toFloat())
            apply()
        }
    }

    // Leer límite Amarillo (Por defecto 500 si no existe)
    fun obtenerAmarillo(): Double {
        return storage.getFloat("limite_amarillo", 500.0f).toDouble()
    }

    // Leer límite Rojo (Por defecto 1000 si no existe)
    fun obtenerRojo(): Double {
        return storage.getFloat("limite_rojo", 1000.0f).toDouble()
    }
}