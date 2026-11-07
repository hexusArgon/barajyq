import Foundation

/// Formats the raw (Russian) schedule / opening-hours / price strings of demo data
/// into the current UI language. Russian is returned unchanged. Порт `domain/Localize.kt`.
enum Localize {
    private static let monthsRu = ["янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"]
    private static let monthsKk = ["қаңтар", "ақпан", "наурыз", "сәуір", "мамыр", "маусым", "шілде", "тамыз", "қыркүйек", "қазан", "қараша", "желтоқсан"]
    private static let monthsEn = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
    private static let dayMonth = try! NSRegularExpression(pattern: "(\\d{1,2})\\s+(\(monthsRu.joined(separator: "|")))[а-я]*")

    /// "8 ноя – 30 ноя, 10:00" → "8 қараша – 30 қараша, 10:00" / "Nov 8 – Nov 30, 10:00".
    static func date(_ raw: String, _ lang: AppLanguage?) -> String {
        if lang == .ru || raw.trimmingCharacters(in: .whitespaces).isEmpty { return raw }
        let ns = raw as NSString
        var out = ""
        var last = 0
        for m in dayMonth.matches(in: raw, range: NSRange(location: 0, length: ns.length)) {
            out += ns.substring(with: NSRange(location: last, length: m.range.location - last))
            let d = ns.substring(with: m.range(at: 1))
            let mon = ns.substring(with: m.range(at: 2))
            let i = monthsRu.firstIndex(of: mon) ?? 0
            out += lang == .kk ? "\(d) \(monthsKk[i])" : "\(monthsEn[i]) \(d)"
            last = m.range.location + m.range.length
        }
        out += ns.substring(from: last)
        return out
    }

    // Compound day ranges first, single days last (reduce is sequential).
    private static let hoursKk: [(String, String)] = [
        ("Пн–Вт — выходной", "Дс–Сс — демалыс"), ("Пн–Пт", "Дс–Жм"), ("Сб–Вс", "Сн–Жс"), ("Ср–Сб", "Ср–Сн"), ("Вт–Чт", "Сс–Бс"), ("Пт–Сб", "Жм–Сн"), 
        ("Вт–Вс", "Сс–Жс"), ("Пн — выходной", "Дс — демалыс"), ("Ежедневно", "Күн сайын"),
        ("Круглосуточно", "Тәулік бойы"), ("Касса", "Касса"), ("Вход за 30 мин", "Кіру 30 мин бұрын"),
        ("Пт", "Жм"), ("Сб", "Сн"), ("Вс", "Жс"), ("Вт", "Сс"), 
    ]
    private static let hoursEn: [(String, String)] = [
        ("Пн–Вт — выходной", "closed Mon–Tue"), ("Пн–Пт", "Mon–Fri"), ("Сб–Вс", "Sat–Sun"), ("Ср–Сб", "Wed–Sat"), ("Вт–Чт", "Tue–Thu"), ("Пт–Сб", "Fri–Sat"), 
        ("Вт–Вс", "Tue–Sun"), ("Пн — выходной", "closed Mon"), ("Ежедневно", "Daily"),
        ("Круглосуточно", "Open 24/7"), ("Касса", "Box office"), ("Вход за 30 мин", "Doors 30 min before"),
        ("Пт", "Fri"), ("Сб", "Sat"), ("Вс", "Sun"), ("Вт", "Tue"), 
    ]

    /// Opening hours / schedule line.
    static func hours(_ raw: String, _ lang: AppLanguage?) -> String {
        switch lang {
        case .ru: return raw
        case .kk: return replaceAll(raw, hoursKk)
        default: return replaceAll(raw, hoursEn)
        }
    }

    private static let priceKk: [(String, String)] = [
        ("Бесплатно", "Тегін"), ("по регистрации", "тіркеу бойынша"), ("вход по расписанию", "кесте бойынша кіру"),
        ("пропуск в нацпарк", "ұлттық саябаққа рұқсатнама"), ("Экскурсия", "Экскурсия"), ("Смотровая", "Шолу алаңы"),
        ("Канатка", "Аспалы жол"),
    ]
    private static let priceEn: [(String, String)] = [
        ("Бесплатно", "Free"), ("по регистрации", "registration required"), ("вход по расписанию", "entry by schedule"),
        ("пропуск в нацпарк", "national park pass"), ("Экскурсия", "Tour"), ("Смотровая", "Observation deck"),
        ("Канатка", "Cable car"),
    ]
    private static let fromPrice = try! NSRegularExpression(pattern: "от ([\\d\u{00A0} ]+ ₸)")

    static func price(_ raw: String, _ lang: AppLanguage?) -> String {
        switch lang {
        case .ru: return raw
        case .kk:
            let s = replaceAll(raw, priceKk)
            return fromPrice.stringByReplacingMatches(in: s, range: NSRange(location: 0, length: (s as NSString).length), withTemplate: "$1 бастап")
        default:
            let s = replaceAll(raw, priceEn)
            return fromPrice.stringByReplacingMatches(in: s, range: NSRange(location: 0, length: (s as NSString).length), withTemplate: "from $1")
        }
    }

    private static func replaceAll(_ s: String, _ pairs: [(String, String)]) -> String {
        pairs.reduce(s) { acc, p in acc.replacingOccurrences(of: p.0, with: p.1) }
    }
}
