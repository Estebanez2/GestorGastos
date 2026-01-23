package com.example.gestorgastos.ui

import android.content.Context
import android.net.Uri
import com.example.gestorgastos.data.AppDatabase
import com.example.gestorgastos.data.BackupData
import com.example.gestorgastos.data.Categoria
import com.example.gestorgastos.data.Gasto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class DataTransferManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.gastoDao()
    private val gson = Gson()

    // --- CLASES PARA EL RESULTADO ---

    // Un conflicto es: El gasto que ya tienes (original) vs el que intentas meter (nuevo)
    data class ConflictoGasto(
        val existente: Gasto,
        val nuevoImportado: Gasto
    )

    data class ConflictoCategoria(
        val categoriaNombre: String,
        val uriActual: String?,
        val uriNueva: String?
    )

    data class ResultadoImportacion(
        val exito: Boolean,
        val gastosInsertados: Int = 0,
        val conflictosGastos: List<ConflictoGasto> = emptyList(),
        val conflictosCategorias: List<ConflictoCategoria> = emptyList()
    )

    // --- EXPORTAR (SIN CAMBIOS, PERO INCLUIDO PARA QUE EL ARCHIVO ESTÉ COMPLETO) ---

    suspend fun exportarDatos(incluirFotos: Boolean, inicio: Long, fin: Long): File? = withContext(Dispatchers.IO) {
        try {
            val gastos = dao.obtenerGastosEnRangoDirecto(inicio, fin)
            val categorias = dao.obtenerTodasLasCategoriasDirecto()
            val nombreBase = generarNombreArchivo(inicio, fin)

            val mapaNombresFotos = mutableMapOf<String, String>()

            if (incluirFotos) {
                val nombresUsados = mutableSetOf<String>()
                val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

                gastos.forEach { gasto ->
                    gasto.uriFoto?.let { uri ->
                        val fechaStr = sdf.format(Date(gasto.fecha))
                        val nombreLimpio = sanitizarNombreArchivo(gasto.nombre)
                        var nombreNuevo = "${fechaStr}_${nombreLimpio}.jpg"
                        var contador = 1
                        while (nombresUsados.contains(nombreNuevo)) {
                            nombreNuevo = "${fechaStr}_${nombreLimpio}_($contador).jpg"
                            contador++
                        }
                        nombresUsados.add(nombreNuevo)
                        mapaNombresFotos[uri] = "images/$nombreNuevo"
                    }
                }

                categorias.forEach { cat ->
                    cat.uriFoto?.let { uri ->
                        if (!mapaNombresFotos.containsKey(uri)) {
                            val nombreLimpio = sanitizarNombreArchivo(cat.nombre)
                            var nombreNuevo = "Categoria_${nombreLimpio}.jpg"
                            var contador = 1
                            while (nombresUsados.contains(nombreNuevo)) {
                                nombreNuevo = "Categoria_${nombreLimpio}_($contador).jpg"
                                contador++
                            }
                            nombresUsados.add(nombreNuevo)
                            mapaNombresFotos[uri] = "images/$nombreNuevo"
                        }
                    }
                }
            }

            val gastosExport = gastos.map { g -> g.copy(uriFoto = g.uriFoto?.let { mapaNombresFotos[it] ?: obtenerNombreRelativoGenerico(it) }) }
            val categoriasExport = categorias.map { c -> c.copy(uriFoto = c.uriFoto?.let { mapaNombresFotos[it] ?: obtenerNombreRelativoGenerico(it) }) }

            val backupData = BackupData(gastos = gastosExport, categorias = categoriasExport)
            val jsonString = gson.toJson(backupData)

            if (!incluirFotos) {
                val file = File(context.externalCacheDir, "$nombreBase.json")
                file.writeText(jsonString)
                return@withContext file
            } else {
                val zipFile = File(context.externalCacheDir, "$nombreBase.zip")
                val fos = FileOutputStream(zipFile)
                val zos = ZipOutputStream(BufferedOutputStream(fos))

                zos.putNextEntry(ZipEntry("gastos.json"))
                zos.write(jsonString.toByteArray())
                zos.closeEntry()

                val csvContent = generarCsvParaZip(gastosExport)
                zos.putNextEntry(ZipEntry("gastos.csv"))
                zos.write(csvContent.toByteArray())
                zos.closeEntry()

                for ((uriOriginal, nombreNuevoRelativo) in mapaNombresFotos) {
                    try {
                        val uri = Uri.parse(uriOriginal)
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            zos.putNextEntry(ZipEntry(nombreNuevoRelativo))
                            inputStream.copyTo(zos)
                            inputStream.close()
                            zos.closeEntry()
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                zos.close()
                return@withContext zipFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun exportarSoloCSV(inicio: Long, fin: Long): File? = withContext(Dispatchers.IO) {
        try {
            val gastos = dao.obtenerGastosEnRangoDirecto(inicio, fin)
            val csvContent = generarCsvParaZip(gastos)
            val nombreArchivo = generarNombreArchivo(inicio, fin)
            val file = File(context.externalCacheDir, "$nombreArchivo.csv")
            file.writeText(csvContent)
            return@withContext file
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    // --- IMPORTAR (SOPORTE PARA ZIP, JSON Y CSV) ---

    suspend fun importarDatos(uriArchivo: Uri, modoSustituir: Boolean): ResultadoImportacion = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val nombreArchivo = obtenerNombreArchivo(uriArchivo)

            // Detectamos el tipo de archivo
            val esZip = nombreArchivo.endsWith(".zip", true)
            val esCsv = nombreArchivo.endsWith(".csv", true)

            var backupData: BackupData? = null
            val mapaFotosNuevas = mutableMapOf<String, String>()

            if (esZip) {
                // --- MODO ZIP ---
                val inputStream = contentResolver.openInputStream(uriArchivo) ?: return@withContext ResultadoImportacion(false)
                val zis = ZipInputStream(BufferedInputStream(inputStream))
                var entry: ZipEntry?
                var jsonString = ""

                while (zis.nextEntry.also { entry = it } != null) {
                    val nombreEntry = entry!!.name
                    if (nombreEntry == "gastos.json") {
                        jsonString = zis.bufferedReader().readText()
                    } else if (nombreEntry.startsWith("images/")) {
                        // ... (Lógica de extracción de fotos igual que antes) ...
                        val nombreArchivoSolo = File(nombreEntry).name
                        val archivoDestino = File(context.filesDir, "imported_${System.currentTimeMillis()}_$nombreArchivoSolo")
                        val fos = FileOutputStream(archivoDestino)
                        zis.copyTo(fos)
                        fos.close()
                        val nuevaUri = androidx.core.content.FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", archivoDestino
                        ).toString()
                        mapaFotosNuevas[nombreEntry] = nuevaUri
                    }
                }
                zis.close()
                if (jsonString.isNotEmpty()) {
                    backupData = gson.fromJson(jsonString, BackupData::class.java)
                }

            } else if (esCsv) {
                // --- MODO CSV (NUEVO) ---
                val csvContent = contentResolver.openInputStream(uriArchivo)?.bufferedReader().use { it?.readText() } ?: ""
                // Convertimos el texto CSV a objetos Gasto y Categorias
                backupData = leerCsvYConvertirAGastos(csvContent)

            } else {
                // --- MODO JSON (Asumimos JSON si no es zip ni csv) ---
                val jsonString = contentResolver.openInputStream(uriArchivo)?.bufferedReader().use { it?.readText() } ?: ""
                if (jsonString.isNotEmpty()) {
                    backupData = gson.fromJson(jsonString, BackupData::class.java)
                }
            }

            if (backupData == null) return@withContext ResultadoImportacion(false)

            // ---------------------------------------------------------
            // A PARTIR DE AQUÍ LA LÓGICA ES COMÚN PARA TODOS LOS FORMATOS
            // ---------------------------------------------------------

            // 1. MODO SUSTITUIR
            if (modoSustituir) {
                dao.borrarTodosLosGastos()
            }

            // 2. PROCESAR CATEGORÍAS
            val categoriasFinales = mutableListOf<Categoria>()
            val conflictosCategorias = mutableListOf<ConflictoCategoria>()

            for (catImportada in backupData.categorias) {
                val catExistente = dao.obtenerCategoriaPorNombre(catImportada.nombre)
                val uriImportada = recuperarUriFoto(catImportada.uriFoto, mapaFotosNuevas)

                if (catExistente != null) {
                    // La categoría existe. ¿Tiene foto importada?
                    if (uriImportada != null) {
                        // REGLA: Si trae foto nueva, PREGUNTAMOS SIEMPRE (incluso si la local ya tiene foto o es icono)
                        conflictosCategorias.add(ConflictoCategoria(
                            categoriaNombre = catExistente.nombre,
                            uriActual = catExistente.uriFoto,
                            uriNueva = uriImportada
                        ))
                    }
                    // Mientras decidimos, mantenemos la actual en la lista para que no falle la FK de los gastos
                    categoriasFinales.add(catExistente)
                } else {
                    // No existe, la creamos tal cual viene
                    categoriasFinales.add(catImportada.copy(uriFoto = uriImportada))
                }
            }
            dao.insertarListaCategorias(categoriasFinales)

            // 3. PROCESAR GASTOS Y DETECTAR DUPLICADOS
            val gastosParaInsertar = mutableListOf<Gasto>()
            val listaConflictos = mutableListOf<ConflictoGasto>()

            for (gastoRaw in backupData.gastos) {
                val nuevaUri = recuperarUriFoto(gastoRaw.uriFoto, mapaFotosNuevas)
                val gastoCandidato = gastoRaw.copy(id = 0L, uriFoto = nuevaUri)

                if (modoSustituir) {
                    gastosParaInsertar.add(gastoCandidato)
                } else {
                    val duplicado = dao.buscarDuplicado(gastoCandidato.nombre, gastoCandidato.cantidad, gastoCandidato.fecha)
                    if (duplicado != null) {
                        listaConflictos.add(ConflictoGasto(existente = duplicado, nuevoImportado = gastoCandidato))
                    } else {
                        gastosParaInsertar.add(gastoCandidato)
                    }
                }
            }

            if (gastosParaInsertar.isNotEmpty()) {
                dao.insertarListaGastos(gastosParaInsertar)
            }

            return@withContext ResultadoImportacion(true, gastosParaInsertar.size, listaConflictos, conflictosCategorias)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext ResultadoImportacion(false)
        }
    }

    // --- PARSEAR CSV MANUALMENTE ---
    private fun leerCsvYConvertirAGastos(csvContent: String): BackupData {
        val lineas = csvContent.lines()
        val gastos = mutableListOf<Gasto>()
        val nombresCategoriasVistas = mutableSetOf<String>()

        // 1. DEFINIMOS LOS 3 NIVELES DE PRECISIÓN
        val sdfFull = SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale.getDefault()) // Formato CSV Nuevo (Perfecto)
        val sdfSeconds = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())  // Formato Intermedio
        val sdfDay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())               // Formato Antiguo o Excel Manual

        val primeraLinea = lineas.firstOrNull() ?: return BackupData(gastos = emptyList(), categorias = emptyList())

        // Detectamos cabecera genérica (ya sea "Fecha;" o "FechaHora;")
        val inicioDatos = if (primeraLinea.startsWith("Fecha")) 1 else 0

        for (i in inicioDatos until lineas.size) {
            val linea = lineas[i].trim()
            if (linea.isEmpty()) continue

            try {
                // Formato esperado: Fecha;Categoria;Nombre;Descripcion;Cantidad;Imagen
                val partes = linea.split(";")
                if (partes.size >= 5) {
                    val fechaStr = partes[0]
                    val categoria = partes[1]
                    val nombre = partes[2]
                    val descripcion = partes[3]
                    val cantidadStr = partes[4]
                    val uriFoto = if (partes.size > 5) partes[5].ifEmpty { null } else null

                    // 2. LÓGICA EN CASCADA (Try-Catch anidados)
                    // Intentamos leer de lo más preciso a lo más simple
                    val fecha = try {
                        sdfFull.parse(fechaStr)?.time // ¿Tiene milisegundos?
                    } catch (e: Exception) {
                        try {
                            sdfSeconds.parse(fechaStr)?.time // ¿Tiene segundos?
                        } catch (e2: Exception) {
                            try {
                                sdfDay.parse(fechaStr)?.time // ¿Es solo fecha (dd/MM/yyyy)?
                            } catch (e3: Exception) {
                                System.currentTimeMillis() // Si todo falla, ponemos fecha actual
                            }
                        }
                    } ?: System.currentTimeMillis()

                    val cantidad = try { cantidadStr.replace(",", ".").toDouble() } catch (e: Exception) { 0.0 }

                    val gasto = Gasto(
                        nombre = nombre,
                        cantidad = cantidad,
                        descripcion = descripcion,
                        categoria = categoria,
                        fecha = fecha,
                        uriFoto = uriFoto
                    )
                    gastos.add(gasto)
                    nombresCategoriasVistas.add(categoria)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val categorias = nombresCategoriasVistas.map { nombreCat ->
            Categoria(nombre = nombreCat, uriFoto = null)
        }

        return BackupData(gastos = gastos, categorias = categorias)
    }


    // Esta función la llamará el Main cuando el usuario decida qué hacer con los repetidos
    suspend fun resolverConflictos(
        aDescartar: List<ConflictoGasto>,
        aReemplazar: List<ConflictoGasto>,
        aDuplicar: List<ConflictoGasto> // Añadir como copia
    ) = withContext(Dispatchers.IO) {

        // 1. Descartar: No hacemos nada, simplemente no se insertan.

        // 2. Reemplazar: Actualizamos el existente con los datos del nuevo
        for (conflicto in aReemplazar) {
            val gastoFinal = conflicto.nuevoImportado.copy(id = conflicto.existente.id) // Usamos ID viejo para machacar
            dao.actualizarGasto(gastoFinal)
        }

        // 3. Duplicar: Insertamos el nuevo (tendrá ID 0, así que se crea uno nuevo)
        // Nota: Tendremos 2 gastos iguales.
        if (aDuplicar.isNotEmpty()) {
            val nuevos = aDuplicar.map { it.nuevoImportado }
            dao.insertarListaGastos(nuevos)
        }
    }

    private fun sanitizarNombreArchivo(nombre: String): String {
        return nombre.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    private fun obtenerNombreRelativoGenerico(uri: String): String {
        return "images/img_${System.currentTimeMillis()}.jpg"
    }

    private fun recuperarUriFoto(rutaRelativa: String?, mapaNuevas: Map<String, String>): String? {
        if (rutaRelativa == null) return null
        return mapaNuevas[rutaRelativa]
    }

    private fun generarCsvParaZip(gastos: List<Gasto>): String {
        val sb = StringBuilder()
        sb.append("FechaHora;Categoria;Nombre;Descripcion;Cantidad;NombreArchivoImagen\n")
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS", Locale.getDefault())
        for (g in gastos) {
            val fecha = sdf.format(Date(g.fecha))
            val nombre = g.nombre.replace(";", ",")
            val desc = g.descripcion.replace(";", ",")
            val cat = g.categoria
            val cant = g.cantidad.toString().replace(".", ",")
            val img = g.uriFoto ?: ""
            sb.append("$fecha;$cat;$nombre;$desc;$cant;$img\n")
        }
        return sb.toString()
    }

    private fun obtenerNombreArchivo(uri: Uri): String {
        var result = ""
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) result = cursor.getString(index)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return result
    }

    // --- HELPER PARA NOMBRES DE ARCHIVO DEFINITIVOS ---
    private fun generarNombreArchivo(inicio: Long, fin: Long): String {
        val sdfDia = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val now = Date()

        // CASO A: HISTORIAL COMPLETO -> "CopiaSeguridad" + FECHA + HORA
        if (inicio == 0L && fin == Long.MAX_VALUE) {
            val fechaHora = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(now)
            return "GestorGastos_HistorialCompleto_$fechaHora"
        }

        // CASO B: RANGO PARCIAL -> "Exportar"
        val fInicio = sdfDia.format(Date(inicio))
        val fFin = sdfDia.format(Date(fin))

        // Si es un solo día (ej: Exportar solo hoy)
        if (fInicio == fFin) {
            return "GestorGastos_Exportar_$fInicio"
        }

        // Si es un rango -> Conector "_a_"
        return "GestorGastos_Exportar_${fInicio}_a_${fFin}"
    }

    suspend fun actualizarFotoCategoria(nombreCategoria: String, nuevaUri: String?) {
        val cat = dao.obtenerCategoriaPorNombre(nombreCategoria)
        if (cat != null) {
            dao.actualizarCategoria(cat.copy(uriFoto = nuevaUri))
        }
    }
}