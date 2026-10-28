package app.numlab.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.numlab.ui.workbench.Effect
import app.numlab.ui.workbench.Lock
import app.numlab.ui.workbench.Module
import app.numlab.ui.workbench.Outcome
import app.numlab.ui.workbench.ROOT_PRESETS
import app.numlab.ui.workbench.SOLVE_DEBOUNCE_MS
import app.numlab.ui.workbench.WorkbenchViewModel
import app.numlab.ui.workbench.INTERP_EXAMPLES
import app.numlab.ui.workbench.UNDO_COMMIT_MS
import app.numlab.domain.roots.RootMethod
import app.numlab.domain.roots.RootResult
import app.numlab.domain.interp.InterpResult
import app.numlab.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkbenchViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val history = FakeHistory()
    private lateinit var vm: WorkbenchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = WorkbenchViewModel(history, FakeSettings(), SavedStateHandle(), dispatcher)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.settle() { advanceTimeBy(SOLVE_DEBOUNCE_MS + 1); advanceUntilIdle() }
    private val st get() = vm.state.value

    @Test
    fun emptyStartIsLockedWithNoError() = runTest(dispatcher) {
        settle()
        assertEquals(Lock.IDLE_ROOT, st.lock)
        assertNull(st.visibleError)
        assertEquals(Outcome.Idle, st.rootOutcome)
    }

    @Test
    fun presetUnlocksAfterDebounceAndMatchesAcceptanceA() = runTest(dispatcher) {
        vm.applyPreset(ROOT_PRESETS[0])
        advanceTimeBy(SOLVE_DEBOUNCE_MS - 50)
        assertEquals(Lock.FIX, st.lock) // not solved yet: still locked during the debounce
        settle()
        assertNull(st.lock)
        val r = (st.rootOutcome as Outcome.Ok<RootResult>).value
        assertEquals("1.521385", app.numlab.domain.format.Fmt.fmt(r.root))
        assertEquals(17, r.iterations)
        assertTrue(r.converged)
        assertEquals(listOf(1.0 to 2.0), st.brackets)
    }

    @Test
    fun syntaxErrorHidesResultImmediatelyThenRecovers() = runTest(dispatcher) {
        vm.applyPreset(ROOT_PRESETS[0]); settle()
        vm.setRoot { it.copy(fx = "x^3 - x - (") }
        assertEquals("Expression ends early", (st.visibleError)?.message)
        vm.setRoot { it.copy(fx = "x^3 - x - 2") }; settle()
        assertNull(st.lock)
    }

    @Test
    fun domainErrorAndSameSignAndNotConverged() = runTest(dispatcher) {
        vm.setRoot { it.copy(fx = "ln(x)+x-2", a = "-1", b = "2") }; settle()
        assertEquals("Log of a non-positive number (ln(-1.0000))", st.visibleError?.message)
        assertEquals(listOf(1.0 to 2.0), st.brackets)
        vm.applyBracket(1.0, 2.0); settle()
        assertNull(st.lock)
        vm.setRoot { it.copy(fx = "x^2+1", a = "0", b = "1") }; settle()
        assertEquals("f(a) and f(b) must have opposite signs", st.visibleError?.message)
        vm.applyPreset(ROOT_PRESETS[0]); vm.setRoot { it.copy(maxIter = "3") }; settle()
        assertFalse((st.rootOutcome as Outcome.Ok<RootResult>).value.converged)
    }

    @Test
    fun newtonAndSecantIterationCounts() = runTest(dispatcher) {
        vm.applyPreset(ROOT_PRESETS[0])
        vm.setRoot { it.copy(method = RootMethod.NEWTON) }; settle()
        assertEquals(3, (st.rootOutcome as Outcome.Ok<RootResult>).value.iterations)
        vm.setRoot { it.copy(method = RootMethod.SECANT) }; settle()
        assertEquals(6, (st.rootOutcome as Outcome.Ok<RootResult>).value.iterations)
    }

    @Test
    fun interpolationLocksAndExample() = runTest(dispatcher) {
        vm.setModule(Module.INTERP); settle()
        assertEquals(Lock.IDLE_INTERP, st.lock)
        vm.setInterp { INTERP_EXAMPLES[0].second.copy(xEval = "") }; settle()
        assertEquals(Lock.NEED_X, st.lock)
        assertNull(st.visibleError)
        vm.setInterp { it.copy(xEval = "1.5") }; settle()
        val r = (st.interpOutcome as Outcome.Ok<InterpResult>).value
        assertEquals("0.976006", app.numlab.domain.format.Fmt.fmt(r.pxe))
    }

    @Test
    fun undoRedo() = runTest(dispatcher) {
        vm.setRoot { it.copy(fx = "x") }
        advanceTimeBy(UNDO_COMMIT_MS + 1)
        vm.setRoot { it.copy(fx = "x+1") }
        assertTrue(st.canUndo)
        vm.undo()
        assertEquals("x", st.root.fx)
        assertTrue(st.canRedo)
        vm.redo()
        assertEquals("x+1", st.root.fx)
        advanceUntilIdle()
    }

    @Test
    fun saveDedupesAndAnnounces() = runTest(dispatcher) {
        vm.applyPreset(ROOT_PRESETS[0]); settle()
        vm.effect.test {
            vm.save(true); advanceUntilIdle()
            assertEquals(R.string.saved, (awaitItem() as Effect.Snack).message)
            vm.save(true); advanceUntilIdle()
            assertEquals(R.string.already_saved, (awaitItem() as Effect.Snack).message)
        }
        assertEquals(1, history.items.value.size)
        assertEquals(1, st.historyCount)
    }

    @Test
    fun saveIgnoredWhileLocked() = runTest(dispatcher) {
        settle()
        vm.save(true); advanceUntilIdle()
        assertTrue(history.items.value.isEmpty())
    }
}
