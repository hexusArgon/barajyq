import SwiftUI

/// "Что вам интересно?" — interests only (no exclusions block).
struct OnboardingView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var selected: Set<Interest> = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 8) {
                Text(session.t("onboarding_title")).font(.largeTitle.weight(.bold))
                Text(session.t("onboarding_subtitle")).font(.body).foregroundColor(Palette.onSurfaceVariant)
                InterestPicker(selected: selected) { i in
                    if selected.contains(i) { selected.remove(i) } else { selected.insert(i) }
                }
                .padding(.top, 16)
            }
            .padding(24)
        }
        .background(Palette.background.ignoresSafeArea())
        .safeAreaInset(edge: .bottom) {
            Button {
                session.saveOnboarding(selected)
            } label: {
                Text(selected.isEmpty ? session.t("onboarding_pick_one") : session.t("onboarding_continue", selected.count))
            }
            .buttonStyle(PrimaryButtonStyle(height: 56))
            .disabled(selected.isEmpty)
            .padding(16)
            .background(Palette.surfaceLowest.shadow(color: .black.opacity(0.08), radius: 8, y: -2).ignoresSafeArea())
        }
        .onAppear { selected = session.profile?.interests ?? [] }
    }
}
