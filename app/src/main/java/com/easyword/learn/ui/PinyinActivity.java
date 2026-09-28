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
import com.easyword.learn.utils.TTSManager;

/**
 * 汉语拼音：把 23 个声母、24 个韵母全列出来，点一下读它的读音。
 */
public class PinyinActivity extends AppCompatActivity {

    private ActivityPinyinBinding binding;
    private TTSManager tts;

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

        fill(binding.initialBox, PinyinHelper.INITIALS, PinyinHelper.INITIAL_SOUNDS, 5);
        fill(binding.finalBox, PinyinHelper.FINALS, PinyinHelper.FINAL_SOUNDS, 5);
    }

    /** 按列数铺成网格，每块显示字母 + 读音，点一下读出来。 */
    private void fill(LinearLayout box, String[] items, String[] sounds, int columns) {
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
            tile.getRoot().setOnClickListener(v -> tts.speak(say));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tile.getRoot().setLayoutParams(lp);
            row.addView(tile.getRoot());
        }
        // 补齐最后一行，让每格宽度一致
        if (row != null) {
            int missing = (columns - items.length % columns) % columns;
            for (int i = 0; i < missing; i++) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(spacer);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tts != null) {
            tts.stop();
        }
    }
}
