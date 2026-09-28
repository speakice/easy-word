package com.easyword.learn.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * 应用唯一的 Room 数据库实例（单例）。
 *
 * <p>数据库名 {@code easyword.db}，版本 3。v1→v2 为旧版 52 字补充顺口溜/组词/场景三列；
 * v2→v3 增加学习统计字段、记忆阶段、识字标记与每日学习时长表。字库内容由仓库在初始化时
 * 从 assets 字库全量回填（同时保留用户学习/收藏记录）。</p>
 */
@Database(entities = {Word.class, DailyRecord.class}, version = 3, exportSchema = false)
public abstract class WordDatabase extends RoomDatabase {

    private static volatile WordDatabase INSTANCE;

    /** @return 数据访问对象 */
    public abstract WordDao wordDao();

    /**
     * 获取数据库单例（线程安全）。
     *
     * @param context 上下文，建议传入 Application
     * @return {@link WordDatabase} 实例
     */
    public static WordDatabase getInstance(@NonNull Context context) {
        if (INSTANCE == null) {
            synchronized (WordDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    WordDatabase.class, "easyword.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /** v1 → v2：新增顺口溜/组词/场景三列并回填旧字库内容。 */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE words ADD COLUMN rhyme TEXT");
            database.execSQL("ALTER TABLE words ADD COLUMN words TEXT");
            database.execSQL("ALTER TABLE words ADD COLUMN usage TEXT");
        }
    };

    /** v2 → v3：新增学习统计/识字标记列与每日学习时长表。 */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE words ADD COLUMN batch INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE words ADD COLUMN weight INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE words ADD COLUMN learnCount INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE words ADD COLUMN studyMillis INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE words ADD COLUMN lastStudyAt INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE words ADD COLUMN reviewStage INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE words ADD COLUMN is_known INTEGER NOT NULL DEFAULT 0");
            database.execSQL("CREATE TABLE IF NOT EXISTS daily ("
                    + "date TEXT NOT NULL PRIMARY KEY, millis INTEGER NOT NULL)");
        }
    };
}