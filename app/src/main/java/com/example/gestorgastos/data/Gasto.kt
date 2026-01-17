package com.example.gestorgastos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabla_gastos")
data class Gasto(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nombre: String,
    val cantidad: Double,
    val descripcion: String,
    val uriFoto: String? = null,
    val fecha: Long = System.currentTimeMillis(),
    val categoria: String = "Otros"
)