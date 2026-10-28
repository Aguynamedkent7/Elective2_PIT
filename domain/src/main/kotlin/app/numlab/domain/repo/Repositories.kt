package app.numlab.domain.repo

import app.numlab.domain.model.HistoryItem
import app.numlab.domain.model.RunInput
import app.numlab.domain.model.Settings
import kotlinx.coroutines.flow.Flow

/** Notebook history. Implemented with Room in :data. */
interface HistoryRepository {
    /** Pinned first, then most recently updated. */
    fun observe(): Flow<List<HistoryItem>>

    /** Inserts, or bumps `updatedAt` when [run] equals the newest entry. Caps at 200 unpinned. */
    suspend fun save(run: RunInput): SaveResult

    /** Inserts a copy of [id] as a new entry (no dedupe). */
    suspend fun duplicate(id: Long)

    suspend fun delete(id: Long): HistoryItem?
    suspend fun restore(item: HistoryItem)
    suspend fun setPinned(id: Long, pinned: Boolean)

    /** Seeds the three sample runs on first launch only. */
    suspend fun seedIfFirstLaunch()
}

enum class SaveResult { INSERTED, ALREADY_SAVED }

/** App settings plus the draft restore slot. Implemented with DataStore in :data. */
interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings)

    /** Last inputs per module, restored on launch only when [Settings.restoreSession] is on (D2). */
    val draft: Flow<Draft>
    suspend fun saveDraft(draft: Draft)
}

data class Draft(val roots: RunInput.Root?, val interp: RunInput.Interp?, val lastModuleIsInterp: Boolean)
