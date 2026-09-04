package io.github.xalrk.nudge.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A notification that was actually delivered. Kept so the calendar can show what already happened. */
@Entity(tableName = "fired_events", indices = [Index("firedAt")])
data class FiredEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val title: String,
    val body: String,
    val kind: Kind,
    val firedAt: Long,
)

@Dao
interface FiredEventDao {
    @Insert suspend fun insert(e: FiredEvent): Long

    @Query("SELECT * FROM fired_events WHERE firedAt >= :since ORDER BY firedAt")
    fun observeSince(since: Long): Flow<List<FiredEvent>>

    @Query("DELETE FROM fired_events WHERE firedAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM fired_events WHERE reminderId = :reminderId")
    suspend fun deleteForReminder(reminderId: Long)

    @Query("DELETE FROM fired_events WHERE reminderId = :reminderId AND firedAt >= :from AND firedAt < :to")
    suspend fun deleteForReminderBetween(reminderId: Long, from: Long, to: Long)

    @Query("DELETE FROM fired_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Removes delivery entries whose reminder no longer exists (left behind by deletes before 1.0.8). */
    @Query("DELETE FROM fired_events WHERE reminderId NOT IN (SELECT id FROM reminders)")
    suspend fun deleteOrphans(): Int

    @Query("DELETE FROM fired_events")
    suspend fun deleteAll()
}
