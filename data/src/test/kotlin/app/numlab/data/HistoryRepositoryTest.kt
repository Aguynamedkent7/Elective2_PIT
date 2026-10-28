package app.numlab.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.numlab.data.db.NumLabDatabase
import app.numlab.data.repo.HISTORY_CAP
import app.numlab.data.repo.RoomHistoryRepository
import app.numlab.domain.model.RunInput
import app.numlab.domain.repo.SaveResult
import app.numlab.domain.roots.RootInput
import app.numlab.domain.roots.RootMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HistoryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: NumLabDatabase
    private lateinit var repo: RoomHistoryRepository
    private var now = 1_000L

    private fun run(fx: String) = RunInput.Root(RootInput(RootMethod.BISECTION, fx, "1", "2"))

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, NumLabDatabase::class.java).allowMainThreadQueries().build()
        val prefs = PreferenceDataStoreFactory.create { tmp.newFile("p.preferences_pb") }
        repo = RoomHistoryRepository(db, prefs) { now++ }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertDedupeAndOrder() = runTest {
        assertEquals(SaveResult.INSERTED, repo.save(run("x")))
        assertEquals(SaveResult.INSERTED, repo.save(run("x+1")))
        assertEquals(SaveResult.ALREADY_SAVED, repo.save(run("x")))
        val items = repo.observe().first()
        assertEquals(2, items.size)
        assertEquals(run("x"), items[0].run) // re-save bumps updatedAt to the top
    }

    @Test
    fun pinnedFirstAndDeleteRestore() = runTest {
        repo.save(run("a"))
        repo.save(run("b"))
        val a = repo.observe().first().first { it.run == run("a") }
        repo.setPinned(a.id, true)
        assertEquals(run("a"), repo.observe().first()[0].run)
        val removed = repo.delete(a.id)!!
        assertEquals(1, repo.observe().first().size)
        repo.restore(removed)
        assertEquals(removed, repo.observe().first()[0])
    }

    @Test
    fun capDropsOldestUnpinned() = runTest {
        repo.save(run("keep"))
        val keep = repo.observe().first()[0]
        repo.setPinned(keep.id, true)
        repeat(HISTORY_CAP + 5) { repo.save(run("r$it")) }
        val items = repo.observe().first()
        assertEquals(HISTORY_CAP, items.size)
        assertTrue(items.any { it.id == keep.id })
        assertTrue(items.none { it.run == run("r0") })
    }

    @Test
    fun seedsOnlyOnce() = runTest {
        repo.seedIfFirstLaunch()
        repo.seedIfFirstLaunch()
        val items = repo.observe().first()
        assertEquals(3, items.size)
        assertTrue(items.all { it.sample })
        assertTrue(items[0].pinned)
    }
}
