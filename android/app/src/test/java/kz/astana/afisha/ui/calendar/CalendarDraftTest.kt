package kz.astana.afisha.ui.calendar

import java.time.Instant
import java.time.LocalDate
import kz.astana.afisha.domain.EventSchedule
import kz.astana.afisha.domain.AppLanguage
import kz.astana.afisha.domain.EventCard
import kz.astana.afisha.domain.Interest
import org.junit.Assert.*
import org.junit.Test

class CalendarDraftTest {
    private fun card(date: String = "2026-11-09T19:00:00+05:00", place: Boolean = false) = EventCard(
        "a", "astana", "Концерт", "Ән кеші", "Concert", date, "Зал", "Өнер залы", "Hall",
        "Address 1", Interest.CONCERTS, null, "", "https://example.com/post/1", "Source",
        isPlace = place, descriptionRu = "Полный текст", descriptionKk = "Толық мәтін", descriptionEn = "Full text")
    @Test fun exactOffsetStartIsKept() {
        assertEquals(Instant.parse("2026-11-09T14:00:00Z").toEpochMilli(), CalendarDraft.from(card(), AppLanguage.RU).beginMillis)
    }
    @Test fun ambiguousDisplayDateNeverInventsYear() {
        assertNull(CalendarDraft.from(card("9 ноя, 19:00"), AppLanguage.RU).beginMillis)
    }
    @Test fun missingOffsetNeverInventsTimezone() {
        assertNull(CalendarDraft.from(card("2026-11-09T19:00:00"), AppLanguage.RU).beginMillis)
    }
    @Test fun dateOnlyAndMissingNeverInventTimeOrAllDay() {
        for (date in listOf("", "2026-11-09", "2026-11-09 — 2026-11-11", "null"))
            assertNull(CalendarDraft.from(card(date), AppLanguage.RU).beginMillis)
    }
    @Test fun placesNeverReceiveEventTimestamp() {
        val draft = CalendarDraft.from(card(place = true), AppLanguage.EN)
        assertNull(draft.beginMillis)
        assertFalse(draft.description.contains("2026-11-09T"))
    }
    @Test fun localizedFactsFullTextAndSpecificSourceSurvive() {
        for ((lang, title, venue, text) in listOf(
            listOf(AppLanguage.RU, "Концерт", "Зал", "Полный текст"),
            listOf(AppLanguage.KK, "Ән кеші", "Өнер залы", "Толық мәтін"),
            listOf(AppLanguage.EN, "Concert", "Hall", "Full text"))) {
            val draft = CalendarDraft.from(card(), lang as AppLanguage)
            assertEquals(title, draft.title)
            assertEquals("$venue, Address 1", draft.location)
            assertTrue(draft.description.contains(text as String))
            assertTrue(draft.description.contains("https://example.com/post/1"))
        }
    }
    @Test fun scheduleGivesNextShowWithTimeButNeverForRunsOrMissingTime() {
        val today = LocalDate.of(2026, 11, 13)
        val multi = card("12 ноя, 26 ноя, 19:00").copy(schedule = EventSchedule.of("2026-11-12", "2026-11-26", listOf("2026-11-12", "2026-11-26"), "19:00"))
        assertEquals(Instant.parse("2026-11-26T14:00:00Z").toEpochMilli(), CalendarDraft.from(multi, AppLanguage.RU, today).beginMillis)
        val noTime = card("13 ноя").copy(schedule = EventSchedule.of("2026-11-13", "2026-11-13", listOf("2026-11-13"), null))
        assertNull(CalendarDraft.from(noTime, AppLanguage.RU, today).beginMillis)
        val run = card("7 ноя – 30 янв, 18:30").copy(schedule = EventSchedule.of("2026-11-07", "2027-01-30", emptyList(), "18:30"))
        assertNull(CalendarDraft.from(run, AppLanguage.RU, today).beginMillis)
    }
}
