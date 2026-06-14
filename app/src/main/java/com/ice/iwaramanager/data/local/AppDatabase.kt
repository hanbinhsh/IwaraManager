package com.ice.iwaramanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ice.iwaramanager.data.local.dao.MatchTaskDao
import com.ice.iwaramanager.data.local.dao.TagDao
import com.ice.iwaramanager.data.local.dao.VideoDao
import com.ice.iwaramanager.data.local.entity.IwaraTagEntity
import com.ice.iwaramanager.data.local.entity.MatchCandidateEntity
import com.ice.iwaramanager.data.local.entity.MatchTaskEntity
import com.ice.iwaramanager.data.local.entity.VideoEntity
import com.ice.iwaramanager.data.local.entity.VideoTagEntity

@Database(
    entities = [
        VideoEntity::class,
        IwaraTagEntity::class,
        VideoTagEntity::class,
        MatchTaskEntity::class,
        MatchCandidateEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun tagDao(): TagDao
    abstract fun matchTaskDao(): MatchTaskDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iwara_manager.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also {
                        INSTANCE = it
                    }
            }
        }

        fun closeCurrent() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE video ADD COLUMN matchedIwaraId TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteTitle TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteDescription TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteAuthorId TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteAuthorName TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteAuthorUsername TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteThumbnailUrl TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteRating TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteVisibility TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteCreatedAt TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteUpdatedAt TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteDurationSeconds INTEGER")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteLikeCount INTEGER")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteViewCount INTEGER")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteCommentCount INTEGER")
                db.execSQL("ALTER TABLE video ADD COLUMN remoteRawJson TEXT")
                db.execSQL("ALTER TABLE video ADD COLUMN matchStatus TEXT NOT NULL DEFAULT 'unmatched'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_libraryRootUriString ON video(libraryRootUriString)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_matchedIwaraId ON video(matchedIwaraId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_matchStatus ON video(matchStatus)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_remoteAuthorUsername ON video(remoteAuthorUsername)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_quality ON video(quality)")

                db.execSQL("CREATE TABLE IF NOT EXISTS iwara_tag (`key` TEXT NOT NULL, namespace TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(`key`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_iwara_tag_namespace_name ON iwara_tag(namespace, name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS video_tag (videoUriString TEXT NOT NULL, tagKey TEXT NOT NULL, PRIMARY KEY(videoUriString, tagKey))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_tag_videoUriString ON video_tag(videoUriString)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_video_tag_tagKey ON video_tag(tagKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS match_task (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, libraryRootUriString TEXT NOT NULL, videoUriString TEXT NOT NULL, displayName TEXT NOT NULL, coverFilePath TEXT, query TEXT NOT NULL, localDurationMs INTEGER, status TEXT NOT NULL, matchedIwaraId TEXT, candidateCount INTEGER NOT NULL, errorMessage TEXT, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_task_libraryRootUriString ON match_task(libraryRootUriString)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_task_videoUriString ON match_task(videoUriString)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_task_status ON match_task(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_task_createdAt ON match_task(createdAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS match_candidate (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, taskId INTEGER NOT NULL, iwaraId TEXT NOT NULL, title TEXT NOT NULL, authorName TEXT, authorUsername TEXT, thumbnailUrl TEXT, rating TEXT, createdAtText TEXT, durationSeconds INTEGER, viewCount INTEGER, likeCount INTEGER, selected INTEGER NOT NULL, rawJson TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_candidate_taskId ON match_candidate(taskId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_match_candidate_iwaraId ON match_candidate(iwaraId)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE video ADD COLUMN remoteAuthorAvatarUrl TEXT")
            }
        }
    }
}
