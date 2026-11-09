package kz.astana.afisha.ui.feed

import java.time.LocalDate
import kz.astana.afisha.domain.AppLanguage
import kz.astana.afisha.domain.EventCard
import kz.astana.afisha.domain.EventSchedule
import kz.astana.afisha.domain.Localize
import java.time.LocalTime
import kz.astana.afisha.domain.Interest
import org.junit.Assert.*
import org.junit.Test

class FeedQueryTest {
    private val today = LocalDate.of(2026, 11, 8)
    private fun card(id: String, title: String = "Concert", date: String = "2026-11-09T19:00:00+05:00", price: String? = "от 1 000 ₸", places: Boolean = false) = EventCard(
        id, "astana", title, "Ән кеші", "Live concert", date, "Зал", "Өнер залы", "Music hall",
        "Кабанбай батыра, 11", Interest.CONCERTS, price, "", "https://example.com/$id", "Test",
        isPlace = places, descriptionRu = "Полное описание: Ёлка и музыка", descriptionKk = "Толық сипаттама: қазақша әндер", descriptionEn = "Full description: orchestra and music")
    private fun run(source: List<EventCard>, options: FeedQueryOptions = FeedQueryOptions(), lang: AppLanguage = AppLanguage.RU,
        hidden: Set<String> = emptySet(), places: Boolean = false, interests: Set<Interest> = Interest.entries.toSet()) =
        FeedQuery.apply(source, options, lang, "astana", interests, hidden, places, today)

    @Test fun combinedToolsApplyInsideScope() {
        val good = card("good")
        val source = listOf(good, card("hidden"), card("other-city").copy(cityId="almaty"), card("other-interest").copy(interest=Interest.SPORT),
            card("missing-price",price=null), card("missing-date",date="9 ноя, 19:00"),card("later",date="2026-12-01"))
        val options = FeedQueryOptions(query="ПОЛНОЕ музыка",categories=setOf(Interest.CONCERTS),price=PriceFilter.PAID,date=DateFilter.NEXT_7_DAYS,sort=FeedSort.NEAREST)
        assertEquals(listOf("good"),run(source,options,hidden=setOf("hidden"),interests=setOf(Interest.CONCERTS)).map{it.id})
    }
    @Test fun localizedCaseInsensitiveSearchUsesFullDescriptionVenueAndAddress() {
        val source=listOf(card("a"))
        for ((lang,query) in listOf(AppLanguage.RU to "ЕЛКА",AppLanguage.KK to "ҚАЗАҚША ӘНДЕР",AppLanguage.EN to "ORCHESTRA",AppLanguage.EN to "music hall",AppLanguage.KK to "ӨНЕР",AppLanguage.EN to "БАТЫРА, 11")) {
            assertEquals(query,1,run(source,FeedQueryOptions(query=query),lang).size)
        }
        assertTrue(run(source,FeedQueryOptions(query="not present")).isEmpty())
    }
    @Test fun priceUnknownNeverBecomesFreeOrPaid() {
        for (raw in listOf(null,"null","None","","  ","Средний чек 3000 ₸","донат","от 0 ₸","0–5000 ₸","500 рублей")) assertEquals(raw,PriceFilter.UNKNOWN,FeedQuery.priceKind(raw))
        for (raw in listOf("Бесплатно","ТЕГІН","free entry","0 ₸")) assertEquals(raw,PriceFilter.FREE,FeedQuery.priceKind(raw))
        for (raw in listOf("от 26 500 ₸","1 250 ₸","from 1000 KZT","500,50 ₸","500.50 KZT")) assertEquals(raw,PriceFilter.PAID,FeedQuery.priceKind(raw))
        assertTrue(run(listOf(card("a",price=null)),FeedQueryOptions(price=PriceFilter.FREE)).isEmpty())
    }
    @Test fun onlyExactDatesAndNoInventedIntervalYear() {
        for (date in listOf("9 ноя, 19:00","7 ноя – 30 янв, 18:30","2026-11-09 (multiple dates)","2026-11-31","2026-11-09/2026-11-13","")) assertNull(date,FeedQuery.exactDate(date))
        assertEquals(today,FeedQuery.exactDate("2026-11-07T22:30:00Z")) // UTC+5 next day
        assertEquals(today,FeedQuery.exactDate("2026-11-08"))
        assertTrue(run(listOf(card("a",date="9 ноя")),FeedQueryOptions(date=DateFilter.TODAY)).isEmpty())
    }
    @Test fun dateWindowBoundariesAreInclusive() {
        val source=(0..7).map{n->card(n.toString(),date=today.plusDays(n.toLong()).toString())}
        assertEquals(1,run(source,FeedQueryOptions(date=DateFilter.TODAY)).size)
        assertEquals(7,run(source,FeedQueryOptions(date=DateFilter.NEXT_7_DAYS)).size)
        assertEquals(8,run(source,FeedQueryOptions(date=DateFilter.NEXT_30_DAYS)).size)
    }
    @Test fun nearestSortIsStableAndMissingDatesLast() {
        val source=listOf(card("unknown-a",date="9 ноя"),card("late",date="2026-11-10"),card("a",date="2026-11-09T18:00:00+05:00"),card("b",date="2026-11-09T18:00:00+05:00"),card("evening",date="2026-11-09T20:00:00+05:00"),card("past",date="2026-11-07"),card("unknown-b",date=""))
        // "past" (2026-11-07) is expired and never shown; undated cards stay, last.
        assertEquals(listOf("a","b","evening","late","unknown-a","unknown-b"),run(source,FeedQueryOptions(sort=FeedSort.NEAREST)).map{it.id})
        assertEquals(source.map{it.id}-"past",run(source).map{it.id})
    }
    @Test fun nameSortUsesLocaleAndStableTies() {
        val source=listOf(card("z",title="Я"),card("a",title="А"),card("b",title="А"))
        assertEquals(listOf("a","b","z"),run(source,FeedQueryOptions(sort=FeedSort.NAME)).map{it.id})
    }
    @Test fun placesIgnoreEventDateFiltersAndCannotReturnEvents() {
        val source=listOf(card("event"),card("place",places=true,date="",price="Бесплатно"))
        assertEquals(listOf("place"),run(source,FeedQueryOptions(price=PriceFilter.FREE,date=DateFilter.TODAY,sort=FeedSort.NEAREST),places=true).map{it.id})
    }
    @Test fun roundupsAndHiddenNeverReturnAfterReset() {
        val source=listOf(card("bad",title="Репертуар на ноябрь"),card("ig-28b8ddc153"),card("hidden"),card("valid"))
        assertEquals(listOf("valid"),run(source,hidden=setOf("hidden")).map{it.id})
        assertEquals(listOf("valid"),run(source,FeedQueryOptions(query="nothing").copy(query=""),hidden=setOf("hidden")).map{it.id})
    }
    @Test fun independentTabStateRoundtripsAndResetDoesNotChangeOtherTab() {
        val events=FeedQueryOptions(query="music",categories=setOf(Interest.CONCERTS),price=PriceFilter.PAID,date=DateFilter.NEXT_7_DAYS,sort=FeedSort.NEAREST)
        val places=FeedQueryOptions(query="museum",price=PriceFilter.FREE,sort=FeedSort.NAME)
        assertEquals(events,FeedQueryOptions.decode(events.encode()))
        assertEquals(places,FeedQueryOptions.decode(places.encode()))
        val resetEvents=events.resetFilters()
        assertEquals("music",resetEvents.query);assertEquals(FeedSort.NEAREST,resetEvents.sort);assertEquals(0,resetEvents.filterCount)
        assertEquals("museum",places.query);assertEquals(PriceFilter.FREE,places.price)
        assertEquals(FeedQueryOptions(),FeedQueryOptions.decode(emptyList()))
    }
    // ---- Structured schedule (feed fields dateFrom/dateTo/dates/time) ----
    private fun d(s: String) = LocalDate.parse(s)
    private fun sched(from: String, to: String = from, dates: List<String> = listOf(from).takeIf { from == to }.orEmpty(), time: String? = null) =
        EventSchedule.of(from, to, dates, time)!!
    private fun scheduled(id: String, s: EventSchedule) = card(id, date = "display text").copy(schedule = s)

    @Test fun expiredEventsAreHiddenEverywhereButRunsStayUntilLastDay() {
        val source = listOf(
            scheduled("yesterday", sched("2026-11-07")),
            scheduled("run-ended", sched("2026-10-01", "2026-11-07")),
            scheduled("run-last-day", sched("2026-10-01", "2026-11-08")),
            scheduled("multi-one-left", sched("2026-11-01", "2026-11-20", listOf("2026-11-01", "2026-11-20"))),
            card("undated", date = "по расписанию"))
        assertEquals(listOf("run-last-day", "multi-one-left", "undated"), run(source).map { it.id })
        assertTrue(run(source, FeedQueryOptions(query = "Полное")).none { it.id == "yesterday" })
    }

    @Test fun dayFiltersUseScheduleAndWeekend() {
        // today = Sun 2026-11-08 → weekend is today only; Fri 2026-11-13 → next Sat–Sun 14–15.
        val source = listOf(
            scheduled("today", sched("2026-11-08", time = "19:00")),
            scheduled("tomorrow", sched("2026-11-09")),
            scheduled("sat", sched("2026-11-14")),
            scheduled("exhibition", sched("2026-11-01", "2026-12-31")),
            scheduled("gap", sched("2026-11-01", "2026-11-30", listOf("2026-11-01", "2026-11-30"))))
        assertEquals(setOf("today", "exhibition"), run(source, FeedQueryOptions(date = DateFilter.TODAY)).map { it.id }.toSet())
        assertEquals(setOf("tomorrow", "exhibition"), run(source, FeedQueryOptions(date = DateFilter.TOMORROW)).map { it.id }.toSet())
        assertEquals(setOf("today", "exhibition"), run(source, FeedQueryOptions(date = DateFilter.WEEKEND)).map { it.id }.toSet())
        val friday = FeedQuery.apply(source, FeedQueryOptions(date = DateFilter.WEEKEND), AppLanguage.RU, "astana",
            Interest.entries.toSet(), emptySet(), false, d("2026-11-13"))
        assertEquals(setOf("sat", "exhibition"), friday.map { it.id }.toSet())
        assertEquals(d("2026-11-14")..d("2026-11-15"), EventSchedule.weekend(d("2026-11-13")))
        assertEquals(d("2026-11-14")..d("2026-11-15"), EventSchedule.weekend(d("2026-11-14")))
    }

    @Test fun nearestGroupsByDayWithOngoingRunsAfterDatedEvents() {
        val source = listOf(
            scheduled("undated-iso-less", sched("2026-11-08").copy()).copy(schedule = null, startsAt = "скоро"),
            scheduled("run-closing-soon", sched("2026-10-01", "2026-11-20")),
            scheduled("run-closing-late", sched("2026-10-01", "2027-01-30")),
            scheduled("later", sched("2026-11-20")),
            scheduled("tomorrow-late", sched("2026-11-09", time = "21:00")),
            scheduled("tomorrow-early", sched("2026-11-09", time = "10:00")),
            scheduled("today", sched("2026-11-08")),
            scheduled("run-starts-later", sched("2026-11-12", "2026-11-14")))
        val sorted = run(source, FeedQueryOptions(sort = FeedSort.NEAREST))
        assertEquals(listOf("today", "tomorrow-early", "tomorrow-late", "run-starts-later", "later",
            "run-closing-soon", "run-closing-late", "undated-iso-less"), sorted.map { it.id })
        assertEquals(listOf(DaySection.TODAY, DaySection.TOMORROW, DaySection.LATER, DaySection.ONGOING, DaySection.UNDATED),
            FeedQuery.sections(sorted, today).map { it.first })
        val monday = d("2026-11-09")
        assertEquals(DaySection.THIS_WEEK, FeedQuery.section(scheduled("x", sched("2026-11-15")), monday))
        assertEquals(DaySection.LATER, FeedQuery.section(scheduled("x", sched("2026-11-16")), monday))
    }

    @Test fun malformedScheduleIsUnknownNotGuessed() {
        assertNull(EventSchedule.of("2026-11-10", "2026-11-01", emptyList(), null))
        assertNull(EventSchedule.of("10 ноя", "2026-11-11", emptyList(), null))
        assertNull(EventSchedule.of(null, "2026-11-11", emptyList(), null))
        assertEquals(LocalTime.of(18, 30), EventSchedule.of("2026-11-10", "2026-11-10", listOf("2026-11-10"), "18:30")!!.time)
        assertEquals(d("2026-11-08"), EventSchedule.fromIso("2026-11-07T22:30:00Z")!!.from)
        assertNull(EventSchedule.fromIso("9 ноя, 19:00"))
    }

    @Test fun whenTextIsRelativeAndLocalized() {
        val tomorrow = sched("2026-11-09", time = "19:00")
        assertEquals("Завтра, 19:00", Localize.schedule(tomorrow, AppLanguage.RU, today))
        assertEquals("Ертең, 19:00", Localize.schedule(tomorrow, AppLanguage.KK, today))
        assertEquals("Tomorrow, 19:00", Localize.schedule(tomorrow, AppLanguage.EN, today))
        assertEquals("Сегодня", Localize.schedule(sched("2026-11-08"), AppLanguage.RU, today))
        val multi = sched("2026-11-12", "2026-11-26", listOf("2026-11-12", "2026-11-19", "2026-11-26"), "19:00")
        assertEquals("Чт, 12 ноя, 19:00 · ещё 2 даты", Localize.schedule(multi, AppLanguage.RU, today))
        assertEquals("Thu, Nov 12, 19:00 · +2 more dates", Localize.schedule(multi, AppLanguage.EN, today))
        assertEquals("Бс, 12 қараша, 19:00 · тағы 2 күн", Localize.schedule(multi, AppLanguage.KK, today))
        val run = sched("2026-11-01", "2027-01-30")
        assertEquals("До 30 янв", Localize.schedule(run, AppLanguage.RU, today))
        assertEquals("30 қаңтарға дейін", Localize.schedule(run, AppLanguage.KK, today))
        assertEquals("Until Jan 30", Localize.schedule(run, AppLanguage.EN, today))
        assertEquals("13–14 ноя", Localize.schedule(sched("2026-11-13", "2026-11-14"), AppLanguage.RU, today))
        assertEquals("Nov 13–14", Localize.schedule(sched("2026-11-13", "2026-11-14"), AppLanguage.EN, today))
        assertEquals("6 дек – 27 янв", Localize.schedule(sched("2026-12-06", "2027-01-27"), AppLanguage.RU, today))
        assertEquals("Последний день", Localize.schedule(sched("2026-10-01", "2026-11-08"), AppLanguage.RU, today))
        assertEquals("13 ноя, 20:00", Localize.date("2026-11-13T20:00:00+05:00", AppLanguage.RU))
        assertEquals("ещё 1 дата", Localize.schedule(sched("2026-11-09", "2026-11-10", listOf("2026-11-09", "2026-11-10")), AppLanguage.RU, today).substringAfter("· "))
    }
    @Test fun cardPreviewSkipsRepeatedTitleLine() {
        assertEquals("Дата: 13 ноября в 20:00", previewText("ANI LORAK в Астане!\n\nДата: 13 ноября в 20:00", "ANI LORAK в Астане!"))
        assertEquals("На концерте комики…", previewText("🎤 Проверочный концерт🎤\n\nНа концерте комики…", "🎤 Проверочный концерт🎤"))
        // Nothing else left → the original text, never an empty preview.
        assertEquals("Bolero X / 13–14 ноября. Билеты", previewText("Bolero X / 13–14 ноября. Билеты", "Bolero X / 13–14 ноября"))
        assertEquals("Жителей приглашают", previewText("Жителей приглашают", "Экскурсия"))
    }
}
