package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.easyword.learn.R;
import com.easyword.learn.databinding.ActivityTilesBinding;
import com.easyword.learn.databinding.ItemTileBinding;
import com.easyword.learn.databinding.SectionHeaderBinding;
import com.easyword.learn.utils.NumberWords;
import com.easyword.learn.utils.SequenceSpeaker;
import com.easyword.learn.utils.TTSManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 数字子页：整数（0~100、101~110、整百、1001~1010、1100/1101、整千）、
 * 小数（1.0~2.0）、99 加法表和乘法表。
 *
 * <p>格子点一下读一个；分类标题右边的喇叭会把这一分类从头读一遍，
 * 读到哪一格哪一格套上黄色边框。</p>
 */
public class NumberTilesActivity extends AppCompatActivity {

    private static final String EXTRA_KEY = "number_key";

    /** 二级菜单的三个入口。 */
    public static final String KEY_INTEGER = "integer";
    public static final String KEY_DECIMAL = "decimal";
    public static final String KEY_CALC = "calc";

    private ActivityTilesBinding binding;
    private TTSManager tts;
    private SequenceSpeaker speaker;
    private int nextSectionId = 1;

    public static Intent intent(Context context, String key) {
        Intent i = new Intent(context, NumberTilesActivity.class);
        i.putExtra(EXTRA_KEY, key);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTilesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.bar.btnBack.setOnClickListener(v -> finish());
        tts = TTSManager.getInstance(this);
        speaker = new SequenceSpeaker(tts);

        String key = getIntent().getStringExtra(EXTRA_KEY);
        if (KEY_DECIMAL.equals(key)) {
            binding.bar.textBarTitle.setText(R.string.number_decimal);
            addNumberSection(R.string.number_section_decimal, decimals(), 4);
        } else if (KEY_CALC.equals(key)) {
            binding.bar.textBarTitle.setText(R.string.number_calc);
            addFormulaSection(R.string.number_add, false);
            addFormulaSection(R.string.number_multiply, true);
        } else {
            binding.bar.textBarTitle.setText(R.string.number_integer);
            addNumberSection(R.string.number_section_integer, integers(), 4);
        }
    }

    /** 整数：0~100 → 101~110 → 整百 → 1001~1010、1100、1101 → 整千。 */
    private static List<String> integers() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i <= 100; i++) {
            items.add(String.valueOf(i));
        }
        for (int i = 101; i <= 110; i++) {
            items.add(String.valueOf(i));
        }
        for (int i = 2; i <= 10; i++) {
            items.add(String.valueOf(i * 100));
        }
        for (int i = 1001; i <= 1010; i++) {
            items.add(String.valueOf(i));
        }
        items.add("1100");
        items.add("1101");
        for (int i = 2; i <= 10; i++) {
            items.add(String.valueOf(i * 1000));
        }
        return items;
    }

    private static List<String> decimals() {
        List<String> items = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            items.add("1." + i);
        }
        items.add("2.0");
        return items;
    }

    /** 数字分类：显示阿拉伯数字，朗读用中文读法（101 → 一百零一）。 */
    private void addNumberSection(int titleRes, List<String> items, int columns) {
        List<String> says = new ArrayList<>();
        for (String item : items) {
            says.add(item.contains(".")
                    ? NumberWords.ofDecimal(item)
                    : NumberWords.of(Integer.parseInt(item)));
        }
        addSection(getString(titleRes), items, says, columns);
    }

    /** 算式分类：99 加法表 / 99 乘法表，算式里的 + × 读成「加 / 乘」。 */
    private void addFormulaSection(int titleRes, boolean multiply) {
        List<String> items = new ArrayList<>();
        List<String> says = new ArrayList<>();
        String op = multiply ? "×" : "+";
        String word = multiply ? "乘" : "加";
        for (int a = 1; a <= 9; a++) {
            for (int b = 1; b <= 9; b++) {
                int result = multiply ? a * b : a + b;
                items.add(a + op + b + "=" + result);
                says.add(a + word + b + "等于" + result);
            }
        }
        addSection(getString(titleRes), items, says, 3);
    }

    /** 铺一个分类：标题（带喇叭）+ 若干行格子。 */
    private void addSection(String title, List<String> items, List<String> says, int columns) {
        SectionHeaderBinding header = SectionHeaderBinding.inflate(
                LayoutInflater.from(this), binding.sectionBox, false);
        header.textSectionTitle.setText(title);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        int top = Math.round(getResources().getDisplayMetrics().density * 14f);
        headerParams.setMargins(0, top, 0, 0);
        header.getRoot().setLayoutParams(headerParams);
        binding.sectionBox.addView(header.getRoot());

        final List<View> tiles = new ArrayList<>();
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        binding.sectionBox.addView(grid);

        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                grid.addView(row);
            }
            ItemTileBinding tile = ItemTileBinding.inflate(
                    LayoutInflater.from(this), row, false);
            tile.textTileMain.setText(items.get(i));
            tile.textTileSub.setVisibility(View.GONE);
            final String spoken = says.get(i);
            // 手动点某格时先停掉自动朗读，避免两个声音叠在一起
            tile.getRoot().setOnClickListener(v -> {
                speaker.stop();
                tts.speak(spoken);
            });
            tile.getRoot().setLayoutParams(tileParams());
            row.addView(tile.getRoot());
            tiles.add(tile.getRoot());
        }
        if (row != null) {
            int missing = (columns - items.size() % columns) % columns;
            for (int i = 0; i < missing; i++) {
                View spacer = new View(this);
                spacer.setLayoutParams(tileParams());
                row.addView(spacer);
            }
        }

        final int sectionId = nextSectionId++;
        header.btnSectionSpeak.setOnClickListener(v -> speaker.toggle(sectionId, tiles, says));
    }

    /** 格子等分一行并留出四周间距（setLayoutParams 会丢掉 XML 里的 margin，得在这里补）。 */
    private LinearLayout.LayoutParams tileParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        int gap = Math.round(getResources().getDisplayMetrics().density * 6f);
        lp.setMargins(gap, gap, gap, gap);
        return lp;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (speaker != null) {
            speaker.stop();
        }
    }
}
