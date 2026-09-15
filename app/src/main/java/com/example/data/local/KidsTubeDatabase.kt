package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.PlaybackProgressDao
import com.example.data.local.dao.TrackedFolderDao
import com.example.data.local.dao.VideoDao
import com.example.data.local.dao.WatchHistoryDao
import com.example.data.local.entity.PlaybackProgressEntity
import com.example.data.local.entity.TrackedFolderEntity
import com.example.data.local.entity.VideoEntity
import com.example.data.local.entity.WatchHistoryEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrackedFolderEntity::class,
        VideoEntity::class,
        PlaybackProgressEntity::class,
        WatchHistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KidsTubeDatabase : RoomDatabase() {
    abstract fun trackedFolderDao(): TrackedFolderDao
    abstract fun videoDao(): VideoDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        private const val DB_NAME = "kidstube_database.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracked_folders ADD COLUMN isPermissionGranted INTEGER NOT NULL DEFAULT 1")
            }
        }

        @Volatile
        private var INSTANCE: KidsTubeDatabase? = null

        fun getInstance(context: Context): KidsTubeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KidsTubeDatabase::class.java,
                    DB_NAME
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
