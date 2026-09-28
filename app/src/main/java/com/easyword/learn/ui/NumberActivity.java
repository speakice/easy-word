package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.easyword.learn.R;
import com.easyword.learn.databinding.ActivityNumberBinding;
import com.easyword.learn.databinding.ItemTileBinding;
import com.easyword.learn.utils.TTSManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 数字：整数（1~100、整百、整千）、小数（1.1~2.0）、99 加法表和乘法表，
 * 都能点着听读音。
 */
public class NumberActivity extends AppCompatActivity {

    private ActivityNumberBinding binding;
    private TTSManager tts;

    public static Intent intent(Context context) {
        return new Intent(context, NumberActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNumberBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.bar.btnBack.setOnClickListener(v -> finish());
        binding.bar.textBarTitle.setText(R.string.number_title);
        tts = TTSManager.getInstance(this);

        // 整数：1~100，然后整百到 1000，再整千到 10000
        List<String> integers = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            integers.add(String.valueOf(i));
        }
        for (int i = 2; i <= 10; i++) {
            integers.add(String.valueOf(i * 100));
        }
        for (int i = 2; i <= 10; i++) {
            integers.add(String.valueOf(i * 1000));
        }
        fill(binding.integerBox, integers, 4);

        // 小数：1.1 ~ 1.9、2.0
        List<String> decimals = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            decimals.add("1." + i);
        }
        decimals.add("2.0");
        fill(binding.decimalBox, decimals, 4);

        // 99 加法表 / 99 乘法表
        List<String> adds = new ArrayList<>();
        List<String> addsSay = new ArrayList<>();
        List<String> mults = new ArrayList<>();
        List<String> multsSay = new ArrayList<>();
        for (int a = 1; a <= 9; a++) {
            for (int b = 1; b <= 9; b++) {
                adds.add(a + "+" + b + "=" + (a + b));
                addsSay.add(a + "加" + b + "等于" + (a + b));
                mults.add(a + "×" + b + "=" + (a * b));
                multsSay.add(a + "乘" + b + "等于" + (a * b));
            }
        }
        fillWithSay(binding.addBox, adds, addsSay, 3);
        fillWithSay(binding.multiplyBox, mults, multsSay, 3);
    }

    private void fill(LinearLayout box, List<String> items, int columns) {
        fillWithSay(box, items, items, columns);
    }

    /** 格子等分一行并留出四周间距（setLayoutParams 会丢掉 XML 里的 margin，得在这里补）。 */
    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int gap = Math.round(getResources().getDisplayMetrics().density * 6f);
        lp.setMargins(gap, gap, gap, gap);
        return lp;
    }

    /** 铺成网格；显示的文本和朗读的文本可以不同（算式要把 + × 读成汉字）。 */
    private void fillWithSay(LinearLayout box, List<String> items, List<String> say,
                             int columns) {
        box.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
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
            tile.textTileMain.setText(items.get(i));
            tile.textTileSub.setText("");
            tile.textTileSub.setVisibility(View.GONE);
            final String spoken = say.get(i);
            tile.getRoot().setOnClickListener(v -> tts.speak(spoken));
            tile.getRoot().setLayoutParams(tileParams());
            row.addView(tile.getRoot());
        }
        if (row != null) {
            int missing = (columns - items.size() % columns) % columns;
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
        if (tts != null) {
            tts.stop();
        }
    }
}
