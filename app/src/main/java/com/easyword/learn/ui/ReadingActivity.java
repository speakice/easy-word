package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.easyword.learn.BaseActivity;
import com.easyword.learn.R;
import com.easyword.learn.databinding.ActivityReadingBinding;
import com.easyword.learn.utils.KaraokeHighlighter;
import com.easyword.learn.utils.TTSManager;

/**
 * 阅读页：大字号显示整篇短文，点“朗读全文”逐字跟随朗读（读到哪个字哪个字变色）。
 */
public class ReadingActivity extends BaseActivity {

    private static final String EXTRA_TITLE = "reading_title";
    private static final String EXTRA_TEXT = "reading_text";

    private ActivityReadingBinding binding;
    private TTSManager tts;
    private KaraokeHighlighter highlighter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static Intent intent(Context context, String title, String text) {
        Intent i = new Intent(context, ReadingActivity.class);
        i.putExtra(EXTRA_TITLE, title);
        i.putExtra(EXTRA_TEXT, text);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityReadingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String text = getIntent().getStringExtra(EXTRA_TEXT);
        if (title == null || title.isEmpty()) {
            title = "阅读";
        }
        if (text == null) {
            text = "";
        }
        binding.textTitle.setText(title);
        binding.textBody.setText(text);
        highlighter = new KaraokeHighlighter(binding.textBody);
        tts = TTSManager.getInstance(this);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRead.setOnClickListener(v -> readAloud());
        binding.btnStop.setOnClickListener(v -> stopReading());
    }

    private void readAloud() {
        CharSequence text = binding.textBody.getText();
        if (text == null || text.length() == 0) {
            return;
        }
        highlighter.start(text, 0, 0, text.length());
        tts.speak(text.toString(), TextToSpeech.QUEUE_FLUSH, new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) {
                handler.post(() -> highlighter.onRange(start, end));
            }

            @Override
            public void onDone(String utteranceId) {
                handler.post(() -> highlighter.stop());
            }

            @Override
            public void onError(String utteranceId) {
                handler.post(() -> highlighter.stop());
            }
        });
    }

    private void stopReading() {
        tts.stop();
        highlighter.stop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopReading();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
