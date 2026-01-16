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
}