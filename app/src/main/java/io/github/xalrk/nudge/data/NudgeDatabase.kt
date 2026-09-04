package io.github.xalrk.nudge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Reminder::class, FiredEvent::class], version = 4, exportSchema = false)
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `color` INTEGER")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `sound` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `vibrate` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `excludedDates` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `meanOverrideMillis` INTEGER")
            }
        }

        fun get(context: Context): NudgeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NudgeDatabase::class.java, "nudge.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
