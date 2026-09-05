// swift-tools-version:5.9
import PackageDescription

// Pure logic shared by the iOS app: dates, recurrence, random scheduling, CSV import/export,
// settings maths and the notification planner. No UIKit, so it builds and tests on Linux too.
let package = Package(
    name: "NudgeCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "NudgeCore", targets: ["NudgeCore"])],
    targets: [
        .target(name: "NudgeCore"),
        .testTarget(name: "NudgeCoreTests", dependencies: ["NudgeCore"]),
    ]
)
