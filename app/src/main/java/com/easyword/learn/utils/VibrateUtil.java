package com.easyword.learn.utils;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/**
 * 触感反馈工具：为收藏、切页、清除笔迹等交互提供轻微震动反馈。
 */
public final class VibrateUtil {

    private VibrateUtil() {
    }

    /**
     * 触发一次轻震动。
     *
     * @param context 上下文
     * @param millis  震动时长（毫秒），15~25 为轻反馈，40~60 为醒目反馈
     */
    public static void vibrate(Context context, int millis) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(Math.max(1, millis),
                    VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            // API 24~25 使用兼容接口
            vibrator.vibrate(millis);
        }
    }
}