import SwiftUI
import UIKit

enum Route: Hashable { case settings, city, language, event(String) }

/// Main feed: Events / Places tabs, cards with route to 2GIS and "Не интересно".
struct FeedView: View {
    @EnvironmentObject private var session: SessionStore
    @Binding var path: [Route]
    /// Id hidden from the detail screen → show the same "Hidden · Undo" toast here.
    @Binding var hiddenFromDetail: String?
    @AppStorage("feed_tab") private var tab = 0
    @State private var toast: Toast?
    /// Remote feed (updated without an App Store release); ended events are dropped against `feed.today`.
    @ObservedObject private var feed = FeedStore.shared
    @Environment(\.scenePhase) private var scenePhase

    private var items: [EventCard] {
        guard let p = session.profile, let c = session.city else { return [] }
        return feed.forProfile(p, city: c, places: tab != 0, hidden: session.hidden)
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            tabs
            Divider().background(Palette.outlineVariant)
            content
        }
        .background(Palette.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .overlay(alignment: .bottom) {
            if let toast {
                ToastView(toast: toast, onAction: {
                    session.unhideEvent(toast.payload)
                    withAnimation { self.toast = nil }
                }, onDismiss: { withAnimation { self.toast = nil } })
            }
        }
        .animation(.easeInOut(duration: 0.2), value: toast)
        .onChange(of: hiddenFromDetail) { id in
            guard let id else { return }
            hiddenFromDetail = nil
            showHiddenToast(id)
        }
        .onAppear {
            if let id = hiddenFromDetail { hiddenFromDetail = nil; showHiddenToast(id) }
            feed.refreshIfNeeded()
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active { feed.refreshIfNeeded() }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in
            feed.dayChanged()
        }
    }

    private var header: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 2) {
                Text(session.t("app_name")).font(.title2.weight(.bold)).foregroundColor(Palette.primary)
                Button { path.append(.city) } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "mappin.circle.fill").font(.caption).foregroundColor(Palette.secondary)
                        Text(session.city?.displayName(session.language) ?? "").font(.subheadline)
                            .foregroundColor(Palette.onSurfaceVariant)
                        Image(systemName: "chevron.down").font(.caption2.weight(.bold)).foregroundColor(Palette.onSurfaceVariant)
                    }
                }
                .buttonStyle(.plain)
            }
            Spacer()
            Button { path.append(.settings) } label: {
                Image(systemName: "gearshape").font(.title3).foregroundColor(Palette.onSurface).frame(width: 44, height: 44)
            }
            .accessibilityLabel(session.t("settings_title"))
        }
        .padding(.horizontal, 16)
        .padding(.top, 4)
    }

    private var tabs: some View {
        HStack(spacing: 0) {
            tabItem(0, "calendar", session.t("tab_events"))
            tabItem(1, "safari", session.t("tab_places"))
        }
        .padding(.top, 8)
    }

    private func tabItem(_ idx: Int, _ symbol: String, _ label: String) -> some View {
        let sel = tab == idx
        return Button { tab = idx } label: {
            VStack(spacing: 6) {
                Image(systemName: symbol).font(.system(size: 18))
                Text(label).font(.subheadline.weight(.semibold))
                Rectangle().fill(sel ? Palette.primary : .clear).frame(height: 3).cornerRadius(1.5).padding(.horizontal, 32)
            }
            .foregroundColor(sel ? Palette.primary : Palette.onSurfaceVariant)
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(sel ? .isSelected : [])
    }

    @ViewBuilder private var content: some View {
        let list = items
        if list.isEmpty {
            ScrollView {
                EmptyStateView(symbol: tab == 0 ? "calendar" : "safari",
                               title: session.t("empty_title"),
                               message: session.t(tab == 0 ? "empty_events" : "empty_places")) {
                    Button { path.append(.settings) } label: {
                        Label(session.t("empty_action"), systemImage: "slider.horizontal.3")
                    }
                    .buttonStyle(TonalButtonStyle())
                    .frame(maxWidth: 280)
                }
            }
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 16) {
                    Text(session.t(tab == 0 ? "feed_count_events" : "feed_count_places", list.count))
                        .font(.subheadline.weight(.medium)).foregroundColor(Palette.onSurfaceVariant)
                        .padding(.leading, 4)
                    ForEach(list) { e in
                        EventCardView(event: e, lang: session.language,
                                      onOpen: { path.append(.event(e.id)) },
                                      onHide: { hide(e) },
                                      onRoute: { Links.open2Gis(e, cityId: session.city?.id, cityName: session.city?.name) })
                        .transition(.opacity.combined(with: .scale(scale: 0.97)))
                    }
                }
                .padding(EdgeInsets(top: 16, leading: 16, bottom: 32, trailing: 16))
                .animation(.easeInOut(duration: 0.25), value: list.map(\.id))
            }
        }
    }

    private func hide(_ e: EventCard) {
        session.hideEvent(e.id)
        showHiddenToast(e.id)
    }

    private func showHiddenToast(_ id: String) {
        let t = Toast(message: session.t("feed_hidden"), action: session.t("undo"), payload: id)
        toast = t
        DispatchQueue.main.asyncAfter(deadline: .now() + 4) { if toast?.id == t.id { toast = nil } }
    }
}

struct EventCardView: View {
    @EnvironmentObject private var session: SessionStore
    let event: EventCard
    let lang: AppLanguage?
    /// Tap on the card → in-app detail (the browser opens only from there).
    let onOpen: () -> Void
    let onHide: () -> Void
    let onRoute: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: event.interest.symbol).font(.caption2)
                    Text(session.t(event.interest.labelKey)).font(.caption.weight(.medium))
                }
                .foregroundColor(Palette.onSecondaryContainer)
                .padding(.horizontal, 10).padding(.vertical, 5)
                .background(RoundedRectangle(cornerRadius: 8).fill(Palette.secondaryContainer))
                Spacer()
                Button(action: onHide) {
                    Image(systemName: "eye.slash").font(.system(size: 16)).foregroundColor(Palette.onSurfaceVariant)
                        .frame(width: 36, height: 36)
                }
                .accessibilityLabel(session.t("not_interested"))
            }
            Text(event.title(lang)).font(.title3.weight(.semibold)).lineLimit(3).padding(.top, 12)

            VStack(alignment: .leading, spacing: 8) {
                if !event.isPlace && !event.startsAt.isEmpty {
                    infoLine("calendar", Localize.date(event.startsAt, lang), emphasize: true)
                }
                infoLine("mappin.and.ellipse", event.place(lang),
                         secondary: (!event.address.isEmpty && event.address != event.placeRu) ? event.address : nil)
            }
            .padding(.top, 12)

            let description = event.description(lang)
            if !description.isEmpty {
                Text(description).font(.subheadline).foregroundColor(Palette.onSurfaceVariant)
                    .lineLimit(2).multilineTextAlignment(.leading).padding(.top, 12)
            }

            FlowLayout(spacing: 8, lineSpacing: 8) {
                if !event.workingHours.isEmpty { pill("clock", Localize.hours(event.workingHours, lang)) }
                if let p = event.price { pill("banknote", Localize.price(p, lang)) }
            }
            .padding(.top, 12)

            HStack(spacing: 10) {
                Button(action: onRoute) {
                    Label(session.t("route_2gis"), systemImage: "arrow.triangle.turn.up.right.diamond.fill").lineLimit(1)
                }
                .buttonStyle(PrimaryButtonStyle(height: 48, radius: 12))
                Button(action: onOpen) {
                    Label(session.t("details"), systemImage: "text.alignleft").lineLimit(1)
                }
                .buttonStyle(TonalButtonStyle())
            }
            .padding(.top, 16)

            HStack {
                Text(session.t("source", event.sourceName)).font(.caption).foregroundColor(Palette.onSurfaceVariant)
                Spacer()
                Button(session.t("not_interested"), action: onHide)
                    .font(.subheadline.weight(.semibold)).foregroundColor(Palette.primary)
                    .padding(.vertical, 8)
            }
            .padding(.top, 4)
        }
        .padding(20)
        .elevatedCard()
        .contentShape(RoundedRectangle(cornerRadius: 20))
        .onTapGesture(perform: onOpen)
        .accessibilityAction(named: Text(session.t("details")), onOpen)
    }
}

// MARK: - Shared card pieces (feed card + detail screen)

func infoLine(_ symbol: String, _ text: String, secondary: String? = nil, emphasize: Bool = false) -> some View {
    HStack(alignment: .top, spacing: 10) {
        Image(systemName: symbol).font(.system(size: 16)).foregroundColor(Palette.primary).frame(width: 20).padding(.top, 1)
        VStack(alignment: .leading, spacing: 2) {
            Text(text).font(emphasize ? .subheadline.weight(.semibold) : .body)
            if let secondary { Text(secondary).font(.subheadline).foregroundColor(Palette.onSurfaceVariant) }
        }
    }
}

func pill(_ symbol: String, _ text: String) -> some View {
    HStack(spacing: 6) {
        Image(systemName: symbol).font(.caption).foregroundColor(Palette.onSurfaceVariant)
        Text(text).font(.caption.weight(.medium))
    }
    .padding(.horizontal, 10).padding(.vertical, 6)
    .background(RoundedRectangle(cornerRadius: 8).fill(Palette.surfaceContainer))
}
