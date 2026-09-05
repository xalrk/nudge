import Foundation

/// ARGB colour maths mirroring android.graphics.Color's HSV helpers.
public enum Colors {
    public static func rgb(_ argb: Int32) -> (r: Double, g: Double, b: Double) {
        let v = UInt32(bitPattern: argb)
        return (Double((v >> 16) & 0xFF) / 255, Double((v >> 8) & 0xFF) / 255, Double(v & 0xFF) / 255)
    }

    public static func argb(r: Double, g: Double, b: Double) -> Int32 {
        func c(_ x: Double) -> UInt32 { UInt32(min(max(x, 0), 1) * 255 + 0.5) }
        return Int32(bitPattern: 0xFF000000 | (c(r) << 16) | (c(g) << 8) | c(b))
    }

    /// Hue in degrees (0..<360), saturation and value in 0...1.
    public static func toHSV(_ argb: Int32) -> (h: Double, s: Double, v: Double) {
        let (r, g, b) = rgb(argb)
        let mx = max(r, g, b), mn = min(r, g, b)
        let d = mx - mn
        var h = 0.0
        if d > 0 {
            if mx == r { h = 60 * ((g - b) / d).truncatingRemainder(dividingBy: 6) }
            else if mx == g { h = 60 * ((b - r) / d + 2) }
            else { h = 60 * ((r - g) / d + 4) }
            if h < 0 { h += 360 }
        }
        return (h, mx == 0 ? 0 : d / mx, mx)
    }

    public static func fromHSV(h: Double, s: Double, v: Double) -> Int32 {
        let hh = (h.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360) / 60
        let i = Int(hh.rounded(.down))
        let f = hh - Double(i)
        let p = v * (1 - s), q = v * (1 - s * f), t = v * (1 - s * (1 - f))
        switch i {
        case 0: return argb(r: v, g: t, b: p)
        case 1: return argb(r: q, g: v, b: p)
        case 2: return argb(r: p, g: v, b: t)
        case 3: return argb(r: p, g: q, b: v)
        case 4: return argb(r: t, g: p, b: v)
        default: return argb(r: v, g: p, b: q)
        }
    }

    /// Hue rotated by 180 degrees, keeping saturation and value; the default notification color.
    public static func complementary(_ argb: Int32) -> Int32 {
        var (h, s, v) = toHSV(argb)
        h = (h + 180).truncatingRemainder(dividingBy: 360)
        // A grey accent has no hue to flip; give it a usable tint instead.
        if s < 0.08 { h = 30; s = 0.85; v = 0.95 }
        return fromHSV(h: h, s: s, v: v)
    }

    /// Same hue at half saturation and reduced brightness: the "already happened" look.
    public static func faded(_ argb: Int32) -> Int32 {
        let (h, s, v) = toHSV(argb)
        return fromHSV(h: h, s: s * 0.5, v: max(v * 0.6, 0.35))
    }

    /// Relative luminance (sRGB), as Compose's Color.luminance().
    public static func luminance(_ argb: Int32) -> Double {
        func lin(_ c: Double) -> Double { c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4) }
        let (r, g, b) = rgb(argb)
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    /// Linear blend of two colours, t = 0 gives a.
    public static func mix(_ a: Int32, _ b: Int32, _ t: Double) -> Int32 {
        let (ar, ag, ab) = rgb(a), (br, bg, bb) = rgb(b)
        return argb(r: ar + (br - ar) * t, g: ag + (bg - ag) * t, b: ab + (bb - ab) * t)
    }

    public static let white: Int32 = Int32(bitPattern: 0xFFFFFFFF)
    public static let black: Int32 = Int32(bitPattern: 0xFF000000)
}
