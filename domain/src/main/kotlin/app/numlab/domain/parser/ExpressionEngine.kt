package app.numlab.domain.parser

/** Compiles `f(x)` source once so it can be evaluated many times. Swappable (PLAN §4.1). */
interface ExpressionEngine {
    /** Fails with a [app.numlab.domain.model.DomainException] carrying a short syntax message. */
    fun compile(src: String): Result<CompiledFn>
}

/** A compiled `f(x)`. [eval] throws [app.numlab.domain.model.DomainException] on domain errors. */
fun interface CompiledFn {
    fun eval(x: Double): Double
}
