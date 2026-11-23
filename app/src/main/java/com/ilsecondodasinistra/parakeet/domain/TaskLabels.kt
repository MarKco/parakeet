package com.ilsecondodasinistra.parakeet.domain

/**
 * Names to SHOW for the activities of the catalog. When the user edits an activity "only in this routine", the
 * routine gets its own copy of it, so the catalog holds two activities with the same name. Wherever both can be
 * seen the copy carries the routines that use it in brackets ("Breakfast (Holidays)"). The stored name stays clean:
 * the brackets disappear by themselves as soon as the names differ again.
 */
object TaskLabels {

    fun of(tasks: List<Task>, routines: List<Routine>): Map<Long, String> {
        val shared = tasks.groupBy { it.name.trim().lowercase() }.filterValues { it.size > 1 }.keys
        return tasks.associate { task ->
            val users = routines.filter { task.id in it.taskIds }.map { it.name }
            task.id to if (task.name.trim().lowercase() in shared && users.isNotEmpty()) {
                "${task.name} (${users.joinToString(", ")})"
            } else task.name
        }
    }
}
