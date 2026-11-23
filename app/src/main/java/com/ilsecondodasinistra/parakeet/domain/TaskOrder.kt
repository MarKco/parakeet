package com.ilsecondodasinistra.parakeet.domain

/**
 * The order of the activities of a routine or of a day. A plan is the list of the ids that are selected, in the order
 * the user chose; the "time for the unexpected" cushion is never an activity of its own, so it always comes after
 * the real ones. What is not selected is not part of the order: the home list shows it afterwards, in catalog order.
 */
object TaskOrder {

    /** [ids] without duplicates or activities that no longer exist, the cushion(s) last. Order of the rest is kept. */
    fun normalize(ids: List<Long>, catalog: List<Task>): List<Long> {
        val byId = catalog.associateBy { it.id }
        val known = ids.distinct().filter { it in byId }
        return known.filter { byId.getValue(it).buffer.not() } + known.filter { byId.getValue(it).buffer }
    }

    /** Adds [id] to the end of the selected ones (before the cushion). Already there: unchanged. */
    fun added(ids: List<Long>, id: Long, catalog: List<Task>): List<Long> =
        if (id in ids) normalize(ids, catalog) else normalize(ids + id, catalog)

    /** The home list: the selected ones in order (cushion last), then the not selected ones in catalog order. */
    fun display(selected: List<Long>, catalog: List<Task>): List<Task> {
        val byId = catalog.associateBy { it.id }
        val chosen = normalize(selected, catalog)
        return chosen.map { byId.getValue(it) } + catalog.filter { it.id !in chosen }
    }

    /** [ids] with the item at [from] moved to [to]; both positions are among the movable ones (not the cushion). */
    fun move(ids: List<Long>, from: Int, to: Int): List<Long> {
        if (from == to || from !in ids.indices || to !in ids.indices) return ids
        return ids.toMutableList().also { it.add(to, it.removeAt(from)) }
    }
}
