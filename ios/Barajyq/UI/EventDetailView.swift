import SwiftUI

extension EventCard {
    /// 2GIS route query shared by the feed card and the detail screen (как на Android `routeQuery`).
    func routeQuery(cityName: String?) -> String {
        [placeRu, address, cityName ?? ""].filter { !$0.isEmpty }.reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }.joined(separator: ", ")
    }
}

/// In-app description of an event or a place (порт Android `ui/detail/EventDetailScreen.kt`):
/// everything from the feed card plus the full text. The source opens in the browser only from its button.
struct EventDetailView: View {
    @EnvironmentObject private var session: SessionStore
    let eventId: String
    let onHidden: (String) -> Void

    private var event: EventCard? {
        guard let c = session.city else { return nil }
        return (FakeEventsRepository.forCity(c) + FakePlacesRepository.forCity(c)).first { $0.id == eventId }
    }

    var body: some View {
        let lang = session.language
        Group {
            if let event {
                content(event, lang)
            } else {
                ScrollView {
                    EmptyStateView(symbol: "info.circle", title: session.t("empty_title"),
                                   message: session.t("detail_not_found"))
                }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle(session.t(event?.isPlace == true ? "place_description" : "event_description"))
        .navigationBarTitleDisplayMode(.inline)
    }

    private func content(_ event: EventCard, _ lang: AppLanguage?) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Header: interest chip + title
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 6) {
                        Image(systemName: event.interest.symbol).font(.caption2)
                        Text(session.t(event.interest.labelKey)).font(.caption.weight(.medium))
                    }
                    .foregroundColor(Palette.onSecondaryContainer)
                    .padding(.horizontal, 10).padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 8).fill(Palette.secondaryContainer))
                    Text(event.title(lang)).font(.title2.weight(.semibold))
                        .fixedSize(horizontal: false, vertical: true)
                }

                // Facts: when / where / hours / price
                VStack(alignment: .leading, spacing: 12) {
                    if !event.isPlace && !event.startsAt.isEmpty {
                        infoLine("calendar", Localize.date(event.startsAt, lang), emphasize: true)
                    }
                    infoLine("mappin.and.ellipse", event.place(lang),
                             secondary: (!event.address.isEmpty && event.address != event.placeRu) ? event.address : nil)
                    FlowLayout(spacing: 8, lineSpacing: 8) {
                        if !event.workingHours.isEmpty { pill("clock", Localize.hours(event.workingHours, lang)) }
                        if let p = event.price { pill("banknote", Localize.price(p, lang)) }
                    }
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
                .elevatedCard()

                // Full description
                let description = event.description(lang)
                if !description.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(session.t(event.isPlace ? "place_description" : "event_description"))
                            .font(.headline).foregroundColor(Palette.primary)
                        Text(description).font(.body).foregroundColor(Palette.onSurface)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }

                Divider().background(Palette.outlineVariant)

                // Actions
                VStack(alignment: .leading, spacing: 10) {
                    Button { Links.open2Gis(event, cityId: session.city?.id, cityName: session.city?.name) } label: {
                        Label(session.t("route_2gis") + " 2GIS", systemImage: "arrow.triangle.turn.up.right.diamond.fill")
                    }
                    .buttonStyle(PrimaryButtonStyle(height: 48, radius: 12))

                    Button { Links.open(event.sourceUrl) } label: {
                        Label(session.t(event.isPlace ? "open_source" : "open_tickets_source"),
                              systemImage: "arrow.up.right.square").lineLimit(1)
                    }
                    .buttonStyle(TonalButtonStyle())

                    Text(session.t("opens_in_browser", event.sourceName))
                        .font(.caption).foregroundColor(Palette.onSurfaceVariant).padding(.leading, 4)

                    Button {
                        session.hideEvent(event.id)
                        onHidden(event.id)
                    } label: {
                        Label(session.t("not_interested"), systemImage: "eye.slash")
                    }
                    .buttonStyle(OutlinedButtonStyle())
                }
            }
            .padding(16)
            .padding(.bottom, 16)
        }
    }
}
