package app.numlab.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One saved run. Stores inputs only (schema-versioned JSON), never results (PLAN §5.1). */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val inputsJson: String,
    val schema: Int,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String? = null,
    val sample: Boolean = false,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY pinned DESC, updatedAt DESC, id DESC")
    fun observe(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE inputsJson = :json LIMIT 1")
    suspend fun findByInputs(json: String): HistoryEntity?

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun get(id: Long): HistoryEntity?

    @Insert
    suspend fun insert(entity: HistoryEntity): Long

    @Query("UPDATE history SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("UPDATE history SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    @Query(
        "DELETE FROM history WHERE id IN " +
            "(SELECT id FROM history WHERE pinned = 0 ORDER BY updatedAt ASC, id ASC LIMIT :n)",
    )
    suspend fun deleteOldestUnpinned(n: Int)
}

@Database(entities = [HistoryEntity::class], version = 1)
abstract class NumLabDatabase : RoomDatabase() {
    abstract fun history(): HistoryDao
}
