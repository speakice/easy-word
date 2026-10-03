package com.easyword.learn.utils;

import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;

import com.easyword.learn.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 按顺序朗读一组小方块（声母 / 韵母 / 整数 / 小数 / 加法表 / 乘法表），
 * 读到哪一格就给哪一格套上黄色选中边框；同一个分类的喇叭再点一次就停。
 */
public class SequenceSpeaker {

    private final TTSManager tts;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<View> tiles = new ArrayList<>();
    private final List<String> says = new ArrayList<>();
    private int token;
    private int playingId = -1;

    /** 单格朗读的人工兜底时限：按字数估算，引擎不出声时也不让黄框一直亮着。 */
    private static final long ONE_SHOT_BASE_MS = 2000L;
    private static final long ONE_SHOT_PER_CHAR_MS = 400L;
    private static final long ONE_SHOT_MAX_MS = 15000L;

    public SequenceSpeaker(TTSManager tts) {
        this.tts = tts;
    }

    /** 正在朗读的分类 id；没在朗读时返回 -1。 */
    public int playingId() {
        return playingId;
    }

    /**
     * 点分类喇叭：没在读就开始按顺序读，正在读这个分类就停下来。
     *
     * @param id    分类标识（每个喇叭一个）
     * @param views 这一组的小方块，顺序 = 朗读顺序
     * @param texts 每格要朗读的内容
     */
    public void toggle(int id, List<View> views, List<String> texts) {
        boolean same = playingId == id;
        stop();
        if (same || views.isEmpty()) {
            return;
        }
        tiles.clear();
        tiles.addAll(views);
        says.clear();
        says.addAll(texts);
        playingId = id;
        final int t = ++token;
        playNext(0, t, 0);
    }

    /** 停止朗读并清掉选中边框。 */
    public void stop() {
        playingId = -1;
        token++;
        clearHighlight();
        tts.stop();
    }

    /**
     * 手动点某一格：只给这一格套上黄色边框，读完自动消失。
     *
     * @param tile 被点的小方块
     * @param text 这一格要朗读的内容
     */
    public void speakOne(final View tile, String text) {
        stop();
        if (tile == null || text == null || text.trim().isEmpty()) {
            return;
        }
        tile.setBackgroundResource(R.drawable.bg_tile_active);
        final int t = ++token;
        final Runnable clear = () -> {
            if (t == token) {
                tile.setBackgroundResource(R.drawable.bg_card);
            }
        };
        long timeout = Math.min(ONE_SHOT_MAX_MS,
                ONE_SHOT_BASE_MS + ONE_SHOT_PER_CHAR_MS * text.length());
        handler.postDelayed(clear, timeout);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                handler.post(clear);
            }

            @Override
            public void onError(String utteranceId) {
                handler.post(clear);
            }
        });
    }

    private void playNext(int index, final int t, int retry) {
        if (t != token) {
            return;
        }
        if (index >= tiles.size()) {
            clearHighlight();
            playingId = -1;
            return;
        }
        highlight(index);
        // 引擎还没就绪时稍后再来，最多等 6 秒，免得"点了没声"
        if (!tts.isReady()) {
            if (retry < 40) {
                handler.postDelayed(() -> playNext(index, t, retry + 1), 150);
            }
            return;
        }
        tts.speak(says.get(index), TextToSpeech.QUEUE_ADD, new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                handler.post(() -> playNext(index + 1, t, 0));
            }

            @Override
            public void onError(String utteranceId) {
                handler.post(() -> playNext(index + 1, t, 0));
            }
        });
    }

    private void highlight(int index) {
        for (int i = 0; i < tiles.size(); i++) {
            tiles.get(i).setBackgroundResource(
                    i == index ? R.drawable.bg_tile_active : R.drawable.bg_card);
        }
    }

    private void clearHighlight() {
        for (View tile : tiles) {
            tile.setBackgroundResource(R.drawable.bg_card);
        }
    }
}
