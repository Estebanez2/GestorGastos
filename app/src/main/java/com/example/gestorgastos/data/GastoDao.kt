package com.example.gestorgastos.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GastoDao {

    // Insertar un gasto nuevo (si ya existe el ID, lo reemplaza)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarGasto(gasto: Gasto)

    // Actualizar un gasto existente
    @Update
    suspend fun actualizarGasto(gasto: Gasto)

    // Borrar un gasto
    @Delete
    suspend fun borrarGasto(gasto: Gasto)

    // Obtener gastos entre dos fechas (para filtrar por mes)
    // Usamos Flow para que si cambias algo, la lista se actualice sola en pantalla
    @Query("SELECT * FROM tabla_gastos WHERE fecha >= :fechaInicio AND fecha <= :fechaFin ORDER BY fecha DESC")
    fun obtenerGastosPorMes(fechaInicio: Long, fechaFin: Long): Flow<List<Gasto>>

    // Obtener la suma total de un rango de fechas (para tus alertas Amarilla/Roja)
    @Query("SELECT SUM(cantidad) FROM tabla_gastos WHERE fecha >= :fechaInicio AND fecha <= :fechaFin")
    fun obtenerSumaGastos(fechaInicio: Long, fechaFin: Long): Flow<Double?>

    // --- MÉTODOS PARA CATEGORÍAS ---
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertarCategoria(categoria: Categoria)

    @Delete
    suspend fun borrarCategoria(categoria: Categoria)

    @Query("SELECT * FROM tabla_categorias ORDER BY nombre ASC")
    fun obtenerCategorias(): kotlinx.coroutines.flow.Flow<List<Categoria>>

    // Actualiza el nombre de la categoría en todos los gastos que la tengan
    @Query("UPDATE tabla_gastos SET categoria = :nuevoNombre WHERE categoria = :viejoNombre")
    suspend fun actualizarCategoriaEnGastos(viejoNombre: String, nuevoNombre: String)

    // CONSULTA DE BUSCADOR AVANZADO
    // Si un parámetro es null, esa parte del filtro se ignora.
    @androidx.room.Query("""
        SELECT * FROM tabla_gastos 
        WHERE (:nombre IS NULL OR LOWER(nombre) LIKE '%' || LOWER(:nombre) || '%' OR LOWER(descripcion) LIKE '%' || LOWER(:nombre) || '%')
        AND (:categoria IS NULL OR categoria = :categoria)
        AND (:precioMin IS NULL OR cantidad >= :precioMin)
        AND (:precioMax IS NULL OR cantidad <= :precioMax)
        AND (:fechaInicio IS NULL OR fecha >= :fechaInicio)
        AND (:fechaFin IS NULL OR fecha <= :fechaFin)
        ORDER BY fecha DESC
    """)
    fun buscarGastosAvanzado(
        nombre: String?,
        categoria: String?,
        precioMin: Double?,
        precioMax: Double?,
        fechaInicio: Long?,
        fechaFin: Long?
    ): Flow<List<Gasto>>
    // Usamos Flow para que si cambias un gasto buscado, se actualice en tiempo real
}