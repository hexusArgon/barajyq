import SwiftUI

/// Pick a city of Kazakhstan: search + popular chips + full list.
struct CitySelectView: View {
    @EnvironmentObject private var session: SessionStore
    var fromSettings = false
    let onSelected: (City) -> Void
    @State private var query = ""

    private var list: [City] {
        KazakhstanCities.all.filter { $0.matches(query) }
            .sorted { $0.displayName(session.language).compare($1.displayName(session.language)) == .orderedAscending }
    }

    private func choose(_ c: City) { session.setCity(c); onSelected(c) }

    var body: some View {
        VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 4) {
                Text(session.t("city_title")).font(.title2.weight(.bold))
                Text(session.t("city_subtitle")).font(.subheadline).foregroundColor(Palette.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, fromSettings ? 4 : 16)

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundColor(Palette.onSurfaceVariant)
                TextField(session.t("city_search"), text: $query)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
                if !query.isEmpty {
                    Button { query = "" } label: { Image(systemName: "xmark.circle.fill").foregroundColor(Palette.onSurfaceVariant) }
                }
            }
            .padding(.horizontal, 16)
            .frame(height: 52)
            .background(Capsule().fill(Palette.surfaceLowest))
            .overlay(Capsule().stroke(Palette.outlineVariant, lineWidth: 1))
            .padding(.horizontal, 16)
            .padding(.vertical, 8)

            ScrollView {
                LazyVStack(alignment: .leading, spacing: 4) {
                    if query.trimmingCharacters(in: .whitespaces).isEmpty {
                        SectionTitle(text: session.t("city_popular"))
                        FlowLayout(spacing: 8, lineSpacing: 8) {
                            ForEach(KazakhstanCities.popular) { c in
                                let sel = c.id == session.city?.id
                                Button { choose(c) } label: {
                                    HStack(spacing: 6) {
                                        Image(systemName: sel ? "checkmark" : "building.2.fill").font(.caption)
                                        Text(c.displayName(session.language)).font(.subheadline)
                                    }
                                    .foregroundColor(Palette.onSurface)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(RoundedRectangle(cornerRadius: 8).fill(sel ? Palette.primaryContainer : Palette.surfaceLowest))
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(sel ? .clear : Palette.outlineVariant))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.bottom, 16)
                        SectionTitle(text: session.t("city_all"))
                    }
                    if list.isEmpty {
                        EmptyStateView(symbol: "magnifyingglass", title: session.t("city_nothing"), message: session.t("city_nothing_body"))
                    }
                    ForEach(list) { c in
                        let sel = c.id == session.city?.id
                        Button { choose(c) } label: {
                            HStack(spacing: 16) {
                                Image(systemName: "building.2").foregroundColor(Palette.onSurfaceVariant).frame(width: 24)
                                Text(c.displayName(session.language)).font(.headline.weight(.regular)).foregroundColor(Palette.onSurface)
                                Spacer()
                                if sel { Image(systemName: "checkmark").foregroundColor(Palette.primary) }
                            }
                            .padding(.horizontal, 16)
                            .frame(minHeight: 56)
                            .background(RoundedRectangle(cornerRadius: 12).fill(sel ? Palette.primaryContainer : Color.clear))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
    }
}
