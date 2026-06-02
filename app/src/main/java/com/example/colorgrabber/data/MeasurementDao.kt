package com.example.colorgrabber.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Insert suspend fun insert(m: Measurement): Long
    @Update suspend fun update(m: Measurement)
    @Delete suspend fun delete(m: Measurement)

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Measurement>>

    @Query("SELECT * FROM measurements ORDER BY timestamp DESC")
    suspend fun getAll(): List<Measurement>

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getById(id: Long): Measurement?
}
