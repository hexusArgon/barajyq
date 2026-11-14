import Foundation
import SwiftUI
import UIKit

/// Machine-readable dates of an event (feed fields dateFrom / dateTo / dates / time, written by tools/schedule.py).
/// Days are ISO strings "yyyy-MM-dd", so comparing them as strings is the same as comparing dates.
struct EventSchedule: Hashable {
    let from: String
    let to: String
    /// Discrete show days; empty = a continuous run from `from` to `to`.
    let dates: [String]
    let time: String?

    /// Over only after its LAST day has ended: an event that has already started today is never dropped.
    func isOver(today: String) -> Bool { to < today }

    /// First day on or after `today` the event can be attended.
    func nextDay(today: String) -> String? {
        if to < today { return nil }
        if !dates.isEmpty { return dates.first { $0 >= today } }
        return from >= today ? from : today
    }

    static let zone = TimeZone(identifier: "Asia/Qyzylorda") ?? TimeZone(secondsFromGMT: 5 * 3600)!

    /// Today in Kazakhstan (UTC+5) as "yyyy-MM-dd".
    static func today(_ now: Date = Date()) -> String {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        let c = cal.dateComponents([.year, .month, .day], from: now)
        return String(format: "%04d-%02d-%02d", c.year ?? 1970, c.month ?? 1, c.day ?? 1)
    }

    private static let dayPattern = try! NSRegularExpression(pattern: "^\\d{4}-\\d{2}-\\d{2}$")

    static func isDay(_ s: String) -> Bool {
        dayPattern.firstMatch(in: s, range: NSRange(location: 0, length: (s as NSString).length)) != nil
    }

    /// From feed fields; nil when they are missing or malformed (a year is never guessed).
    static func of(from: String?, to: String?, dates: [String], time: String?) -> EventSchedule? {
        guard let from, let to, isDay(from), isDay(to), from <= to else { return nil }
        let days = Array(Set(dates.filter { isDay($0) && $0 >= from && $0 <= to })).sorted()
        return EventSchedule(from: from, to: to, dates: days, time: (time?.isEmpty ?? true) ? nil : time)
    }
}

/// Events and places from the remote feed (GitHub Pages of hexusArgon/barajyq-feed), the last downloaded copy on
/// disk, or the copy bundled into the app — the newest complete feed wins as a whole (same rules as Android
/// `FeedRepository`). Cities without feed rows fall back to the demo templates.
/// Used from SwiftUI views on the main thread; the download result is applied back on the main actor.
final class FeedStore: ObservableObject {
    static let shared = FeedStore()
    static let feedURL = URL(string: "https://hexusargon.github.io/barajyq-feed/events.json")!
    private static let staleAfter: TimeInterval = 15 * 60

    @Published private(set) var events: [EventCard] = []
    @Published private(set) var places: [EventCard] = []
    /// Kazakhstan date the lists are filtered against; refreshed on foreground and at midnight.
    @Published private(set) var today: String = EventSchedule.today()

    private var updatedAt: String?
    private var lastFetch: Date?
    private var loading = false

    private init() {
        if let local = Self.newestLocal() { apply(local) }
    }

    /// Call when the app becomes active: re-checks the date and downloads the feed if the copy is older than 15 min.
    func refreshIfNeeded() {
        today = EventSchedule.today()
        if loading { return }
        if let lastFetch, Date().timeIntervalSince(lastFetch) < Self.staleAfter { return }
        loading = true
        lastFetch = Date()
        Task { @MainActor in
            defer { self.loading = false }
            var request = URLRequest(url: Self.feedURL, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 20)
            request.setValue("application/json", forHTTPHeaderField: "Accept")
            guard let result = try? await URLSession.shared.data(for: request),
                  (result.1 as? HTTPURLResponse)?.statusCode == 200,
                  let feed = Self.parse(result.0), !feed.events.isEmpty else { return }
            let data = result.0
            if let current = self.updatedAt, let incoming = feed.updatedAt, Self.stamp(incoming) < Self.stamp(current) { return }
            try? data.write(to: Self.cacheFile, options: .atomic)
            self.apply(feed)
        }
    }

    /// The date changed (midnight, time zone change): ended events disappear without a restart.
    func dayChanged() { today = EventSchedule.today() }

    func forCity(_ city: City, places wantPlaces: Bool) -> [EventCard] {
        let source = wantPlaces ? places : events
        let rows = source.filter { $0.cityId == city.id }
        if !rows.isEmpty { return rows }
        return wantPlaces ? FakePlacesRepository.forCity(city) : FakeEventsRepository.forCity(city)
    }

    /// Feed for the profile: interests, hidden items, ended events dropped, nearest first (undated last).
    func forProfile(_ profile: UserProfile, city: City, places wantPlaces: Bool, hidden: Set<String>) -> [EventCard] {
        let day = today
        let rows = forCity(city, places: wantPlaces).filter {
            profile.interests.contains($0.interest) && !hidden.contains($0.id) && !Self.isExpired($0, today: day)
        }
        if wantPlaces { return rows }
        return rows.enumerated().sorted { a, b in
            let ka = a.element.schedule?.nextDay(today: day) ?? "9999-99-99"
            let kb = b.element.schedule?.nextDay(today: day) ?? "9999-99-99"
            if ka != kb { return ka < kb }
            let ta = a.element.schedule?.time ?? "99:99", tb = b.element.schedule?.time ?? "99:99"
            if ta != tb { return ta < tb }
            return a.offset < b.offset
        }.map { $0.element }
    }

    func find(_ id: String, city: City) -> EventCard? {
        (forCity(city, places: false) + forCity(city, places: true)).first { $0.id == id }
    }

    static func isExpired(_ card: EventCard, today: String) -> Bool {
        guard !card.isPlace, let s = card.schedule else { return false }
        return s.isOver(today: today)
    }

    // MARK: - Loading

    private struct Parsed {
        let events: [EventCard]
        let places: [EventCard]
        let updatedAt: String?
    }

    private func apply(_ feed: Parsed) {
        events = feed.events
        if !feed.places.isEmpty || places.isEmpty { places = feed.places }
        updatedAt = feed.updatedAt
    }

    private static var cacheFile: URL {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("events_feed_remote.json")
    }

    /// Downloaded copy if it is newer than the bundled one, else the bundled one.
    private static func newestLocal() -> Parsed? {
        let bundled = Bundle.main.url(forResource: "events", withExtension: "json")
            .flatMap { try? Data(contentsOf: $0) }.flatMap(parse)
        let cached = (try? Data(contentsOf: cacheFile)).flatMap(parse)
        guard let cached, !cached.events.isEmpty else { return bundled }
        guard let bundled else { return cached }
        return stamp(cached.updatedAt ?? "") > stamp(bundled.updatedAt ?? "") ? cached : bundled
    }

    private static func stamp(_ iso: String) -> Date {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let d = f.date(from: iso) { return d }
        f.formatOptions = [.withInternetDateTime]
        return f.date(from: iso) ?? .distantPast
    }

    private static func parse(_ data: Data) -> Parsed? {
        guard let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return nil }
        func cards(_ key: String) -> [EventCard] {
            ((root[key] as? [[String: Any]]) ?? []).compactMap(card)
        }
        return Parsed(events: cards("events"), places: cards("places"), updatedAt: root["updatedAt"] as? String)
    }

    private static func card(_ o: [String: Any]) -> EventCard? {
        func s(_ k: String) -> String { (o[k] as? String) ?? "" }
        func d(_ k: String) -> Double? { (o[k] as? NSNumber)?.doubleValue }
        guard let id = o["id"] as? String, !id.isEmpty else { return nil }
        let interest = Interest(rawValue: s("interest")) ?? .free
        let isPlace = (o["isPlace"] as? Bool) ?? false
        let price = (o["price"] as? String).flatMap { $0.isEmpty || $0 == "null" ? nil : $0 }
        let image = (o["imageUrl"] as? String).flatMap { $0.hasPrefix("https://") ? $0 : nil }
        var schedule: EventSchedule? = nil
        if !isPlace {
            schedule = EventSchedule.of(from: o["dateFrom"] as? String, to: o["dateTo"] as? String,
                                        dates: (o["dates"] as? [String]) ?? [], time: o["time"] as? String)
        }
        return EventCard(
            id: id, cityId: s("cityId").isEmpty ? "astana" : s("cityId"),
            titleRu: s("titleRu"), titleKk: s("titleKk"), titleEn: s("titleEn"),
            startsAt: s("startsAt"),
            placeRu: s("placeRu"), placeKk: s("placeKk"), placeEn: s("placeEn"),
            address: s("address"), interest: interest, price: price,
            whyMatches: "Интерес: \(interest.labelRu.lowercased())",
            sourceUrl: s("sourceUrl"), sourceName: s("sourceName"),
            isPlace: isPlace, workingHours: s("workingHours"),
            descriptionRu: s("descriptionRu"), descriptionKk: s("descriptionKk"), descriptionEn: s("descriptionEn"),
            lat: d("lat"), lon: d("lon"), schedule: schedule, imageUrl: image
        )
    }
}
