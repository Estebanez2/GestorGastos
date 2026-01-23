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

    data class ResultadoImportacion(
        val exito: Boolean,
        val gastosInsertados: Int = 0,
        val conflictos: List<ConflictoGasto> = emptyList() // Lista de repetidos para revisar
    )

    // --- EXPORTAR (SIN CAMBIOS, PERO INCLUIDO PARA QUE EL ARCHIVO ESTÉ COMPLETO) ---

    suspend fun exportarDatos(incluirFotos: Boolean): File? = withContext(Dispatchers.IO) {
        try {
            val gastos = dao.obtenerTodosLosGastosDirecto()
            val categorias = dao.obtenerTodasLasCategoriasDirecto()
            val timeStamp = System.currentTimeMillis()
            val nombreBase = "backup_gastos_$timeStamp"

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

                zos.putNextEntry(ZipEntry("data.json"))
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

    suspend fun exportarSoloCSV(): File? = withContext(Dispatchers.IO) {
        try {
            val gastos = dao.obtenerTodosLosGastosDirecto()
            val csvContent = generarCsvParaZip(gastos)
            val timeStamp = System.currentTimeMillis()
            val file = File(context.externalCacheDir, "gastos_$timeStamp.csv")
            file.writeText(csvContent)
            return@withContext file
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    // --- IMPORTAR (CON DETECCIÓN DE DUPLICADOS) ---

    suspend fun importarDatos(uriArchivo: Uri, modoSustituir: Boolean): ResultadoImportacion = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val nombreArchivo = obtenerNombreArchivo(uriArchivo)
            val esZip = nombreArchivo.endsWith(".zip", true)

            var jsonString = ""
            val mapaFotosNuevas = mutableMapOf<String, String>()

            if (!esZip) {
                jsonString = contentResolver.openInputStream(uriArchivo)?.bufferedReader().use { it?.readText() } ?: ""
            } else {
                val inputStream = contentResolver.openInputStream(uriArchivo) ?: return@withContext ResultadoImportacion(false)
                val zis = ZipInputStream(BufferedInputStream(inputStream))
                var entry: ZipEntry?

                while (zis.nextEntry.also { entry = it } != null) {
                    val nombreEntry = entry!!.name
                    if (nombreEntry == "data.json") {
                        jsonString = zis.bufferedReader().readText()
                    } else if (nombreEntry.startsWith("images/")) {
                        val nombreArchivoSolo = File(nombreEntry).name
                        val archivoDestino = File(context.filesDir, "imported_${System.currentTimeMillis()}_$nombreArchivoSolo")
                        val fos = FileOutputStream(archivoDestino)
                        zis.copyTo(fos)
                        fos.close()
                        val nuevaUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivoDestino).toString()
                        mapaFotosNuevas[nombreEntry] = nuevaUri
                    }
                }
                zis.close()
            }

            if (jsonString.isEmpty()) return@withContext ResultadoImportacion(false)

            val backupData = gson.fromJson(jsonString, BackupData::class.java)

            // 1. SI ES MODO SUSTITUIR -> Borramos todo. No hay duplicados posibles.
            if (modoSustituir) {
                dao.borrarTodosLosGastos()
            }

            // 2. CATEGORÍAS (Lógica de mezcla inteligente que hicimos antes)
            val categoriasFinales = mutableListOf<Categoria>()
            for (catImportada in backupData.categorias) {
                val catExistente = dao.obtenerCategoriaPorNombre(catImportada.nombre)
                val uriImportada = recuperarUriFoto(catImportada.uriFoto, mapaFotosNuevas)
                if (catExistente != null) {
                    val uriFinal = if (catExistente.uriFoto != null) catExistente.uriFoto else uriImportada
                    categoriasFinales.add(catImportada.copy(uriFoto = uriFinal))
                } else {
                    categoriasFinales.add(catImportada.copy(uriFoto = uriImportada))
                }
            }
            dao.insertarListaCategorias(categoriasFinales)

            // 3. GASTOS: DETECCIÓN DE DUPLICADOS
            val gastosParaInsertar = mutableListOf<Gasto>()
            val listaConflictos = mutableListOf<ConflictoGasto>()

            for (gastoRaw in backupData.gastos) {
                val nuevaUri = recuperarUriFoto(gastoRaw.uriFoto, mapaFotosNuevas)
                val gastoCandidato = gastoRaw.copy(id = 0L, uriFoto = nuevaUri)

                if (modoSustituir) {
                    // Si estamos sustituyendo, entra todo directo
                    gastosParaInsertar.add(gastoCandidato)
                } else {
                    // Si estamos AÑADIENDO, verificamos si ya existe
                    val duplicado = dao.buscarDuplicado(gastoCandidato.nombre, gastoCandidato.cantidad, gastoCandidato.fecha)

                    if (duplicado != null) {
                        // ¡CONFLICTO! Lo guardamos para que el usuario decida
                        listaConflictos.add(ConflictoGasto(existente = duplicado, nuevoImportado = gastoCandidato))
                    } else {
                        // Limpio, adentro
                        gastosParaInsertar.add(gastoCandidato)
                    }
                }
            }

            // Insertamos los que son seguros (nuevos o todos si sustituimos)
            if (gastosParaInsertar.isNotEmpty()) {
                dao.insertarListaGastos(gastosParaInsertar)
            }

            // Devolvemos resultado. Si listaConflictos no está vacía, el Main tendrá que actuar.
            return@withContext ResultadoImportacion(true, gastosParaInsertar.size, listaConflictos)

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext ResultadoImportacion(false)
        }
    }

    // --- NUEVO HELPER PÚBLICO: RESOLVER CONFLICTOS ---
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

    // --- HELPERS PRIVADOS (IGUAL QUE ANTES) ---

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
        sb.append("Fecha;Categoria;Nombre;Descripcion;Cantidad;NombreArchivoImagen\n")
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
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
}