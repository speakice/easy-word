package com.easyword.learn.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.easyword.learn.data.DailyRecord;
import com.easyword.learn.data.Word;
import com.easyword.learn.data.WordDao;
import com.easyword.learn.data.WordDatabase;
import com.easyword.learn.data.WordRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 主视图模型：字库初始化、批次解锁（每组 80% 识字后进入下一组）、
 * 记忆曲线随机顺序、单字学习计时、测验权重与各项统计。
 */
public class WordViewModel extends AndroidViewModel {

    /** 记忆阶段间隔（分钟）：0 新字、1 五分钟、…、8 十五天。 */
    private static final long[] STAGE_MIN = {0, 5, 30, 720, 1440, 2880, 5760, 10080, 21600};

    public static class Stats {
        public final int total;
        public final int learned;
        public final int known;
        public final int unknown;
        public final int currentBatch;
        /** 每一批（年级）的识字进度百分比，下标 1~10；考试解锁就看这个。 */
        public final int[] batchPercent;
        /** 每一批已识的字数，下标 1~10。 */
        public final int[] batchKnown;
        /** 每一批已学的字数，下标 1~10。 */
        public final int[] batchLearned;

        Stats(int total, int learned, int known, int unknown, int currentBatch,
              int[] batchPercent, int[] batchKnown, int[] batchLearned) {
            this.total = total;
            this.learned = learned;
            this.known = known;
            this.unknown = unknown;
            this.currentBatch = currentBatch;
            this.batchPercent = batchPercent;
            this.batchKnown = batchKnown;
            this.batchLearned = batchLearned;
        }

        /** 某一批的学习进度（0~100）。 */
        public int percentOfBatch(int batch) {
            if (batchPercent == null || batch < 1 || batch >= batchPercent.length) {
                return 0;
            }
            return batchPercent[batch];
        }

        /** 某一批已识的字数。 */
        public int knownOfBatch(int batch) {
            if (batchKnown == null || batch < 1 || batch >= batchKnown.length) {
                return 0;
            }
            return batchKnown[batch];
        }

        /** 某一批已学的字数。 */
        public int learnedOfBatch(int batch) {
            if (batchLearned == null || batch < 1 || batch >= batchLearned.length) {
                return 0;
            }
            return batchLearned[batch];
        }
    }

    private final WordRepository repository;
    private final MutableLiveData<List<Word>> studyOrder = new MutableLiveData<>();
    private final MutableLiveData<Stats> stats = new MutableLiveData<>();
    private final MutableLiveData<List<Word>> quizDeck = new MutableLiveData<>();
    private long pendingDaily;
    private boolean seeded;

    public WordViewModel(@NonNull Application application) {
        super(application);
        WordDao dao = WordDatabase.getInstance(application).wordDao();
        repository = new WordRepository(dao);
    }

    /** 首次初始化：回填 950 字库并生成学习顺序。 */
    public void init() {
        if (seeded) {
            refreshStudyOrder();
            return;
        }
        seeded = true;
        repository.seedLibrary(getApplication(), this::refreshStudyOrder);
    }

    // ---------- 学习顺序（记忆曲线） ----------

    /**
     * 解锁当前批次并构建首页滑动顺序（随机化 + 权重排序）：
     * 未识字(学过但没记牢，权重高优先) → 全新字 → 到期的已识字复习 → 未到期已识字。
     */
    public void refreshStudyOrder() {
        repository.execute(() -> {
            int currentBatch = currentBatch();
            List<Word> all = repository != null ? daoList(currentBatch) : null;
            if (all == null) {
                return;
            }
            long now = System.currentTimeMillis();
            List<Word> pristine = new ArrayList<>();
            List<Word> weak = new ArrayList<>();
            List<Word> due = new ArrayList<>();
            List<Word> fresh = new ArrayList<>();
            for (Word w : all) {
                if (!w.isLearned()) {
                    pristine.add(w);
                } else if (!w.isKnown()) {
                    weak.add(w);
                } else if (isDue(w, now)) {
                    due.add(w);
                } else {
                    fresh.add(w);
                }
            }
            // 权重越高越靠前（测验判定“不识”后加大首页权重）
            weak.sort((a, b) -> Integer.compare(b.getWeight(), a.getWeight()));
            List<Word> order = new ArrayList<>();
            order.addAll(shuffle(weak));
            order.addAll(shuffle(pristine));
            order.addAll(shuffle(due));
            order.addAll(shuffle(fresh));
            studyOrder.postValue(order);

            WordDao dao = dao();
            // 记录每一批的学习进度（识字率），考试解锁用
            int[] batchPercent = new int[11];
            int[] batchKnown = new int[11];
            int[] batchLearned = new int[11];
            for (int b = 1; b <= 10; b++) {
                int batchTotal = dao.countInBatch(b);
                batchKnown[b] = dao.countKnownInBatch(b);
                batchLearned[b] = dao.countLearnedInBatch(b);
                // 学习进度 = 已学比例（考试解锁也用它）
                batchPercent[b] = batchTotal == 0
                        ? 0 : batchLearned[b] * 100 / batchTotal;
            }
            stats.postValue(new Stats(dao.countWords(), dao.countLearned(),
                    dao.countKnown(), dao.countUnknown(), currentBatch,
                    batchPercent, batchKnown, batchLearned));
        });
    }

    private List<Word> daoList(int batch) {
        return dao().getWordsUpToBatchSync(batch);
    }

    private WordDao dao() {
        return WordDatabase.getInstance(getApplication()).wordDao();
    }

    private int currentBatch() {
        WordDao dao = dao();
        int cur = 1;
        for (int b = 1; b <= 10; b++) {
            int total = dao.countInBatch(b);
            if (total == 0) {
                break;
            }
            // 按"已学"算批次进度：首页翻到这个字就算学过，不会卡住（已识要靠考试判定）
            int learned = dao.countLearnedInBatch(b);
            if (learned * 100 >= total * 80) {
                cur = b + 1;
            } else {
                break;
            }
        }
        return Math.min(cur, 10);
    }

    private static boolean isDue(Word w, long now) {
        int stage = Math.min(8, w.getReviewStage());
        if (stage <= 0) {
            return w.getLastStudyAt() > 0 && now - w.getLastStudyAt() >= STAGE_MIN[1] * 60000L;
        }
        return now - w.getLastStudyAt() >= STAGE_MIN[stage] * 60000L;
    }

    private static List<Word> shuffle(List<Word> list) {
        List<Word> out = new ArrayList<>(list);
        Collections.shuffle(out);
        return out;
    }

    // ---------- 学习行为 ----------

    /** 记录单字停留：学习次数+1、累计时长入账、今日时长累计。 */
    public void recordStudy(Word word, long millis) {
        if (word == null || word.isLearned()) {
            return;
        }
        long now = System.currentTimeMillis();
        int stage = nextStage(word, now);
        repository.markStudied(word.getId(), word.getLearnCount() + 1, Math.max(0, millis),
                now, stage);
        addDaily(millis);
    }

    /** 重复学习已识字时也记时长并推进记忆阶段。 */
    public void recordReview(Word word, long millis) {
        if (word == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int stage = nextStage(word, now);
        repository.markStudied(word.getId(), word.getLearnCount() + 1, Math.max(0, millis),
                now, stage);
        addDaily(millis);
    }

    private static int nextStage(Word w, long now) {
        if (!w.isKnown()) {
            return 0;
        }
        int stage = Math.min(8, w.getReviewStage());
        long interval = STAGE_MIN[stage] * 60000L;
        if (stage > 0 && now - w.getLastStudyAt() >= interval) {
            return Math.min(8, stage + 1);
        }
        return stage;
    }

    /** 今日学习时长入账（写库）。 */
    public void addDaily(long millis) {
        if (millis <= 0) {
            return;
        }
        pendingDaily += millis;
        repository.addDaily(today(), millis);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public void toggleFavorite(Word word) {
        repository.updateFavorite(word.getId(), !word.isFavorite());
    }

    public void setFavorite(int id, boolean favorite) {
        repository.updateFavorite(id, favorite);
    }

    /** 测验：认识 → 识字并降低首页权重；不认识 → 未识并加大首页权重。 */
    public void answerQuiz(Word word, boolean known) {
        if (word == null) {
            return;
        }
        int weight = known ? Math.max(0, word.getWeight() - 2) : word.getWeight() + 3;
        repository.updateKnown(word.getId(), known, weight);
        refreshStudyOrder();
        refreshQuiz();
    }

    /** 测验字卡：学习次数最多的 + 权重高优先，取前 20。 */
    public void refreshQuiz() {
        repository.execute(() -> {
            List<Word> learned = dao().getLearnedWordsSync();
            learned.sort((a, b) -> {
                int c = Integer.compare(b.getLearnCount(), a.getLearnCount());
                return c != 0 ? c : Integer.compare(b.getWeight(), a.getWeight());
            });
            List<Word> deck = new ArrayList<>();
            for (int i = 0; i < learned.size() && i < 20; i++) {
                deck.add(learned.get(i));
            }
            Collections.shuffle(deck);
            quizDeck.postValue(deck);
        });
    }

    /** 每日记录（我的页热力图）。 */
    public void loadDaily(WordRepository.Callback<List<DailyRecord>> cb) {
        repository.loadDaily(cb);
    }

    /** 异步取识字集合（阅读推荐用）。 */
    public void loadKnownChars(java.util.function.Consumer<Set<String>> callback) {
        repository.execute(() -> {
            Set<String> set = new HashSet<>();
            for (Word w : dao().getKnownWordsSync()) {
                set.add(w.getWord());
            }
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> callback.accept(set));
        });
    }

    /** 列表类型：0 已学 / 1 已识 / 2 未识 / 3 收藏。 */
    public static final int TYPE_LEARNED = 0;
    public static final int TYPE_KNOWN = 1;
    public static final int TYPE_UNKNOWN = 2;
    public static final int TYPE_FAVORITE = 3;

    /** 异步取某种字表（我的页/字表学习页用）。 */
    public void loadList(int type, java.util.function.Consumer<List<Word>> callback) {
        repository.execute(() -> {
            List<Word> list;
            switch (type) {
                case TYPE_KNOWN:
                    list = dao().getKnownWordsSync();
                    break;
                case TYPE_UNKNOWN:
                    list = dao().getUnknownWordsSync();
                    break;
                case TYPE_FAVORITE:
                    list = dao().getFavoriteWordsSync();
                    break;
                default:
                    list = dao().getLearnedWordsSync();
                    break;
            }
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> callback.accept(list));
        });
    }

    // ---------- 单元测试 / 期末考试 ----------

    /** 取某个批次（年级）的全部字，用来出单元测试题。 */
    public void loadBatchWords(int batch, java.util.function.Consumer<List<Word>> callback) {
        repository.execute(() -> {
            List<Word> list = dao().getWordsInBatchSync(batch);
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> callback.accept(list));
        });
    }

    /** 取前 N 批的全部字，用来出期末考试题。 */
    public void loadWordsUpToBatch(int batch, java.util.function.Consumer<List<Word>> callback) {
        repository.execute(() -> {
            List<Word> list = dao().getWordsUpToBatchWithPinyinSync(batch);
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> callback.accept(list));
        });
    }

    /**
     * 把一次测试的结果写回学习数据：答对的记为识字并降低首页权重，
     * 答错的加入未识表并提高权重，让首页多推几次。
     */
    public void applyTestResults(List<Word> correctWords, List<Word> wrongWords) {
        repository.execute(() -> {
            WordDao dao = dao();
            if (correctWords != null) {
                for (Word w : correctWords) {
                    dao.updateKnown(w.getId(), true, Math.max(0, w.getWeight() - 2));
                }
            }
            if (wrongWords != null) {
                for (Word w : wrongWords) {
                    dao.updateKnown(w.getId(), false, w.getWeight() + 3);
                }
            }
        });
        refreshStudyOrder();
    }

    // ---------- 暴露 ----------

    public LiveData<List<Word>> getStudyOrder() {
        return studyOrder;
    }

    /** 收藏列表（响应式），供收藏页使用。 */
    public LiveData<List<Word>> getFavoriteWords() {
        return repository.getFavoriteWords();
    }

    public LiveData<Stats> getStats() {
        return stats;
    }

    public LiveData<List<Word>> getQuizDeck() {
        return quizDeck;
    }
}
