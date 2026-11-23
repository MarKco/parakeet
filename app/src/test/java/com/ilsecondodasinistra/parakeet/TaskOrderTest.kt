package com.ilsecondodasinistra.parakeet

import com.ilsecondodasinistra.parakeet.domain.Task
import com.ilsecondodasinistra.parakeet.domain.TaskOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskOrderTest {

    private val catalog = listOf(
        Task(1, "Doccia", 15, true, 0), Task(2, "Vestirsi", 10, true, 1), Task(3, "Colazione", 20, true, 2),
        Task(4, "Imprevisti", 5, true, 3, buffer = true), Task(5, "Barba", 10, false, 4), Task(6, "Caffè", 5, false, 5),
    )

    @Test fun theCushionIsAlwaysLastAmongTheSelected() {
        assertEquals(listOf(3L, 1L, 2L, 4L), TaskOrder.normalize(listOf(4L, 3L, 1L, 2L), catalog))
    }

    @Test fun unknownAndDuplicateIdsDrop() {
        assertEquals(listOf(2L, 1L), TaskOrder.normalize(listOf(2L, 99L, 1L, 2L), catalog))
    }

    @Test fun homeListIsSelectedThenCushionThenTheRestInCatalogOrder() {
        val shown = TaskOrder.display(listOf(3L, 4L, 1L), catalog).map { it.id }
        assertEquals(listOf(3L, 1L, 4L, 2L, 5L, 6L), shown)
    }

    @Test fun anAddedActivityGoesAfterTheLastSelectedAndBeforeTheCushion() {
        assertEquals(listOf(2L, 1L, 5L, 4L), TaskOrder.added(listOf(2L, 1L, 4L), 5L, catalog))
        assertEquals(listOf(2L, 1L, 4L), TaskOrder.added(listOf(2L, 1L, 4L), 1L, catalog))
    }

    @Test fun moveShiftsOneItem() {
        assertEquals(listOf(2L, 3L, 1L), TaskOrder.move(listOf(1L, 2L, 3L), 0, 2))
        assertEquals(listOf(3L, 1L, 2L), TaskOrder.move(listOf(1L, 2L, 3L), 2, 0))
        assertEquals(listOf(1L, 2L, 3L), TaskOrder.move(listOf(1L, 2L, 3L), 1, 1))
    }
}
