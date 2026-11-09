package kz.astana.afisha.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Machine-readable dates of an event, written into the feed by tools/schedule.py at import time
 * (fields dateFrom / dateTo / dates / time). The app never guesses a year itself: a card without
 * these fields (and without an ISO `startsAt`) has an unknown schedule and is shown as "date TBA".
 */
data class EventSchedule(
    val from: LocalDate,
    val to: LocalDate,
    /** Discrete days the event happens on; empty = a continuous run from [from] to [to] (exhibitions). */
    val dates: List<LocalDate> = emptyList(),
    /** Start time in [ZONE]; null when the source has none or several. */
    val time: LocalTime? = null,
) {
    /** A continuous multi-day run (exhibition, festival) rather than separate show days. */
    val isRun: Boolean get() = dates.isEmpty() && from != to

    fun isOver(today: LocalDate): Boolean = to < today

    /** First day on or after [today] the event can be attended; null when it is over. */
    fun nextDay(today: LocalDate): LocalDate? = when {
        to < today -> null
        dates.isNotEmpty() -> dates.firstOrNull { it >= today }
        from >= today -> from
        else -> today
    }

    /** Remaining show days after [nextDay] (discrete schedules only). */
    fun moreDays(today: LocalDate): Int {
        val next = nextDay(today) ?: return 0
        return dates.count { it > next }
    }

    /** True when the event takes place on at least one day of [start]..[end] (inclusive). */
    fun occursBetween(start: LocalDate, end: LocalDate): Boolean =
        if (dates.isNotEmpty()) dates.any { it in start..end } else !(to < start || from > end)

    /** Ongoing run that started before today: shown in the "On now" group, not under a day. */
    fun isOngoingRun(today: LocalDate): Boolean = isRun && from < today && to >= today

    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Qyzylorda")

        fun today(): LocalDate = LocalDate.now(ZONE)

        /** Saturday–Sunday of the current weekend, or the next one on weekdays. */
        fun weekend(today: LocalDate): ClosedRange<LocalDate> {
            val start = if (today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY) today
                else today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
            return start..today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        }

        /** From feed fields; null when they are missing or malformed (never a guessed date). */
        fun of(from: String?, to: String?, dates: List<String>, time: String?): EventSchedule? = runCatching {
            if (from.isNullOrBlank() || to.isNullOrBlank()) return null
            val start = LocalDate.parse(from)
            val end = LocalDate.parse(to)
            if (end < start) return null
            EventSchedule(
                from = start, to = end,
                dates = dates.map { LocalDate.parse(it) }.filter { it in start..end }.distinct().sorted(),
                time = time?.takeIf { it.isNotBlank() }?.let { LocalTime.parse(it) },
            )
        }.getOrNull()

        /** Fallback for older feeds: an ISO `startsAt` with an explicit offset or a bare ISO day. */
        fun fromIso(startsAt: String): EventSchedule? {
            val raw = startsAt.trim()
            if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(raw)) {
                return runCatching { LocalDate.parse(raw) }.getOrNull()?.let { EventSchedule(it, it, listOf(it)) }
            }
            val stamp = runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(ZONE) }.getOrNull() ?: return null
            val day = stamp.toLocalDate()
            return EventSchedule(day, day, listOf(day), stamp.toLocalTime().withSecond(0).withNano(0))
        }
    }
}
