/**
 * Exact decimal with Java `BigDecimal` semantics, which also match Python's `Decimal` for the
 * values reports contain: an unscaled integer and an exponent, so "10.00" keeps its two decimal
 * places. Equality is structural, like `BigDecimal.equals` ("2.0" != "2.00"). Foundation's
 * `Decimal` drops trailing zeros and has 38 digits, so it cannot reproduce stored values and
 * source keys.
 */
struct ExactDecimal: Hashable, CustomStringConvertible {
    /// Never true for zero.
    let negative: Bool
    /// Unscaled magnitude, most significant digit first, without leading zeros; [0] for zero.
    let digits: [UInt8]
    /// value = unscaled × 10^exponent (exponent = -scale).
    let exponent: Int

    static let zero = ExactDecimal(negative: false, digits: [0], exponent: 0)

    /// Python's default `Decimal` context: 28 significant digits.
    static let pythonPrecision = 28

    init(negative: Bool, digits: [UInt8], exponent: Int) {
        let magnitude = Natural.trimmed(digits)
        self.negative = negative && magnitude != [0]
        self.digits = magnitude
        self.exponent = exponent
    }

    /// Parses `BigDecimal(String)` syntax: sign, digits with an optional point, optional exponent.
    init?(_ text: String) {
        var scalars = Substring(text).unicodeScalars[...]
        var negative = false
        if let sign = scalars.first, sign == "-" || sign == "+" {
            negative = sign == "-"
            scalars = scalars.dropFirst()
        }
        var digits: [UInt8] = []
        var fraction = 0
        var seenPoint = false
        var exponent = 0
        while let scalar = scalars.first {
            if let digit = Self.digit(scalar) {
                digits.append(digit)
                if seenPoint { fraction += 1 }
            } else if scalar == "." && !seenPoint {
                seenPoint = true
            } else if scalar == "e" || scalar == "E" {
                guard let value = Int(String(scalars.dropFirst())), !scalars.dropFirst().isEmpty else { return nil }
                exponent = value
                scalars = scalars[scalars.endIndex...]
                break
            } else {
                return nil
            }
            scalars = scalars.dropFirst()
        }
        guard !digits.isEmpty else { return nil }
        self.init(negative: negative, digits: digits, exponent: exponent - fraction)
    }

    static let one = ExactDecimal(negative: false, digits: [1], exponent: 0)

    var signum: Int { digits == [0] ? 0 : (negative ? -1 : 1) }

    var magnitude: ExactDecimal { ExactDecimal(negative: false, digits: digits, exponent: exponent) }

    var negated: ExactDecimal { ExactDecimal(negative: !negative, digits: digits, exponent: exponent) }

    /// `BigDecimal.movePointLeft(n)`: value × 10^-n, with a scale of at least zero.
    func movedPointLeft(_ n: Int) -> ExactDecimal {
        let shifted = exponent - n
        if shifted <= 0 { return ExactDecimal(negative: negative, digits: digits, exponent: shifted) }
        return ExactDecimal(negative: negative, digits: digits + Array(repeating: 0, count: shifted), exponent: 0)
    }

    /// Exact product (`BigDecimal.multiply` without a context).
    static func * (lhs: ExactDecimal, rhs: ExactDecimal) -> ExactDecimal {
        ExactDecimal(negative: lhs.negative != rhs.negative, digits: Natural.multiply(lhs.digits, rhs.digits), exponent: lhs.exponent + rhs.exponent)
    }

    /// `multiply(other, MathContext(precision, HALF_EVEN))`.
    func multiplied(by other: ExactDecimal, precision: Int = pythonPrecision) -> ExactDecimal {
        (self * other).rounded(precision)
    }

    /// `add(other, MathContext(precision, HALF_EVEN))`: the exact sum at the smaller exponent, then rounded.
    func adding(_ other: ExactDecimal, precision: Int = pythonPrecision) -> ExactDecimal {
        let exponent = min(self.exponent, other.exponent)
        let a = Natural.trimmed(digits + Array(repeating: 0, count: self.exponent - exponent))
        let b = Natural.trimmed(other.digits + Array(repeating: 0, count: other.exponent - exponent))
        let sum: ExactDecimal
        if negative == other.negative {
            sum = ExactDecimal(negative: negative, digits: Natural.add(a, b), exponent: exponent)
        } else if Natural.compare(a, b) >= 0 {
            sum = ExactDecimal(negative: negative, digits: Natural.subtract(a, b), exponent: exponent)
        } else {
            sum = ExactDecimal(negative: other.negative, digits: Natural.subtract(b, a), exponent: exponent)
        }
        return sum.rounded(precision)
    }

    /// `subtract(other, MathContext(precision, HALF_EVEN))`.
    func subtracting(_ other: ExactDecimal, precision: Int = pythonPrecision) -> ExactDecimal {
        adding(other.negated, precision: precision)
    }

    /// Numeric comparison (`BigDecimal.compareTo`): -1, 0 or 1, ignoring scale.
    func compare(_ other: ExactDecimal) -> Int {
        subtracting(other, precision: Int.max).signum
    }

    /// `setScale(scale, HALF_EVEN)`: exactly `scale` decimal places, e.g. 2 for cents.
    func withScale(_ scale: Int) -> ExactDecimal {
        let target = -scale
        if exponent >= target { return ExactDecimal(negative: negative, digits: digits + Array(repeating: 0, count: exponent - target), exponent: target) }
        let keep = digits.count - (target - exponent)
        if keep < 0 { return ExactDecimal(negative: false, digits: [0], exponent: target) }
        let kept = keep == 0 ? [0] : Array(digits[..<keep])
        let dropped = digits[keep...]
        var magnitude = kept
        let first = dropped.first!
        let restZero = dropped.dropFirst().allSatisfy { $0 == 0 }
        if first > 5 || (first == 5 && (!restZero || kept.last! % 2 == 1)) { magnitude = Natural.add(kept, [1]) }
        return ExactDecimal(negative: negative, digits: magnitude, exponent: target)
    }

    /// Rounds half-even to `precision` significant digits; shorter values are unchanged.
    func rounded(_ precision: Int) -> ExactDecimal {
        guard digits.count > precision else { return self }
        var kept = Array(digits[..<precision])
        let dropped = digits[precision...]
        var exponent = self.exponent + dropped.count
        let first = dropped.first!
        let restZero = dropped.dropFirst().allSatisfy { $0 == 0 }
        if first > 5 || (first == 5 && (!restZero || kept.last! % 2 == 1)) {
            kept = Natural.add(kept, [1])
            if kept.count > precision {
                kept.removeLast()
                exponent += 1
            }
        }
        return ExactDecimal(negative: negative, digits: kept, exponent: exponent)
    }

    /**
     * `BigDecimal.divide(divisor, MathContext(precision, HALF_EVEN))`, which is also Python's
     * `Decimal` division: rounded to `precision` significant digits, and an exact quotient loses
     * trailing zeros down to the preferred exponent. Returns nil when dividing by zero.
     */
    func divided(by divisor: ExactDecimal, precision: Int = pythonPrecision) -> ExactDecimal? {
        guard divisor.signum != 0 else { return nil }
        let preferred = exponent - divisor.exponent
        let negative = self.negative != divisor.negative
        guard signum != 0 else { return ExactDecimal(negative: false, digits: [0], exponent: preferred) }

        // Scale the dividend so the integer quotient has more than `precision` digits.
        let shift = max(0, precision + divisor.digits.count - digits.count + 1)
        var (quotient, remainder) = Natural.divide(digits + Array(repeating: 0, count: shift), by: divisor.digits)
        var resultExponent = preferred - shift
        var exact = remainder == [0]
        if quotient.count > precision {
            let dropped = Array(quotient[precision...])
            quotient = Array(quotient[..<precision])
            resultExponent += dropped.count
            let rest = dropped.dropFirst()
            let restZero = rest.allSatisfy { $0 == 0 } && exact
            exact = exact && dropped.allSatisfy { $0 == 0 }
            let roundUp = dropped[0] > 5 || (dropped[0] == 5 && (!restZero || quotient.last! % 2 == 1))
            if roundUp {
                quotient = Natural.add(quotient, [1])
                if quotient.count > precision {
                    quotient.removeLast()
                    resultExponent += 1
                }
            }
        }
        if exact {
            while resultExponent < preferred, quotient.count > 1, quotient.last == 0 {
                quotient.removeLast()
                resultExponent += 1
            }
        }
        return ExactDecimal(negative: negative, digits: quotient, exponent: resultExponent)
    }

    /// `BigDecimal.toString()`, the same scientific-string rules as Python's `str(Decimal)`.
    var description: String {
        let coefficient = String(digits.map { Character(String($0)) })
        let adjusted = exponent + coefficient.count - 1
        var text: String
        if exponent <= 0 && adjusted >= -6 {
            let point = coefficient.count + exponent
            if exponent == 0 {
                text = coefficient
            } else if point > 0 {
                text = String(coefficient.prefix(point)) + "." + String(coefficient.dropFirst(point))
            } else {
                text = "0." + String(repeating: "0", count: -point) + coefficient
            }
        } else {
            text = String(coefficient.prefix(1))
            if coefficient.count > 1 { text += "." + String(coefficient.dropFirst()) }
            text += "E" + (adjusted >= 0 ? "+" : "") + String(adjusted)
        }
        return negative ? "-" + text : text
    }

    private static func digit(_ scalar: Unicode.Scalar) -> UInt8? {
        ("0"..."9").contains(scalar) ? UInt8(scalar.value - 48) : nil
    }
}

/// Non-negative integers as decimal digit arrays, most significant first.
private enum Natural {
    static func trimmed(_ digits: [UInt8]) -> [UInt8] {
        let start = digits.firstIndex { $0 != 0 } ?? max(digits.count - 1, 0)
        return digits.isEmpty ? [0] : Array(digits[start...])
    }

    static func compare(_ a: [UInt8], _ b: [UInt8]) -> Int {
        if a.count != b.count { return a.count < b.count ? -1 : 1 }
        for (x, y) in zip(a, b) where x != y { return x < y ? -1 : 1 }
        return 0
    }

    static func add(_ a: [UInt8], _ b: [UInt8]) -> [UInt8] {
        var result: [UInt8] = []
        var carry: UInt8 = 0
        var i = a.count - 1, j = b.count - 1
        while i >= 0 || j >= 0 || carry > 0 {
            let sum = (i >= 0 ? a[i] : 0) + (j >= 0 ? b[j] : 0) + carry
            result.append(sum % 10)
            carry = sum / 10
            i -= 1
            j -= 1
        }
        return trimmed(result.reversed())
    }

    /// a - b for a >= b.
    static func subtract(_ a: [UInt8], _ b: [UInt8]) -> [UInt8] {
        var result: [UInt8] = []
        var borrow = 0
        var j = b.count - 1
        for i in stride(from: a.count - 1, through: 0, by: -1) {
            var difference = Int(a[i]) - borrow - (j >= 0 ? Int(b[j]) : 0)
            borrow = difference < 0 ? 1 : 0
            if difference < 0 { difference += 10 }
            result.append(UInt8(difference))
            j -= 1
        }
        return trimmed(result.reversed())
    }

    static func multiply(_ a: [UInt8], _ b: [UInt8]) -> [UInt8] {
        var product = [Int](repeating: 0, count: a.count + b.count)
        for (i, x) in a.reversed().enumerated() {
            for (j, y) in b.reversed().enumerated() { product[i + j] += Int(x) * Int(y) }
        }
        for k in 0..<(product.count - 1) {
            product[k + 1] += product[k] / 10
            product[k] %= 10
        }
        return trimmed(product.reversed().map { UInt8($0) })
    }

    /// Long division; the divisor must not be zero.
    static func divide(_ dividend: [UInt8], by divisor: [UInt8]) -> (quotient: [UInt8], remainder: [UInt8]) {
        var quotient: [UInt8] = []
        var remainder: [UInt8] = [0]
        for digit in dividend {
            remainder = trimmed(remainder + [digit])
            var count: UInt8 = 0
            while compare(remainder, divisor) >= 0 {
                remainder = subtract(remainder, divisor)
                count += 1
            }
            quotient.append(count)
        }
        return (trimmed(quotient), remainder)
    }
}
