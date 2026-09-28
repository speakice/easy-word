package com.easyword.learn.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.easyword.learn.utils.WordLibrary;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据仓库：封装 DAO，向 ViewModel 提供数据。
 *
 * <p>只读 LiveData 直接透传 Room；写操作与同步统计在单线程后台执行器执行。
 * 首次打开时把 assets 里的 950 字库回填进数据库（只更新内容，保留用户学习/收藏记录）。</p>
 */
public class WordRepository {

    /** 后台结果回调。 */
    public interface Callback<T> {
        void onResult(T result);
    }

    private final WordDao wordDao;
    private final java.util.concurrent.ExecutorService ioExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WordRepository(WordDao wordDao) {
        this.wordDao = wordDao;
    }

    /** 后台执行一段读写。 */
    public void execute(java.lang.Runnable task) {
        ioExecutor.execute(task);
    }

    // ---------- 响应式查询 ----------

    public LiveData<List<Word>> getAllWords() {
        return wordDao.getAllWords();
    }

    public LiveData<List<Word>> getFavoriteWords() {
        return wordDao.getFavoriteWords();
    }

    public LiveData<List<Word>> getLearnedWords() {
        return wordDao.getLearnedWords();
    }

    public LiveData<List<Word>> getKnownWords() {
        return wordDao.getKnownWords();
    }

    public LiveData<List<Word>> getUnknownWords() {
        return wordDao.getUnknownWords();
    }

    // ---------- 写操作 ----------

    /** 记录一次学习：学习次数 +1、累计停留毫秒、更新时间、推进/保持记忆阶段。 */
    public void markStudied(int id, int count, long addMillis, long lastAt, int stage) {
        ioExecutor.execute(() -> wordDao.markStudied(id, count, addMillis, lastAt, stage));
    }

    /** 测验判定识字/不识，并调整推荐权重。 */
    public void updateKnown(int id, boolean known, int weight) {
        ioExecutor.execute(() -> wordDao.updateKnown(id, known, weight));
    }

    public void updateFavorite(int id, boolean favorite) {
        ioExecutor.execute(() -> wordDao.updateFavorite(id, favorite));
    }

    public void updateLearned(int id, boolean learned) {
        ioExecutor.execute(() -> wordDao.updateLearned(id, learned));
    }

    /** 累计今日学习时长。 */
    public void addDaily(String date, long millis) {
        ioExecutor.execute(() -> {
            Long cur = wordDao.getDailyMillis(date);
            wordDao.upsertDaily(new DailyRecord(date, (cur == null ? 0L : cur) + millis));
        });
    }

    /** 异步读取全部每日记录（日历热力图用）。 */
    public void loadDaily(Callback<List<DailyRecord>> callback) {
        ioExecutor.execute(() -> {
            List<DailyRecord> records = wordDao.getAllDaily();
            // 回调必须回到主线程：界面在回调里直接改 View，后台线程改 View 会崩溃
            mainHandler.post(() -> callback.onResult(records));
        });
    }

    // ---------- 字库回填 ----------

    /**
     * 把 assets 950 字库同步进数据库：新增缺失字，已有字仅刷新内容字段
     * （拼音/顺口溜/组词/场景/批次），不动收藏与学习记录。
     */
    public void seedLibrary(Context appContext, Runnable onDone) {
        ioExecutor.execute(() -> {
            try {
                List<WordLibrary.LibWord> lib = WordLibrary.all(appContext);
                List<Word> existing = wordDao.getAllWordsSync();
                Map<String, Word> byChar = new HashMap<>();
                for (Word w : existing) {
                    byChar.put(w.getWord(), w);
                }
                for (WordLibrary.LibWord lw : lib) {
                    String joined = join(lw.words, " ");
                    Word old = byChar.get(lw.ch);
                    if (old == null) {
                        Word nw = new Word();
                        nw.setWord(lw.ch);
                        nw.setPinyin(lw.py);
                        nw.setCategory("常用");
                        nw.setRhyme(lw.rhyme);
                        nw.setWords(joined);
                        nw.setUsage(lw.usage);
                        nw.setBatch(lw.batch);
                        wordDao.insert(nw);
                    } else {
                        boolean need = !equals(lw.py, old.getPinyin())
                                || !equals(lw.rhyme, old.getRhyme())
                                || !equals(joined, old.getWords())
                                || !equals(lw.usage, old.getUsage())
                                || old.getBatch() != lw.batch;
                        if (need) {
                            wordDao.updateLibraryContent(old.getId(), lw.py, lw.rhyme,
                                    joined, lw.usage, "常用", lw.batch);
                        }
                    }
                }
            } catch (Exception ignore) {
            }
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.post(onDone);
        });
    }

    private static String join(List<String> list, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static boolean equals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
