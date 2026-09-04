package io.github.xalrk.nudge.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY kind, nextAt IS NULL, nextAt, title")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observeById(id: Long): Flow<Reminder?>

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND kind = 'RANDOM'")
    suspend fun enabledRandom(): List<Reminder>

    @Query("SELECT COUNT(*) FROM reminders WHERE enabled = 1 AND kind = 'RANDOM'")
    suspend fun countEnabledRandom(): Int

    @Query("SELECT * FROM reminders WHERE enabled = 1 AND ((nextAt IS NOT NULL AND nextAt <= :now) OR (snoozeAt IS NOT NULL AND snoozeAt <= :now))")
    suspend fun due(now: Long): List<Reminder>

    @Query("SELECT MIN(t) FROM (SELECT nextAt AS t FROM reminders WHERE enabled = 1 AND nextAt IS NOT NULL UNION ALL SELECT snoozeAt AS t FROM reminders WHERE enabled = 1 AND snoozeAt IS NOT NULL)")
    suspend fun earliestTrigger(): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(r: Reminder): Long

    /** Returns -1 when a reminder with the same dedupeKey already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(r: Reminder): Long

    @Update
    suspend fun update(r: Reminder)

    @Update
    suspend fun updateAll(rs: List<Reminder>)

    @Delete
    suspend fun delete(r: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
