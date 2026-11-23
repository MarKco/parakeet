package com.ilsecondodasinistra.parakeet

import com.ilsecondodasinistra.parakeet.domain.Routine
import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.domain.TaskLabels
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TaskLabelsTest {

    private fun routine(id: Long, name: String, vararg tasks: Long) =
        Routine(id, name, 0, LocalTime.of(8, 0), tasks.toList())

    @Test fun uniqueNamesStayPlain() {
        val tasks = listOf(Task(1, "Doccia", 15, true), Task(2, "Colazione", 20, true))
        val labels = TaskLabels.of(tasks, listOf(routine(1, "Feriali", 1, 2)))
        assertEquals("Doccia", labels[1])
        assertEquals("Colazione", labels[2])
    }

    @Test fun theCopyShowsItsRoutineAndTheOriginalToo() {
        val tasks = listOf(Task(1, "Colazione", 20, true), Task(2, "Colazione", 40, true))
        val labels = TaskLabels.of(tasks, listOf(routine(1, "Feriali", 1), routine(2, "Ferie", 2)))
        assertEquals("Colazione (Feriali)", labels[1])
        assertEquals("Colazione (Ferie)", labels[2])
    }

    @Test fun severalRoutinesAreJoinedAndUnusedStaysPlain() {
        val tasks = listOf(Task(1, "colazione", 20, true), Task(2, "Colazione ", 40, true))
        val labels = TaskLabels.of(tasks, listOf(routine(1, "Feriali", 1), routine(2, "Weekend", 1)))
        assertEquals("colazione (Feriali, Weekend)", labels[1])
        assertEquals("Colazione ", labels[2])
    }
}
