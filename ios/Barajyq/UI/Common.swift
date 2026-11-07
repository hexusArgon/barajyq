import SwiftUI

/// Round tonal badge with an icon — used in heroes and empty states.
struct IconBadge: View {
    let symbol: String
    var size: CGFloat = 72
    var body: some View {
        ZStack {
            Circle().fill(Palette.primaryContainer)
            Image(systemName: symbol)
                .font(.system(size: size / 2.4, weight: .semibold))
                .foregroundColor(Palette.primary)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

struct EmptyStateView<Action: View>: View {
    let symbol: String
    let title: String
    let message: String
    @ViewBuilder var action: () -> Action

    var body: some View {
        VStack(spacing: 12) {
            IconBadge(symbol: symbol, size: 88)
            Text(title).font(.title3.weight(.semibold)).multilineTextAlignment(.center)
            Text(message).font(.subheadline).foregroundColor(Palette.onSurfaceVariant).multilineTextAlignment(.center)
            action()
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 32)
        .padding(.vertical, 56)
    }
}

extension EmptyStateView where Action == EmptyView {
    init(symbol: String, title: String, message: String) {
        self.init(symbol: symbol, title: title, message: message) { EmptyView() }
    }
}

struct SectionTitle: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.caption.weight(.semibold))
            .foregroundColor(Palette.primary)
            .padding(.leading, 4)
            .padding(.bottom, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Wrapping row (Compose `FlowRow`).
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    var lineSpacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, lineH: CGFloat = 0, widest: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > 0 && x + s.width > maxW { y += lineH + lineSpacing; x = 0; lineH = 0 }
            x += s.width + spacing
            lineH = max(lineH, s.height)
            widest = max(widest, x - spacing)
        }
        return CGSize(width: proposal.width ?? widest, height: y + lineH)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, lineH: CGFloat = 0
        for v in subviews {
            let s = v.sizeThatFits(.unspecified)
            if x > bounds.minX && x + s.width > bounds.maxX { y += lineH + lineSpacing; x = bounds.minX; lineH = 0 }
            v.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(s))
            x += s.width + spacing
            lineH = max(lineH, s.height)
        }
    }
}

/// Interest chips (Android `InterestPicker` — no exclusions block).
struct InterestPicker: View {
    @EnvironmentObject private var session: SessionStore
    let selected: Set<Interest>
    let onToggle: (Interest) -> Void

    var body: some View {
        FlowLayout(spacing: 8, lineSpacing: 8) {
            ForEach(Interest.allCases) { i in
                let sel = selected.contains(i)
                Button { onToggle(i) } label: {
                    HStack(spacing: 8) {
                        Image(systemName: sel ? "checkmark" : i.symbol)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(sel ? Palette.primary : Palette.onSurfaceVariant)
                        Text(session.t(i.labelKey)).font(.subheadline.weight(.medium)).foregroundColor(Palette.onSurface)
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 12).fill(sel ? Palette.primaryContainer : Palette.surfaceLowest))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(sel ? Color.clear : Palette.outlineVariant, lineWidth: 1))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(sel ? .isSelected : [])
            }
        }
    }
}

/// Snackbar with an action (Material `Snackbar` with Undo).
struct Toast: Equatable {
    let id = UUID()
    let message: String
    let action: String
    let payload: String
}

struct ToastView: View {
    let toast: Toast
    let onAction: () -> Void
    let onDismiss: () -> Void
    var body: some View {
        HStack(spacing: 12) {
            Text(toast.message).font(.subheadline).foregroundColor(.white)
            Spacer()
            Button(toast.action, action: onAction).font(.subheadline.weight(.semibold)).foregroundColor(Palette.primaryContainer)
            Button(action: onDismiss) { Image(systemName: "xmark").foregroundColor(.white.opacity(0.8)) }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 8).fill(Color(hex: 0x303331)))
        .padding(.horizontal, 12)
        .padding(.bottom, 12)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

extension SessionStore {
    /// Localized string in the in-app language.
    func t(_ key: String) -> String { L10n.tr(key, language) }
    func t(_ key: String, _ args: CVarArg...) -> String {
        String(format: L10n.tr(key, language), locale: Locale(identifier: language?.tag ?? "en"), arguments: args)
    }
}
