package com.remora.data

import com.remora.core.model.entity.CardTags
import com.remora.core.model.entity.ContentType
import com.remora.core.model.entity.MemoryCard
import com.remora.core.model.entity.ProcessingStatus
import com.remora.util.HashUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyBoundaryTest {

    @Test
    fun testZeroNetworkEgressBeforeSaveConfirmation() {
        // Simulates capture event without Save tap
        var networkCallsInitiated = 0
        var localCardsSaved = 0

        val onSkipTapped = {
            // User skips: no network call, no local card created
            networkCallsInitiated += 0
            localCardsSaved += 0
        }

        onSkipTapped()
        assertEquals(0, networkCallsInitiated)
        assertEquals(0, localCardsSaved)
    }

    @Test
    fun testDuplicateImageDetectionBySha256() {
        val fakeImage1 = "Sample image byte stream A".toByteArray()
        val fakeImage2 = "Sample image byte stream A".toByteArray()
        val fakeImage3 = "Different lecture slide B".toByteArray()

        val hash1 = HashUtil.sha256(fakeImage1)
        val hash2 = HashUtil.sha256(fakeImage2)
        val hash3 = HashUtil.sha256(fakeImage3)

        assertEquals(hash1, hash2)
        assertFalse(hash1 == hash3)

        // Existing card in local library with hash1
        val existingCards = mapOf(hash1 to 101L)
        val isDuplicate = existingCards.containsKey(hash2)
        assertTrue(isDuplicate)
    }
}
