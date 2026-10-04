package com.easyword.learn.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * 汉字的数据库访问对象（DAO）。读操作返回 {@link LiveData} 由 Room 自动异步执行；
 * 写操作在上层仓库的后台线程中调用。
 */
@Dao
public interface WordDao {

    // ---------- 同步查询（用于建库回填与统计） ----------

    @Query("SELECT * FROM words")
    List<Word> getAllWordsSync();

    @Query("SELECT * FROM words WHERE word = :ch LIMIT 1")
    Word findByChar(String ch);

    @Query("SELECT COUNT(*) FROM words")
    int countWords();

    @Query("SELECT COUNT(*) FROM words WHERE is_known = 1 AND is_learned = 1")
    int countKnown();

    @Query("SELECT COUNT(*) FROM words WHERE is_learned = 1")
    int countLearned();

    @Query("SELECT COUNT(*) FROM words WHERE is_learned = 1 AND is_known = 0")
    int countUnknown();

    @Query("SELECT COUNT(*) FROM words WHERE batch = :batch AND is_known = 1 AND is_learned = 1")
    int countKnownInBatch(int batch);

    @Query("SELECT COUNT(*) FROM words WHERE batch = :batch AND is_learned = 1")
    int countLearnedInBatch(int batch);

    @Query("SELECT COUNT(*) FROM words WHERE batch = :batch")
    int countInBatch(int batch);

    @Query("SELECT * FROM words WHERE batch <= :batch")
    List<Word> getWordsUpToBatchSync(int batch);

    @Query("SELECT * FROM words WHERE batch = :batch AND pinyin != ''")
    List<Word> getWordsInBatchSync(int batch);

    @Query("SELECT * FROM words WHERE batch <= :batch AND pinyin != ''")
    List<Word> getWordsUpToBatchWithPinyinSync(int batch);

    @Query("SELECT id FROM words WHERE batch = :batch ORDER BY id ASC")
    List<Integer> idsInBatch(int batch);

    @Query("UPDATE words SET batch = :batch WHERE id = :id")
    void updateBatch(int id, int batch);

    /**
     * 把字挪到别的批次，同时换一个更小的 id：常用字要排在选定批次的最前面，
     * 这样每批超出 100 字顺延时，让出去的是这一批原有的字。
     */
    @Query("UPDATE words SET id = :newId, batch = :batch WHERE id = :oldId")
    void moveToBatch(int oldId, int newId, int batch);

    @Query("SELECT COUNT(*) FROM words WHERE word = :ch")
    int countByChar(String ch);

    @Query("SELECT * FROM words WHERE is_learned = 1")
    List<Word> getLearnedWordsSync();

    @Query("SELECT * FROM words WHERE is_learned = 1 AND is_known = 1")
    List<Word> getKnownWordsSync();

    @Query("SELECT * FROM words WHERE is_favorite = 1")
    List<Word> getFavoriteWordsSync();

    @Query("SELECT * FROM words WHERE is_learned = 1 AND is_known = 0")
    List<Word> getUnknownWordsSync();

    // ---------- 响应式查询 ----------

    @Query("SELECT * FROM words ORDER BY batch ASC, id ASC")
    LiveData<List<Word>> getAllWords();

    @Query("SELECT * FROM words WHERE batch <= :batch")
    LiveData<List<Word>> getWordsUpToBatch(int batch);

    @Query("SELECT * FROM words WHERE is_learned = 1 AND is_known = 0")
    LiveData<List<Word>> getUnknownWords();

    @Query("SELECT * FROM words WHERE is_learned = 1 AND is_known = 1")
    LiveData<List<Word>> getKnownWords();

    @Query("SELECT * FROM words WHERE is_learned = 1")
    LiveData<List<Word>> getLearnedWords();

    @Query("SELECT * FROM words WHERE is_favorite = 1")
    LiveData<List<Word>> getFavoriteWords();

    // ---------- 写操作 ----------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Word word);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<Word> words);

    /** 回填字库内容（保留学习/收藏/统计字段）。 */
    @Query("UPDATE words SET pinyin = :py, rhyme = :rhyme, words = :words, usage = :usage, "
            + "category = :category, batch = :batch WHERE id = :id")
    void updateLibraryContent(int id, String py, String rhyme, String words,
                              String usage, String category, int batch);

    @Query("UPDATE words SET is_favorite = :favorite WHERE id = :id")
    void updateFavorite(int id, boolean favorite);

    @Query("UPDATE words SET is_learned = :learned WHERE id = :id")
    void updateLearned(int id, boolean learned);

    @Query("UPDATE words SET is_known = :known, weight = :weight WHERE id = :id")
    void updateKnown(int id, boolean known, int weight);

    @Query("UPDATE words SET weight = :weight WHERE id = :id")
    void updateWeight(int id, int weight);

    @Query("UPDATE words SET learnCount = :count, studyMillis = studyMillis + :addMillis, "
            + "lastStudyAt = :lastAt, reviewStage = :stage, is_learned = 1 WHERE id = :id")
    void markStudied(int id, int count, long addMillis, long lastAt, int stage);

    @Query("UPDATE words SET reviewStage = :stage, lastStudyAt = :lastAt WHERE id = :id")
    void updateStage(int id, int stage, long lastAt);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertDaily(DailyRecord record);

    @Query("SELECT millis FROM daily WHERE date = :date LIMIT 1")
    Long getDailyMillis(String date);

    @Query("SELECT * FROM daily ORDER BY date ASC")
    List<DailyRecord> getAllDaily();
}
