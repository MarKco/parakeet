package com.ilsecondodasinistra.parakeet.data

import com.ilsecondodasinistra.parakeet.domain.DayPlan
import com.ilsecondodasinistra.parakeet.domain.Routine
import com.ilsecondodasinistra.parakeet.domain.RoutineColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    /** Creates or updates [routine]; its days are removed from every other routine. Returns its id. */
    suspend fun saveRoutine(routine: Routine): Long {
        val id = if (routine.id == 0L) {
            dao.insertRoutine(routine.toEntity().copy(id = 0, position = dao.nextRoutinePosition()))
        } else {
            dao.updateRoutine(routine.toEntity()); routine.id
        }
        dao.replaceRoutineTasks(id, routine.taskIds)
        if (routine.days.isNotEmpty()) setDays(id, routine.days)
        return id
    }

    /** Weekly assignment, exclusive: a weekday belongs to one routine at most. */
    suspend fun setDays(routineId: Long, days: Set<DayOfWeek>) {
        val all = dao.routines()
        val updated = all.map {
            when {
                it.id == routineId -> it.copy(daysMask = days.toMask())
                it.daysMask and days.toMask() != 0 -> it.copy(daysMask = it.daysMask and days.toMask().inv())
                else -> it
            }
        }
        dao.updateRoutines(updated.filterIndexed { i, r -> r != all[i] })
    }

    suspend fun setEnabled(routine: Routine, enabled: Boolean) = dao.updateRoutine(routine.copy(enabled = enabled).toEntity())

    suspend fun deleteRoutine(id: Long) {
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

    suspend fun setDayPlan(plan: DayPlan) = dao.upsertDayPlan(plan.toEntity())

    suspend fun clearDayPlan(date: LocalDate) = dao.deleteDayPlan(date.toEpochDay())

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
            )
            val active = s.activeAlarm
            if (active != null && active.wakeAtMillis > System.currentTimeMillis() && s.alarmMode == AlarmMode.INTERNAL) {
                val date = Instant.ofEpochMilli(active.exitAtMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                setDayPlan(DayPlan.UseRoutine(date, id))
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
