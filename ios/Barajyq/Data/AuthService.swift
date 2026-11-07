import Foundation
import AuthenticationServices
import UIKit
#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

struct AuthAccount {
    let userId: String
    let provider: AuthProvider
    let email: String?
    let displayName: String?
    /// Google ID token (JWT) — for a future backend. Not persisted on device.
    let idToken: String?
}

enum AuthError: LocalizedError {
    case notConfigured, cancelled, failed(String)
    var errorDescription: String? {
        switch self {
        case .notConfigured: return "GOOGLE_IOS_CLIENT_ID is not set (Config/Google.xcconfig)"
        case .cancelled: return "cancelled"
        case .failed(let m): return m
        }
    }
}

/// Google Sign-In (GoogleSignIn-iOS SDK, Swift Package).
/// Client ID comes from Info.plist key `GIDClientID` ← build setting `GOOGLE_IOS_CLIENT_ID`
/// in `Config/Google.xcconfig`. It is a separate **iOS** OAuth client, not the Android one.
enum GoogleAuth {
    static var clientID: String {
        (Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String) ?? ""
    }

    static func isConfigured() -> Bool {
        let id = clientID
        return !id.isEmpty && !id.hasPrefix("REPLACE") && id.hasSuffix(".apps.googleusercontent.com")
    }

    @MainActor
    static func signIn() async throws -> AuthAccount {
        guard isConfigured() else { throw AuthError.notConfigured }
        #if canImport(GoogleSignIn)
        guard let root = UIApplication.shared.topViewController else { throw AuthError.failed("No window") }
        var config = GIDConfiguration(clientID: clientID)
        if let server = Bundle.main.object(forInfoDictionaryKey: "GIDServerClientID") as? String,
           !server.isEmpty, !server.hasPrefix("REPLACE") {
            config = GIDConfiguration(clientID: clientID, serverClientID: server)
        }
        GIDSignIn.sharedInstance.configuration = config
        do {
            let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: root)
            let user = result.user
            return AuthAccount(
                userId: user.userID ?? user.profile?.email ?? UUID().uuidString,
                provider: .google,
                email: user.profile?.email,
                displayName: user.profile?.name,
                idToken: user.idToken?.tokenString
            )
        } catch let e as GIDSignInError where e.code == .canceled {
            throw AuthError.cancelled
        } catch {
            throw AuthError.failed(error.localizedDescription)
        }
        #else
        throw AuthError.failed("GoogleSignIn package is missing")
        #endif
    }

    static func handle(_ url: URL) -> Bool {
        #if canImport(GoogleSignIn)
        return GIDSignIn.sharedInstance.handle(url)
        #else
        return false
        #endif
    }

    static func signOut(disconnect: Bool = false) {
        #if canImport(GoogleSignIn)
        if disconnect { GIDSignIn.sharedInstance.disconnect { _ in } } else { GIDSignIn.sharedInstance.signOut() }
        #endif
    }
}

/// Sign in with Apple — required by App Store Review Guideline 4.8 when an app offers
/// a third-party login (Google). Used by `SignInWithAppleButton` in `SignInView`.
enum AppleAuth {
    static func account(from authorization: ASAuthorization) -> AuthAccount? {
        guard let cred = authorization.credential as? ASAuthorizationAppleIDCredential else { return nil }
        let name = [cred.fullName?.givenName, cred.fullName?.familyName].compactMap { $0 }.joined(separator: " ")
        return AuthAccount(
            userId: cred.user,
            provider: .apple,
            email: cred.email,
            displayName: name.isEmpty ? nil : name,
            idToken: cred.identityToken.flatMap { String(data: $0, encoding: .utf8) }
        )
    }
}

extension UIApplication {
    var topViewController: UIViewController? {
        let scene = connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
            ?? connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var top = scene?.windows.first { $0.isKeyWindow }?.rootViewController ?? scene?.windows.first?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
