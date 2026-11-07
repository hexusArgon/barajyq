import Foundation

/// Interest topics (порт enum Interest из Android `domain/Models.kt`).
enum Interest: String, CaseIterable, Codable, Hashable, Identifiable {
    // rawValue == Android enum name, so the persisted values are identical across platforms.
    case concerts = "CONCERTS"
    case theater = "THEATER"
    case exhibitions = "EXHIBITIONS"
    case cinema = "CINEMA"
    case standup = "STANDUP"
    case bars = "BARS"
    case sport = "SPORT"
    case outdoor = "OUTDOOR"
    case it = "IT"
    case kids = "KIDS"
    case festivals = "FESTIVALS"
    case free = "FREE"

    var id: String { rawValue }

    var labelRu: String {
        switch self {
        case .concerts: return "Концерты"
        case .theater: return "Театр / опера / балет"
        case .exhibitions: return "Выставки / музеи"
        case .cinema: return "Кино"
        case .standup: return "Стендап / квизы"
        case .bars: return "Бары / ночная жизнь"
        case .sport: return "Спорт"
        case .outdoor: return "Забеги / outdoor"
        case .it: return "IT / митапы"
        case .kids: return "Детям / мастер-классы"
        case .festivals: return "Фестивали / форумы"
        case .free: return "Бесплатные события"
        }
    }

    /// Key in Localizable.strings.
    var labelKey: String { "interest_" + rawValue.lowercased() }

    /// SF Symbol (аналог Material-иконок из Android `ui/common/Common.kt`).
    var symbol: String {
        switch self {
        case .concerts: return "music.note"
        case .theater: return "theatermasks.fill"
        case .exhibitions: return "building.columns.fill"
        case .cinema: return "film.fill"
        case .standup: return "mic.fill"
        case .bars: return "wineglass.fill"
        case .sport: return "sportscourt.fill"
        case .outdoor: return "figure.run"
        case .it: return "desktopcomputer"
        case .kids: return "figure.and.child.holdinghands"
        case .festivals: return "party.popper.fill"
        case .free: return "gift.fill"
        }
    }
}

/// UI languages supported by the app.
enum AppLanguage: String, CaseIterable, Identifiable {
    case kk, ru, en

    var id: String { rawValue }
    var tag: String { rawValue }

    var nativeName: String {
        switch self {
        case .kk: return "Қазақша"
        case .ru: return "Русский"
        case .en: return "English"
        }
    }

    var flag: String {
        switch self {
        case .kk: return "🇰🇿"
        case .ru: return "🇷🇺"
        case .en: return "🇬🇧"
        }
    }

    static func fromTag(_ tag: String?) -> AppLanguage? {
        guard let tag else { return nil }
        return AppLanguage(rawValue: tag)
    }
}

struct UserProfile: Equatable {
    /// Google `userID` (sub) or Apple user identifier.
    var userId: String
    var provider: AuthProvider
    var email: String?
    var displayName: String?
    var interests: Set<Interest> = []

    var onboardingDone: Bool { !interests.isEmpty }
}

enum AuthProvider: String {
    case google, apple
}

/// One afisha item (event or place). Titles / venues in all three UI languages.
struct EventCard: Identifiable, Hashable {
    let id: String
    let cityId: String
    let titleRu: String
    let titleKk: String
    let titleEn: String
    /// Raw schedule in Russian ("8 ноя – 30 ноя, 10:00"); format with `Localize.date`.
    let startsAt: String
    let placeRu: String
    let placeKk: String
    let placeEn: String
    let address: String
    let interest: Interest
    let price: String?
    let whyMatches: String
    let sourceUrl: String
    let sourceName: String
    var isPlace: Bool = false
    var workingHours: String = ""
    /// Full description shown in-app on the detail screen (kk / ru / en, blank → ru fallback).
    var descriptionRu: String = ""
    var descriptionKk: String = ""
    var descriptionEn: String = ""
    /// Venue coordinates for the 2GIS route deep link (nil → search by address/name).
    var lat: Double? = nil
    var lon: Double? = nil

    func title(_ lang: AppLanguage?) -> String { pick(lang, kk: titleKk, ru: titleRu, en: titleEn) }
    func place(_ lang: AppLanguage?) -> String { pick(lang, kk: placeKk, ru: placeRu, en: placeEn) }
    func description(_ lang: AppLanguage?) -> String { pick(lang, kk: descriptionKk, ru: descriptionRu, en: descriptionEn) }
}

/// Picks a string for the language; blank kk/en fall back to Russian (как на Android).
func pick(_ lang: AppLanguage?, kk: String, ru: String, en: String) -> String {
    switch lang {
    case .kk: return kk.trimmingCharacters(in: .whitespaces).isEmpty ? ru : kk
    case .ru: return ru
    default: return en.trimmingCharacters(in: .whitespaces).isEmpty ? ru : en
    }
}
