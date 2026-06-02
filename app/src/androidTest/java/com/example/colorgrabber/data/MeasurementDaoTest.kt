package com.example.colorgrabber.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MeasurementDao

    private fun sample() = Measurement(
        timestamp = 1000L, source = "live",
        roiX = 0, roiY = 0, roiW = 10, roiH = 10,
        rawR = 200, rawG = 100, rawB = 50, normR = 210, normG = 105, normB = 55,
        hsvH = 20.0, hsvS = 0.7, hsvV = 0.8, labL = 50.0, labA = 30.0, labB = 40.0,
        absR = null, absG = null, absB = null,
        gainR = 1.05, gainG = 1.0, gainB = 1.1, tempAdjust = 0.0
    )

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        dao = db.measurementDao()
    }

    @After fun teardown() = db.close()

    @Test fun insertAndRead() = runBlocking {
        val id = dao.insert(sample())
        val read = dao.getById(id)
        assertEquals(200, read?.rawR)
        assertEquals(1, dao.getAll().size)
    }
}
