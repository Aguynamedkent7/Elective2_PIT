package app.numlab.domain.format

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Shared number formatting. Mirrors JavaScript's `toFixed` / `toExponential` / `toPrecision`
 * and `Number#toString` exactly (round half up on the exact binary value, unpadded exponents),
 * because the reference implementation and golden data were produced in JS.
 */
object Fmt {
    /** `fmt` from math.js: `—` for null/NaN/∞, `0` for 0, scientific outside [1e-4, 1e7). */
    fun fmt(v: Double?, decimals: Int = 6): String {
        if (v == null || !v.isFinite()) return "—"
        if (v == 0.0) return "0"
        val a = abs(v)
        return if (a < 1e-4 || a >= 1e7) toExponential(v, 3) else toFixed(v, decimals)
    }

    /** `fmtE` from math.js: 3 significant digits, scientific. */
    fun fmtE(v: Double?): String = when {
        v == null || !v.isFinite() -> "—"
        v == 0.0 -> "0"
        else -> toExponential(v, 2)
    }

    /** JS `Number#toFixed`. */
    fun toFixed(v: Double, digits: Int): String {
        val s = BigDecimal(v).setScale(digits, RoundingMode.HALF_UP).toPlainString()
        return if (v < 0 && !s.startsWith("-")) "-$s" else s
    }

    /** JS `Number#toExponential(fractionDigits)`: `1.235e+7`, `-1.234e-5`. */
    fun toExponential(v: Double, fractionDigits: Int): String {
        if (v == 0.0) return "0" + (if (fractionDigits > 0) "." + "0".repeat(fractionDigits) else "") + "e+0"
        val bd = BigDecimal(v).round(MathContext(fractionDigits + 1, RoundingMode.HALF_UP))
        val digits = bd.unscaledValue().abs().toString().padEnd(fractionDigits + 1, '0')
        val exp = bd.precision() - bd.scale() - 1
        val mantissa = if (fractionDigits > 0) digits[0] + "." + digits.substring(1) else digits
        return (if (v < 0) "-" else "") + mantissa + "e" + (if (exp >= 0) "+" else "-") + abs(exp)
    }

    /** `String(+v.toPrecision(5))` from math.js: 5 significant digits, trailing zeros dropped. */
    fun sig5(v: Double): String =
        jsNum(BigDecimal(v).round(MathContext(5, RoundingMode.HALF_UP)).toDouble())

    /**
     * JS `Number#toString` for finite doubles: plain notation for 1e-7 < |v| < 1e21,
     * otherwise `1.5e-7` / `1e+21`.
     */
    // ponytail: relies on Double.toString being shortest-repr; JDK < 19 is off by one digit on
    // rare inputs. Only CSV export and display copy use this path, golden values don't.
    fun jsNum(v: Double): String {
        if (v.isNaN()) return "NaN"
        if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
        if (v == 0.0) return "0"
        val bd = BigDecimal(v.toString()).stripTrailingZeros()
        val exp = bd.precision() - bd.scale() - 1
        if (exp in -6..20) return bd.toPlainString()
        val digits = bd.unscaledValue().abs().toString()
        val mantissa = if (digits.length > 1) digits[0] + "." + digits.substring(1) else digits
        return (if (v < 0) "-" else "") + mantissa + "e" + (if (exp >= 0) "+" else "-") + abs(exp)
    }
}
