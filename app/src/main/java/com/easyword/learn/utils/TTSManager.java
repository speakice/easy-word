package com.easyword.learn.utils;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * 文字转语音（TTS）管理器，单例，全应用复用同一个 {@link TextToSpeech} 实例。
 *
 * <p>朗读不会因为引擎“还没准备好”而静默丢失：未就绪时先暂存文本，
 * 引擎就绪后自动补读。若设备缺中文语音数据，可通过
 * {@link #installDataIfNeeded(Context)} 拉起系统安装页。</p>
 */
public final class TTSManager {

    private static final String TAG = "TTSManager";
    private static volatile TTSManager instance;

    // 非 final：构造时在引擎回调中还要引用它设置语言
    private TextToSpeech tts;
    private volatile boolean ready;
    private volatile boolean languageMissing;
    private String pendingText;
    private long sequenceId;

    private TTSManager(Context context) {
        tts = new TextToSpeech(context.getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.SUCCESS) {
                    Log.w(TAG, "TTS init failed, status=" + status);
                    return;
                }
                // 依次尝试多种中文语言，保证尽量能读
                int result = tts.setLanguage(Locale.CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                }
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault()); // 退回系统默认语言
                    languageMissing = true;
                }
                ready = true;
                flushPending();
            }
        });
    }

    /** 获取单例，首次调用时初始化引擎。 */
    public static TTSManager getInstance(Context context) {
        if (instance == null) {
            synchronized (TTSManager.class) {
                if (instance == null) {
                    instance = new TTSManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 朗读指定文本。若引擎未就绪则暂存，就绪后自动补读。
     *
     * @param text 要朗读的文本
     */
    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!ready) {
            pendingText = text;
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word_" + text);
    }

    /**
     * 朗读一段文本并监听其结束（用于逐行朗读与高亮跟随）。
     *
     * @param text       朗读内容
     * @param queueMode  队列模式：QUEUE_FLUSH 打断当前朗读，QUEUE_ADD 追加到队列
     * @param listener   启动/结束回调，可为 null
     */
    public void speak(String text, int queueMode, UtteranceProgressListener listener) {
        if (text == null || text.trim().isEmpty() || !ready) {
            return;
        }
        String id = "seq_" + (++sequenceId);
        if (listener != null) {
            // 通过全局进度监听器接收当前段落的开始/结束回调
            tts.setOnUtteranceProgressListener(listener);
        }
        tts.speak(text, queueMode, (Bundle) null, id);
    }

    /** 停止所有朗读（换页/手动点击时调用）。 */
    public void stop() {
        if (tts != null) {
            tts.stop();
        }
    }

    /** 引擎就绪后把暂存的文本读出来。 */
    private void flushPending() {
        synchronized (TTSManager.class) {
            if (pendingText != null) {
                String text = pendingText;
                pendingText = null;
                speak(text);
            }
        }
    }

    /** TTS 引擎是否已就绪。 */
    public boolean isReady() {
        return ready;
    }

    /** 设备是否缺少可用的中文语音数据。 */
    public boolean isLanguageMissing() {
        return languageMissing;
    }

    /**
     * 引导用户安装系统 TTS 语音数据（打开系统设置页，仅在缺数据时调用）。
     *
     * @param context 上下文
     */
    public void installDataIfNeeded(Context context) {
        if (!languageMissing) {
            return;
        }
        try {
            context.startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
        } catch (Exception e) {
            Log.w(TAG, "open TTS install page failed", e);
        }
    }

    /** 释放引擎资源。 */
    public void shutdown() {
        if (tts != null) {
            tts.shutdown();
        }
    }
}