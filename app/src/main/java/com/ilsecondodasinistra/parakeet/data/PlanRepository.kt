package com.ilsecondodasinistra.parakeet.data

import com.ilsecondodasinistra.parakeet.domain.DayPlan
import com.ilsecondodasinistra.parakeet.domain.Routine
import com.ilsecondodasinistra.parakeet.domain.RoutineColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

fun Set<DayOfWeek>.toMask(): Int = fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }
fun Int.toDays(): Set<DayOfWeek> = DayOfWeek.entries.filter { this and (1 shl (it.value - 1)) != 0 }.toSet()

private fun LocalTime.minuteOfDay() = hour * 60 + minute
private fun Int.toTime(): LocalTime = LocalTime.of(this / 60, this % 60)

@Singleton
class PlanRepository @Inject constructor(
    private val dao: PlanDao,
    private val tasks: TaskRepository,
    private val settings: SettingsRepository,
    private val sync: SyncDao,
    private val clock: Clock,
) {

    val routines: Flow<List<Routine>> = combine(dao.observeRoutines(), dao.observeRoutineTasks()) { list, links ->
        val byRoutine = links.groupBy({ it.routineId }, { it.taskId })
        list.map { e ->
            Routine(
                id = e.id, name = e.name, color = e.color, exitTime = e.exitMinute.toTime(),
                taskIds = byRoutine[e.id].orEmpty().toSet(), days = e.daysMask.toDays(),
                enabled = e.enabled, position = e.position,
            )
        }
    }

    val overrides: Flow<Map<LocalDate, DayPlan>> = dao.observeDayPlans().map { list ->
        list.mapNotNull { it.toDomain() }.associateBy { it.date }
    }

    suspend fun currentRoutines(): List<Routine> = routines.first()
    suspend fun currentOverrides(): Map<LocalDate, DayPlan> = overrides.first()

    // ---- routines ----------------------------------------------------------------------------

    /**
     * Creates or updates [routine]; if it is on, its days are removed from the other routines that are on. Returns its id.
     * A [seed] routine (onboarding, 5.0 conversion) keeps `updatedAt` 0: it does not travel to other devices.
     */
    suspend fun saveRoutine(routine: Routine, seed: Boolean = false): Long {
        val stamp = if (seed) 0 else clock.millis()
        val id = if (routine.id == 0L) {
            dao.insertRoutine(routine.toEntity().copy(id = 0, position = dao.nextRoutinePosition(), uid = newUid(), updatedAt = stamp))
        } else {
            val old = dao.routineById(routine.id)
            dao.updateRoutine(routine.toEntity().copy(uid = old?.uid ?: newUid(), updatedAt = stamp)); routine.id
        }
        dao.replaceRoutineTasks(id, routine.taskIds)
        if (routine.days.isNotEmpty()) setDays(id, routine.days, stamp)
        return id
    }

    /**
     * Weekly assignment. Routines that are on never share a weekday: when [routineId] is on (or [takeOver], used when the
     * user picks it for a weekday and it is about to be switched on), its days are removed from the other routines that
     * are on. A routine that is off keeps its days and is not touched.
     */
    suspend fun setDays(routineId: Long, days: Set<DayOfWeek>, stamp: Long = clock.millis(), takeOver: Boolean = false) {
        val all = dao.routines()
        val now = clock.millis()
        val mask = days.toMask()
        val strip = takeOver || all.firstOrNull { it.id == routineId }?.enabled == true
        val updated = all.map {
            when {
                it.id == routineId -> it.copy(daysMask = mask, updatedAt = stamp)
                strip && it.enabled && it.daysMask and mask != 0 -> it.copy(daysMask = it.daysMask and mask.inv(), updatedAt = now)
                else -> it
            }
        }
        dao.updateRoutines(updated.filterIndexed { i, r -> r != all[i] })
    }

    suspend fun setEnabled(routine: Routine, enabled: Boolean) {
        val old = dao.routineById(routine.id) ?: return
        dao.updateRoutine(old.copy(enabled = enabled, updatedAt = clock.millis()))
    }

    suspend fun deleteRoutine(id: Long) {
        val old = dao.routineById(id)
        if (old != null && old.updatedAt > 0) sync.putTombstone(TombstoneEntity(TombstoneEntity.ROUTINE, old.uid, id, clock.millis()))
        dao.deleteDayPlansOf(id)
        dao.clearRoutineTasks(id)
        dao.deleteRoutine(id)
    }

    /** Next free color of the palette, so two routines rarely look alike. */
    suspend fun nextColor(): Int {
        val used = dao.routines().map { it.color }
        return RoutineColors.firstOrNull { it !in used } ?: RoutineColors[used.size % RoutineColors.size]
    }

    // ---- one-time day plans ------------------------------------------------------------------

    suspend fun setDayPlan(plan: DayPlan, seed: Boolean = false) {
        sync.deleteTombstone(TombstoneEntity.DAY_PLAN, plan.date.toEpochDay().toString())
        dao.upsertDayPlan(plan.toEntity().copy(updatedAt = if (seed) 0 else clock.millis()))
    }

    suspend fun clearDayPlan(date: LocalDate) {
        val old = dao.dayPlanOf(date.toEpochDay()) ?: return
        dao.deleteDayPlan(old.epochDay)
        if (old.updatedAt > 0) sync.putTombstone(TombstoneEntity(TombstoneEntity.DAY_PLAN, old.epochDay.toString(), old.epochDay, clock.millis()))
    }

    /** Day plans of past days are useless: removed now and then. */
    suspend fun prune(today: LocalDate) = dao.deleteDayPlansBefore(today.minusDays(1).toEpochDay())

    // ---- 5.0 → 5.1 -----------------------------------------------------------------------------

    /**
     * Turns the single 5.0 setup (exit time + ticked activities) into a first routine without weekdays, so
     * nothing starts ringing on days the user never chose. An alarm that was still armed becomes a one-time
     * plan for its date, so it keeps ringing as expected.
     */
    suspend fun migrateFromSingleSetupIfNeeded(defaultName: String) {
        // 3.x data is imported by LegacyMigration first (exit time, activities): wait for it
        if (settings.isPlanMigrated() || !settings.isLegacyMigrated()) return
        val s = settings.current()
        if (dao.routineCount() == 0 && s.exitTime != null) {
            val checked = tasks.tasks.first().filter { it.checked }.map { it.id }.toSet()
            val id = saveRoutine(
                Routine(name = defaultName, color = RoutineColors.first(), exitTime = s.exitTime, taskIds = checked),
                seed = true,
            )
            val active = s.activeAlarm
            if (active != null && active.wakeAtMillis > System.currentTimeMillis() && s.alarmMode == AlarmMode.INTERNAL) {
                val date = Instant.ofEpochMilli(active.exitAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                setDayPlan(DayPlan.UseRoutine(date, id), seed = true)
            }
        }
        settings.markPlanMigrated()
    }

    private fun Routine.toEntity() = RoutineEntity(
        id = id, name = name, color = color, exitMinute = exitTime.minuteOfDay(),
        daysMask = days.toMask(), enabled = enabled, position = position,
    )

    private fun DayPlanEntity.toDomain(): DayPlan? {
        val date = LocalDate.ofEpochDay(epochDay)
        return when (kind) {
            KIND_SKIP -> DayPlan.Skip(date)
            KIND_ROUTINE -> routineId?.let { DayPlan.UseRoutine(date, it) }
            KIND_CUSTOM -> DayPlan.Custom(
                date, routineId, (exitMinute ?: return null).toTime(),
                taskIds.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet(),
            )
            else -> null
        }
    }

    private fun DayPlan.toEntity(): DayPlanEntity = when (this) {
        is DayPlan.Skip -> DayPlanEntity(date.toEpochDay(), KIND_SKIP, null, null, "")
        is DayPlan.UseRoutine -> DayPlanEntity(date.toEpochDay(), KIND_ROUTINE, routineId, null, "")
        is DayPlan.Custom -> DayPlanEntity(date.toEpochDay(), KIND_CUSTOM, routineId, exitTime.minuteOfDay(), taskIds.joinToString(","))
    }

    private companion object {
        const val KIND_SKIP = "SKIP"
        const val KIND_ROUTINE = "ROUTINE"
        const val KIND_CUSTOM = "CUSTOM"
    }
}
