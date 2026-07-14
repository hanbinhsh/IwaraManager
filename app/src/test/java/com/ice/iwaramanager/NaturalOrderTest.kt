package com.ice.iwaramanager

import com.ice.iwaramanager.domain.util.NaturalOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderTest {
    @Test
    fun numericSegmentsAreSortedByValue() {
        val input = listOf("video10.mp4", "video2.mp4", "video01.mp4", "video1.mp4")

        val sorted = input.sortedWith(NaturalOrder::compare)

        assertEquals(listOf("video01.mp4", "video1.mp4", "video2.mp4", "video10.mp4"), sorted)
    }

    @Test
    fun pathsAreComparedCaseInsensitively() {
        val input = listOf("Folder10/A.mp4", "folder2/B.mp4", "folder1/C.mp4")

        val sorted = input.sortedWith(NaturalOrder::compare)

        assertEquals(listOf("folder1/C.mp4", "folder2/B.mp4", "Folder10/A.mp4"), sorted)
    }
}
