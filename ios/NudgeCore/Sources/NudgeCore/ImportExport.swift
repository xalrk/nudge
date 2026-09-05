import Foundation

public struct ParseResult: Equatable {
    public var reminders: [Reminder]
    public var errors: [String]
    public init(reminders: [Reminder], errors: [String]) { self.reminders = reminders; self.errors = errors }
}

struct ImportError: Error, CustomStringConvertible {
    let description: String
    init(_ d: String) { description = d }
}

/// Import/export format: CSV with one reminder per row.
///
///     title,details,date,time,repeat,every,weekdays,until,zone,follow_device_zone
///     Drink some water,,,,,,,,,
///     Call mom,she is home on Sundays,2026-09-14,18:00,weekly,1,sun,,,
///     Standup,,2026-09-07,09:30,weekly,1,mon;tue;wed;thu;fri,2026-12-19,,
///     Flight,,2026-10-20,06:30,,,,,America/Denver,no
///
/// - The header row is optional; without it the columns are read in the order above.
///   With it, columns may appear in any order and unknown columns are ignored.
/// - Only `title` is required. A row with no date and no time is a random reminder.
/// - Weekdays are separated by ";" or spaces (commas work too if the cell is quoted).
///
/// JSON in the same shape ({"reminders": [{"title": ..., "at": "2026-09-10T14:30", ...}]})
/// is still accepted for people who prefer it. Byte-for-byte the same rules as the Android app.
public enum ImportExport {
    public static let csvColumns = ["title", "details", "date", "time", "repeat", "every", "weekdays", "until", "zone", "follow_device_zone"]

    private static let columnAliases: [String: String] = [
        "title": "title", "message": "title", "text": "title", "name": "title", "reminder": "title",
        "details": "details", "body": "details", "description": "details", "notes": "details", "note": "details",
        "date": "date", "day": "date", "start": "date", "start_date": "date",
        "time": "time", "at": "time", "start_time": "time",
        "repeat": "repeat", "recurrence": "repeat", "frequency": "repeat",
        "every": "every", "interval": "every",
        "weekdays": "weekdays", "days": "weekdays", "on": "weekdays",
        "until": "until", "end": "until", "end_date": "until", "ends": "until",
        "zone": "zone", "timezone": "zone", "time_zone": "zone", "tz": "zone",
        "follow_device_zone": "follow_device_zone", "floating": "follow_device_zone", "follow_device_timezone": "follow_device_zone",
    ]

    private static let bom = "\u{FEFF}"

    public static func parse(_ content: String, now: LocalDateTime = Date().toLocal(in: .device), zone: TimeZone = .device) -> ParseResult {
        var trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix(bom) { trimmed.removeFirst() }
        return (trimmed.hasPrefix("[") || trimmed.hasPrefix("{")) ? parseJson(trimmed, zone: zone) : parseCsv(trimmed, now: now, zone: zone)
    }

    // ----------------------------------------------------------------- csv

    public static func parseCsv(_ content0: String, now: LocalDateTime = Date().toLocal(in: .device), zone: TimeZone = .device) -> ParseResult {
        var content = content0
        if content.hasPrefix(bom) { content.removeFirst() }
        let rows = readCsv(content)
        var out: [Reminder] = []
        var errors: [String] = []
        if rows.isEmpty { return ParseResult(reminders: out, errors: errors) }

        let first = rows[0]
        let hasHeader = (!first.isEmpty && columnAliases[normHeader(first[0])] == "title" && first.count > 1) ||
            (!first.isEmpty && first.allSatisfy { columnAliases[normHeader($0)] != nil || $0.isBlank } && first.contains { !$0.isBlank } && first.count > 1)
        let columns: [String?] = hasHeader ? first.map { columnAliases[normHeader($0)] } : csvColumns

        for (i, cells) in rows.dropFirst(hasHeader ? 1 : 0).enumerated() {
            let lineNo = i + 1 + (hasHeader ? 1 : 0)
            if cells.allSatisfy({ $0.isBlank }) { continue }
            if let f = cells.first, f.drop(while: { $0.isWhitespace }).hasPrefix("#") { continue }
            var field: [String: String] = [:]
            for (c, name) in columns.enumerated() {
                if let name = name, c < cells.count { field[name] = cells[c].trimmingCharacters(in: .whitespaces) }
            }
            do { out.append(try fromFields(field, now: now, zone: zone)) }
            catch { errors.append("Row \(lineNo): \(String(describing: error))") }
        }
        return ParseResult(reminders: out, errors: errors)
    }

    private static func normHeader(_ s: String) -> String {
        var t = s.trimmingCharacters(in: .whitespaces).lowercased()
        // Runs of whitespace or dashes become a single underscore.
        var outS = ""
        var inRun = false
        for ch in t {
            if ch.isWhitespace || ch == "-" {
                if !inRun { outS.append("_"); inRun = true }
            } else { outS.append(ch); inRun = false }
        }
        t = outS
        if t.hasPrefix(bom) { t.removeFirst() }
        return t
    }

    /// Minimal RFC 4180 reader: quoted cells, doubled quotes, newlines inside quotes. Auto-detects ";" delimiters.
    /// Works on unicode scalars because Swift treats "\r\n" as a single Character.
    public static func readCsv(_ text: String) -> [[String]] {
        let scalars = Array(text.unicodeScalars)
        var firstLine = ""
        for u in scalars { if u == "\n" { break }; firstLine.unicodeScalars.append(u) }
        let delim: Unicode.Scalar = (!firstLine.contains(",") && firstLine.contains(";")) ? ";" : ","
        var rows: [[String]] = []
        var row: [String] = []
        var cell = ""
        var inQuotes = false
        var i = 0
        while i < scalars.count {
            let ch = scalars[i]
            if inQuotes {
                if ch == "\"" {
                    if i + 1 < scalars.count && scalars[i + 1] == "\"" { cell.append("\""); i += 1 } else { inQuotes = false }
                } else { cell.unicodeScalars.append(ch) }
            } else if ch == "\"" { inQuotes = true }
            else if ch == delim { row.append(cell); cell = "" }
            else if ch == "\r" { }
            else if ch == "\n" { row.append(cell); cell = ""; rows.append(row); row = [] }
            else { cell.unicodeScalars.append(ch) }
            i += 1
        }
        if !cell.isEmpty || !row.isEmpty { row.append(cell); rows.append(row) }
        return rows
    }

    private static func fromFields(_ f: [String: String], now: LocalDateTime, zone: TimeZone) throws -> Reminder {
        let title = (f["title"] ?? "").trimmingCharacters(in: .whitespaces)
        guard !title.isEmpty else { throw ImportError("missing title") }
        let body = (f["details"] ?? "").trimmingCharacters(in: .whitespaces)
        let dateStr = f["date"] ?? ""
        let timeStr = f["time"] ?? ""
        if dateStr.isBlank && timeStr.isBlank {
            guard (f["repeat"] ?? "").isBlank else { throw ImportError("a repeating reminder needs a date or time") }
            return Reminder(title: title, body: body, kind: .random).withDedupeKey()
        }

        let date = dateStr.isBlank ? nil : try parseDate(dateStr)
        let time = timeStr.isBlank ? LocalTime(9, 0) : try parseTime(timeStr)
        let (rep, shorthandDays) = try parseRepeat(f["repeat"] ?? "")
        var weekdays = shorthandDays
        if let s = f["weekdays"], !s.isBlank {
            let parts = s.split(whereSeparator: { ";,/|".contains($0) || $0.isWhitespace }).map(String.init).filter { !$0.isBlank }
            var days: [DayOfWeek] = []
            for p in parts {
                guard let d = parseDay(p) else { throw ImportError("unknown weekday \"\(p)\"") }
                days.append(d)
            }
            weekdays = Reminder.maskOf(days)
        }
        var every = 1
        if let e = f["every"], !e.isBlank {
            guard let n = Int(e.trimmingCharacters(in: .whitespaces)) else { throw ImportError("\"every\" must be a number") }
            every = n
        }
        let until: LocalDate? = (f["until"] ?? "").isBlank ? nil : try parseDate(f["until"]!)
        var tz = zone
        if let z = f["zone"], !z.isBlank {
            guard let t = TimeZone(identifier: z.trimmingCharacters(in: .whitespaces)) else { throw ImportError("unknown zone \"\(z)\"") }
            tz = t
        }
        let floating = (f["follow_device_zone"] ?? "").isBlank ? true : try parseBool(f["follow_device_zone"]!)

        let effectiveRepeat: Repeat = (rep == .none && weekdays != 0) ? .weekly : rep
        var start = (date ?? now.date).atTime(time)
        if date == nil {
            if !start.isAfter(now) { start = start.plusDays(1) }
            if effectiveRepeat == .weekly && weekdays != 0 {
                let set = DayOfWeek.set(fromMask: weekdays)
                var guardCount = 0
                while !set.contains(start.dayOfWeek) && guardCount < 7 { start = start.plusDays(1); guardCount += 1 }
            }
        }
        return Reminder(
            title: title, body: body, kind: .scheduled,
            localDateTime: start.description, zoneId: tz.identifier, floating: floating,
            repeatRule: effectiveRepeat, interval: max(every, 1),
            weekdays: effectiveRepeat == .weekly ? weekdays : 0,
            endDate: until?.description
        ).withDedupeKey()
    }

    private static let monthNames = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"]

    static func parseDate(_ s: String) throws -> LocalDate {
        let t = s.trimmingCharacters(in: .whitespaces)
        if let d = LocalDate(iso: t) { return d }
        func numeric(_ parts: [String], _ order: (Int, Int, Int)) -> LocalDate? {
            guard parts.count == 3, let a = Int(parts[order.0]), let b = Int(parts[order.1]), let c = Int(parts[order.2]) else { return nil }
            return LocalDate.isValid(a, b, c) ? LocalDate(a, b, c) : nil
        }
        // yyyy/MM/dd, yyyy.MM.dd (two-digit month and day), then d/M/yyyy, M/d/yyyy, d.M.yyyy.
        for sep in ["/", "."] {
            let p = t.split(separator: Character(sep), omittingEmptySubsequences: false).map(String.init)
            if p.count == 3 {
                if p[0].count == 4 && p[1].count == 2 && p[2].count == 2, let d = numeric(p, (0, 1, 2)) { return d }
                if p[2].count == 4 {
                    if let d = numeric(p, (2, 1, 0)) { return d }          // d/M/yyyy
                    if sep == "/", let d = numeric(p, (2, 0, 1)) { return d } // M/d/yyyy
                }
            }
        }
        // "MMM d yyyy" and "d MMM yyyy" with English month abbreviations.
        let words = t.split(whereSeparator: { $0.isWhitespace }).map(String.init)
        if words.count == 3 {
            func month(_ w: String) -> Int? { monthNames.firstIndex(of: String(w.lowercased().prefix(3))).map { $0 + 1 } }
            if let m = month(words[0]), let d = Int(words[1]), let y = Int(words[2]), words[2].count == 4, LocalDate.isValid(y, m, d) { return LocalDate(y, m, d) }
            if let d = Int(words[0]), let m = month(words[1]), let y = Int(words[2]), words[2].count == 4, LocalDate.isValid(y, m, d) { return LocalDate(y, m, d) }
        }
        throw ImportError("bad date \"\(s)\" (use YYYY-MM-DD)")
    }

    static func parseTime(_ s: String) throws -> LocalTime {
        var t = s.trimmingCharacters(in: .whitespaces).lowercased()
        var ap = ""
        for suffix in ["am", "pm", "a", "p"] where t.hasSuffix(suffix) {
            ap = String(suffix.prefix(1))
            t = String(t.dropLast(suffix.count)).trimmingCharacters(in: .whitespaces)
            break
        }
        let parts = t.split(separator: ":", omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 1 || parts.count == 2, (1...2).contains(parts[0].count), parts[0].allSatisfy({ $0.isNumber }),
              var h = Int(parts[0]) else { throw ImportError("bad time \"\(s)\" (use HH:MM)") }
        var min = 0
        if parts.count == 2 {
            guard parts[1].count == 2, parts[1].allSatisfy({ $0.isNumber }), let m = Int(parts[1]) else { throw ImportError("bad time \"\(s)\" (use HH:MM)") }
            min = m
        }
        if ap == "p" && h < 12 { h += 12 }
        if ap == "a" && h == 12 { h = 0 }
        guard (0...23).contains(h), (0...59).contains(min) else { throw ImportError("bad time \"\(s)\"") }
        return LocalTime(h, min)
    }

    /// Returns repeat + a weekday mask for shorthand values such as "weekdays".
    static func parseRepeat(_ s: String) throws -> (Repeat, Int) {
        switch s.trimmingCharacters(in: .whitespaces).lowercased() {
        case "", "none", "no", "once", "never", "0": return (.none, 0)
        case "daily", "day", "days", "every day": return (.daily, 0)
        case "weekly", "week", "weeks", "every week": return (.weekly, 0)
        case "weekday", "weekdays": return (.weekly, Reminder.maskOf(DayOfWeek.allCases.filter { $0.rawValue <= 5 }))
        case "weekend", "weekends": return (.weekly, Reminder.maskOf([DayOfWeek.saturday, .sunday]))
        case "monthly", "month", "months", "every month": return (.monthly, 0)
        case "yearly", "year", "years", "annually", "annual", "every year": return (.yearly, 0)
        default: throw ImportError("unknown repeat \"\(s)\" (use daily, weekly, monthly, yearly)")
        }
    }

    private static func parseBool(_ s: String) throws -> Bool {
        switch s.trimmingCharacters(in: .whitespaces).lowercased() {
        case "yes", "y", "true", "1", "on": return true
        case "no", "n", "false", "0", "off": return false
        default: throw ImportError("\"follow_device_zone\" must be yes or no")
        }
    }

    static func parseDay(_ s: String) -> DayOfWeek? {
        let k = s.trimmingCharacters(in: .whitespaces).lowercased()
        if k.count < 2 { return nil }
        return DayOfWeek.allCases.first { d in
            let n = d.name.lowercased()
            return n.hasPrefix(k) || k.hasPrefix(String(n.prefix(3)))
        }
    }

    public static func toCsv(_ reminders: [Reminder]) -> String {
        var s = csvColumns.joined(separator: ",") + "\r\n"
        for r in reminders {
            let ldt = r.localDateTimeOrNil()
            let repeating = r.isScheduled && r.repeatRule != .none
            let cells = [
                r.title,
                r.body,
                ldt.map { $0.date.description } ?? "",
                ldt.map { $0.time.hhmm } ?? "",
                repeating ? r.repeatRule.rawValue.lowercased() : "",
                repeating ? String(r.interval) : "",
                (r.isScheduled && r.repeatRule == .weekly && r.weekdays != 0) ? r.weekdaySet().sorted().map { $0.name.prefix(3).lowercased() }.joined(separator: ";") : "",
                r.endDate ?? "",
                r.isScheduled ? (r.zoneId ?? "") : "",
                r.isScheduled ? (r.floating ? "yes" : "no") : "",
            ]
            s += cells.map(csvCell).joined(separator: ",") + "\r\n"
        }
        return s
    }

    private static func csvCell(_ s: String) -> String {
        if s.contains(where: { $0 == "," || $0 == "\"" || $0 == "\n" || $0 == "\r" || $0 == ";" }) || s.hasPrefix(" ") || s.hasSuffix(" ") {
            return "\"" + s.replacingOccurrences(of: "\"", with: "\"\"") + "\""
        }
        return s
    }

    // ---------------------------------------------------------------- json

    public static func parseJson(_ content: String, zone: TimeZone = .device) -> ParseResult {
        var out: [Reminder] = []
        var errors: [String] = []
        let arr: [Any]
        do {
            let obj = try JSONSerialization.jsonObject(with: Data(content.utf8))
            if let a = obj as? [Any] { arr = a }
            else if let o = obj as? [String: Any] { arr = (o["reminders"] as? [Any]) ?? [] }
            else { arr = [] }
        } catch {
            return ParseResult(reminders: [], errors: ["Invalid JSON: \(error.localizedDescription)"])
        }
        for (i, item) in arr.enumerated() {
            do {
                guard let o = item as? [String: Any] else { throw ImportError("not an object") }
                out.append(try fromJson(o, zone: zone))
            } catch { errors.append("Item \(i + 1): \(String(describing: error))") }
        }
        return ParseResult(reminders: out, errors: errors)
    }

    private static func optString(_ o: [String: Any], _ key: String, _ def: String = "") -> String {
        guard let v = o[key] else { return def }
        if v is NSNull { return def }
        if let s = v as? String { return s }
        if let b = v as? Bool { return b ? "true" : "false" }
        return "\(v)"
    }
    private static func optBool(_ o: [String: Any], _ key: String, _ def: Bool) -> Bool {
        if let b = o[key] as? Bool { return b }
        if let s = o[key] as? String { if s.lowercased() == "true" { return true }; if s.lowercased() == "false" { return false } }
        return def
    }
    private static func optInt(_ o: [String: Any], _ key: String, _ def: Int) -> Int {
        if let n = o[key] as? Int { return n }
        if let d = o[key] as? Double { return Int(d) }
        if let s = o[key] as? String, let n = Int(s) { return n }
        return def
    }

    private static func fromJson(_ o: [String: Any], zone: TimeZone) throws -> Reminder {
        var title = optString(o, "title").trimmingCharacters(in: .whitespaces)
        if title.isEmpty { title = optString(o, "message").trimmingCharacters(in: .whitespaces) }
        guard !title.isEmpty else { throw ImportError("missing \"title\"") }
        let body = optString(o, "body", optString(o, "details", ""))
        let at = optString(o, "at", "").trimmingCharacters(in: .whitespaces)
        let enabled = optBool(o, "enabled", true)
        if at.isEmpty { return Reminder(title: title, body: body, kind: .random, enabled: enabled).withDedupeKey() }

        let ldt: LocalDateTime
        if let l = LocalDateTime(iso: at) { ldt = l }
        else if let d = LocalDate(iso: at) { ldt = d.atTime(9, 0) }
        else { throw ImportError("bad \"at\" value \"\(at)\" (use 2026-09-10T14:30)") }
        let (rep, shorthandDays) = try parseRepeat(optString(o, "repeat", "none"))
        var weekdays = shorthandDays
        if let a = o["weekdays"] as? [Any] {
            weekdays = Reminder.maskOf(a.compactMap { parseDay("\($0)") })
        }
        var zoneId = optString(o, "zone", "")
        if zoneId.isEmpty { zoneId = zone.identifier }
        guard TimeZone(identifier: zoneId) != nil else { throw ImportError("unknown zone \"\(zoneId)\"") }
        var until: String? = nil
        let u = optString(o, "until", "")
        if !u.isEmpty {
            guard let d = LocalDate(iso: u) else { throw ImportError("bad \"until\" value \"\(u)\"") }
            until = d.description
        }
        return Reminder(
            title: title, body: body, kind: .scheduled,
            localDateTime: ldt.description, zoneId: zoneId, floating: optBool(o, "floating", true),
            repeatRule: rep, interval: max(optInt(o, "interval", 1), 1), weekdays: weekdays,
            endDate: until, enabled: enabled
        ).withDedupeKey()
    }
}

public extension String {
    public var isBlank: Bool { allSatisfy { $0.isWhitespace } }
}
