import SwiftUI

/// A wireframe die rendered as a real cube. rotX/rotY are in degrees; at the angles from
/// `anglesFor` a single face is flat toward the viewer, which is how a roll ends.
struct Die3D: View {
    @Environment(\.theme) private var theme
    let rotX: Double
    let rotY: Double
    var line: Color? = nil

    var body: some View {
        Canvas { ctx, size in
            drawDie(&ctx, size, rotX, rotY, line ?? theme.onSurface, theme.background)
        }
        .accessibilityLabel("Re-roll times")
    }
}

/// Rotation (x, y) in degrees that presents `face` (1..6) squarely to the viewer.
func anglesFor(_ face: Int) -> (Double, Double) {
    switch max(1, min(6, face)) {
    case 1: return (0, 0)
    case 2: return (0, -90)
    case 3: return (90, 0)
    case 4: return (-90, 0)
    case 5: return (0, 90)
    default: return (0, 180)
    }
}

private struct V3 { var x: Double; var y: Double; var z: Double }

private func rotate(_ v: V3, _ ax: Double, _ ay: Double) -> V3 {
    let cx = cos(ax), sx = sin(ax)
    let y1 = v.y * cx - v.z * sx
    let z1 = v.y * sx + v.z * cx
    let cy = cos(ay), sy = sin(ay)
    let x2 = v.x * cy + z1 * sy
    let z2 = -v.x * sy + z1 * cy
    return V3(x: x2, y: y1, z: z2)
}

/// Faces as (normal, u axis, v axis, pip count). Opposite faces sum to seven.
private let faces: [(V3, V3, V3, Int)] = [
    (V3(x: 0, y: 0, z: 1), V3(x: 1, y: 0, z: 0), V3(x: 0, y: 1, z: 0), 1),
    (V3(x: 1, y: 0, z: 0), V3(x: 0, y: 0, z: -1), V3(x: 0, y: 1, z: 0), 2),
    (V3(x: 0, y: 1, z: 0), V3(x: 1, y: 0, z: 0), V3(x: 0, y: 0, z: -1), 3),
    (V3(x: 0, y: -1, z: 0), V3(x: 1, y: 0, z: 0), V3(x: 0, y: 0, z: 1), 4),
    (V3(x: -1, y: 0, z: 0), V3(x: 0, y: 0, z: 1), V3(x: 0, y: 1, z: 0), 5),
    (V3(x: 0, y: 0, z: -1), V3(x: -1, y: 0, z: 0), V3(x: 0, y: 1, z: 0), 6),
]

private func pips(_ n: Int) -> [(Double, Double)] {
    let o = 0.5
    switch n {
    case 1: return [(0, 0)]
    case 2: return [(-o, -o), (o, o)]
    case 3: return [(-o, -o), (0, 0), (o, o)]
    case 4: return [(-o, -o), (o, -o), (-o, o), (o, o)]
    case 5: return [(-o, -o), (o, -o), (0, 0), (-o, o), (o, o)]
    default: return [(-o, -o), (o, -o), (-o, 0), (o, 0), (-o, o), (o, o)]
    }
}

private func drawDie(_ ctx: inout GraphicsContext, _ size: CGSize, _ rotXDeg: Double, _ rotYDeg: Double, _ line: Color, _ fill: Color) {
    let ax = rotXDeg * .pi / 180
    let ay = rotYDeg * .pi / 180
    let minDim = min(size.width, size.height)
    let half = minDim * 0.30
    let camera = minDim * 2.2
    let center = CGPoint(x: size.width / 2, y: size.height / 2)
    func project(_ v: V3) -> CGPoint {
        let r = rotate(V3(x: v.x * half, y: v.y * half, z: v.z * half), ax, ay)
        let s = camera / (camera - r.z)
        return CGPoint(x: center.x + r.x * s, y: center.y - r.y * s)
    }
    let stroke = minDim * 0.075
    let visible = faces.map { ($0, rotate($0.0, ax, ay).z) }.filter { $0.1 > 0.02 }.sorted { $0.1 < $1.1 }
    for (face, _) in visible {
        let (n, u, v, count) = face
        func corner(_ su: Double, _ sv: Double) -> CGPoint {
            project(V3(x: n.x + u.x * su + v.x * sv, y: n.y + u.y * su + v.y * sv, z: n.z + u.z * su + v.z * sv))
        }
        var path = Path()
        path.move(to: corner(-1, -1)); path.addLine(to: corner(1, -1)); path.addLine(to: corner(1, 1)); path.addLine(to: corner(-1, 1)); path.closeSubpath()
        ctx.fill(path, with: .color(fill))
        ctx.stroke(path, with: .color(line), style: StrokeStyle(lineWidth: stroke, lineJoin: .round))
        let facing = min(max(rotate(n, ax, ay).z, 0), 1)
        for (pu, pv) in pips(count) {
            let p = project(V3(x: n.x + u.x * pu + v.x * pv, y: n.y + u.y * pu + v.y * pv, z: n.z + u.z * pu + v.z * pv))
            let r = minDim * 0.07 * (0.6 + 0.4 * facing)
            ctx.fill(Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r)), with: .color(line))
        }
    }
}
