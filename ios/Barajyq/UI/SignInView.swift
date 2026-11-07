import SwiftUI
import AuthenticationServices

/// Google Sign-In is required (as on Android). Sign in with Apple is offered next to it:
/// App Store Review Guideline 4.8 requires it when a third-party login is used.
struct SignInView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var loading = false
    @State private var error: String?
    private let configured = GoogleAuth.isConfigured()

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Spacer().frame(height: 40)
                IconBadge(symbol: "safari.fill", size: 96)
                Spacer().frame(height: 20)
                Text(session.t("app_name")).font(.system(size: 36, weight: .bold)).foregroundColor(Palette.primary)
                Spacer().frame(height: 8)
                Text(session.t("signin_tagline")).font(.headline.weight(.regular))
                    .foregroundColor(Palette.onSurfaceVariant).multilineTextAlignment(.center)
                Spacer().frame(height: 32)
                VStack(alignment: .leading, spacing: 16) {
                    feature("sparkles", session.t("signin_feature_1"))
                    feature("building.columns.fill", session.t("signin_feature_2"))
                    feature("arrow.triangle.turn.up.right.diamond.fill", session.t("signin_feature_3"))
                }
                .padding(20)
                .frame(maxWidth: .infinity, alignment: .leading)
                .elevatedCard()
                Spacer().frame(height: 40)

                if !configured {
                    Text(session.t("signin_not_configured")).font(.footnote).foregroundColor(Palette.error)
                        .multilineTextAlignment(.center).padding(.bottom, 12)
                }
                Button(action: google) {
                    if loading {
                        ProgressView().tint(.white)
                    } else {
                        HStack(spacing: 12) {
                            Text("G").font(.system(size: 18, weight: .bold))
                            Text(session.t("signin_google"))
                        }
                    }
                }
                .buttonStyle(PrimaryButtonStyle(height: 56))
                .disabled(!configured || loading)

                Text(session.t("signin_or")).font(.footnote).foregroundColor(Palette.onSurfaceVariant).padding(.vertical, 10)

                SignInWithAppleButton(.signIn) { req in
                    req.requestedScopes = [.fullName, .email]
                } onCompletion: { result in
                    switch result {
                    case .success(let auth):
                        if let acc = AppleAuth.account(from: auth) { finish(acc) } else { error = session.t("signin_error") }
                    case .failure(let e):
                        if (e as? ASAuthorizationError)?.code == .canceled { error = session.t("signin_cancelled") }
                        else { error = session.t("signin_error") + "\n" + e.localizedDescription }
                    }
                }
                .signInWithAppleButtonStyle(.black)
                .frame(height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .disabled(loading)

                if let error {
                    Text(error).font(.footnote).foregroundColor(Palette.error).multilineTextAlignment(.center).padding(.top, 12)
                }
                Spacer().frame(height: 16)
            }
            .padding(24)
        }
        .background(LinearGradient(colors: [Palette.primaryContainer, Palette.background], startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea())
    }

    private func feature(_ symbol: String, _ text: String) -> some View {
        HStack(spacing: 14) {
            Image(systemName: symbol).font(.system(size: 18)).foregroundColor(Palette.secondary)
                .frame(width: 38, height: 38)
                .background(RoundedRectangle(cornerRadius: 8).fill(Palette.secondaryContainer))
            Text(text).font(.body)
        }
    }

    private func google() {
        error = nil
        loading = true
        Task {
            defer { loading = false }
            do {
                finish(try await GoogleAuth.signIn())
            } catch AuthError.cancelled {
                error = session.t("signin_cancelled")
            } catch AuthError.notConfigured {
                error = session.t("signin_not_configured")
            } catch {
                self.error = session.t("signin_error") + "\n" + error.localizedDescription
            }
        }
    }

    private func finish(_ acc: AuthAccount) {
        session.signIn(userId: acc.userId, provider: acc.provider, email: acc.email, displayName: acc.displayName)
    }
}
