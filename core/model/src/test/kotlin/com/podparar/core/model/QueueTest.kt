package com.podparar.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "Up next" rules: an ordered list of episode ids with no duplicates, changed only by pure functions. */
class QueueTest {
    private val abc = listOf("a", "b", "c")

    // --- playNext ---

    @Test
    fun `playNext on an empty queue gives just that id`() {
        assertEquals(listOf("x"), Queue.playNext(emptyList(), "x"))
    }

    @Test
    fun `playNext inserts a new id at the front`() {
        assertEquals(listOf("x", "a", "b", "c"), Queue.playNext(abc, "x"))
    }

    @Test
    fun `playNext moves an id already queued to the front without duplicating it`() {
        assertEquals(listOf("c", "a", "b"), Queue.playNext(abc, "c"))
        assertEquals(listOf("b", "a", "c"), Queue.playNext(abc, "b"))
    }

    @Test
    fun `playNext of the first id leaves the queue as it was`() {
        assertEquals(abc, Queue.playNext(abc, "a"))
    }

    @Test
    fun `playNext does not change the list passed in`() {
        val queue = listOf("a", "b")
        Queue.playNext(queue, "b")
        assertEquals(listOf("a", "b"), queue)
    }

    // --- add ---

    @Test
    fun `add on an empty queue gives just that id`() {
        assertEquals(listOf("x"), Queue.add(emptyList(), "x"))
    }

    @Test
    fun `add appends a new id at the end`() {
        assertEquals(listOf("a", "b", "c", "x"), Queue.add(abc, "x"))
    }

    @Test
    fun `add of an id already queued leaves the queue unchanged`() {
        assertEquals(abc, Queue.add(abc, "a"))
        assertEquals(abc, Queue.add(abc, "b"))
        assertEquals(abc, Queue.add(abc, "c"))
    }

    @Test
    fun `adding the same id twice keeps one copy`() {
        assertEquals(listOf("x"), Queue.add(Queue.add(emptyList(), "x"), "x"))
    }

    // --- remove ---

    @Test
    fun `remove takes the id out and keeps the others in order`() {
        assertEquals(listOf("b", "c"), Queue.remove(abc, "a"))
        assertEquals(listOf("a", "c"), Queue.remove(abc, "b"))
        assertEquals(listOf("a", "b"), Queue.remove(abc, "c"))
    }

    @Test
    fun `remove of an id not queued leaves the queue unchanged`() {
        assertEquals(abc, Queue.remove(abc, "x"))
    }

    @Test
    fun `remove on an empty queue gives an empty queue`() {
        assertEquals(emptyList<String>(), Queue.remove(emptyList(), "x"))
    }

    @Test
    fun `remove of the only id gives an empty queue`() {
        assertEquals(emptyList<String>(), Queue.remove(listOf("x"), "x"))
    }

    // --- move ---

    @Test
    fun `move forward to a later index`() {
        assertEquals(listOf("b", "c", "a"), Queue.move(abc, 0, 2))
        assertEquals(listOf("b", "a", "c"), Queue.move(abc, 0, 1))
    }

    @Test
    fun `move back to an earlier index`() {
        assertEquals(listOf("c", "a", "b"), Queue.move(abc, 2, 0))
        assertEquals(listOf("a", "c", "b"), Queue.move(abc, 2, 1))
    }

    @Test
    fun `move to the same index leaves the queue unchanged`() {
        assertEquals(abc, Queue.move(abc, 1, 1))
    }

    @Test
    fun `move clamps a target past the end to the last index`() {
        assertEquals(listOf("b", "c", "a"), Queue.move(abc, 0, 10))
    }

    @Test
    fun `move clamps a negative target to the first index`() {
        assertEquals(listOf("c", "a", "b"), Queue.move(abc, 2, -5))
    }

    @Test
    fun `move with an out of range source leaves the queue unchanged`() {
        assertEquals(abc, Queue.move(abc, 3, 0))
        assertEquals(abc, Queue.move(abc, 99, 1))
        assertEquals(abc, Queue.move(abc, -1, 1))
    }

    @Test
    fun `move on an empty queue gives an empty queue`() {
        assertEquals(emptyList<String>(), Queue.move(emptyList(), 0, 0))
        assertEquals(emptyList<String>(), Queue.move(emptyList(), 0, 3))
    }

    @Test
    fun `move keeps every id exactly once`() {
        val queue = listOf("a", "b", "c", "d", "e")
        for (from in queue.indices) {
            for (to in -1..queue.size) {
                val moved = Queue.move(queue, from, to)
                assertEquals("move($from, $to)", queue.sorted(), moved.sorted())
                assertEquals("move($from, $to)", queue[from], moved[to.coerceIn(0, queue.lastIndex)])
            }
        }
    }

    // --- takeNext ---

    @Test
    fun `takeNext returns the first id and the rest of the queue`() {
        assertEquals("a" to listOf("b", "c"), Queue.takeNext(abc))
    }

    @Test
    fun `takeNext of a single id empties the queue`() {
        assertEquals("x" to emptyList<String>(), Queue.takeNext(listOf("x")))
    }

    @Test
    fun `takeNext on an empty queue gives null and an empty queue`() {
        val (next, rest) = Queue.takeNext(emptyList())
        assertNull(next)
        assertEquals(emptyList<String>(), rest)
    }

    @Test
    fun `takeNext repeatedly drains the queue in order`() {
        var queue = abc
        val taken = mutableListOf<String>()
        while (true) {
            val (next, rest) = Queue.takeNext(queue)
            if (next == null) break
            taken += next
            queue = rest
        }
        assertEquals(abc, taken)
        assertEquals(emptyList<String>(), queue)
    }

    // --- no duplicates across operations ---

    @Test
    fun `a mix of operations never produces duplicates`() {
        var queue = emptyList<String>()
        queue = Queue.add(queue, "a")
        queue = Queue.add(queue, "b")
        queue = Queue.playNext(queue, "b")
        queue = Queue.add(queue, "a")
        queue = Queue.playNext(queue, "c")
        queue = Queue.playNext(queue, "a")
        queue = Queue.add(queue, "c")
        assertEquals(listOf("a", "c", "b"), queue)
        assertEquals(queue.size, queue.toSet().size)
    }
}
