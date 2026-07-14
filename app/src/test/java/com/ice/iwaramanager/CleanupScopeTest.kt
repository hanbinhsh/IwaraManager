package com.ice.iwaramanager

import com.ice.iwaramanager.data.repository.resolveCleanupScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanupScopeTest {
    @Test
    fun emptyRequestCoversEveryConfiguredSource() {
        val scope = resolveCleanupScope(listOf("a", "b"), emptyList())
        assertEquals(setOf("a", "b"), scope.targetSourceIds)
        assertTrue(scope.coversAllConfiguredSources)
    }

    @Test
    fun emptyConfigurationStillAllowsOrphanCleanup() {
        val scope = resolveCleanupScope(emptyList(), emptyList())
        assertTrue(scope.targetSourceIds.isEmpty())
        assertTrue(scope.coversAllConfiguredSources)
    }

    @Test
    fun subsetDoesNotCoverOtherSources() {
        val scope = resolveCleanupScope(listOf("a", "b"), listOf("a"))
        assertEquals(setOf("a"), scope.targetSourceIds)
        assertFalse(scope.coversAllConfiguredSources)
    }
}
