package app.numlab.domain

import app.numlab.domain.export.ExportFormat
import app.numlab.domain.format.Fmt
import app.numlab.domain.interp.InterpInput
import app.numlab.domain.interp.InterpMethod
import app.numlab.domain.interp.PointRow
import app.numlab.domain.interp.Truth
import app.numlab.domain.model.HistoryItem
import app.numlab.domain.model.RunCodec
import app.numlab.domain.model.RunInput
import app.numlab.domain.repo.Draft
import app.numlab.domain.repo.HistoryRepository
import app.numlab.domain.repo.SaveResult
import app.numlab.domain.roots.RootInput
import app.numlab.domain.roots.RootMethod
import app.numlab.domain.usecase.BuildExportUseCase
import app.numlab.domain.usecase.DeleteRunUseCase
import app.numlab.domain.usecase.ExplainStepUseCase
import app.numlab.domain.usecase.FindBracketsUseCase
import app.numlab.domain.usecase.InterpolateUseCase
import app.numlab.domain.usecase.ObserveHistoryUseCase
import app.numlab.domain.usecase.ParsePointsUseCase
import app.numlab.domain.usecase.SaveRunUseCase
import app.numlab.domain.usecase.SolveRootUseCase
import app.numlab.domain.usecase.TogglePinUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtrasTest {
    private val root = RootInput(RootMethod.SECANT, "x^3 - x - 2", "1", "2", "1.5", "1", "2")
    private val sin = InterpInput(InterpMethod.NEWTON_DD,
        listOf(PointRow("0", "0"), PointRow("1", "0.8415"), PointRow("2", "0.9093"), PointRow("3", "0.1411")), "1.5", Truth.VALUE, yTrue = "0.9975")

    @Test
    fun codecRoundTripsBothKinds() {
        for (run in listOf(RunInput.Root(root), RunInput.Interp(sin))) assertEquals(run, RunCodec.decode(RunCodec.encode(run)))
        assertTrue(RunCodec.encode(RunInput.Root(root)).contains("\"type\":\"root\""))
    }

    @Test
    fun useCasesAndExports() {
        val res = SolveRootUseCase()(root).getOrThrow()
        val ex = BuildExportUseCase()
        assertTrue(ex(res, ExportFormat.CSV).startsWith("iter,x,f(x)"))
        assertEquals("x* ≈ 1.521380", ex(res, ExportFormat.PLAIN))
        val explain = ExplainStepUseCase()
        assertEquals("Starting guess", explain(res, 0).lines[0].first)
        assertEquals("Secant step", explain(res, 2).lines[0].first)
        val newton = SolveRootUseCase()(root.copy(method = RootMethod.NEWTON)).getOrThrow()
        assertEquals("Newton step", explain(newton, 1).lines[1].first)
        val notConverged = SolveRootUseCase()(root.copy(method = RootMethod.BISECTION, maxIter = "3")).getOrThrow()
        assertTrue(explain(notConverged, 2).note!!.startsWith("Stopped at the iteration limit"))

        val ip = InterpolateUseCase()(sin).getOrThrow()
        assertEquals("x,y\n0,0\n1,0.8415\n2,0.9093\n3,0.1411", ex(ip, ExportFormat.CSV))
        assertEquals("P(1.5) = 0.976006\nP(x) = −0.010383x³ − 0.3557x² + 1.2076x", ex(ip, ExportFormat.PLAIN))
        assertTrue(ex(ip, ExportFormat.LATEX).startsWith("P(x) = -0.010383x^{3}"))
        assertEquals(null, ip.metrics!!.bound)

        assertEquals(listOf(1.0 to 2.0), FindBracketsUseCase()("x^3 - x - 2"))
        assertEquals(emptyList<Pair<Double, Double>>(), FindBracketsUseCase()("x +"))
        assertEquals(2, ParsePointsUseCase()("1 2, 3 4").rows.size)
        assertEquals("Enter a number for max iterations", SolveRootUseCase()(root.copy(maxIter = "")).exceptionOrNull()?.message)
        assertEquals("Max iterations must be a whole number ≥ 1", SolveRootUseCase()(root.copy(maxIter = "2.5")).exceptionOrNull()?.message)
        assertEquals("Max iterations must be at most 10000", SolveRootUseCase()(root.copy(maxIter = "20000")).exceptionOrNull()?.message)
    }

    @Test
    fun jsNumberFormatting() {
        assertEquals("1e+21", Fmt.jsNum(1e21))
        assertEquals("1.5e-7", Fmt.jsNum(1.5e-7))
        assertEquals("0.000001", Fmt.jsNum(1e-6))
        assertEquals("-Infinity", Fmt.jsNum(Double.NEGATIVE_INFINITY))
        assertEquals("NaN", Fmt.jsNum(Double.NaN))
        assertEquals("0.00e+0", Fmt.toExponential(0.0, 2))
        assertEquals("2e+3", Fmt.toExponential(1500.0, 0))
        assertEquals("-0.00", Fmt.toFixed(-0.0004, 2))
        assertEquals("123460", Fmt.sig5(123456.0))
    }

    @Test
    fun historyUseCasesDelegate() = runBlocking {
        val repo = object : HistoryRepository {
            val items = MutableStateFlow(listOf(HistoryItem(1, RunInput.Root(root), false, 0, 0, false)))
            override fun observe() = items
            override suspend fun save(run: RunInput) = SaveResult.INSERTED
            override suspend fun duplicate(id: Long) = Unit
            override suspend fun delete(id: Long) = items.value.first().also { items.value = emptyList() }
            override suspend fun restore(item: HistoryItem) = Unit
            override suspend fun setPinned(id: Long, pinned: Boolean) { items.value = items.value.map { it.copy(pinned = pinned) } }
            override suspend fun seedIfFirstLaunch() = Unit
        }
        assertEquals(SaveResult.INSERTED, SaveRunUseCase(repo)(RunInput.Root(root)))
        TogglePinUseCase(repo)(repo.items.value.first())
        assertTrue(ObserveHistoryUseCase(repo)().first().first().pinned)
        assertEquals(1L, DeleteRunUseCase(repo)(1)?.id)
        assertEquals(Draft(null, null, false), Draft(null, null, false))
    }
}
