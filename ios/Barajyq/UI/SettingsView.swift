import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var session: SessionStore
    @Binding var path: [Route]
    @State private var confirmDelete = false

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                section(session.t("settings_general")) {
                    row("character.bubble", session.t("settings_language"),
                        session.language.map { "\($0.flag)  \($0.nativeName)" } ?? "—") { path.append(.language) }
                    Divider().padding(.leading, 56)
                    row("building.2", session.t("settings_city"), session.city?.displayName(session.language) ?? "—") { path.append(.city) }
                }

                section(session.t("settings_interests")) {
                    VStack(alignment: .leading, spacing: 12) {
                        Text(session.t("settings_interests_hint")).font(.subheadline).foregroundColor(Palette.onSurfaceVariant)
                        let interests = session.profile?.interests ?? []
                        InterestPicker(selected: interests) { i in
                            var next = interests
                            if next.contains(i) { next.remove(i) } else { next.insert(i) }
                            if !next.isEmpty { session.setInterests(next) }
                        }
                    }
                    .padding(16)
                }

                section(session.t("settings_hidden")) {
                    HStack(spacing: 16) {
                        Image(systemName: "eye.slash").foregroundColor(Palette.primary).frame(width: 24)
                        Text(session.t("settings_hidden_count", session.hidden.count)).font(.body)
                        Spacer()
                        Button(session.t("settings_restore")) { session.clearHidden() }
                            .font(.subheadline.weight(.semibold)).disabled(session.hidden.isEmpty)
                    }
                    .padding(16)
                }

                section(session.t("settings_account")) {
                    HStack(spacing: 16) {
                        Image(systemName: session.profile?.provider == .apple ? "apple.logo" : "person.crop.circle.fill")
                            .font(.title2).foregroundColor(Palette.primary).frame(width: 24)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(session.profile?.displayName ?? session.profile?.email
                                 ?? (session.profile?.provider == .apple ? "Apple ID" : session.profile?.provider == .guest ? session.t("account_guest") : "Google")).font(.body)
                            if let mail = session.profile?.email { Text(mail).font(.subheadline).foregroundColor(Palette.onSurfaceVariant) }
                        }
                        Spacer()
                    }
                    .padding(16)
                    VStack(spacing: 10) {
                        Button {
                            if session.profile?.provider == .google { GoogleAuth.signOut() }
                            session.signOut()
                        } label: {
                            Label(session.t("settings_sign_out"), systemImage: "rectangle.portrait.and.arrow.right")
                        }
                        .buttonStyle(OutlinedButtonStyle())
                        Button { confirmDelete = true } label: {
                            Label(session.t("settings_delete"), systemImage: "trash")
                        }
                        .buttonStyle(OutlinedButtonStyle(tint: Palette.error))
                    }
                    .padding(EdgeInsets(top: 0, leading: 16, bottom: 16, trailing: 16))
                }

                if let v = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String {
                    Text("Barajyq \(v)").font(.caption).foregroundColor(Palette.onSurfaceVariant)
                }
            }
            .padding(16)
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle(session.t("settings_title"))
        .navigationBarTitleDisplayMode(.inline)
        .alert(session.t("settings_delete_title"), isPresented: $confirmDelete) {
            Button(session.t("settings_delete_confirm"), role: .destructive) {
                if session.profile?.provider == .google { GoogleAuth.signOut(disconnect: true) }
                session.deleteAccountAndData()
            }
            Button(session.t("cancel"), role: .cancel) {}
        } message: {
            Text(session.t("settings_delete_body"))
        }
    }

    private func section<C: View>(_ title: String, @ViewBuilder _ content: () -> C) -> some View {
        VStack(spacing: 0) {
            SectionTitle(text: title)
            VStack(spacing: 0, content: content).elevatedCard()
        }
    }

    private func row(_ symbol: String, _ title: String, _ value: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: symbol).foregroundColor(Palette.primary).frame(width: 24)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.headline.weight(.medium)).foregroundColor(Palette.onSurface)
                    Text(value).font(.subheadline).foregroundColor(Palette.onSurfaceVariant)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundColor(Palette.onSurfaceVariant)
            }
            .padding(16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
