package com.easyword.learn.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 文字转语音（TTS）管理器，单例，全应用复用同一个 {@link TextToSpeech} 实例。
 *
 * <p>朗读不会因为引擎"还没准备好"而静默丢失：未就绪时先暂存文本，引擎就绪后自动补读。</p>
 *
 * <p>针对国产 ROM（OPPO 的 ColorOS、vivo 的 OriginOS 等）做了几件事，
 * 那里"点喇叭没声音"基本就是下面几种原因：</p>
 * <ul>
 *   <li><b>引擎绑定偶发失败</b>：同一个引擎会自动重试，再不行就换下一个已安装的引擎；</li>
 *   <li><b>默认引擎不支持中文</b>（例如只装了英文语音包）：换其它引擎，都不行时给出
 *       {@link Status#NO_CHINESE}，由 {@link TtsGuide} 引导去装中文语音包；</li>
 *   <li><b>没有任何可用引擎 / 被系统限制</b>：给出 {@link Status#NO_ENGINE}，引导去系统设置；</li>
 *   <li><b>媒体音量是 0</b>：第一次朗读时提示用户按音量键调大。</li>
 * </ul>
 */
public final class TTSManager {

    /** 引擎状态。 */
    public enum Status {
        /** 正在初始化（刚打开 App 的头一两秒）。 */
        INITIALIZING,
        /** 可以正常朗读。 */
        READY,
        /** 有引擎，但没有中文语音包（用它会读不出中文）。 */
        NO_CHINESE,
        /** 手机没有可用的朗读引擎，或者引擎绑定一直失败。 */
        NO_ENGINE
    }

    private static final String TAG = "TTSManager";

    /** 系统默认引擎的占位符：排在最前面，正常手机上行为跟以前完全一样。 */
    private static final String DEFAULT_ENGINE = "";

    /** 同一个引擎最多绑定几次（绑定失败往往是暂时的，多试一次常常就好了）。 */
    private static final int MAX_ATTEMPTS_PER_ENGINE = 2;
    /** 绑定失败后的重试间隔。 */
    private static final long RETRY_DELAY_MS = 500L;
    /** 明确不可用之后，隔多久允许再整体重试一轮（用户可能刚去系统里装好语音包）。 */
    private static final long REINIT_INTERVAL_MS = 5000L;
    /** 点喇叭时引导框的最短间隔，避免连环弹。 */
    private static final long PROMPT_INTERVAL_MS = 6000L;

    private static volatile TTSManager instance;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextToSpeech tts;
    private volatile Status status = Status.INITIALIZING;

    /** 依次尝试的引擎包名（{@link #DEFAULT_ENGINE} 表示系统默认）。 */
    private List<String> engines = new ArrayList<>();
    private int engineIndex;
    private int attemptsOfEngine;
    private boolean languageFailure;
    private String currentEngine = DEFAULT_ENGINE;
    /** 每轮初始化的编号，用来丢弃上一轮残留的异步回调。 */
    private int initToken;
    private long lastReinitAt;

    private String pendingText;
    private int pendingMode = TextToSpeech.QUEUE_FLUSH;
    private UtteranceProgressListener pendingListener;
    private long sequenceId;

    private WeakReference<Activity> host;
    private long lastPromptAt;
    private boolean autoWarned;
    private boolean volumeHintShown;

    private TTSManager(Context context) {
        appContext = context.getApplicationContext();
        startInit();
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

    // ---------- 初始化 ----------

    /** 从头初始化：先试系统默认引擎，再依次试其它已安装的引擎。 */
    private void startInit() {
        final int token = ++initToken;
        engines = new ArrayList<>();
        engines.add(DEFAULT_ENGINE);
        engines.addAll(installedEngines());
        engineIndex = 0;
        attemptsOfEngine = 0;
        languageFailure = false;
        status = Status.INITIALIZING;
        bindEngine(token, engines.get(0));
    }

    private void bindEngine(final int token, String enginePackage) {
        if (token != initToken) {
            return;
        }
        release();
        currentEngine = enginePackage;
        try {
            TextToSpeech.OnInitListener listener = code -> onInit(token, code);
            if (DEFAULT_ENGINE.equals(enginePackage)) {
                tts = new TextToSpeech(appContext, listener);
            } else {
                tts = new TextToSpeech(appContext, listener, enginePackage);
            }
        } catch (Exception e) {
            // 个别 ROM 上连构造都会抛（引擎服务被系统限制），不能让 App 崩
            Log.w(TAG, "创建朗读引擎失败：" + enginePackage, e);
            tts = null;
            languageFailure = false;
            nextAttempt(token);
        }
    }

    private void onInit(int token, int code) {
        if (token != initToken) {
            return;   // 上一轮初始化残留的回调，忽略
        }
        if (code == TextToSpeech.SUCCESS && tts != null) {
            applyAudioAttributes(tts);
            int result = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
            if (missingLanguage(result)) {
                result = tts.setLanguage(Locale.CHINESE);
            }
            if (!missingLanguage(result)) {
                status = Status.READY;
                flushPending();
                return;
            }
            languageFailure = true;
            Log.w(TAG, "引擎 " + currentEngine + " 不会中文，result=" + result);
        } else {
            languageFailure = false;
            Log.w(TAG, "引擎 " + currentEngine + " 绑定失败，status=" + code);
        }
        nextAttempt(token);
    }

    /** 当前引擎不行了：先原地重试，再换下一个引擎，都不行就给出明确状态。 */
    private void nextAttempt(final int token) {
        if (token != initToken) {
            return;
        }
        // 引擎本身起不来时原地重试：ColorOS 上偶发绑定失败，重试常常就好了
        if (!languageFailure && attemptsOfEngine + 1 < MAX_ATTEMPTS_PER_ENGINE) {
            attemptsOfEngine++;
            handler.postDelayed(() -> bindEngine(token, currentEngine),
                    RETRY_DELAY_MS * attemptsOfEngine);
            return;
        }
        attemptsOfEngine = 0;
        engineIndex++;
        if (engineIndex < engines.size()) {
            final String next = engines.get(engineIndex);
            handler.postDelayed(() -> bindEngine(token, next), RETRY_DELAY_MS);
            return;
        }
        // 所有引擎都试过了
        boolean hadRequest = pendingText != null;
        pendingText = null;
        pendingListener = null;
        status = languageFailure ? Status.NO_CHINESE : Status.NO_ENGINE;
        Log.w(TAG, "没有可用的朗读引擎，最终状态=" + status);
        // 用户这一轮点过喇叭（只是恰好撞上重试窗口），也得把原因说清楚
        if (hadRequest) {
            promptUnavailable(status);
        }
    }

    /**
     * 明确不可用时再试一轮（由 {@code BaseActivity.onResume} 和点喇叭触发）。
     * 用户去系统设置里装好语音包、回来就能自动恢复。
     */
    public void retryIfUnavailable() {
        if (status == Status.READY || status == Status.INITIALIZING) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastReinitAt < REINIT_INTERVAL_MS) {
            return;
        }
        lastReinitAt = now;
        startInit();
    }

    /** 枚举手机上装了的朗读引擎（系统默认引擎之外的备选）。 */
    private List<String> installedEngines() {
        List<String> packages = new ArrayList<>();
        try {
            Intent intent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
            List<ResolveInfo> services =
                    appContext.getPackageManager().queryIntentServices(intent, 0);
            if (services != null) {
                for (ResolveInfo info : services) {
                    if (info.serviceInfo == null || info.serviceInfo.packageName == null) {
                        continue;
                    }
                    String pkg = info.serviceInfo.packageName;
                    if (!packages.contains(pkg)) {
                        packages.add(pkg);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "枚举朗读引擎失败", e);
        }
        return packages;
    }

    private static void applyAudioAttributes(TextToSpeech engine) {
        try {
            engine.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
        } catch (Exception e) {
            Log.w(TAG, "设置音频属性失败", e);
        }
    }

    private static boolean missingLanguage(int result) {
        return result == TextToSpeech.LANG_MISSING_DATA
                || result == TextToSpeech.LANG_NOT_SUPPORTED;
    }

    // ---------- 朗读 ----------

    /**
     * 朗读指定文本。若引擎未就绪则暂存，就绪后自动补读；
     * 引擎不可用时会弹引导框，告诉用户怎么把声音修好。
     *
     * @param text 要朗读的文本
     */
    public void speak(String text) {
        speak(text, TextToSpeech.QUEUE_FLUSH, null);
    }

    /**
     * 朗读一段文本并监听其结束（用于逐行朗读与高亮跟随）。
     *
     * @param text       朗读内容
     * @param queueMode  队列模式：QUEUE_FLUSH 打断当前朗读，QUEUE_ADD 追加到队列
     * @param listener   启动/结束回调，可为 null
     */
    public void speak(String text, int queueMode, UtteranceProgressListener listener) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (status == Status.READY && tts != null) {
            speakNow(text, queueMode, listener);
            return;
        }
        // 引擎还没就绪：先记下来，就绪后补读
        pendingText = text;
        pendingMode = queueMode;
        pendingListener = listener;
        if (status == Status.INITIALIZING) {
            return;
        }
        // 真的不可用：说清楚为什么没声音，同时再试一轮
        promptUnavailable(status);
        retryIfUnavailable();
    }

    private void speakNow(String text, int queueMode, UtteranceProgressListener listener) {
        if (tts == null) {
            return;
        }
        warnIfVolumeZero();
        String id = "easyword_" + (++sequenceId);
        if (listener != null) {
            tts.setOnUtteranceProgressListener(listener);
        }
        tts.speak(text, queueMode, (Bundle) null, id);
    }

    /** 停止所有朗读（换页 / 手动点击时调用）。 */
    public void stop() {
        pendingText = null;
        pendingListener = null;
        if (tts != null) {
            tts.stop();
        }
    }

    /** 引擎就绪后把暂存的文本读出来。 */
    private void flushPending() {
        if (pendingText == null) {
            return;
        }
        String text = pendingText;
        int mode = pendingMode;
        UtteranceProgressListener listener = pendingListener;
        pendingText = null;
        pendingListener = null;
        pendingMode = TextToSpeech.QUEUE_FLUSH;
        speakNow(text, mode, listener);
    }

    // ---------- 状态与提示 ----------

    /** TTS 引擎是否已经可以朗读。 */
    public boolean isReady() {
        return status == Status.READY;
    }

    /** 设备是否缺少可用的中文语音数据。 */
    public boolean isLanguageMissing() {
        return status == Status.NO_CHINESE;
    }

    /** 当前引擎状态。 */
    public Status getStatus() {
        return status;
    }

    /**
     * 自动朗读用：引擎不可用时提示一次（整个进程只提示一次，不反复打扰）。
     * 首页一进来就该有声音，这里主动把"为什么没声音"说清楚。
     */
    public void warnIfUnavailable() {
        if (autoWarned || status == Status.READY || status == Status.INITIALIZING) {
            return;
        }
        autoWarned = true;
        promptUnavailable(status);
    }

    private void promptUnavailable(Status unavailable) {
        Activity activity = hostActivity();
        if (activity == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastPromptAt < PROMPT_INTERVAL_MS) {
            return;
        }
        lastPromptAt = now;
        TtsGuide.show(activity, unavailable);
    }

    /** 媒体音量是 0 的话，怎么读都没声音，提醒一次。 */
    private void warnIfVolumeZero() {
        if (volumeHintShown) {
            return;
        }
        Activity activity = hostActivity();
        if (activity == null) {
            return;
        }
        AudioManager audio = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null || audio.getStreamVolume(AudioManager.STREAM_MUSIC) > 0) {
            return;
        }
        volumeHintShown = true;
        TtsGuide.showVolumeZero(activity);
    }

    /**
     * 引导用户去装系统 TTS 语音数据（仅在缺引擎 / 缺中文语音时调用）。
     *
     * @param context 上下文
     */
    public void installDataIfNeeded(Context context) {
        if (status == Status.NO_ENGINE || status == Status.NO_CHINESE) {
            TtsGuide.openTtsSettings(context);
        }
    }

    // ---------- 前台页面登记 ----------

    /** 由 {@code BaseActivity} 登记当前前台页面：弹引导框时需要它。 */
    public void setHost(Activity activity) {
        host = new WeakReference<>(activity);
    }

    public void clearHost(Activity activity) {
        if (hostActivity() == activity) {
            host = null;
        }
    }

    private Activity hostActivity() {
        Activity activity = host == null ? null : host.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return null;
        }
        return activity;
    }

    // ---------- 释放 ----------

    /** 释放引擎资源。 */
    public void shutdown() {
        initToken++;
        handler.removeCallbacksAndMessages(null);
        release();
    }

    private void release() {
        if (tts == null) {
            return;
        }
        try {
            tts.shutdown();
        } catch (Exception e) {
            Log.w(TAG, "关闭 TTS 失败", e);
        }
        tts = null;
    }
}
