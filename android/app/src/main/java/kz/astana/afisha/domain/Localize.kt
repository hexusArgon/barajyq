package kz.astana.afisha.domain

/**
 * Formats the raw (Russian) schedule / opening-hours / price strings of demo data
 * into the current UI language. Russian is returned unchanged.
 */
object Localize {
    private val monthsRu = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    private val monthsKk = listOf("қаңтар", "ақпан", "наурыз", "сәуір", "мамыр", "маусым", "шілде", "тамыз", "қыркүйек", "қазан", "қараша", "желтоқсан")
    private val monthsEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val dayMonth = Regex("(\\d{1,2})\\s+(${monthsRu.joinToString("|")})[а-я]*")

    /** "8 ноя – 30 ноя, 10:00" → "8 қараша – 30 қараша, 10:00" / "Nov 8 – Nov 30, 10:00". */
    fun date(raw: String, lang: AppLanguage?): String {
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
