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

@Database(
    entities = [
        TrackedFolderEntity::class,
        VideoEntity::class,
        PlaybackProgressEntity::class,
        WatchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KidsTubeDatabase : RoomDatabase() {
    abstract fun trackedFolderDao(): TrackedFolderDao
    abstract fun videoDao(): VideoDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun watchHistoryDao(): WatchHistoryDao

    companion object {
        private const val DB_NAME = "kidstube_database.db"

        @Volatile
        private var INSTANCE: KidsTubeDatabase? = null

        fun getInstance(context: Context): KidsTubeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KidsTubeDatabase::class.java,
                    DB_NAME
                )
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
