import SwiftUI

/// First launch: pick UI language (also reachable from Settings).
struct LanguageView: View {
    @EnvironmentObject private var session: SessionStore
    var fromSettings = false
    let onSelected: (AppLanguage) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                if !fromSettings {
                    IconBadge(symbol: "character.bubble.fill", size: 88)
                    Text(session.t("app_name")).font(.largeTitle.weight(.bold)).foregroundColor(Palette.primary)
                    // Trilingual on purpose: the user has not picked a language yet.
                    Text("Тілді таңдаңыз · Выберите язык · Choose language")
                        .font(.headline).foregroundColor(Palette.onSurfaceVariant).multilineTextAlignment(.center)
                    Spacer().frame(height: 12)
                }
                ForEach(AppLanguage.allCases) { lang in
                    let selected = lang == session.language
                    Button { onSelected(lang) } label: {
                        HStack(spacing: 16) {
                            Text(lang.flag).font(.system(size: 30))
                            Text(lang.nativeName).font(.title3.weight(.semibold)).foregroundColor(Palette.onSurface)
                            Spacer()
                            if selected { Image(systemName: "checkmark.circle.fill").foregroundColor(Palette.primary) }
                        }
                        .padding(20)
                        .background(RoundedRectangle(cornerRadius: 20).fill(selected ? Palette.primaryContainer : Palette.surfaceLowest))
                        .overlay(RoundedRectangle(cornerRadius: 20)
                            .stroke(selected ? Palette.primary : Palette.outlineVariant, lineWidth: selected ? 2 : 1))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 24)
            .padding(.top, fromSettings ? 16 : 80)
            .padding(.bottom, 24)
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle(fromSettings ? session.t("settings_language") : "")
        .navigationBarTitleDisplayMode(.inline)
    }
}
