package kz.barajyq.app.ui.calendar

import java.time.LocalDate
import java.time.OffsetDateTime
import kz.barajyq.app.domain.AppLanguage
import kz.barajyq.app.domain.EventCard
import kz.barajyq.app.domain.EventSchedule

/**
 * Prefill for the system calendar editor. The start is set only for a known show day WITH a start
 * time (the next upcoming one); no invented duration, end, all-day flag or date for a place.
 */
data class CalendarDraft(val title: String, val location: String, val description: String, val beginMillis: Long?) {
    companion object {
        fun from(card: EventCard, language: AppLanguage?, today: LocalDate = EventSchedule.today()): CalendarDraft {
            val begin = if (card.isPlace) null else beginOf(card, today)
            return CalendarDraft(card.title(language),
                listOf(card.place(language), card.address).filter { it.isNotBlank() }.distinct().joinToString(", "),
                listOf(card.description(language), card.startsAt.takeUnless { card.isPlace }.orEmpty(), card.sourceUrl)
                    .filter { it.isNotBlank() }.joinToString("\n\n"), begin)
        }

        private fun beginOf(card: EventCard, today: LocalDate): Long? {
            card.schedule?.let { s ->
                val time = s.time ?: return null
                if (s.isRun) return null // exhibitions: the visitor picks the day
                val day = s.nextDay(today) ?: return null
                return day.atTime(time).atZone(EventSchedule.ZONE).toInstant().toEpochMilli()
            }
            return runCatching { OffsetDateTime.parse(card.startsAt.trim()).toInstant().toEpochMilli() }.getOrNull()
        }
    }
}
