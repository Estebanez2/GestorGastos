package com.example.gestorgastos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabla_gastos")
data class Gasto(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0, // El ID se crea solo (1, 2, 3...)

    val nombre: String,
    val cantidad: Double,
    val descripcion: String = "", // Opcional, por defecto vacía
    val fecha: Long, // Guardaremos la fecha en milisegundos para ordenar fácil
    val uriFoto: String? = null // Puede ser null si no hay foto
)