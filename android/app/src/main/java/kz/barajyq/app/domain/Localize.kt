package kz.barajyq.app.domain

import java.time.LocalDate

/**
 * Formats the raw (Russian) schedule / opening-hours / price strings of demo data
 * into the current UI language. Russian is returned unchanged.
 */
object Localize {
    private val monthsRu = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    private val monthsKk = listOf("қаңтар", "ақпан", "наурыз", "сәуір", "мамыр", "маусым", "шілде", "тамыз", "қыркүйек", "қазан", "қараша", "желтоқсан")
    private val monthsEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val dayMonth = Regex("(\\d{1,2})\\s+(${monthsRu.joinToString("|")})[а-я]*")

    private val monthsKkDative = listOf("қаңтарға", "ақпанға", "наурызға", "сәуірге", "мамырға", "маусымға", "шілдеге",
        "тамызға", "қыркүйекке", "қазанға", "қарашаға", "желтоқсанға")
    private val weekdaysRu = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    private val weekdaysKk = listOf("Дс", "Сс", "Ср", "Бс", "Жм", "Сн", "Жс")
    private val weekdaysEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /** "15 ноя" / "15 қараша" / "Nov 15". */
    fun day(d: LocalDate, lang: AppLanguage?): String = when (lang) {
        AppLanguage.RU -> "${d.dayOfMonth} ${monthsRu[d.monthValue - 1]}"
        AppLanguage.KK -> "${d.dayOfMonth} ${monthsKk[d.monthValue - 1]}"
        else -> "${monthsEn[d.monthValue - 1]} ${d.dayOfMonth}"
    }

    private fun weekday(d: LocalDate, lang: AppLanguage?): String =
        (when (lang) { AppLanguage.RU -> weekdaysRu; AppLanguage.KK -> weekdaysKk; else -> weekdaysEn })[d.dayOfWeek.value - 1]

    /** "13–14 ноя" within one month, otherwise "6 дек – 27 янв". */
    private fun span(a: LocalDate, b: LocalDate, lang: AppLanguage?): String =
        if (a.year == b.year && a.month == b.month) when (lang) {
            AppLanguage.RU -> "${a.dayOfMonth}–${b.dayOfMonth} ${monthsRu[a.monthValue - 1]}"
            AppLanguage.KK -> "${a.dayOfMonth}–${b.dayOfMonth} ${monthsKk[a.monthValue - 1]}"
            else -> "${monthsEn[a.monthValue - 1]} ${a.dayOfMonth}–${b.dayOfMonth}"
        } else "${day(a, lang)} – ${day(b, lang)}"

    private fun moreDays(n: Int, lang: AppLanguage?): String = when (lang) {
        AppLanguage.RU -> "ещё $n " + when {
            n % 10 == 1 && n % 100 != 11 -> "дата"
            n % 10 in 2..4 && n % 100 !in 12..14 -> "даты"
            else -> "дат"
        }
        AppLanguage.KK -> "тағы $n күн"
        else -> "+$n more " + if (n == 1) "date" else "dates"
    }

    /**
     * Human "when" for a structured schedule, relative to [today]:
     * "Сегодня, 19:00", "Сб, 15 ноя, 19:00 · ещё 1 дата", "До 30 янв", "13–14 ноя", "Последний день".
     */
    fun schedule(s: EventSchedule, lang: AppLanguage?, today: LocalDate): String {
        if (s.isOver(today)) return if (s.from == s.to) day(s.from, lang) else span(s.from, s.to, lang)
        if (s.isRun) return when {
            s.to == today -> when (lang) { AppLanguage.RU -> "Последний день"; AppLanguage.KK -> "Соңғы күн"; else -> "Last day" }
            s.from <= today -> when (lang) {
                AppLanguage.RU -> "До ${day(s.to, lang)}"
                AppLanguage.KK -> "${s.to.dayOfMonth} ${monthsKkDative[s.to.monthValue - 1]} дейін"
                else -> "Until ${day(s.to, lang)}"
            }
            else -> span(s.from, s.to, lang)
        }
        val next = s.nextDay(today) ?: return day(s.to, lang)
        val dayText = when (next) {
            today -> when (lang) { AppLanguage.RU -> "Сегодня"; AppLanguage.KK -> "Бүгін"; else -> "Today" }
            today.plusDays(1) -> when (lang) { AppLanguage.RU -> "Завтра"; AppLanguage.KK -> "Ертең"; else -> "Tomorrow" }
            else -> "${weekday(next, lang)}, ${day(next, lang)}"
        }
        val withTime = s.time?.let { "$dayText, $it" } ?: dayText
        val more = s.moreDays(today)
        return if (more > 0) "$withTime · ${moreDays(more, lang)}" else withTime
    }

    /** Day strip chip: "Сегодня", "Завтра", then "Пт 14" / "Жм 14" / "Fri 14". */
    fun dayChip(d: LocalDate, lang: AppLanguage?, today: LocalDate): String = when (d) {
        today -> when (lang) { AppLanguage.RU -> "Сегодня"; AppLanguage.KK -> "Бүгін"; else -> "Today" }
        today.plusDays(1) -> when (lang) { AppLanguage.RU -> "Завтра"; AppLanguage.KK -> "Ертең"; else -> "Tomorrow" }
        else -> "${weekday(d, lang)} ${d.dayOfMonth}"
    }

    /** Date line of an event card: structured schedule when known, else the source text. */
    fun eventWhen(card: EventCard, lang: AppLanguage?, today: LocalDate = EventSchedule.today()): String {
        val s = card.schedule ?: EventSchedule.fromIso(card.startsAt)
        return if (s != null) schedule(s, lang, today) else date(card.startsAt, lang)
    }

    /** "8 ноя – 30 ноя, 10:00" → "8 қараша – 30 қараша, 10:00" / "Nov 8 – Nov 30, 10:00". */
    fun date(raw: String, lang: AppLanguage?): String {
        // ISO stamps from importers are never shown raw ("2026-11-13T20:00:00+05:00" → "13 ноя, 20:00").
        EventSchedule.fromIso(raw)?.let { s -> return listOfNotNull(day(s.from, lang), s.time?.toString()).joinToString(", ") }
        if (lang == AppLanguage.RU || raw.isBlank()) return raw
        return dayMonth.replace(raw) { m ->
            val d = m.groupValues[1]
            val i = monthsRu.indexOf(m.groupValues[2])
            if (lang == AppLanguage.KK) "$d ${monthsKk[i]}" else "${monthsEn[i]} $d"
        }
    }

    // Compound day ranges first, single days last (fold is sequential).
    private val hoursKk = listOf(
        "Пн–Вт — выходной" to "Дс–Сс — демалыс", "Пн–Пт" to "Дс–Жм", "Сб–Вс" to "Сн–Жс", "Ср–Сб" to "Ср–Сн", "Вт–Чт" to "Сс–Бс", "Пт–Сб" to "Жм–Сн", 
        "Вт–Вс" to "Сс–Жс", "Пн — выходной" to "Дс — демалыс", "Ежедневно" to "Күн сайын",
        "Круглосуточно" to "Тәулік бойы", "Касса" to "Касса", "Вход за 30 мин" to "Кіру 30 мин бұрын",
        "Пт" to "Жм", "Сб" to "Сн", "Вс" to "Жс", "Вт" to "Сс", 
    )
    private val hoursEn = listOf(
        "Пн–Вт — выходной" to "closed Mon–Tue", "Пн–Пт" to "Mon–Fri", "Сб–Вс" to "Sat–Sun", "Ср–Сб" to "Wed–Sat", "Вт–Чт" to "Tue–Thu", "Пт–Сб" to "Fri–Sat", 
        "Вт–Вс" to "Tue–Sun", "Пн — выходной" to "closed Mon", "Ежедневно" to "Daily",
        "Круглосуточно" to "Open 24/7", "Касса" to "Box office", "Вход за 30 мин" to "Doors 30 min before",
        "Пт" to "Fri", "Сб" to "Sat", "Вс" to "Sun", "Вт" to "Tue", 
    )

    /** Opening hours / schedule line. */
    fun hours(raw: String, lang: AppLanguage?): String = when (lang) {
        AppLanguage.RU -> raw
        AppLanguage.KK -> hoursKk.fold(raw) { s, (a, b) -> s.replace(a, b) }
        else -> hoursEn.fold(raw) { s, (a, b) -> s.replace(a, b) }
    }

    private val priceKk = listOf(
        "Бесплатно" to "Тегін", "по регистрации" to "тіркеу бойынша", "вход по расписанию" to "кесте бойынша кіру",
        "пропуск в нацпарк" to "ұлттық саябаққа рұқсатнама", "Экскурсия" to "Экскурсия", "Смотровая" to "Шолу алаңы",
        "Канатка" to "Аспалы жол",
    )
    private val priceEn = listOf(
        "Бесплатно" to "Free", "по регистрации" to "registration required", "вход по расписанию" to "entry by schedule",
        "пропуск в нацпарк" to "national park pass", "Экскурсия" to "Tour", "Смотровая" to "Observation deck",
        "Канатка" to "Cable car",
    )
    private val fromPrice = Regex("от ([\\d  ]+ ₸)")

    fun price(raw: String, lang: AppLanguage?): String = when (lang) {
        AppLanguage.RU -> raw
        AppLanguage.KK -> fromPrice.replace(priceKk.fold(raw) { s, (a, b) -> s.replace(a, b) }) { "${it.groupValues[1]} бастап" }
        else -> fromPrice.replace(priceEn.fold(raw) { s, (a, b) -> s.replace(a, b) }) { "from ${it.groupValues[1]}" }
    }
}
