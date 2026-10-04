package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.easyword.learn.BaseActivity;
import com.easyword.learn.R;
import com.easyword.learn.databinding.ActivitySettingsBinding;
import com.easyword.learn.databinding.ItemCustomTextBinding;
import com.easyword.learn.data.TestCatalog;
import com.easyword.learn.utils.CustomText;
import com.easyword.learn.utils.Settings;
import com.easyword.learn.viewmodel.WordViewModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 设置：换头像、改昵称，以及调考试门槛（解锁进度 / 及格 / 良好 / 优秀）。
 * 头像会复制到 App 私有目录，不依赖相册权限。
 */
public class SettingsActivity extends BaseActivity {

    private static final int REQ_PICK = 2001;
    private static final String AVATAR_FILE = "avatar.png";

    private ActivitySettingsBinding binding;
    private WordViewModel viewModel;
    private final List<CustomText.Entry> customEntries = new ArrayList<>();
    private int currentBatch = 1;

    public static Intent intent(Context context) {
        return new Intent(context, SettingsActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bar.btnBack.setOnClickListener(v -> finish());
        binding.bar.textBarTitle.setText(R.string.settings_title);

        // 圆形头像
        binding.imgAvatar.setClipToOutline(true);
        binding.imgAvatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        binding.imgAvatar.setOnClickListener(v -> pickImage());
        binding.rowAvatar.setOnClickListener(v -> pickImage());

        loadCurrent();
        binding.btnSaveSettings.setOnClickListener(v -> save());

        viewModel = new ViewModelProvider(this).get(WordViewModel.class);
        viewModel.init();
        viewModel.getStats().observe(this, stats -> {
            if (stats != null) {
                currentBatch = stats.currentBatch;
            }
        });
        setupCustomText();
    }

    private void loadCurrent() {
        binding.editNickname.setText(Settings.nickname(this));
        binding.editUnlock.setText(String.valueOf(Settings.unlockPercent(this)));
        binding.editPass.setText(String.valueOf(Settings.passScore(this)));
        binding.editGood.setText(String.valueOf(Settings.goodScore(this)));
        binding.editExcellent.setText(String.valueOf(Settings.excellentScore(this)));
        showAvatar();
    }

    private void showAvatar() {
        File file = new File(getFilesDir(), AVATAR_FILE);
        if (file.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                binding.imgAvatar.setImageBitmap(bitmap);
                return;
            }
        }
        binding.imgAvatar.setImageResource(R.drawable.bg_circle_gold);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, R.string.settings_pick_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (copyAvatar(uri)) {
            showAvatar();
        } else {
            Toast.makeText(this, R.string.settings_pick_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 把选中的图片压到 512px 存进 App 私有目录，之后不依赖相册权限。 */
    private boolean copyAvatar(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return false;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);

            int sample = 1;
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            while (max / sample > 512) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in2 = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in2, null, options);
            }
            if (bitmap == null) {
                return false;
            }
            File file = new File(getFilesDir(), AVATAR_FILE);
            try (OutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bitmap.recycle();
            Settings.setAvatarPath(this, file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- 常用写字：把姓名、籍贯里的新字加进字库 ----------

    private void setupCustomText() {
        customEntries.clear();
        customEntries.addAll(CustomText.entries(this));
        if (customEntries.isEmpty()) {
            for (int i = 0; i < 2 && i < CustomText.PRESETS.length; i++) {
                customEntries.add(new CustomText.Entry(CustomText.PRESETS[i], ""));
            }
        }

        binding.presetBox.removeAllViews();
        for (String preset : CustomText.PRESETS) {
            TextView chip = new TextView(this);
            chip.setText(preset);
            chip.setTextSize(14f);
            chip.setGravity(Gravity.CENTER);
            int padH = dp(14), padV = dp(7);
            chip.setPadding(padH, padV, padH, padV);
            chip.setBackgroundResource(R.drawable.bg_chip);
            chip.setTextColor(0xFFCCCCCC);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                // 先收回已经输入的内容，否则重绘时刚打的字会丢
                collectCustomRows();
                int existing = indexOfLabel(preset);
                if (existing >= 0) {
                    // 已经有这一项了：不重复添加，直接把光标送过去
                    focusValueOf(existing);
                    return;
                }
                customEntries.add(new CustomText.Entry(preset, ""));
                renderCustomRows();
                focusValueOf(customEntries.size() - 1);
            });
            binding.presetBox.addView(chip);
        }

        binding.btnAddCustom.setOnClickListener(v -> {
            collectCustomRows();
            customEntries.add(new CustomText.Entry("", ""));
            renderCustomRows();
            focusValueOf(customEntries.size() - 1);
        });
        binding.btnMergeCustom.setOnClickListener(v -> mergeCustomText());
        renderCustomRows();
    }

    @Override
    protected void onPause() {
        // 输入到一半直接返回也不丢：离开页面前先把界面上的内容收回并保存
        collectCustomRows();
        CustomText.save(this, customEntries);
        super.onPause();
    }

    /** 找到名称相同的那一项，没有返回 -1。 */
    private int indexOfLabel(String label) {
        for (int i = 0; i < customEntries.size(); i++) {
            if (label.equals(customEntries.get(i).label)) {
                return i;
            }
        }
        return -1;
    }

    /** 把焦点和光标送到某一行的「内容」输入框，接着就能打字。 */
    private void focusValueOf(int index) {
        if (index < 0 || index >= binding.customBox.getChildCount()) {
            return;
        }
        EditText value = binding.customBox.getChildAt(index).findViewById(R.id.editValue);
        if (value == null) {
            return;
        }
        value.requestFocus();
        value.setSelection(value.getText().length());
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(value, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void renderCustomRows() {
        binding.customBox.removeAllViews();
        for (int i = 0; i < customEntries.size(); i++) {
            final int index = i;
            ItemCustomTextBinding row = ItemCustomTextBinding.inflate(
                    LayoutInflater.from(this), binding.customBox, false);
            CustomText.Entry entry = customEntries.get(i);
            row.editLabel.setText(entry.label);
            row.editValue.setText(entry.value);
            row.btnRemove.setOnClickListener(v -> {
                collectCustomRows();
                if (index < customEntries.size()) {
                    customEntries.remove(index);
                }
                renderCustomRows();
            });
            binding.customBox.addView(row.getRoot());
        }
    }

    /** 把界面上的输入收回列表。 */
    private void collectCustomRows() {
        for (int i = 0; i < binding.customBox.getChildCount() && i < customEntries.size(); i++) {
            View row = binding.customBox.getChildAt(i);
            EditText label = row.findViewById(R.id.editLabel);
            EditText value = row.findViewById(R.id.editValue);
            customEntries.get(i).label = label.getText().toString().trim();
            customEntries.get(i).value = value.getText().toString().trim();
        }
    }

    /** 分析资料里的字，问清楚加到哪一批，然后插入字库。 */
    private void mergeCustomText() {
        collectCustomRows();
        CustomText.save(this, customEntries);
        String text = CustomText.allText(this);
        if (text.trim().isEmpty()) {
            Toast.makeText(this, R.string.settings_custom_empty, Toast.LENGTH_LONG).show();
            return;
        }
        viewModel.analyzeCustomText(text, analysis -> {
            if (analysis.fresh.isEmpty()) {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.settings_custom)
                        .setMessage(getString(R.string.settings_custom_all_known,
                                join(analysis.known)))
                        .setPositiveButton("知道了", null)
                        .show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.settings_custom)
                    .setMessage(getString(R.string.settings_custom_analysis,
                            analysis.known.size() + analysis.fresh.size(),
                            analysis.known.size(), analysis.fresh.size(),
                            join(analysis.fresh)))
                    .setNegativeButton("取消", null)
                    .setPositiveButton(R.string.settings_custom_pick_batch,
                            (d, w) -> pickBatch(analysis.fresh))
                    .show();
        });
    }

    /** 选批次（默认当前正在学的那批）。 */
    private void pickBatch(List<String> fresh) {
        String[] names = new String[10];
        int checked = Math.max(0, Math.min(9, currentBatch - 1));
        for (int i = 0; i < 10; i++) {
            names[i] = TestCatalog.batchName(i + 1) + "（第 " + (i + 1) + " 批）";
        }
        final int[] choice = {checked};
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_custom_pick_batch)
                .setSingleChoiceItems(names, checked, (d, which) -> choice[0] = which)
                .setNegativeButton("取消", null)
                .setPositiveButton("加入", (d, w) -> viewModel.addCustomChars(
                        fresh, choice[0] + 1, added -> Toast.makeText(this,
                                getString(R.string.settings_custom_added, added),
                                Toast.LENGTH_LONG).show()))
                .show();
    }

    private static String join(List<String> chars) {
        StringBuilder sb = new StringBuilder();
        for (String ch : chars) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void save() {
        collectCustomRows();
        CustomText.save(this, customEntries);
        Settings.setNickname(this, binding.editNickname.getText().toString());
        Settings.setUnlockPercent(this, parseInt(binding.editUnlock.getText().toString(),
                Settings.DEFAULT_UNLOCK));
        Settings.setPassScore(this, parseInt(binding.editPass.getText().toString(),
                Settings.DEFAULT_PASS));
        Settings.setGoodScore(this, parseInt(binding.editGood.getText().toString(),
                Settings.DEFAULT_GOOD));
        Settings.setExcellentScore(this, parseInt(binding.editExcellent.getText().toString(),
                Settings.DEFAULT_EXCELLENT));
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
