package com.remora.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RemoraDatabase::class.java
    )

    @Test
    fun migrate1To2_addsConsentGrantedAndSetsExistingRowsToTrue() {
        // Create DB at version 1 with existing row
        helper.createDatabase(testDbName, 1).apply {
            execSQL("INSERT INTO app_setting (id, notifications_enabled, paused, pause_until) VALUES (1, 1, 0, 0)")
            close()
        }

        // Re-open database with version 2 and run MIGRATION_1_2
        val db = helper.runMigrationsAndValidate(testDbName, 2, true, RemoraDatabase.MIGRATION_1_2)

        // Verify the migrated row has consent_granted = 1
        db.query("SELECT id, consent_granted, notifications_enabled, paused, pause_until FROM app_setting WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(cursor.getColumnIndexOrThrow("id")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("consent_granted")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("notifications_enabled")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("paused")))
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("pause_until")))
        }
    }
}
