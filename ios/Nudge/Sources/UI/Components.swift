import SwiftUI
import NudgeCore

struct ColorDot: View {
    let argb: Int32
    var size: CGFloat = 10
    var body: some View { Circle().fill(Color(argb: argb)).frame(width: size, height: size) }
}

struct ReminderRow: View {
    @Environment(\.theme) private var theme
    let r: Reminder
    let subtitle: String
    let color: Int32
    let onTap: () -> Void
    var onToggle: ((Bool) -> Void)? = nil

    var body: some View {
        HStack(spacing: 12) {
            Button(action: onTap) {
                HStack(spacing: 12) {
                    ColorDot(argb: color)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(r.title).font(.body).foregroundColor(theme.onSurface).lineLimit(2)
                        if !r.body.isBlank { Text(r.body).font(.footnote).foregroundColor(theme.onSurfaceVariant).lineLimit(2) }
                        Text(subtitle).font(.caption).foregroundColor(theme.primary)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            if let onToggle = onToggle {
                Toggle("", isOn: Binding(get: { r.enabled }, set: onToggle)).labelsHidden()
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
    }
}

/// Shown at the top of the main tabs while every reminder is muted.
struct PauseBanner: View {
    @Environment(\.theme) private var theme
    let settings: SettingsSnapshot
    let onResume: () -> Void

    var body: some View {
        if settings.isPaused() {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("All reminders paused").font(.subheadline)
                    Text("Until " + Fmt.dayTime(settings.pausedUntil)).font(.footnote).foregroundColor(theme.onSurfaceVariant)
                }
                Spacer()
                Button("Resume", action: onResume)
            }
            .padding(.leading, 16).padding(.trailing, 8).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 12).fill(theme.surfaceContainerHigh))
            .padding(.horizontal, 16).padding(.vertical, 8)
        }
    }
}

/// Seven equal circles, Monday first, filled when on. Shared by Settings and the editor.
struct DayCircles: View {
    @Environment(\.theme) private var theme
    let selected: Set<DayOfWeek>
    let onToggle: (DayOfWeek) -> Void

    var body: some View {
        HStack(spacing: 6) {
            ForEach(DayOfWeek.allCases, id: \.rawValue) { d in
                let on = selected.contains(d)
                Button { onToggle(d) } label: {
                    Text(d.narrow)
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(on ? theme.onPrimary : theme.onSurface)
                        .frame(maxWidth: .infinity)
                        .aspectRatio(1, contentMode: .fit)
                        .background(Circle().fill(on ? theme.primary : Color.clear))
                        .overlay(Circle().strokeBorder(on ? Color.clear : theme.outlineVariant, lineWidth: 1))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.top, 8)
    }
}

/// A round accent button pinned to the bottom-right corner.
struct Fab: View {
    @Environment(\.theme) private var theme
    let systemImage: String
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.title2.weight(.semibold))
                .foregroundColor(theme.onPrimary)
                .frame(width: 56, height: 56)
                .background(RoundedRectangle(cornerRadius: 16).fill(theme.primary))
                .shadow(color: .black.opacity(theme.dark ? 0 : 0.2), radius: 6, y: 3)
        }
        .accessibilityLabel(label)
        .padding(16)
    }
}

struct SectionTitle: View {
    @Environment(\.theme) private var theme
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text).font(.headline).foregroundColor(theme.primary).padding(.top, 16).padding(.bottom, 8)
    }
}

struct Hint: View {
    @Environment(\.theme) private var theme
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View { Text(text).font(.footnote).foregroundColor(theme.onSurfaceVariant).fixedSize(horizontal: false, vertical: true) }
}

struct LabeledToggle: View {
    @Environment(\.theme) private var theme
    let title: String
    var subtitle: String? = nil
    var subtitleAccent: Bool = false
    @Binding var isOn: Bool
    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                if let s = subtitle { Text(s).font(.footnote).foregroundColor(subtitleAccent ? theme.primary : theme.onSurfaceVariant) }
            }
        }
    }
}

/// Outlined pill button in the accent colour, used for date/time choices.
struct OutlineButton: View {
    @Environment(\.theme) private var theme
    let title: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title).font(.subheadline).foregroundColor(theme.primary)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .overlay(Capsule().strokeBorder(theme.outline, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// Filled accent button.
struct FilledButton: View {
    @Environment(\.theme) private var theme
    let title: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title).font(.subheadline.weight(.medium)).foregroundColor(theme.onPrimary)
                .padding(.horizontal, 18).padding(.vertical, 9)
                .background(Capsule().fill(theme.primary))
        }
        .buttonStyle(.plain)
    }
}

/// Segmented choice with the platform picker.
struct Segmented<T: Hashable>: View {
    let options: [(T, String)]
    @Binding var selection: T
    var body: some View {
        Picker("", selection: $selection) {
            ForEach(options, id: \.0) { Text($0.1).tag($0.0) }
        }
        .pickerStyle(.segmented)
    }
}

/// Presets, then the user's custom colors, then a "+" that opens the system color picker.
/// `autoColor` adds a leading "A" swatch meaning "automatic" (selected when `current` is nil).
struct SwatchRow: View {
    @Environment(\.theme) private var theme
    let current: Int32?
    let customColors: [Int32]
    var autoColor: Int32? = nil
    let onPick: (Int32?) -> Void
    let onAddCustom: (Int32) -> Void
    let onRemoveCustom: (Int32) -> Void

    @State private var pickerColor: Color = .blue
    @State private var showPicker = false

    private let columns = [GridItem(.adaptive(minimum: 36), spacing: 10)]

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            LazyVGrid(columns: columns, alignment: .leading, spacing: 10) {
                if let auto = autoColor {
                    Swatch(argb: auto, selected: current == nil, name: "Automatic", badge: "A", onTap: { onPick(nil) })
                }
                ForEach(SettingsMath.accentPresets, id: \.argb) { p in
                    Swatch(argb: p.argb, selected: current == p.argb, name: p.name, onTap: { onPick(p.argb) })
                }
                ForEach(customColors.filter { c in !SettingsMath.accentPresets.contains { $0.argb == c } }, id: \.self) { c in
                    Swatch(argb: c, selected: current == c, name: SettingsMath.toHex(c), onTap: { onPick(c) }, onLongPress: { onRemoveCustom(c) })
                }
                Button {
                    pickerColor = Color(argb: current ?? autoColor ?? SettingsMath.defaultAccent)
                    showPicker = true
                } label: {
                    Image(systemName: "plus").font(.body).foregroundColor(theme.onSurfaceVariant)
                        .frame(width: 36, height: 36)
                        .overlay(Circle().strokeBorder(theme.outline, lineWidth: 1.5))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Add a custom color")
            }
            .padding(.top, 8)
            if !customColors.isEmpty { Hint("Long-press a custom color to remove it.") }
        }
        .sheet(isPresented: $showPicker) {
            CustomColorSheet(color: $pickerColor) { argb in onAddCustom(argb); onPick(argb) }
        }
    }
}

private struct Swatch: View {
    @Environment(\.theme) private var theme
    let argb: Int32
    let selected: Bool
    let name: String
    var badge: String? = nil
    let onTap: () -> Void
    var onLongPress: (() -> Void)? = nil

    var body: some View {
        let fg: Color = Colors.luminance(argb) < 0.4 ? .white : .black
        ZStack {
            Circle().fill(Color(argb: argb))
            if selected { Circle().strokeBorder(theme.onSurface, lineWidth: 3) }
            if selected { Image(systemName: "checkmark").font(.footnote.weight(.bold)).foregroundColor(fg) }
            else if let b = badge { Text(b).font(.subheadline.weight(.semibold)).foregroundColor(fg) }
        }
        .frame(width: 36, height: 36)
        .contentShape(Circle())
        .onTapGesture(perform: onTap)
        .onLongPressGesture { onLongPress?() }
        .accessibilityLabel(name)
    }
}

/// The system colour picker plus a hex field, so any exact value can be typed.
struct CustomColorSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @Binding var color: Color
    let onAdd: (Int32) -> Void
    @State private var hex: String = ""

    private var argb: Int32 {
        let ui = UIColor(color)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        ui.getRed(&r, green: &g, blue: &b, alpha: &a)
        return Colors.argb(r: Double(r), g: Double(g), b: Double(b))
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                ColorPicker("Pick a color", selection: $color, supportsOpacity: false)
                HStack {
                    Text("Hex")
                    TextField("#RRGGBB", text: $hex)
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.characters)
                        .onSubmit { if let v = SettingsMath.parseHex(hex) { color = Color(argb: v) } }
                    Button("Apply") { if let v = SettingsMath.parseHex(hex) { color = Color(argb: v) } }
                }
                RoundedRectangle(cornerRadius: 12).fill(color).frame(height: 60)
                Spacer()
            }
            .padding()
            .background(theme.background.ignoresSafeArea())
            .navigationTitle("Custom color")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Add") { onAdd(argb); dismiss() } }
            }
            .onAppear { hex = SettingsMath.toHex(argb) }
            .onChange(of: color) { _ in hex = SettingsMath.toHex(argb) }
        }
        .presentationDetents([.medium])
    }
}

/// Bottom message that replaces itself and fades: 4 s, or 2.5 s for brief ones.
struct ToastView: View {
    @Environment(\.theme) private var theme
    let toast: Toast
    var body: some View {
        Text(toast.text)
            .font(.subheadline)
            .foregroundColor(theme.dark ? .black : .white)
            .padding(.horizontal, 16).padding(.vertical, 12)
            .background(RoundedRectangle(cornerRadius: 8).fill(theme.dark ? Color(argb: Int32(bitPattern: 0xFFEDEDED)) : Color(argb: Int32(bitPattern: 0xFF323232))))
            .padding(.horizontal, 16)
            .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}
