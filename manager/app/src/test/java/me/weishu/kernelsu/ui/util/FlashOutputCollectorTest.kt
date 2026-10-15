package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor

class FlashOutputCollectorTest {
    @Test
    fun outputIsStoredBeforeUiCallbackDelivery() {
        val pending = ArrayDeque<Runnable>()
        val delivered = mutableListOf<String>()
        val output = FlashOutputCollector(Executor { pending.add(it) }, delivered::add)
        val line = "Installing module"

        val reader = Thread { output.add(line) }
        reader.start()
        reader.join()

        assertEquals(listOf(line), output.toList())
        assertTrue(delivered.isEmpty())
        pending.removeFirst().run()
        assertEquals(listOf(line), delivered)
    }

    @Test
    fun everyLineIsStoredWithoutDrainingUiQueue() {
        val pending = ArrayDeque<Runnable>()
        val stdout = FlashOutputCollector(Executor { pending.add(it) }) {}
        val stderr = FlashOutputCollector(Executor { pending.add(it) }) {}
        val reader = Thread {
            stdout.add("Installing module")
            stderr.add("Error: cannot read archive")
        }
        reader.start()
        reader.join()

        assertTrue(stdout.any { it == "Installing module" })
        assertTrue(stderr.any {
            it.trimStart().removePrefix("Error: ").startsWith("cannot read archive")
        })
        assertEquals(2, pending.size)
    }
}
