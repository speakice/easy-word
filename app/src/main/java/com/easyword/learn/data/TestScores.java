package com.easyword.learn.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 考试成绩记录：每个考试保存最好成绩和最近一次成绩（存在 SharedPreferences 里，
 * 不涉及数据库表结构，升级不会影响已有的学习记录）。
 *
 * <p>另外每次考试都会往 {@code history_<考试id>} 里追加一条「时间 + 分数」，
 * 所以同一个年级在不同日期考的每一次都单独保留，不会互相覆盖。</p>
 */
public final class TestScores {

    private static final String FILE = "easyword_scores";

    private final SharedPreferences prefs;

    public TestScores(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public void save(String testId, int score) {
        long now = System.currentTimeMillis();
        prefs.edit()
                .putInt("last_" + testId, score)
                .putInt("best_" + testId, Math.max(score, best(testId)))
                .putLong("time_" + testId, now)
                .putString("history_" + testId, appendAttempt(testId, now, score))
                .apply();
    }

    public int last(String testId) {
        return prefs.getInt("last_" + testId, -1);
    }

    public int best(String testId) {
        return prefs.getInt("best_" + testId, -1);
    }

    /** 最近一次考试的时间（毫秒），没考过返回 -1。 */
    public long time(String testId) {
        return prefs.getLong("time_" + testId, -1L);
    }

    /** 毕业证书是否已经申请并颁发。 */
    public boolean applied(String testId) {
        return prefs.getBoolean("applied_" + testId, false);
    }

    /** 申请毕业：记下申请时的成绩和颁发时间。 */
    public void applyForDiploma(String testId, int score) {
        prefs.edit()
                .putBoolean("applied_" + testId, true)
                .putInt("diploma_score_" + testId, score)
                .putLong("issued_" + testId, System.currentTimeMillis())
                .apply();
    }

    /** 证书颁发时间，没颁发返回 -1。 */
    public long issuedAt(String testId) {
        return prefs.getLong("issued_" + testId, -1L);
    }

    /** 毕业证书上记录的分数（申请那一刻的成绩）。 */
    public int diplomaScore(String testId) {
        return prefs.getInt("diploma_score_" + testId, 0);
    }

    /** 后来考得更好时，把毕业证书上的成绩更新上去。 */
    public void updateDiplomaScore(String testId, int score) {
        prefs.edit()
                .putInt("diploma_score_" + testId, score)
                .apply();
    }

    // ---------- 逐次记录 ----------

    /** 一次考试记录。 */
    public static class Attempt {
        public final String testId;
        /** 当天最好那一次的时间。 */
        public final long time;
        /** 当天的最好成绩。 */
        public final int score;
        /** 这一天一共考了几次（多次会合并成一条）。 */
        public final int count;

        Attempt(String testId, long time, int score, int count) {
            this.testId = testId;
            this.time = time;
            this.score = score;
            this.count = count;
        }

        public TestCatalog.TestSpec spec() {
            return TestCatalog.byId(testId);
        }
    }

    private String appendAttempt(String testId, long time, int score) {
        JSONArray arr = history(testId);
        // 老版本只存过"最近一次"，第一次写历史时先把它补进来，别丢记录
        if (arr.length() == 0) {
            int legacyLast = last(testId);
            long legacyTime = time(testId);
            if (legacyLast >= 0 && legacyTime > 0) {
                JSONObject old = new JSONObject();
                try {
                    old.put("t", legacyTime);
                    old.put("s", legacyLast);
                    arr.put(old);
                } catch (Exception ignored) {
                    // 补不进去也不影响新记录
                }
            }
        }
        JSONObject item = new JSONObject();
        try {
            item.put("t", time);
            item.put("s", score);
        } catch (Exception ignored) {
            return arr.toString();
        }
        arr.put(item);
        return arr.toString();
    }

    private JSONArray history(String testId) {
        try {
            return new JSONArray(prefs.getString("history_" + testId, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /**
     * 全部考试记录，按日期从新到旧排列。
     *
     * <p>规则：同一天、同一场考试考了多次，合并成一条并取当天最好的成绩；
     * 不同日期分开记录。老版本只存了"最好/最近一次"，会自动补成一条，不会丢成绩。</p>
     */
    public List<Attempt> allAttempts() {
        // key = 考试 id + 日期，同一天只留最高分
        java.util.LinkedHashMap<String, Attempt> merged = new java.util.LinkedHashMap<>();
        for (TestCatalog.TestSpec spec : TestCatalog.ALL) {
            JSONArray arr = history(spec.id);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                long time = item.optLong("t");
                int score = item.optInt("s");
                String key = spec.id + "@" + dayKey(time);
                Attempt old = merged.get(key);
                if (old == null) {
                    merged.put(key, new Attempt(spec.id, time, score, 1));
                } else if (score > old.score) {
                    merged.put(key, new Attempt(spec.id, time, score, old.count + 1));
                } else {
                    merged.put(key, new Attempt(spec.id, old.time, old.score, old.count + 1));
                }
            }
            if (arr.length() == 0) {
                int last = last(spec.id);
                long time = time(spec.id);
                if (last >= 0) {
                    merged.put(spec.id + "@" + dayKey(time),
                            new Attempt(spec.id, time, last, 1));
                }
            }
        }
        List<Attempt> out = new ArrayList<>(merged.values());
        Collections.sort(out, (a, b) -> Long.compare(b.time, a.time));
        return out;
    }

    /** 日期 key：yyyy-MM-dd。 */
    private static String dayKey(long millis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        return String.format(Locale.CHINA, "%04d-%02d-%02d",
                c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    /** 一共考过多少次。 */
    public int attemptCount() {
        return allAttempts().size();
    }

    /** 汇总文字，例如「已考 3 次 · 平均 72 分」。 */
    public String summary() {
        List<Attempt> all = allAttempts();
        if (all.isEmpty()) {
            return "还没考过";
        }
        int sum = 0;
        for (Attempt a : all) {
            sum += a.score;
        }
        return String.format(Locale.CHINA, "已考 %d 次 · 平均 %d 分",
                all.size(), Math.round(sum / (float) all.size()));
    }

}
