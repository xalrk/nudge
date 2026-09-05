import SwiftUI
import NudgeCore

extension Color {
    init(argb: Int32) {
        let (r, g, b) = Colors.rgb(argb)
        self.init(.sRGB, red: r, green: g, blue: b, opacity: 1)
    }
}

/// Flat, single-accent palette derived from one user-chosen color. Light mode is plain white;
/// dark mode is true black so OLED panels switch those pixels off. No gradients, no tinted elevation.
struct Theme {
    let dark: Bool
    let accentArgb: Int32
    let primary: Color
    let onPrimary: Color
    let background: Color
    let onSurface: Color
    let onSurfaceVariant: Color
    let surfaceContainer: Color
    let surfaceContainerHigh: Color
    let outline: Color
    let outlineVariant: Color
    let error: Color

    static func make(accent: Int32, dark: Bool) -> Theme {
        if dark {
            // Lift the accent so it keeps contrast on pure black; very dark accents get lifted more.
            let primary = Colors.mix(accent, Colors.white, Colors.luminance(accent) < 0.05 ? 0.65 : 0.3)
            return Theme(
                dark: true, accentArgb: accent,
                primary: Color(argb: primary),
                onPrimary: Colors.luminance(primary) < 0.4 ? .white : Color(argb: Int32(bitPattern: 0xFF111111)),
                background: .black,
                onSurface: Color(argb: Int32(bitPattern: 0xFFEDEDED)),
                onSurfaceVariant: Color(argb: Int32(bitPattern: 0xFF9E9E9E)),
                surfaceContainer: Color(argb: Int32(bitPattern: 0xFF0D0D0D)),
                surfaceContainerHigh: Color(argb: Int32(bitPattern: 0xFF161616)),
                outline: Color(argb: Int32(bitPattern: 0xFF5C5C5C)),
                outlineVariant: Color(argb: Int32(bitPattern: 0xFF262626)),
                error: Color(argb: Int32(bitPattern: 0xFFEF5350))
            )
        }
        return Theme(
            dark: false, accentArgb: accent,
            primary: Color(argb: accent),
            onPrimary: Colors.luminance(accent) < 0.4 ? .white : Color(argb: Int32(bitPattern: 0xFF111111)),
            background: .white,
            onSurface: Color(argb: Int32(bitPattern: 0xFF111111)),
            onSurfaceVariant: Color(argb: Int32(bitPattern: 0xFF616161)),
            surfaceContainer: Color(argb: Int32(bitPattern: 0xFFF6F6F6)),
            surfaceContainerHigh: Color(argb: Int32(bitPattern: 0xFFEFEFEF)),
            outline: Color(argb: Int32(bitPattern: 0xFFBDBDBD)),
            outlineVariant: Color(argb: Int32(bitPattern: 0xFFE0E0E0)),
            error: Color(argb: Int32(bitPattern: 0xFFD32F2F))
        )
    }
}

private struct ThemeKey: EnvironmentKey {
    static let defaultValue = Theme.make(accent: SettingsMath.defaultAccent, dark: false)
}

extension EnvironmentValues {
    var theme: Theme {
        get { self[ThemeKey.self] }
        set { self[ThemeKey.self] = newValue }
    }
}

/// Screen scaffold: flat background in both modes and the accent as tint.
struct Screen<Content: View>: View {
    @Environment(\.theme) private var theme
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        content
            .background(theme.background.ignoresSafeArea())
            .foregroundColor(theme.onSurface)
    }
}
