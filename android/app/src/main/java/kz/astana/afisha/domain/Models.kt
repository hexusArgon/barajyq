package kz.astana.afisha.domain

import androidx.annotation.StringRes
import kz.astana.afisha.R

enum class Interest(val labelRu: String, @StringRes val labelRes: Int) {
    CONCERTS("Концерты", R.string.interest_concerts),
    THEATER("Театр / опера / балет", R.string.interest_theater),
    EXHIBITIONS("Выставки / музеи", R.string.interest_exhibitions),
    CINEMA("Кино", R.string.interest_cinema),
    STANDUP("Стендап / квизы", R.string.interest_standup),
    BARS("Бары / ночная жизнь", R.string.interest_bars),
    SPORT("Спорт", R.string.interest_sport),
    OUTDOOR("Забеги / outdoor", R.string.interest_outdoor),
    IT("IT / митапы", R.string.interest_it),
    KIDS("Детям / мастер-классы", R.string.interest_kids),
    FESTIVALS("Фестивали / форумы", R.string.interest_festivals),
    FREE("Бесплатные события", R.string.interest_free),
}

/** UI languages supported by the app. */
enum class AppLanguage(val tag: String, val nativeName: String, val flag: String) {
    KK("kk", "Қазақша", "🇰🇿"),
    RU("ru", "Русский", "🇷🇺"),
    EN("en", "English", "🇬🇧");

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }
    }
}

data class UserProfile(
    val googleId: String,
    val idToken: String? = null,
    val email: String?,
    val displayName: String?,
    val interests: Set<Interest> = emptySet(),
    val exclusions: Set<Interest> = emptySet(),
) {
    val onboardingDone: Boolean get() = interests.isNotEmpty()
}

/**
 * One afisha item (event or place). Titles and venue names are stored in all three
 * UI languages, mirroring the kk / ru / en versions of the source sites
 * (nmrk.kz, astana.kz, ticketon, sxodim …). Use [title] / [place] to pick by locale.
 */
data class EventCard(
    val id: String,
    val cityId: String,
    val titleRu: String,
    val titleKk: String,
    val titleEn: String,
    /** Raw schedule in Russian (e.g. "8 ноя – 30 ноя, 10:00"); format with Localize.date(). */
    val startsAt: String,
    val placeRu: String,
    val placeKk: String,
    val placeEn: String,
    val address: String,
    val interest: Interest,
    val price: String?,
    val whyMatches: String,
    val sourceUrl: String,
    val sourceName: String,
    val isPlace: Boolean = false,
    val workingHours: String = "",
    /** Full description shown in-app on the detail screen (kk / ru / en, blank → ru fallback). */
    val descriptionRu: String = "",
    val descriptionKk: String = "",
    val descriptionEn: String = "",
    /** Venue coordinates for the 2GIS route deep link (null → search by address/name). */
    val lat: Double? = null,
    val lon: Double? = null,
    /** Structured dates for filtering, sorting and hiding past events (null = unknown, events only). */
    val schedule: EventSchedule? = null,
) {
    fun title(lang: AppLanguage?): String = pick(lang, titleKk, titleRu, titleEn)
    fun place(lang: AppLanguage?): String = pick(lang, placeKk, placeRu, placeEn)
    fun description(lang: AppLanguage?): String = pick(lang, descriptionKk, descriptionRu, descriptionEn)
}

fun pick(lang: AppLanguage?, kk: String, ru: String, en: String): String = when (lang) {
    AppLanguage.KK -> kk.ifBlank { ru }
    AppLanguage.RU -> ru
    else -> en.ifBlank { ru }
}
