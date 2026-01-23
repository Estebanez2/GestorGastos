package com.example.gestorgastos.data

/**
 * Clase que representa la estructura completa del archivo de copia de seguridad (JSON).
 */
data class BackupData(
    val fechaExportacion: Long = System.currentTimeMillis(),
    val gastos: List<Gasto>,
    val categorias: List<Categoria> // Asumiendo que tienes esta clase
)