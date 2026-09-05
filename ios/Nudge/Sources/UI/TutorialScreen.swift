import SwiftUI
import NudgeCore

private struct Page {
    let title: String
    let lines: [String]
    let art: Int
}

/// A short first-run walkthrough. It only covers what the layout does not make obvious:
/// how random reminders behave, the calendar's color language, and what keeps
/// notifications coming on iOS. Swipe or use the buttons. Reachable again from Settings.
struct TutorialScreen: View {
    @Environment(\.theme) private var theme
    let onDone: () -> Void
    @State private var index = 0

    private let pages = [
        Page(title: "Nudge", lines: [
            "Reminders on a calendar, plus a pool of reminders with no time at all that surface when you least expect them.",
            "Two minutes of setup is all it needs.",
        ], art: 0),
        Page(title: "Random Reminders", lines: [
            "Anything without a date or time goes into the random pool. Each one fires at an unpredictable moment inside your active hours, about once every two weeks on average by default.",
            "They work best as a big list you rarely think about: affirmations, small habits, questions to sit with, things you keep meaning to do. Import a whole list at once from Settings.",
            "The Settings slider sets the average rate for the pool; any reminder can override it with its own rate.",
        ], art: 1),
        Page(title: "Calendar", lines: [
            "Tap a day, then + to add something on that day.",
            "Each dot is a reminder in its own color. A faded dot means it was already delivered.",
            "Changing or deleting a repeating reminder asks whether you mean only that occurrence, everything from then on, or the whole series.",
        ], art: 2),
        Page(title: "Keep them coming", lines: [
            "Nudge hands the next 60 notifications to iOS in advance, so they arrive even when the app is closed and it costs no battery in between.",
            "iOS will not let an app queue more than that, so open Nudge every few days to top the queue up. Settings → Reliability shows how far ahead you are covered.",
            "Every notification can be snoozed for 10 minutes, an hour, or until tomorrow morning: press and hold the banner.",
        ], art: 3),
    ]

    var body: some View {
        let last = index == pages.count - 1
        VStack(spacing: 0) {
            HStack {
                Spacer()
                if !last { Button("Skip", action: onDone).padding(.top, 8).padding(.trailing, 12) }
            }
            .frame(height: 44)
            TabView(selection: $index) {
                ForEach(Array(pages.enumerated()), id: \.offset) { i, page in
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            ZStack { TutorialArt(kind: page.art, active: index == i) }.frame(maxWidth: .infinity).frame(height: 180)
                            Spacer().frame(height: 12)
                            Text(page.title).font(.title.weight(.semibold))
                            ForEach(page.lines, id: \.self) { line in
                                Text(line).font(.body).foregroundColor(theme.onSurfaceVariant).padding(.bottom, 4)
                            }
                        }
                        .padding(.horizontal, 24)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .tag(i)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .never))
            HStack {
                HStack(spacing: 6) {
                    ForEach(0..<pages.count, id: \.self) { i in
                        Circle().fill(i == index ? theme.primary : theme.outlineVariant).frame(width: i == index ? 10 : 6, height: i == index ? 10 : 6)
                    }
                }
                Spacer()
                if index > 0 { Button("Back") { withAnimation { index -= 1 } } }
                FilledButton(title: last ? "Done" : "Next") { if last { onDone() } else { withAnimation { index += 1 } } }.padding(.leading, 8)
            }
            .padding(.horizontal, 24).padding(.vertical, 16)
        }
        .background(theme.background.ignoresSafeArea())
        .foregroundColor(theme.onSurface)
    }
}

/// Simple, cheap illustrations: a bell that drops and rings, a week of random dots, a calendar strip, a swinging bell.
private struct TutorialArt: View {
    @Environment(\.theme) private var theme
    let kind: Int
    let active: Bool
    @State private var t: Double = 0
    @State private var hits: [(Double, Double)] = (0..<9).map { _ in (Double.random(in: 0...1), Double.random(in: 0...1)) }.sorted { $0.0 < $1.0 }

    var body: some View {
        Group {
            switch kind {
            case 0: intro
            case 1: randomWeek
            case 2: calendarStrip
            default: ringingBell
            }
        }
        .onAppear { if active { start() } }
        .onChange(of: active) { if $0 { start() } }
    }

    private func start() {
        t = 0
        let duration: Double = kind == 3 ? 1.4 : (kind == 1 ? 2.6 : 2.2)
        if kind == 1 || kind == 3 {
            withAnimation(.linear(duration: duration).repeatForever(autoreverses: false)) { t = 1 }
        } else {
            withAnimation(.easeOut(duration: duration)) { t = 1 }
        }
    }

    private var intro: some View {
        ZStack {
            Image(systemName: "bell.fill").font(.system(size: 84)).foregroundColor(theme.primary)
                .offset(y: (1 - t) * -120)
                .rotationEffect(.degrees(sin(t * .pi * 4) * 12 * (1 - t)), anchor: .top)
            Circle().fill(Color(red: 1, green: 0.23, blue: 0.19)).frame(width: 24, height: 24).offset(x: 30, y: -30)
                .scaleEffect(t > 0.6 ? 1 : 0.01)
        }
    }

    private var randomWeek: some View {
        Canvas { ctx, size in
            let days = 7
            let colW: Double = Double(size.width) / Double(days)
            let top: Double = Double(size.height) * 0.25
            let bandH: Double = Double(size.height) * 0.45
            for d in 0..<days {
                let rect = CGRect(x: Double(d) * colW + 3.0, y: top, width: colW - 6.0, height: bandH)
                ctx.fill(Path(roundedRect: rect, cornerRadius: 6), with: .color(theme.surfaceContainerHigh))
            }
            for h in hits where h.0 <= t {
                let age = min(max((t - h.0) / 0.12, 0), 1)
                let r = 5.0 * (1 + 0.6 * (1 - age))
                let cx: Double = h.0 * Double(size.width)
                let cy: Double = top + bandH * (0.15 + 0.7 * h.1)
                let c = CGPoint(x: cx, y: cy)
                ctx.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)), with: .color(theme.primary))
            }
            let lx: Double = t * Double(size.width)
            var line = Path()
            line.move(to: CGPoint(x: lx, y: top - 6.0))
            line.addLine(to: CGPoint(x: lx, y: top + bandH + 6.0))
            ctx.stroke(line, with: .color(theme.primary.opacity(0.6)), lineWidth: 2)
        }
        .frame(height: 150)
    }

    private var calendarStrip: some View {
        Canvas { ctx, size in
            let days = 7
            let colW: Double = Double(size.width) / Double(days)
            let today = 3
            let faded = Color(argb: Colors.faded(theme.accentArgb))
            for d in 0..<days {
                let cx: Double = Double(d) * colW + colW / 2.0
                let cy: Double = Double(size.height) * 0.45
                if d == today {
                    ctx.fill(Path(roundedRect: CGRect(x: cx - 16, y: cy - 16, width: 32, height: 32), cornerRadius: 10), with: .color(theme.primary))
                } else {
                    ctx.fill(Path(ellipseIn: CGRect(x: cx - 3, y: cy - 3, width: 6, height: 6)), with: .color(theme.onSurface))
                }
                let appear = min(max((t - Double(d) * 0.08) / 0.25, 0), 1)
                if appear > 0 && d != 1 && d != 5 {
                    let col = d < today ? faded : theme.primary
                    let r = 4 * appear
                    ctx.fill(Path(ellipseIn: CGRect(x: cx - r, y: cy + 26 - r, width: 2 * r, height: 2 * r)), with: .color(col.opacity(appear)))
                }
            }
        }
        .frame(height: 150)
    }

    private var ringingBell: some View {
        Image(systemName: "bell.fill").font(.system(size: 84)).foregroundColor(theme.primary)
            .rotationEffect(.degrees(sin(t * .pi * 2) * 10), anchor: .top)
    }
}
