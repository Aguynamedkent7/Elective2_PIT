package app.numlab.ui

import app.numlab.domain.model.HistoryItem
import app.numlab.domain.model.RunInput
import app.numlab.domain.model.Settings
import app.numlab.domain.repo.Draft
import app.numlab.domain.repo.HistoryRepository
import app.numlab.domain.repo.SaveResult
import app.numlab.domain.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeHistory : HistoryRepository {
    val items = MutableStateFlow<List<HistoryItem>>(emptyList())
    private var id = 0L
    override fun observe(): Flow<List<HistoryItem>> = items
    override suspend fun save(run: RunInput): SaveResult {
        if (items.value.any { it.run == run }) return SaveResult.ALREADY_SAVED
        items.update { listOf(HistoryItem(++id, run, false, id, id, false)) + it }
        return SaveResult.INSERTED
    }
    override suspend fun duplicate(id: Long) {
        val e = items.value.first { it.id == id }
        items.update { listOf(e.copy(id = ++this.id)) + it }
    }
    override suspend fun delete(id: Long) = items.value.find { it.id == id }?.also { d -> items.update { it - d } }
    override suspend fun restore(item: HistoryItem) = items.update { listOf(item) + it }
    override suspend fun setPinned(id: Long, pinned: Boolean) =
        items.update { l -> l.map { if (it.id == id) it.copy(pinned = pinned) else it } }
    override suspend fun seedIfFirstLaunch() = Unit
}

class FakeSettings(initial: Settings = Settings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<Settings> = state
    override suspend fun update(transform: (Settings) -> Settings) = state.update(transform)
    override val draft = MutableStateFlow(Draft(null, null, false))
    override suspend fun saveDraft(draft: Draft) { this.draft.value = draft }
}
