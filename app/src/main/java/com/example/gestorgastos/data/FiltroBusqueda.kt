package com.example.gestorgastos.data

// Creamos un Enum para identificar claramente qué quiere el usuario
enum class ModoFiltroFecha {
    MES_ACTUAL,  // Busca en el mes que se ve en pantalla (con opción de filtrar días 1-31)
    RANGO_FECHAS, // Busca entre dos fechas concretas (ignora el mes de la pantalla)
    TODOS        // Busca en todo el historial
}

data class FiltroBusqueda(
    val nombre: String? = null,
    val descripcion: String? = null,
    val categoria: String? = null,
    val precioMin: Double? = null,
    val precioMax: Double? = null,

    // OPCIÓN A: Días sueltos (para el mes actual)
    val diaInicio: Int? = null,
    val diaFin: Int? = null,

    // OPCIÓN B: Fechas completas (para rangos largos)
    val fechaInicioAbs: Long? = null,
    val fechaFinAbs: Long? = null,

    // SELECTOR DE MODO
    val modoFecha: ModoFiltroFecha = ModoFiltroFecha.MES_ACTUAL
)