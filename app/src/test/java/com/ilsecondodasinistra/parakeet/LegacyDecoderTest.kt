package com.ilsecondodasinistra.parakeet

import com.ilsecondodasinistra.parakeet.data.LegacyDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.util.LinkedList

class LegacyDecoderTest {

    /** Same encoding as the v3.x ObjectSerializer.encodeBytes. */
    private fun encodeLikeV3(obj: java.io.Serializable): String {
        val out = ByteArrayOutputStream()
        ObjectOutputStream(out).use { it.writeObject(obj) }
        val sb = StringBuilder()
        for (b in out.toByteArray()) {
            sb.append((((b.toInt() shr 4) and 0xF) + 'a'.code).toChar())
            sb.append(((b.toInt() and 0xF) + 'a'.code).toChar())
        }
        return sb.toString()
    }

    @Test fun decodesTasksSavedByV3() {
        val list = LinkedList<ThingToDo>().apply {
            add(ThingToDo("doccia", 20, true))
            add(ThingToDo("colazione", 15, false))
        }
        val tasks = LegacyDecoder.decodeTasks(encodeLikeV3(list))
        assertEquals(2, tasks.size)
        assertEquals("doccia", tasks[0].name)
        assertEquals(20, tasks[0].minutes)
        assertEquals(true, tasks[0].checked)
        assertEquals("colazione", tasks[1].name)
        assertEquals(false, tasks[1].checked)
        assertEquals(listOf(0, 1), tasks.map { it.position })
    }

    @Test fun blankOrCorruptInputGivesEmptyList() {
        assertTrue(LegacyDecoder.decodeTasks(null).isEmpty())
        assertTrue(LegacyDecoder.decodeTasks("").isEmpty())
        assertTrue(LegacyDecoder.decodeTasks("abcdefgh").isEmpty())
    }
}
