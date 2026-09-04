package io.github.xalrk.nudge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Reminder::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class NudgeDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao

    companion object {
        @Volatile private var instance: NudgeDatabase? = null

        fun get(context: Context): NudgeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, NudgeDatabase::class.java, "nudge.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
