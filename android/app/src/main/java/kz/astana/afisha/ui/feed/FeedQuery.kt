package kz.astana.afisha.ui.feed

import java.text.Collator
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale
import kz.astana.afisha.data.EventContentPolicy
import kz.astana.afisha.domain.AppLanguage
import kz.astana.afisha.domain.EventCard
import kz.astana.afisha.domain.EventSchedule
import kz.astana.afisha.domain.Interest

enum class PriceFilter { ALL, FREE, PAID, UNKNOWN }
enum class DateFilter { ANY, TODAY, TOMORROW, WEEKEND, NEXT_7_DAYS, NEXT_30_DAYS }
enum class FeedSort { ORIGINAL, NEAREST, NAME }

/** Day groups of the events list when it is sorted by date. */
enum class DaySection { TODAY, TOMORROW, THIS_WEEK, LATER, ONGOING, UNDATED }

data class FeedQueryOptions(
    val query: String = "",
    val categories: Set<Interest> = emptySet(),
    val price: PriceFilter = PriceFilter.ALL,
    val date: DateFilter = DateFilter.ANY,
    val sort: FeedSort = FeedSort.ORIGINAL,
) {
    val filterCount: Int get() = categories.size + (if (price != PriceFilter.ALL) 1 else 0) + (if (date != DateFilter.ANY) 1 else 0)
    val isNarrowed: Boolean get() = query.isNotBlank() || filterCount > 0
    fun resetFilters() = copy(categories = emptySet(), price = PriceFilter.ALL, date = DateFilter.ANY)
    // Only primitive strings: saveable across navigation, rotation and process recreation.
    fun encode(): List<String> = listOf(query, categories.sortedBy { it.name }.joinToString(",") { it.name }, price.name, date.name, sort.name)
    companion object {
        /** Events open sorted by date; places keep the curated order. */
        val EVENTS_DEFAULT = FeedQueryOptions(sort = FeedSort.NEAREST)
        val PLACES_DEFAULT = FeedQueryOptions()
        fun decode(values: List<String>): FeedQueryOptions = runCatching {
            FeedQueryOptions(values[0], values[1].split(',').mapNotNull { n -> Interest.entries.find { it.name == n } }.toSet(),
                PriceFilter.valueOf(values[2]), DateFilter.valueOf(values[3]), FeedSort.valueOf(values[4]))
        }.getOrDefault(FeedQueryOptions())
    }
}

/** Pure query layer. Never expands the profile/city/hidden scope or bypasses content policy. */
object FeedQuery {
    val zone: ZoneId = EventSchedule.ZONE
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace('ё', 'е').replace(Regex("\\s+"), " ").trim()

    /** Missing price is not free. Only explicit free wording or an unambiguous KZT price. */
    fun priceKind(raw: String?): PriceFilter {
        val price = EventContentPolicy.normalizePrice(raw)?.let(::normalize) ?: return PriceFilter.UNKNOWN
        if (price in setOf("бесплатно", "вход свободный", "тегін", "кіру тегін", "free", "free entry")) return PriceFilter.FREE
        val match = Regex("^(?:(?:от|from|бастап)\\s+)?([0-9]+(?:[ \\u00a0][0-9]{3})*)(?:[.,]([0-9]{1,2}))?\\s*(?:₸|тг\\.?|тенге|kzt)$").matchEntire(price)
            ?: return PriceFilter.UNKNOWN
        val whole = match.groupValues[1].replace(" ", "").replace(" ", "").toLongOrNull() ?: return PriceFilter.UNKNOWN
        val fraction = match.groupValues[2].ifEmpty { "0" }.toIntOrNull() ?: return PriceFilter.UNKNOWN
        // "от 0" is a variable tariff, not verified free admission.
        if (whole == 0L && fraction == 0) return if (price.startsWith("0")) PriceFilter.FREE else PriceFilter.UNKNOWN
        return PriceFilter.PAID
    }

    /** Only full ISO source dates, never infer a year from display dates or publication time. */
    fun exactDate(raw: String): LocalDate? = exactStart(raw)?.atZone(zone)?.toLocalDate()

    private fun exactStart(raw: String): Instant? {
        val value = raw.trim()
        if (Regex("\\d{4}-\\d{2}-\\d{2}").matches(value)) return runCatching { LocalDate.parse(value).atStartOfDay(zone).toInstant() }.getOrNull()
        return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
    }

    /** Structured schedule of an event card; places have none. */
    fun scheduleOf(card: EventCard): EventSchedule? =
        if (card.isPlace) null else card.schedule ?: EventSchedule.fromIso(card.startsAt)

    /** Events whose last day is before [today] are not shown anywhere in the app. */
    fun isExpired(card: EventCard, today: LocalDate): Boolean = scheduleOf(card)?.isOver(today) == true

    fun apply(
        source: List<EventCard>, options: FeedQueryOptions, language: AppLanguage?,
        cityId: String, interests: Set<Interest>, hidden: Set<String>, places: Boolean,
        today: LocalDate = LocalDate.now(zone),
    ): List<EventCard> {
        val tokens = normalize(options.query).split(' ').filter { it.isNotBlank() }
        val filtered = EventContentPolicy.clean(source).filter { card ->
            card.cityId == cityId && card.isPlace == places && card.id !in hidden && card.interest in interests &&
                (places || !isExpired(card, today)) &&
                (options.categories.isEmpty() || card.interest in options.categories) &&
                (options.price == PriceFilter.ALL || priceKind(card.price) == options.price) &&
                (places || matchesDate(card, options.date, today)) &&
                (tokens.isEmpty() || normalize(listOf(card.title(language), card.description(language), card.place(language), card.address)
                    .joinToString("\n")).let { haystack -> tokens.all(haystack::contains) })
        }
        return when {
            options.sort == FeedSort.NAME -> {
                val locale = Locale.forLanguageTag(when (language) { AppLanguage.KK -> "kk"; AppLanguage.RU -> "ru"; else -> "en" })
                val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
                // Stable TimSort: equal titles retain source order.
                filtered.sortedWith(Comparator { a, b -> collator.compare(a.title(language), b.title(language)) })
            }
            options.sort == FeedSort.NEAREST && !places -> filtered.sortedWith(
                compareBy<EventCard> { section(it, today).ordinal }
                    .thenBy { card -> sortDay(card, today) ?: LocalDate.MAX }
                    .thenBy { card -> scheduleOf(card)?.time ?: LocalTime.MAX })
            else -> filtered
        }
    }

    /** Day group of an (unexpired) event relative to [today]. */
    fun section(card: EventCard, today: LocalDate): DaySection {
        val schedule = scheduleOf(card) ?: return DaySection.UNDATED
        if (schedule.isOngoingRun(today)) return DaySection.ONGOING
        val next = schedule.nextDay(today) ?: return DaySection.UNDATED
        val endOfWeek = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return when {
            next == today -> DaySection.TODAY
            next == today.plusDays(1) -> DaySection.TOMORROW
            next <= endOfWeek -> DaySection.THIS_WEEK
            else -> DaySection.LATER
        }
    }

    /** Ongoing runs are ordered by their closing day ("closes soon" first), the rest by their next day. */
    private fun sortDay(card: EventCard, today: LocalDate): LocalDate? {
        val schedule = scheduleOf(card) ?: return null
        return if (schedule.isOngoingRun(today)) schedule.to else schedule.nextDay(today)
    }

    /** Consecutive groups of an already NEAREST-sorted list, for section headers. */
    fun sections(sorted: List<EventCard>, today: LocalDate): List<Pair<DaySection, List<EventCard>>> {
        val out = mutableListOf<Pair<DaySection, MutableList<EventCard>>>()
        for (card in sorted) {
            val s = section(card, today)
            if (out.lastOrNull()?.first == s) out.last().second += card else out += s to mutableListOf(card)
        }
        return out
    }

    private fun matchesDate(card: EventCard, filter: DateFilter, today: LocalDate): Boolean {
        if (filter == DateFilter.ANY) return true
        val schedule = scheduleOf(card) ?: return false
        val range = when (filter) {
            DateFilter.TODAY -> today..today
            DateFilter.TOMORROW -> today.plusDays(1)..today.plusDays(1)
            DateFilter.WEEKEND -> EventSchedule.weekend(today)
            DateFilter.NEXT_7_DAYS -> today..today.plusDays(6)
            DateFilter.NEXT_30_DAYS -> today..today.plusDays(29)
            DateFilter.ANY -> return true
        }
        return schedule.occursBetween(range.start, range.endInclusive)
    }
}
