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

    @Delete
    suspend fun borrarListaGastos(gastos: List<Gasto>)

    @Query("SELECT * FROM tabla_categorias ORDER BY nombre ASC")
    fun obtenerCategorias(): kotlinx.coroutines.flow.Flow<List<Categoria>>

    // Actualiza el nombre de la categoría en todos los gastos que la tengan
    @Query("UPDATE tabla_gastos SET categoria = :nuevoNombre WHERE categoria = :viejoNombre")
    suspend fun actualizarCategoriaEnGastos(viejoNombre: String, nuevoNombre: String)

    // CONSULTA DE BUSCADOR AVANZADO
    // Si un parámetro es null, esa parte del filtro se ignora.
    @Query("""
        SELECT * FROM tabla_gastos 
        WHERE (:nombre IS NULL OR LOWER(nombre) LIKE '%' || LOWER(:nombre) || '%')
        AND (:descripcion IS NULL OR LOWER(descripcion) LIKE '%' || LOWER(:descripcion) || '%')
        AND (:categoria IS NULL OR categoria = :categoria)
        AND (:precioMin IS NULL OR cantidad >= :precioMin)
        AND (:precioMax IS NULL OR cantidad <= :precioMax)
        AND (:fechaInicio IS NULL OR fecha >= :fechaInicio)
        AND (:fechaFin IS NULL OR fecha <= :fechaFin)
        ORDER BY fecha DESC
    """)
    fun buscarGastosAvanzado(
        nombre: String?,
        descripcion: String?,
        categoria: String?,
        precioMin: Double?,
        precioMax: Double?,
        fechaInicio: Long?,
        fechaFin: Long?
    ): Flow<List<Gasto>>
    // Usamos Flow para que si cambias un gasto buscado, se actualice en tiempo real


    // --- MÉTODOS PARA IMPORTACIÓN/EXPORTACIÓN ---

    // 1. Para exportar todo de golpe
    @Query("SELECT * FROM tabla_gastos")
    suspend fun obtenerTodosLosGastosDirecto(): List<Gasto> // Sin Flow, lista directa

    @Query("SELECT * FROM tabla_categorias")
    suspend fun obtenerTodasLasCategoriasDirecto(): List<Categoria> // Sin Flow

    // 2. Para el modo "Sustituir" (Borrar todo antes de importar)
    @Query("DELETE FROM tabla_gastos")
    suspend fun borrarTodosLosGastos()

    @Query("DELETE FROM tabla_categorias")
    suspend fun borrarTodasLasCategorias()

    // 3. Para insertar listas masivas (OnConflictStrategy.REPLACE o IGNORE según prefieras)
    // Usamos REPLACE para categorías por si ya existen actualizar su foto
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarListaCategorias(categorias: List<Categoria>)

    // Para gastos, al importar, el código se encargará de poner ID=0 para que se creen nuevos
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarListaGastos(gastos: List<Gasto>)

    // Obtener categoria por nombre
    @Query("SELECT * FROM tabla_categorias WHERE nombre = :nombre LIMIT 1")
    suspend fun obtenerCategoriaPorNombre(nombre: String): Categoria?

    // Busca si existe un gasto IDÉNTICO (mismo nombre, cantidad y fecha exacta)
    @Query("SELECT * FROM tabla_gastos WHERE nombre = :nombre AND cantidad = :cantidad AND fecha = :fecha LIMIT 1")
    suspend fun buscarDuplicado(nombre: String, cantidad: Double, fecha: Long): Gasto?

    // Para exportar por rango (fechaInicio <= fecha <= fechaFin)
    @Query("SELECT * FROM tabla_gastos WHERE fecha BETWEEN :inicio AND :fin ORDER BY fecha DESC")
    suspend fun obtenerGastosEnRangoDirecto(inicio: Long, fin: Long): List<Gasto>
}