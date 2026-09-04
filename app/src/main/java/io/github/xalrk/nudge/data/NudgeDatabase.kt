package io.github.xalrk.nudge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Reminder::class, FiredEvent::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NudgeDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun firedEvents(): FiredEventDao

    companion object {
        @Volatile private var instance: NudgeDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fired_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`reminderId` INTEGER NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `kind` TEXT NOT NULL, `firedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fired_events_firedAt` ON `fired_events` (`firedAt`)")
            }
        }

        fun get(context: Context): NudgeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NudgeDatabase::class.java, "nudge.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
