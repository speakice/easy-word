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
import com.easyword.learn.utils.NumberWords;
import com.easyword.learn.utils.SequenceSpeaker;
import com.easyword.learn.utils.TTSManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 数字：整数（0~100、101~110、整百、1001~1010、1100/1101、整千）、
 * 小数（1.0~2.0）、99 加法表和乘法表，点格子就能听读音；
 * 每个分类标题右边的喇叭会从第一格开始自动朗读，读到哪一格哪一格套黄色边框。
 */
public class NumberActivity extends AppCompatActivity {

    private ActivityNumberBinding binding;
    private TTSManager tts;
    private SequenceSpeaker speaker;

    /** 分类 id：点同一个喇叭再点一次就是停。 */
    private static final int SECTION_INTEGER = 1;
    private static final int SECTION_DECIMAL = 2;
    private static final int SECTION_ADD = 3;
    private static final int SECTION_MULTIPLY = 4;

    private final List<View> integerTiles = new ArrayList<>();
    private final List<String> integerSays = new ArrayList<>();
    private final List<View> decimalTiles = new ArrayList<>();
    private final List<String> decimalSays = new ArrayList<>();
    private final List<View> addTiles = new ArrayList<>();
    private final List<String> addSays = new ArrayList<>();
    private final List<View> multiplyTiles = new ArrayList<>();
    private final List<String> multiplySays = new ArrayList<>();

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
        speaker = new SequenceSpeaker(tts);

        binding.headerInteger.textSectionTitle.setText(R.string.number_integer);
        binding.headerDecimal.textSectionTitle.setText(R.string.number_decimal);
        binding.headerAdd.textSectionTitle.setText(R.string.number_add);
        binding.headerMultiply.textSectionTitle.setText(R.string.number_multiply);
        binding.headerInteger.btnSectionSpeak.setOnClickListener(
                v -> speaker.toggle(SECTION_INTEGER, integerTiles, integerSays));
        binding.headerDecimal.btnSectionSpeak.setOnClickListener(
                v -> speaker.toggle(SECTION_DECIMAL, decimalTiles, decimalSays));
        binding.headerAdd.btnSectionSpeak.setOnClickListener(
                v -> speaker.toggle(SECTION_ADD, addTiles, addSays));
        binding.headerMultiply.btnSectionSpeak.setOnClickListener(
                v -> speaker.toggle(SECTION_MULTIPLY, multiplyTiles, multiplySays));

        // 整数：0~100 → 101~110 → 整百 → 1001~1010、1100、1101 → 整千
        List<String> integers = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            integers.add(String.valueOf(i));
        }
        for (int i = 101; i <= 110; i++) {
            integers.add(String.valueOf(i));
        }
        for (int i = 2; i <= 10; i++) {
            integers.add(String.valueOf(i * 100));
        }
        for (int i = 1001; i <= 1010; i++) {
            integers.add(String.valueOf(i));
        }
        integers.add("1100");
        integers.add("1101");
        for (int i = 2; i <= 10; i++) {
            integers.add(String.valueOf(i * 1000));
        }
        fillNumbers(binding.integerBox, integers, 4, integerTiles, integerSays);

        // 小数：1.0 ~ 1.9、2.0
        List<String> decimals = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            decimals.add("1." + i);
        }
        decimals.add("2.0");
        fillNumbers(binding.decimalBox, decimals, 4, decimalTiles, decimalSays);

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
        fillWithSay(binding.addBox, adds, addsSay, 3, addTiles, addSays);
        fillWithSay(binding.multiplyBox, mults, multsSay, 3, multiplyTiles, multiplySays);
    }

    /** 数字格：显示阿拉伯数字，朗读用中文读法（101 → 一百零一）。 */
    private void fillNumbers(LinearLayout box, List<String> items, int columns,
                             List<View> views, List<String> says) {
        List<String> spoken = new ArrayList<>();
        for (String item : items) {
            spoken.add(item.contains(".") ? NumberWords.ofDecimal(item) : NumberWords.of(
                    Integer.parseInt(item)));
        }
        fillWithSay(box, items, spoken, columns, views, says);
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
                             int columns, List<View> views, List<String> says) {
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
            // 手动点某格时先停掉自动朗读，避免两个声音叠在一起
            tile.getRoot().setOnClickListener(v -> {
                speaker.stop();
                tts.speak(spoken);
            });
            tile.getRoot().setLayoutParams(tileParams());
            row.addView(tile.getRoot());
            views.add(tile.getRoot());
            says.add(spoken);
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
        if (speaker != null) {
            speaker.stop();
        }
    }
}
