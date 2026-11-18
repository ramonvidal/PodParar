package com.podparar.core.playback

import com.podparar.core.model.CarBrowse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarMediaItemsPageTest {

    private fun items(n: Int): List<Int> = (0 until n).toList()

    private fun allPages(list: List<Int>, pageSize: Int, maxItems: Int? = null): List<Int> {
        val out = mutableListOf<Int>()
        var page = 0
        while (true) {
            val slice = if (maxItems == null) {
                CarMediaItems.page(list, page, pageSize)
            } else {
                CarMediaItems.page(list, page, pageSize, maxItems)
            }
            if (slice.isEmpty()) break
            out += slice
            page++
        }
        return out
    }

    @Test
    fun defaultCapIs50On120Items() {
        assertEquals(50, CarBrowse.MAX_ITEMS)
        assertEquals(items(50), allPages(items(120), pageSize = 20))
    }

    @Test
    fun maxItems200On120ItemsYieldsAll() {
        assertEquals(items(120), allPages(items(120), pageSize = 25, maxItems = 200))
    }

    @Test
    fun maxItems30Caps() {
        assertEquals(items(30), allPages(items(120), pageSize = 10, maxItems = 30))
    }

    @Test
    fun pagePastEndIsEmpty() {
        assertTrue(CarMediaItems.page(items(120), page = 5, pageSize = 10).isEmpty())
        assertTrue(CarMediaItems.page(items(120), page = 100, pageSize = 10, maxItems = 200).isEmpty())
    }

    @Test
    fun zeroOrNegativeMaxItemsIsEmpty() {
        assertTrue(CarMediaItems.page(items(120), 0, 10, maxItems = 0).isEmpty())
        assertTrue(CarMediaItems.page(items(120), 0, 10, maxItems = -5).isEmpty())
    }

    @Test
    fun lastPartialPage() {
        val last = CarMediaItems.page(items(120), page = 2, pageSize = 20, maxItems = 50)
        assertEquals((40 until 50).toList(), last)
        val partial = CarMediaItems.page(items(120), page = 3, pageSize = 25, maxItems = 60)
        assertTrue(partial.isEmpty())
        val partial2 = CarMediaItems.page(items(120), page = 2, pageSize = 25, maxItems = 60)
        assertEquals((50 until 60).toList(), partial2)
    }
}
