import Foundation
import SwiftUI

/// Session + settings, persisted in `UserDefaults.standard` (Library/Preferences/<bundle id>.plist).
/// It lives in the app container, so it survives App Store / TestFlight updates — only deleting
/// the app wipes it. Keys mirror Android `data/SessionStore.kt`. Never cleared on version bumps.
@MainActor
final class SessionStore: ObservableObject {
    static let shared = SessionStore()

    private enum K {
        static let userId = "google_id" // same key as Android; holds Google or Apple user id
        static let provider = "auth_provider"
        static let email = "email"
        static let name = "display_name"
        static let interests = "interests"
        static let cityId = "city_id"
        static let cityName = "city_name"
        static let hidden = "hidden_event_ids"
        static let language = "language"
    }

    private let defaults: UserDefaults

    @Published private(set) var profile: UserProfile?
    @Published private(set) var city: City?
    @Published private(set) var hidden: Set<String> = []
    @Published private(set) var language: AppLanguage?

    /// Interests are kept even after sign-out, so re-signing in restores them.
    private var savedInterests: Set<Interest> = []

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        savedInterests = Set((defaults.stringArray(forKey: K.interests) ?? []).compactMap(Interest.init(rawValue:)))
        if let id = defaults.string(forKey: K.userId) {
            profile = UserProfile(
                userId: id,
                provider: AuthProvider(rawValue: defaults.string(forKey: K.provider) ?? "") ?? .google,
                email: defaults.string(forKey: K.email),
                displayName: defaults.string(forKey: K.name),
                interests: savedInterests
            )
        }
        if let cid = defaults.string(forKey: K.cityId) {
            city = KazakhstanCities.byId(cid) ?? City(id: cid, name: defaults.string(forKey: K.cityName) ?? cid)
        }
        hidden = Set(defaults.stringArray(forKey: K.hidden) ?? [])
        language = AppLanguage.fromTag(defaults.string(forKey: K.language))
    }

    func signIn(userId: String, provider: AuthProvider, email: String?, displayName: String?) {
        // Apple sends name/email only on the very first authorization — keep previous values if nil.
        let sameUser = defaults.string(forKey: K.userId) == userId
        let mail = email ?? (sameUser ? defaults.string(forKey: K.email) : nil)
        let name = displayName ?? (sameUser ? defaults.string(forKey: K.name) : nil)
        profile = UserProfile(userId: userId, provider: provider, email: mail, displayName: name, interests: savedInterests)
        defaults.set(userId, forKey: K.userId)
        defaults.set(provider.rawValue, forKey: K.provider)
        set(mail, K.email)
        set(name, K.name)
    }

    func saveOnboarding(_ interests: Set<Interest>) { setInterests(interests) }

    func setInterests(_ interests: Set<Interest>) {
        savedInterests = interests
        profile?.interests = interests
        defaults.set(interests.map(\.rawValue).sorted(), forKey: K.interests)
    }

    func setCity(_ city: City) {
        self.city = city
        defaults.set(city.id, forKey: K.cityId)
        defaults.set(city.name, forKey: K.cityName)
    }

    func setLanguage(_ lang: AppLanguage) {
        language = lang
        defaults.set(lang.tag, forKey: K.language)
        // Also make iOS use this language for system-provided strings on next launch.
        defaults.set([lang.tag], forKey: "AppleLanguages")
    }

    func hideEvent(_ id: String) { hidden.insert(id); saveHidden() }
    func unhideEvent(_ id: String) { hidden.remove(id); saveHidden() }
    func clearHidden() { hidden = []; saveHidden() }

    /// Signs out but keeps city, language, interests and hidden items.
    func signOut() {
        profile = nil
        [K.userId, K.provider, K.email, K.name].forEach(defaults.removeObject(forKey:))
    }

    /// App Store guideline 5.1.1(v): in-app account deletion. There is no backend yet,
    /// so this wipes everything stored on the device (the app returns to first launch).
    func deleteAccountAndData() {
        profile = nil; city = nil; hidden = []; language = nil; savedInterests = []
        [K.userId, K.provider, K.email, K.name, K.interests, K.cityId, K.cityName, K.hidden, K.language, "AppleLanguages"]
            .forEach(defaults.removeObject(forKey:))
    }

    private func saveHidden() { defaults.set(Array(hidden).sorted(), forKey: K.hidden) }

    private func set(_ value: String?, _ key: String) {
        if let value { defaults.set(value, forKey: key) } else { defaults.removeObject(forKey: key) }
    }
}
