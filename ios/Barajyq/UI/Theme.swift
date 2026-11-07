import SwiftUI

/// Palette from Android `ui/theme/Theme.kt` (light theme only, like Android).
enum Palette {
    static let primary = Color(hex: 0x0E7C70)          // Teal
    static let onPrimary = Color.white
    static let primaryContainer = Color(hex: 0xCDEFE8)
    static let secondary = Color(hex: 0xE8863A)        // Amber
    static let secondaryContainer = Color(hex: 0xFFE2CC)
    static let onSecondaryContainer = Color(hex: 0x5C2A00)
    static let background = Color(hex: 0xF8F6F2)
    static let surface = Color(hex: 0xF8F6F2)
    static let surfaceLowest = Color.white
    static let surfaceContainer = Color(hex: 0xF1EFEA)
    static let onSurface = Color(hex: 0x1B1F1E)
    static let onSurfaceVariant = Color(hex: 0x5A635F)
    static let outlineVariant = Color(hex: 0xD6DBD8)
    static let error = Color(hex: 0xBA1A1A)
}

extension Color {
    init(hex: UInt32) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: 1)
    }
}

/// Filled teal button (Material `Button`).
struct PrimaryButtonStyle: ButtonStyle {
    var height: CGFloat = 52
    var radius: CGFloat = 16
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: height)
            .padding(.horizontal, 12)
            .foregroundColor(enabled ? Palette.onPrimary : Palette.onSurface.opacity(0.38))
            .background(RoundedRectangle(cornerRadius: radius)
                .fill(enabled ? Palette.primary : Palette.onSurface.opacity(0.12)))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}

/// Tonal button (Material `FilledTonalButton`).
struct TonalButtonStyle: ButtonStyle {
    var height: CGFloat = 48
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: height)
            .padding(.horizontal, 12)
            .foregroundColor(Palette.primary)
            .background(RoundedRectangle(cornerRadius: 12).fill(Palette.primaryContainer))
            .opacity(configuration.isPressed ? 0.8 : 1)
    }
}

/// Outlined button (Material `OutlinedButton`).
struct OutlinedButtonStyle: ButtonStyle {
    var tint: Color = Palette.primary
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .frame(maxWidth: .infinity, minHeight: 44)
            .foregroundColor(tint)
            .background(RoundedRectangle(cornerRadius: 12).stroke(Palette.outlineVariant, lineWidth: 1))
            .opacity(configuration.isPressed ? 0.7 : 1)
    }
}

extension View {
    /// Material `ElevatedCard`.
    func elevatedCard(radius: CGFloat = 20) -> some View {
        background(RoundedRectangle(cornerRadius: radius).fill(Palette.surfaceLowest)
            .shadow(color: .black.opacity(0.08), radius: 4, x: 0, y: 1))
    }
}
