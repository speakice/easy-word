package com.easyword.learn.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 个性化设置：昵称、头像、考试门槛（解锁进度 / 及格 / 良好 / 优秀）。
 * 全部存在本机，随时可改。
 */
public final class Settings {

    private static final String FILE = "easyword_settings";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_AVATAR = "avatar_file";
    private static final String KEY_UNLOCK = "unlock_percent";
    private static final String KEY_PASS = "pass_score";
    private static final String KEY_GOOD = "good_score";
    private static final String KEY_EXCELLENT = "excellent_score";

    public static final String DEFAULT_NICKNAME = "我的学习";
    public static final int DEFAULT_UNLOCK = 80;
    public static final int DEFAULT_PASS = 60;
    public static final int DEFAULT_GOOD = 80;
    public static final int DEFAULT_EXCELLENT = 90;

    private Settings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---------- 昵称 / 头像 ----------

    public static String nickname(Context context) {
        String value = prefs(context).getString(KEY_NICKNAME, DEFAULT_NICKNAME);
        return value == null || value.trim().isEmpty() ? DEFAULT_NICKNAME : value.trim();
    }

    public static void setNickname(Context context, String nickname) {
        prefs(context).edit().putString(KEY_NICKNAME,
                nickname == null ? "" : nickname.trim()).apply();
    }

    /** 头像图片在本机的路径；没设置过返回 null。 */
    public static String avatarPath(Context context) {
        return prefs(context).getString(KEY_AVATAR, null);
    }

    public static void setAvatarPath(Context context, String path) {
        prefs(context).edit().putString(KEY_AVATAR, path).apply();
    }

    // ---------- 考试门槛 ----------

    /** 本批识字率达到这个百分比才解锁该年级考试。 */
    public static int unlockPercent(Context context) {
        return clamp(prefs(context).getInt(KEY_UNLOCK, DEFAULT_UNLOCK), 1, 100);
    }

    public static void setUnlockPercent(Context context, int value) {
        prefs(context).edit().putInt(KEY_UNLOCK, clamp(value, 1, 100)).apply();
    }

    /** 及格分数线（毕业考试 / 成绩是否合格）。 */
    public static int passScore(Context context) {
        return clamp(prefs(context).getInt(KEY_PASS, DEFAULT_PASS), 1, 100);
    }

    public static void setPassScore(Context context, int value) {
        prefs(context).edit().putInt(KEY_PASS, clamp(value, 1, 100)).apply();
    }

    /** 良好分数线。 */
    public static int goodScore(Context context) {
        return clamp(prefs(context).getInt(KEY_GOOD, DEFAULT_GOOD), 1, 100);
    }

    public static void setGoodScore(Context context, int value) {
        prefs(context).edit().putInt(KEY_GOOD, clamp(value, 1, 100)).apply();
    }

    /** 优秀分数线。 */
    public static int excellentScore(Context context) {
        return clamp(prefs(context).getInt(KEY_EXCELLENT, DEFAULT_EXCELLENT), 1, 100);
    }

    public static void setExcellentScore(Context context, int value) {
        prefs(context).edit().putInt(KEY_EXCELLENT, clamp(value, 1, 100)).apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------- 成绩评语（跟着设置走） ----------

    /** 考试结果页的评语：优秀 / 良好 / 及格 / 要加油。 */
    public static String comment(Context context, int score) {
        if (score >= excellentScore(context)) {
            return "优秀";
        }
        if (score >= goodScore(context)) {
            return "良好";
        }
        if (score >= passScore(context)) {
            return "及格";
        }
        return "要加油";
    }

    /** 毕业证上的用语：成绩优秀 / 成绩良好 / 成绩合格。 */
    public static String gradeWord(Context context, int score) {
        if (score >= excellentScore(context)) {
            return "成绩优秀";
        }
        if (score >= goodScore(context)) {
            return "成绩良好";
        }
        return "成绩合格";
    }
}
