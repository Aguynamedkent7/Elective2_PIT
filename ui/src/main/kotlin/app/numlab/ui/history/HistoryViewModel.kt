package app.numlab.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.numlab.domain.interp.interpolate
import app.numlab.domain.model.HistoryItem
import app.numlab.domain.model.RunInput
import app.numlab.domain.model.domainCatching
import app.numlab.domain.repo.HistoryRepository
import app.numlab.domain.roots.solveRoot
import app.numlab.domain.usecase.DeleteRunUseCase
import app.numlab.domain.usecase.ObserveHistoryUseCase
import app.numlab.domain.usecase.TogglePinUseCase
import app.numlab.ui.components.Solved
import app.numlab.ui.workbench.ComputeDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Filter { ALL, ROOTS, INTERP, PINNED }

/** A history entry with its re-solved result (inputs only are stored; PLAN §5.1). */
data class Card(val item: HistoryItem, val result: Result<Solved>)

data class HistoryState(val all: Int = 0, val cards: List<Card> = emptyList(), val filter: Filter = Filter.ALL, val loaded: Boolean = false)

/** Re-solves a stored run. Deterministic and cheap, so history never shows stale numbers. */
fun solve(run: RunInput): Result<Solved> = domainCatching {
    when (run) {
        is RunInput.Root -> Solved.Root(solveRoot(run.input))
        is RunInput.Interp -> Solved.Interp(interpolate(run.input), run.input.xEval)
    }
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repo: HistoryRepository,
    @param:ComputeDispatcher private val compute: CoroutineDispatcher,
) : ViewModel() {
    private val observe = ObserveHistoryUseCase(repo)
    private val deleteRun = DeleteRunUseCase(repo)
    private val togglePin = TogglePinUseCase(repo)
    private val filter = MutableStateFlow(Filter.ALL)
    private val cache = HashMap<RunInput, Result<Solved>>()

    private val solved = observe().map { items ->
        // ponytail: unbounded memo keyed by inputs; history is capped at 200 so it stays small.
        items.map { Card(it, synchronized(cache) { cache.getOrPut(it.run) { solve(it.run) } }) }
    }.flowOn(compute)

    val state: StateFlow<HistoryState> = combine(solved, filter) { cards, f ->
        HistoryState(cards.size, cards.filter {
            when (f) {
                Filter.ALL -> true
                Filter.ROOTS -> it.item.run is RunInput.Root
                Filter.INTERP -> it.item.run is RunInput.Interp
                Filter.PINNED -> it.item.pinned
            }
        }, f, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())

    fun setFilter(f: Filter) { filter.value = f }
    fun togglePin(item: HistoryItem) = viewModelScope.launch { togglePin.invoke(item) }
    fun duplicate(item: HistoryItem) = viewModelScope.launch { repo.duplicate(item.id) }

    /** Deletes and returns the removed item so the UI can offer Undo. */
    fun delete(item: HistoryItem, onDeleted: (HistoryItem) -> Unit) = viewModelScope.launch {
        deleteRun(item.id)?.let(onDeleted)
    }

    fun restore(item: HistoryItem) = viewModelScope.launch { repo.restore(item) }
}
