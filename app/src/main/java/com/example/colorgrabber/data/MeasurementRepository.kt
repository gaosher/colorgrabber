package com.example.colorgrabber.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.Flow

class MeasurementRepository(private val context: Context) {
    private val dao = AppDatabase.get(context).measurementDao()

    fun observeAll(): Flow<List<Measurement>> = dao.observeAll()
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun insert(m: Measurement): Long = dao.insert(m)
    suspend fun update(m: Measurement) = dao.update(m)

    suspend fun delete(m: Measurement, alsoDeleteImage: Boolean) {
        if (alsoDeleteImage) m.imageUri?.let { AlbumStore.deleteImage(context, Uri.parse(it)) }
        dao.delete(m)
    }

    fun saveImageToAlbum(bitmap: Bitmap, name: String): Uri? =
        AlbumStore.saveImage(context, bitmap, name)

    suspend fun exportCsv(): String = CsvExporter.toCsv(dao.getAll())
}
