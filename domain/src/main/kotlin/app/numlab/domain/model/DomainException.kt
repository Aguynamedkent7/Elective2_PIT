package app.numlab.domain.model

import app.numlab.domain.parser.ExprParser

/** A user-facing math or input error. [message] is shown verbatim in the UI. */
open class DomainException(override val message: String) : RuntimeException(message)

/** Parses a numeric input field; throws `Enter a number for <label>` when empty or invalid. */
fun parseNumber(raw: String, label: String): Double {
    return parseNumberOrNull(raw) ?: throw DomainException("Enter a number for $label")
}

/** Parses a finite constant expression for use in numeric fields. */
fun parseNumberOrNull(raw: String): Double? {
    val expression = raw.trim().replace('−', '-')
    if (expression.isEmpty()) return null
    return try {
        ExprParser.evaluateConstantOrThrow(expression)
    } catch (_: DomainException) {
        null
    }
}

/** Runs [block], turning [DomainException] into a failed [Result]; anything else propagates. */
inline fun <T> domainCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: DomainException) {
        Result.failure(e)
    }
