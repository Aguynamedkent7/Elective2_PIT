package app.numlab.domain.model

/** A user-facing math or input error. [message] is shown verbatim in the UI. */
open class DomainException(override val message: String) : RuntimeException(message)

private val NUMBER = Regex("""^[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?$""")

/** Parses a numeric input field; throws `Enter a number for <label>` when empty or invalid. */
fun parseNumber(raw: String, label: String): Double {
    val s = raw.trim()
    val v = if (NUMBER.matches(s)) s.toDouble() else null
    if (v == null || !v.isFinite()) throw DomainException("Enter a number for $label")
    return v
}

/** Runs [block], turning [DomainException] into a failed [Result]; anything else propagates. */
inline fun <T> domainCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: DomainException) {
        Result.failure(e)
    }
