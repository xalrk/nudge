import XCTest
@testable import NudgeCore

final class SettingsAndColorTests: XCTestCase {
    func testHexParsing() {
        XCTAssertEqual(SettingsMath.parseHex("#3D5AFE"), Int32(bitPattern: 0xFF3D5AFE))
        XCTAssertEqual(SettingsMath.parseHex("3d5afe"), Int32(bitPattern: 0xFF3D5AFE))
        XCTAssertEqual(SettingsMath.parseHex("#F00"), Int32(bitPattern: 0xFFFF0000))
        XCTAssertNil(SettingsMath.parseHex("#12345"))
        XCTAssertNil(SettingsMath.parseHex("#GGGGGG"))
        XCTAssertNil(SettingsMath.parseHex(""))
        XCTAssertEqual(SettingsMath.toHex(Int32(bitPattern: 0xFF3D5AFE)), "#3D5AFE")
        XCTAssertEqual(SettingsMath.toHex(Int32(bitPattern: 0xFF000A0B)), "#000A0B")
        XCTAssertEqual(SettingsMath.defaultAccent, SettingsMath.accentPresets[0].argb)
    }

    func testSliderRoundTrip() {
        for ms in [SettingsMath.minMeanMillis, SettingsMath.defaultMeanMillis, SettingsMath.maxMeanMillis] {
            let back = SettingsMath.sliderToMillis(SettingsMath.millisToSlider(ms))
            XCTAssertTrue(abs(back - ms) < ms / 100, "\(ms) -> \(back)")
        }
    }

    func testDescribeInterval() {
        XCTAssertEqual(SettingsMath.describeInterval(SettingsMath.hourMillis), "about once an hour")
        XCTAssertEqual(SettingsMath.describeInterval(SettingsMath.defaultMeanMillis), "about once every 2 weeks")
        XCTAssertEqual(SettingsMath.describeInterval(SettingsMath.dayMillis), "about once every 24 hours")
        XCTAssertEqual(SettingsMath.describeInterval(SettingsMath.hourMillis * 5), "about once every 5 hours")
        XCTAssertEqual(SettingsMath.describeInterval(SettingsMath.dayMillis * 3 + SettingsMath.hourMillis * 12), "about once every 3.5 days")
        XCTAssertEqual(SettingsMath.describeInterval(SettingsMath.maxMeanMillis), "about once every 6 months")
        XCTAssertEqual(SettingsMath.hourLabel(0), "midnight"); XCTAssertEqual(SettingsMath.hourLabel(12), "noon")
        XCTAssertEqual(SettingsMath.hourLabel(7), "7 am"); XCTAssertEqual(SettingsMath.hourLabel(23), "11 pm")
    }

    func testColors() {
        let blue = Int32(bitPattern: 0xFF008DCA)
        let (h, s, v) = Colors.toHSV(blue)
        XCTAssertEqual(h, 198, accuracy: 1); XCTAssertEqual(s, 1, accuracy: 0.01); XCTAssertEqual(v, 0.792, accuracy: 0.01)
        XCTAssertEqual(Colors.fromHSV(h: h, s: s, v: v), blue)
        // Complement of Nudge blue is an orange (hue ~18).
        let comp = Colors.toHSV(Colors.complementary(blue))
        XCTAssertEqual(comp.h, 18, accuracy: 1)
        // Grey gets a tint instead of staying grey.
        XCTAssertTrue(Colors.toHSV(Colors.complementary(Int32(bitPattern: 0xFF212121))).s > 0.8)
        XCTAssertEqual(Colors.toHex(Colors.faded(blue)).count, 7)
        XCTAssertTrue(Colors.luminance(Colors.white) > 0.99 && Colors.luminance(Colors.black) < 0.01)
        XCTAssertEqual(Colors.mix(Colors.black, Colors.white, 0.5), Int32(bitPattern: 0xFF808080))
    }
}

extension Colors { static func toHex(_ c: Int32) -> String { SettingsMath.toHex(c) } }
