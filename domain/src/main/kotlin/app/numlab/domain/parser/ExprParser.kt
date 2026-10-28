package app.numlab.domain.parser

import app.numlab.domain.format.Fmt.fmt
import app.numlab.domain.format.Fmt.jsNum
import app.numlab.domain.model.DomainException
import app.numlab.domain.model.domainCatching
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

private typealias Fn = (Double) -> Double

private val UNARY: Map<String, Fn> = mapOf(
    "sin" to Math::sin, "cos" to Math::cos, "tan" to Math::tan,
    "asin" to Math::asin, "acos" to Math::acos, "atan" to Math::atan,
    "sinh" to Math::sinh, "cosh" to Math::cosh, "tanh" to Math::tanh, "exp" to Math::exp,
    "ln" to Math::log, "log" to Math::log, "log10" to Math::log10, "log2" to { v -> ln(v) / ln(2.0) },
    "sqrt" to Math::sqrt, "cbrt" to Math::cbrt, "abs" to Math::abs,
)
private val CONSTS = mapOf("pi" to PI, "e" to E)
private val LOGS = setOf("ln", "log", "log10", "log2")
// JS `\s`, spelled out: Android ICU rejects the JVM-only `(?U)` flag.
private val WS = Regex("[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")
private val EXP_TAIL = Regex("^[+-]?\\d")

private fun applyFn(name: String, v: Double): Double {
    if (name in LOGS && v <= 0) throw DomainException("Log of a non-positive number ($name(${fmt(v, 4)}))")
    if (name == "sqrt" && v < 0) throw DomainException("Square root of a negative number (${fmt(v, 4)})")
    if ((name == "asin" || name == "acos") && abs(v) > 1) throw DomainException("$name(x) needs |x| ≤ 1")
    return UNARY.getValue(name)(v)
}

private sealed interface Tok {
    data class Num(val v: Double) : Tok
    data class Id(val v: String) : Tok
    data class Op(val v: Char) : Tok
}

private fun Tok.text(): String = when (this) {
    is Tok.Num -> jsNum(v)
    is Tok.Id -> v
    is Tok.Op -> v.toString()
}

private fun tokenize(src: String): List<Tok> {
    val s = src.replace(WS, "").replace("π", "pi").replace('×', '*').replace('−', '-').replace('÷', '/')
    val toks = mutableListOf<Tok>()
    var i = 0
    while (i < s.length) {
        val c = s[i]
        when {
            c.isAsciiDigit() || c == '.' -> {
                var j = i
                while (j < s.length && (s[j].isAsciiDigit() || s[j] == '.')) j++
                if (j < s.length && (s[j] == 'e' || s[j] == 'E') && EXP_TAIL.containsMatchIn(s.substring(j + 1))) {
                    j++
                    if (s[j] == '+' || s[j] == '-') j++
                    while (j < s.length && s[j].isAsciiDigit()) j++
                }
                val text = s.substring(i, j)
                toks += Tok.Num(text.toDoubleOrNull() ?: throw DomainException("Bad number \"$text\""))
                i = j
            }
            c.isAsciiLetter() || c == '_' -> {
                var j = i
                while (j < s.length && (s[j].isAsciiLetter() || s[j].isAsciiDigit() || s[j] == '_')) j++
                toks += Tok.Id(s.substring(i, j).lowercase())
                i = j
            }
            c == '√' -> { toks += Tok.Id("sqrt"); i++ }
            c in "+-*/^()," -> { toks += Tok.Op(c); i++ }
            else -> throw DomainException("Unexpected character \"$c\"")
        }
    }
    return toks
}

private fun Char.isAsciiDigit() = this in '0'..'9'
private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'

/** Recursive-descent port of `compileExpr` in docs/reference/math.js. */
private class Parser(private val toks: List<Tok>) {
    private var p = 0
    private fun peek() = toks.getOrNull(p)
    private fun isOp(t: Tok?, v: Char) = t is Tok.Op && t.v == v
    private fun startsAtom(t: Tok?) = t is Tok.Num || t is Tok.Id || isOp(t, '(')

    fun parse(): Fn {
        val fn = expr()
        if (p < toks.size) throw DomainException("Unexpected \"${toks[p].text()}\"")
        return fn
    }

    private fun expr(): Fn {
        var l = term()
        while (isOp(peek(), '+') || isOp(peek(), '-')) {
            val op = (toks[p++] as Tok.Op).v
            val r = term()
            val a = l
            l = if (op == '+') { x -> a(x) + r(x) } else { x -> a(x) - r(x) }
        }
        return l
    }

    private fun term(): Fn {
        var l = unary()
        while (true) {
            val t = peek() ?: break
            val a = l
            if (isOp(t, '*') || isOp(t, '/')) {
                p++
                val r = unary()
                l = if ((t as Tok.Op).v == '*') {
                    { x -> a(x) * r(x) }
                } else {
                    { x ->
                        val d = r(x)
                        if (d == 0.0) throw DomainException("Division by zero at x = ${fmt(x, 4)}")
                        a(x) / d
                    }
                }
            } else if (startsAtom(t)) {
                val r = power()
                l = { x -> a(x) * r(x) }
            } else {
                break
            }
        }
        return l
    }

    private fun unary(): Fn {
        if (isOp(peek(), '-')) {
            p++
            val a = unary()
            return { x -> -a(x) }
        }
        if (isOp(peek(), '+')) {
            p++
            return unary()
        }
        return power()
    }

    private fun power(): Fn {
        val base = atom()
        if (!isOp(peek(), '^')) return base
        p++
        val ex = unary()
        return { x ->
            val b = base(x)
            val e = ex(x)
            if (b == 0.0 && e < 0) throw DomainException("Division by zero at x = ${fmt(x, 4)}")
            val r = b.pow(e)
            if (r.isNaN()) throw DomainException("Negative base with a fractional power")
            r
        }
    }

    private fun atom(): Fn {
        val t = toks.getOrNull(p++) ?: throw DomainException("Expression ends early")
        return when {
            t is Tok.Num -> { _ -> t.v }
            t is Tok.Id && t.v in UNARY -> {
                val arg = if (isOp(peek(), '(')) {
                    p++
                    val inner = expr()
                    if (!isOp(peek(), ')')) throw DomainException("Missing \")\"")
                    p++
                    inner
                } else {
                    power()
                }
                { x -> applyFn(t.v, arg(x)) }
            }
            t is Tok.Id && t.v == "x" -> { x -> x }
            t is Tok.Id && t.v in CONSTS -> CONSTS.getValue(t.v).let { c -> { _ -> c } }
            t is Tok.Id -> throw DomainException("Unknown symbol \"${t.v}\"")
            isOp(t, '(') -> {
                val inner = expr()
                if (!isOp(peek(), ')')) throw DomainException("Missing \")\"")
                p++
                inner
            }
            else -> throw DomainException("Unexpected \"${t.text()}\"")
        }
    }
}

/** Hand-written engine (decision D4, see docs/DECISIONS.md). */
object ExprParser : ExpressionEngine {
    override fun compile(src: String): Result<CompiledFn> = domainCatching { compileOrThrow(src) }

    /** Like [compile] but throws [DomainException]. */
    fun compileOrThrow(src: String): CompiledFn {
        if (src.isBlank()) throw DomainException("Enter a function of x")
        val fn = Parser(tokenize(src)).parse()
        return CompiledFn { x ->
            val v = fn(x)
            if (!v.isFinite()) throw DomainException("f(x) is undefined at x = ${fmt(x, 4)}")
            v
        }
    }
}
