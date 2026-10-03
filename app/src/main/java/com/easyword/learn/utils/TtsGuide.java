package com.easyword.learn.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;

import com.easyword.learn.R;

/**
 * 朗读不出声时的引导。
 *
 * <p>长辈的手机上常见三种情况：没有可用的朗读引擎（ColorOS / OriginOS 上还可能被系统限制住）、
 * 有引擎但没装中文语音包、媒体音量被按到了 0。这三种在 App 里表现出来的都是"点了喇叭没反应"，
 * 所以这里统一弹一个说人话的框，并给一个能直接跳到系统设置的按钮。</p>
 */
public final class TtsGuide {

    private static final String TAG = "TtsGuide";

    /** 同一时间只留一个引导框，避免点几下叠一堆。 */
    private static AlertDialog current;

    private TtsGuide() {
    }

    /** 按引擎状态弹对应的引导框。 */
    public static void show(Activity activity, TTSManager.Status status) {
        if (!canShow(activity)) {
            return;
        }
        if (status == TTSManager.Status.NO_ENGINE) {
            showDialog(activity, R.string.tts_guide_no_engine_title,
                    R.string.tts_guide_no_engine_body, R.string.tts_guide_open_settings);
        } else if (status == TTSManager.Status.NO_CHINESE) {
            showDialog(activity, R.string.tts_guide_no_chinese_title,
                    R.string.tts_guide_no_chinese_body, R.string.tts_guide_install);
        }
    }

    /** 媒体音量为 0 的提示。 */
    public static void showVolumeZero(Activity activity) {
        if (!canShow(activity)) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.tts_guide_volume_title)
                .setMessage(R.string.tts_guide_volume_body)
                .setPositiveButton(R.string.tts_guide_ok, null);
        current = builder.show();
    }

    /**
     * 打开系统的「文字转语音」设置页。各家 ROM 的入口不一样，逐个试。
     *
     * @param context 上下文
     */
    public static void openTtsSettings(Context context) {
        if (start(context, new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))) {
            return;
        }
        if (start(context, new Intent("com.android.settings.TTS_SETTINGS"))) {
            return;
        }
        if (start(context, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) {
            return;
        }
        Toast.makeText(context, R.string.tts_guide_manual, Toast.LENGTH_LONG).show();
    }

    private static void showDialog(Activity activity, int titleRes, int bodyRes, int actionRes) {
        current = new AlertDialog.Builder(activity)
                .setTitle(titleRes)
                .setMessage(bodyRes)
                .setPositiveButton(actionRes, (dialog, which) -> openTtsSettings(activity))
                .setNegativeButton(R.string.tts_guide_ok, null)
                .show();
    }

    private static boolean canShow(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return false;
        }
        return current == null || !current.isShowing();
    }

    private static boolean start(Context context, Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "打不开 " + intent.getAction(), e);
            return false;
        }
    }
}
