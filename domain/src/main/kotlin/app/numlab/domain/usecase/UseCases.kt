package app.numlab.domain.usecase

import app.numlab.domain.export.ExportFormat
import app.numlab.domain.export.toCsv
import app.numlab.domain.export.toLatex
import app.numlab.domain.export.toPlain
import app.numlab.domain.interp.InterpInput
import app.numlab.domain.interp.InterpResult
import app.numlab.domain.interp.ParsedPoints
import app.numlab.domain.interp.interpolate
import app.numlab.domain.interp.parsePoints
import app.numlab.domain.model.domainCatching
import app.numlab.domain.parser.ExprParser
import app.numlab.domain.roots.RootInput
import app.numlab.domain.roots.RootResult
import app.numlab.domain.roots.Working
import app.numlab.domain.roots.explainStep
import app.numlab.domain.roots.findBrackets
import app.numlab.domain.roots.solveRoot

// Thin entry points for ViewModels (PLAN §3.2). Failures carry a DomainException whose
// message is shown verbatim; RootFailure also carries the rows computed before the error.

class SolveRootUseCase {
    operator fun invoke(input: RootInput): Result<RootResult> = domainCatching { solveRoot(input) }
}

class InterpolateUseCase {
    operator fun invoke(input: InterpInput): Result<InterpResult> = domainCatching { interpolate(input) }
}

class FindBracketsUseCase {
    /** Empty when `fx` doesn't compile. */
    operator fun invoke(fx: String): List<Pair<Double, Double>> =
        ExprParser.compile(fx).map { findBrackets(it) }.getOrDefault(emptyList())
}

class ExplainStepUseCase {
    operator fun invoke(result: RootResult, index: Int): Working = explainStep(result, index)
}

class ParsePointsUseCase {
    operator fun invoke(text: String): ParsedPoints = parsePoints(text)
}

class BuildExportUseCase {
    operator fun invoke(result: RootResult, format: ExportFormat): String = when (format) {
        ExportFormat.CSV -> result.toCsv()
        ExportFormat.LATEX -> result.toLatex()
        ExportFormat.PLAIN -> result.toPlain()
    }

    operator fun invoke(result: InterpResult, format: ExportFormat): String = when (format) {
        ExportFormat.CSV -> result.toCsv()
        ExportFormat.LATEX -> result.toLatex()
        ExportFormat.PLAIN -> result.toPlain()
    }
}
