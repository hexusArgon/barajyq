import SwiftUI

@main
struct BarajyqApp: App {
    @StateObject private var session = SessionStore.shared

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .environment(\.locale, Locale(identifier: (session.language ?? L10n.deviceLanguage).tag))
                .tint(Palette.primary)
                .preferredColorScheme(.light)
                .onOpenURL { url in _ = GoogleAuth.handle(url) }
        }
    }
}

/// Same flow as Android `MainActivity.AppRoot`:
/// Language → City → Sign-In → Interests → Feed (Settings / City / Language / event detail pushed from the feed).
struct RootView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var path: [Route] = []
    @State private var hiddenFromDetail: String?

    var body: some View {
        Group {
            if session.language == nil {
                LanguageView { session.setLanguage($0) }
            } else if session.city == nil {
                CitySelectView { _ in }
            } else if session.profile == nil {
                SignInView()
            } else if session.profile?.onboardingDone != true {
                OnboardingView()
            } else {
                NavigationStack(path: $path) {
                    FeedView(path: $path, hiddenFromDetail: $hiddenFromDetail)
                        .navigationDestination(for: Route.self) { route in
                            switch route {
                            case .settings: SettingsView(path: $path)
                            case .city: CitySelectView(fromSettings: true) { _ in path.removeLast() }
                            case .language: LanguageView(fromSettings: true) { lang in
                                session.setLanguage(lang); path.removeLast()
                            }
                            case .event(let id): EventDetailView(eventId: id) { hiddenId in
                                hiddenFromDetail = hiddenId
                                if !path.isEmpty { path.removeLast() }
                            }
                            }
                        }
                }
            }
        }
        .animation(.easeInOut(duration: 0.25), value: stage)
        .onChange(of: stage) { _ in path = [] }
    }

    private var stage: Int {
        if session.language == nil { return 0 }
        if session.city == nil { return 1 }
        if session.profile == nil { return 2 }
        if session.profile?.onboardingDone != true { return 3 }
        return 4
    }
}
