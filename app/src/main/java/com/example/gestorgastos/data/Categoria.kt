package com.example.gestorgastos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tabla_categorias")
data class Categoria(
    @PrimaryKey val nombre: String, // El nombre es único (ej: "Comida")
    val uriFoto: String? = null     // La foto que elijas de la galería
)