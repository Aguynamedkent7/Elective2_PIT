package app.numlab.ui.workbench

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numlab.domain.format.Fmt
import app.numlab.domain.interp.InterpInput
import app.numlab.domain.model.RunCodec
import app.numlab.domain.model.RunInput
import app.numlab.domain.parser.ExprParser
import app.numlab.domain.repo.Draft
import app.numlab.domain.repo.HistoryRepository
import app.numlab.domain.repo.SaveResult
import app.numlab.domain.repo.SettingsRepository
import app.numlab.domain.roots.RootFailure
import app.numlab.domain.roots.RootInput
import app.numlab.domain.usecase.FindBracketsUseCase
import app.numlab.domain.usecase.InterpolateUseCase
import app.numlab.domain.usecase.SolveRootUseCase
import app.numlab.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dispatcher for solving and sampling (Dispatchers.Default in the app, a test dispatcher in tests). */
@Qualifier
annotation class ComputeDispatcher

/** One-off UI effects. */
sealed interface Effect {
    data class Snack(@StringRes val message: Int, val action: SnackAction? = null) : Effect
}

enum class SnackAction(@StringRes val label: Int) { VIEW_HISTORY(R.string.view) }

const val SOLVE_DEBOUNCE_MS = 250L
const val UNDO_COMMIT_MS = 600L

/**
 * State for Home and the graph canvas, which edit the same inputs (PLAN §6.3: the canvas Inputs
 * tab is bound to the same state). Solves are debounced and cancelled on new input.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class WorkbenchViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    private val saved: SavedStateHandle,
    @param:ComputeDispatcher private val compute: CoroutineDispatcher,
) : ViewModel() {
    private val solveRoot = SolveRootUseCase()
    private val interpolate = InterpolateUseCase()
    private val findBrackets = FindBracketsUseCase()

    private val _state = MutableStateFlow(restoreSaved())
    val state: StateFlow<WorkbenchState> = _state.asStateFlow()

    private val effects = Channel<Effect>(Channel.BUFFERED)
    val effect = effects.receiveAsFlow()

    private val rootUndo = UndoStack(_state.value.root)
    private val interpUndo = UndoStack(_state.value.interp)
    private var commitJob: Job? = null

    init {
        val s = viewModelScope
        s.launch { history.seedIfFirstLaunch() }
        s.launch { history.observe().collect { list -> _state.update { it.copy(historyCount = list.size) } } }
        s.launch { settings.settings.collect { st -> _state.update { it.copy(decimals = st.decimals) } } }
        s.launch { applyStartupDefaults() }
        s.launch {
            _state.map { it.root }.distinctUntilChanged().debounce(SOLVE_DEBOUNCE_MS).collectLatest { r ->
                val o = if (r.fx.isBlank()) Outcome.Idle else withContext(compute) {
                    solveRoot(r).fold({ Outcome.Ok(it) }, { e -> Outcome.Err(e.message!!, (e as? RootFailure)?.rows.orEmpty()) })
                }
                _state.update { if (it.root == r) it.copy(rootOutcome = o) else it }
                settleGraph()
            }
        }
        s.launch {
            _state.map { it.interp }.distinctUntilChanged().debounce(SOLVE_DEBOUNCE_MS).collectLatest { i ->
                val o = if (pointCount(i.rows) < 2) Outcome.Idle else withContext(compute) {
                    interpolate(i).fold({ Outcome.Ok(it) }, { e -> Outcome.Err(e.message!!) })
                }
                _state.update { if (it.interp == i) it.copy(interpOutcome = o) else it }
                settleGraph()
            }
        }
        s.launch {
            _state.map { it.root.fx }.distinctUntilChanged().debounce(SOLVE_DEBOUNCE_MS).collectLatest { fx ->
                val b = withContext(compute) { findBrackets(fx) }
                _state.update { it.copy(brackets = b) }
            }
        }
        s.launch {
            _state.map { Draft(RunInput.Root(it.root), RunInput.Interp(it.interp), it.module == Module.INTERP) }
                .distinctUntilChanged().debounce(1000).collect { settings.saveDraft(it) }
        }
    }

    // ---- inputs ----

    fun setModule(m: Module) = mutate { it.copy(module = m) }

    fun setRoot(transform: (RootInput) -> RootInput) = mutate { st ->
        val r = transform(st.root)
        // A syntax error hides the previous result at once instead of after the debounce (PLAN §8).
        val syntax = if (r.fx.isBlank()) null else ExprParser.compile(r.fx).exceptionOrNull()?.message
        st.copy(root = r, rootSyntax = syntax)
    }

    fun setInterp(transform: (InterpInput) -> InterpInput) = mutate { it.copy(interp = transform(it.interp)) }

    fun applyPreset(p: RootPreset) = setRoot { it.copy(fx = p.fx, a = p.a, b = p.b, x0 = p.x0, s0 = p.s0, s1 = p.s1) }

    fun applyBracket(a: Double, b: Double) = setRoot {
        it.copy(a = Fmt.jsNum(a), b = Fmt.jsNum(b), x0 = Fmt.jsNum(Fmt.toFixed((a + b) / 2, 4).toDouble()),
            s0 = Fmt.jsNum(a), s1 = Fmt.jsNum(b))
    }

    /** Drag a data point on the canvas: the row whose x equals [x] gets the new y. */
    fun dragPoint(x: Double, y: Double) = setInterp { inp ->
        val ri = inp.rows.indexOfFirst { it.x.trim().toDoubleOrNull() == x }
        if (ri < 0) inp
        else inp.copy(rows = inp.rows.mapIndexed { j, r -> if (j == ri) r.copy(y = Fmt.jsNum(Fmt.toFixed(y, 4).toDouble())) else r })
    }

    fun setXEval(x: Double) = setInterp { it.copy(xEval = Fmt.jsNum(Fmt.toFixed(x, 3).toDouble())) }

    fun setDecimals(d: Int) {
        viewModelScope.launch { settings.update { it.copy(decimals = d.coerceIn(3, 10)) } }
    }

    /** Adopt a run from history (Open). */
    fun load(run: RunInput) = mutate { st ->
        when (run) {
            // A changed input drops its (now stale) outcome so nothing waits on an old result.
            is RunInput.Root -> st.copy(module = Module.ROOTS, root = run.input,
                rootOutcome = if (run.input == st.root) st.rootOutcome else Outcome.Idle,
                rootSyntax = run.input.fx.takeIf { it.isNotBlank() }?.let { ExprParser.compile(it).exceptionOrNull()?.message })
            is RunInput.Interp -> run.input.copy(rows = run.input.rows.ifEmpty { BLANK_ROWS }).let {
                st.copy(module = Module.INTERP, interp = it, interpOutcome = if (it == st.interp) st.interpOutcome else Outcome.Idle)
            }
        }
    }

    // ---- undo ----

    fun undo() = step { it.undo() }
    fun redo() = step { it.redo() }

    private fun step(move: (UndoStack<Any>) -> Any?) {
        commitNow()
        @Suppress("UNCHECKED_CAST")
        val stack = (if (_state.value.module == Module.ROOTS) rootUndo else interpUndo) as UndoStack<Any>
        val v = move(stack) ?: return
        _state.update {
            when (v) {
                is RootInput -> it.copy(root = v)
                is InterpInput -> it.copy(interp = v)
                else -> it
            }.withUndoFlags()
        }
        persist()
    }

    private fun commitNow() {
        commitJob?.cancel()
        rootUndo.commit(_state.value.root)
        interpUndo.commit(_state.value.interp)
    }

    private fun WorkbenchState.withUndoFlags(): WorkbenchState {
        val (u, changed) = if (module == Module.ROOTS) rootUndo to (root != rootUndo.current)
        else interpUndo to (interp != interpUndo.current)
        return copy(canUndo = u.canUndo || changed, canRedo = u.canRedo && !changed)
    }

    private fun mutate(f: (WorkbenchState) -> WorkbenchState) {
        _state.update { f(it).withUndoFlags() }
        persist()
        commitJob?.cancel()
        commitJob = viewModelScope.launch {
            delay(UNDO_COMMIT_MS)
            rootUndo.commit(_state.value.root)
            interpUndo.commit(_state.value.interp)
            _state.update { it.withUndoFlags() }
        }
    }

    // ---- graph canvas ----

    /** Where the circular reveal starts (Graph button centre), or null for bottom-centre. */
    var graphOrigin: androidx.compose.ui.geometry.Offset? = null
        private set

    /** Opens the canvas and auto-saves the run (deduped, silent; PLAN §6.2). */
    fun openGraph(origin: androidx.compose.ui.geometry.Offset?) {
        if (_state.value.lock != null) return
        graphOrigin = origin
        save(false)
        _state.update { it.copy(graphOpen = true) }
        saved[KEY_GRAPH] = true
    }

    private var graphPending = false

    /** Opens the canvas once the just-loaded run has been solved (History → Open graph). */
    fun openGraphWhenReady() {
        graphPending = true
        settleGraph()
    }

    private fun settleGraph() {
        if (!graphPending) return
        val st = _state.value
        if (st.lock == null) { graphPending = false; openGraph(null) }
        else if (st.visibleError != null) graphPending = false
    }

    fun closeGraph() {
        _state.update { it.copy(graphOpen = false) }
        saved[KEY_GRAPH] = false
    }

    // ---- saving ----

    /** Save to history. [announce] shows a snackbar (Save button) vs silent (opening the graph). */
    fun save(announce: Boolean) {
        val st = _state.value
        if (st.lock != null) return
        viewModelScope.launch {
            val r = history.save(st.run)
            if (announce) {
                effects.send(Effect.Snack(if (r == SaveResult.INSERTED) R.string.saved else R.string.already_saved,
                    SnackAction.VIEW_HISTORY))
            }
        }
    }

    // ---- persistence: SavedStateHandle for process death, DataStore draft for D2 ----

    private fun persist() {
        val st = _state.value
        saved[KEY_ROOT] = RunCodec.encode(RunInput.Root(st.root))
        saved[KEY_INTERP] = RunCodec.encode(RunInput.Interp(st.interp))
        saved[KEY_MODULE] = st.module.name
    }

    private fun restoreSaved(): WorkbenchState {
        val root = saved.get<String>(KEY_ROOT)?.let { (RunCodec.decode(it) as RunInput.Root).input }
        val interp = saved.get<String>(KEY_INTERP)?.let { (RunCodec.decode(it) as RunInput.Interp).input }
        val module = saved.get<String>(KEY_MODULE)?.let(Module::valueOf)
        return WorkbenchState(module ?: Module.ROOTS, root ?: START_ROOT, interp ?: START_INTERP,
            graphOpen = saved.get<Boolean>(KEY_GRAPH) == true)
    }

    /** Fresh start (not a process-death restore): settings defaults, then the draft if D2 is on. */
    private suspend fun applyStartupDefaults() {
        if (saved.contains(KEY_ROOT)) return
        val st = settings.settings.first()
        val draft = if (st.restoreSession) settings.draft.first() else null
        _state.update {
            it.copy(
                root = draft?.roots?.input ?: it.root.copy(tol = st.defaultTolerance, maxIter = st.defaultMaxIter),
                interp = draft?.interp?.input ?: it.interp,
                module = if (draft?.lastModuleIsInterp == true) Module.INTERP else it.module,
            )
        }
        rootUndo.reset(_state.value.root)
        interpUndo.reset(_state.value.interp)
    }

    private companion object {
        const val KEY_ROOT = "root"
        const val KEY_INTERP = "interp"
        const val KEY_MODULE = "module"
        const val KEY_GRAPH = "graph"
    }
}

