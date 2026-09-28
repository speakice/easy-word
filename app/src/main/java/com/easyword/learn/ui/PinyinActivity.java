package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.easyword.learn.R;
import com.easyword.learn.databinding.ActivityPinyinBinding;
import com.easyword.learn.databinding.ItemTileBinding;
import com.easyword.learn.utils.PinyinHelper;
import com.easyword.learn.utils.SequenceSpeaker;
import com.easyword.learn.utils.TTSManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 汉语拼音：把 23 个声母、24 个韵母全列出来，点一下读它的读音；
 * 每个分类标题右边的喇叭会从第一个开始自动朗读，读到哪一格哪一格套黄色边框。
 */
public class PinyinActivity extends AppCompatActivity {

    private ActivityPinyinBinding binding;
    private TTSManager tts;
    private SequenceSpeaker speaker;

    /** 一行的格数：格子留大一点，看着不挤。 */
    private static final int COLUMNS = 4;

    /** 分类 id：点同一个喇叭再点一次就是停。 */
    private static final int SECTION_INITIALS = 1;
    private static final int SECTION_FINALS = 2;

    private final List<View> initialTiles = new ArrayList<>();
    private final List<String> initialSays = new ArrayList<>();
    private final List<View> finalTiles = new ArrayList<>();
    private final List<String> finalSays = new ArrayList<>();

    public static Intent intent(Context context) {
        return new Intent(context, PinyinActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPinyinBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.bar.btnBack.setOnClickListener(v -> finish());
        binding.bar.textBarTitle.setText(R.string.pinyin_title);
        tts = TTSManager.getInstance(this);
        speaker = new SequenceSpeaker(tts);

        binding.headerInitials.textSectionTitle.setText(R.string.pinyin_initials);
        binding.headerFinals.textSectionTitle.setText(R.string.pinyin_finals);
        binding.headerInitials.btnSectionSpeak.setOnClickListener(
                v -> speaker.toggle(SECTION_INITIALS, initialTiles, initialSays));
        binding.headerFinals.btnSectionSpeak.setOnClickListener(
                v -> speaker.toggle(SECTION_FINALS, finalTiles, finalSays));

        fill(binding.initialBox, PinyinHelper.INITIALS, PinyinHelper.INITIAL_SOUNDS, COLUMNS,
                initialTiles, initialSays);
        fill(binding.finalBox, PinyinHelper.FINALS, PinyinHelper.FINAL_SOUNDS, COLUMNS,
                finalTiles, finalSays);
    }

    /** 格子等分一行并留出四周间距（setLayoutParams 会丢掉 XML 里的 margin，得在这里补）。 */
    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int gap = Math.round(getResources().getDisplayMetrics().density * 6f);
        lp.setMargins(gap, gap, gap, gap);
        return lp;
    }

    /** 按列数铺成网格，每块显示字母 + 读音，点一下读出来。 */
    private void fill(LinearLayout box, String[] items, String[] sounds, int columns,
                      List<View> views, List<String> says) {
        box.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < items.length; i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                box.addView(row);
            }
            ItemTileBinding tile = ItemTileBinding.inflate(
                    LayoutInflater.from(this), row, false);
            tile.textTileMain.setText(items[i]);
            tile.textTileSub.setText(sounds[i]);
            final String say = sounds[i];
            // 手动点某格时先停掉自动朗读，避免两个声音叠在一起
            tile.getRoot().setOnClickListener(v -> {
                speaker.stop();
                tts.speak(say);
            });
            tile.getRoot().setLayoutParams(tileParams());
            row.addView(tile.getRoot());
            views.add(tile.getRoot());
            says.add(say);
        }
        // 补齐最后一行，让每格宽度一致
        if (row != null) {
            int missing = (columns - items.length % columns) % columns;
            for (int i = 0; i < missing; i++) {
                View spacer = new View(this);
                spacer.setLayoutParams(tileParams());
                row.addView(spacer);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (speaker != null) {
            speaker.stop();
        }
    }
}
