import Foundation

/// In-app language switch (kk / ru / en) without restarting the app:
/// strings are looked up in the `<lang>.lproj` bundle chosen in `SessionStore.language`.
/// Before a language is picked we follow the device language (fallback: English).
enum L10n {
    static func bundle(for lang: AppLanguage?) -> Bundle {
        let tag = lang?.tag ?? deviceLanguage.tag
        if let path = Bundle.main.path(forResource: tag, ofType: "lproj"), let b = Bundle(path: path) { return b }
        return .main
    }

    static var deviceLanguage: AppLanguage {
        for id in Locale.preferredLanguages {
            if let l = AppLanguage(rawValue: String(id.prefix(2))) { return l }
        }
        return .en
    }

    static func tr(_ key: String, _ lang: AppLanguage?) -> String {
        bundle(for: lang).localizedString(forKey: key, value: nil, table: nil)
    }

    static func tr(_ key: String, _ lang: AppLanguage?, _ args: CVarArg...) -> String {
        String(format: tr(key, lang), locale: Locale(identifier: lang?.tag ?? "en"), arguments: args)
    }
}
