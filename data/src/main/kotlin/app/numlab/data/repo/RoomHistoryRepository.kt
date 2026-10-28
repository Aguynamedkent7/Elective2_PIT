package app.numlab.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.withTransaction
import app.numlab.data.db.HistoryEntity
import app.numlab.data.db.NumLabDatabase
import app.numlab.domain.interp.InterpInput
import app.numlab.domain.interp.InterpMethod
import app.numlab.domain.interp.PointRow
import app.numlab.domain.interp.Truth
import app.numlab.domain.model.HistoryItem
import app.numlab.domain.model.RunCodec
import app.numlab.domain.model.RunInput
import app.numlab.domain.repo.HistoryRepository
import app.numlab.domain.repo.SaveResult
import app.numlab.domain.roots.RootInput
import app.numlab.domain.roots.RootMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

const val HISTORY_CAP = 200
private val SEEDED = booleanPreferencesKey("seededHistory")

class RoomHistoryRepository(
    private val db: NumLabDatabase,
    private val prefs: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) : HistoryRepository {
    private val dao = db.history()

    override fun observe(): Flow<List<HistoryItem>> = dao.observe().map { list -> list.map { it.toItem() } }

    // Dedupe against any entry with the same inputs (prototype behaviour), not only the newest.
    override suspend fun save(run: RunInput): SaveResult = db.withTransaction {
        val json = RunCodec.encode(run)
        val now = clock()
        val existing = dao.findByInputs(json)
        if (existing != null) {
            dao.touch(existing.id, now)
            return@withTransaction SaveResult.ALREADY_SAVED
        }
        dao.insert(HistoryEntity(kind = run.kind(), inputsJson = json, schema = RunCodec.SCHEMA,
            pinned = false, createdAt = now, updatedAt = now))
        val over = dao.count() - HISTORY_CAP
        if (over > 0) dao.deleteOldestUnpinned(over)
        SaveResult.INSERTED
    }

    override suspend fun duplicate(id: Long) {
        val e = dao.get(id) ?: return
        val now = clock()
        dao.insert(e.copy(id = 0, pinned = false, sample = false, createdAt = now, updatedAt = now))
    }

    override suspend fun delete(id: Long): HistoryItem? {
        val e = dao.get(id) ?: return null
        dao.delete(id)
        return e.toItem()
    }

    override suspend fun restore(item: HistoryItem) {
        dao.insert(HistoryEntity(id = item.id, kind = item.run.kind(), inputsJson = RunCodec.encode(item.run),
            schema = RunCodec.SCHEMA, pinned = item.pinned, createdAt = item.createdAt,
            updatedAt = item.updatedAt, sample = item.sample))
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    override suspend fun seedIfFirstLaunch() {
        if (prefs.data.first()[SEEDED] == true) return
        val now = clock()
        SAMPLES.forEachIndexed { i, (run, pinned) ->
            val at = now - i * 1000L
            dao.insert(HistoryEntity(kind = run.kind(), inputsJson = RunCodec.encode(run), schema = RunCodec.SCHEMA,
                pinned = pinned, createdAt = at, updatedAt = at, sample = true))
        }
        prefs.edit { it[SEEDED] = true }
    }

    private fun HistoryEntity.toItem() =
        HistoryItem(id, RunCodec.decode(inputsJson), pinned, createdAt, updatedAt, sample)

    private fun RunInput.kind() = if (this is RunInput.Root) "root" else "interp"

    companion object {
        /** PLAN §5.1 seeds: bisection x³−x−2 (pinned), Lagrange sin 4-point, Newton eˣ−3x. */
        val SAMPLES: List<Pair<RunInput, Boolean>> = listOf(
            RunInput.Root(RootInput(RootMethod.BISECTION, "x^3 - x - 2", "1", "2", "1.5", "1", "2")) to true,
            RunInput.Interp(
                InterpInput(
                    InterpMethod.LAGRANGE,
                    listOf(PointRow("0", "0"), PointRow("1", "0.8415"), PointRow("2", "0.9093"), PointRow("3", "0.1411")),
                    "1.5", Truth.FN, "sin(x)",
                ),
            ) to false,
            RunInput.Root(RootInput(RootMethod.NEWTON, "exp(x) - 3x", "0", "1", "0.5", "0", "1")) to false,
        )
    }
}
