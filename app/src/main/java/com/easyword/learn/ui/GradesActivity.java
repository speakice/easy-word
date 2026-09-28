package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.easyword.learn.WordListActivity;
import com.easyword.learn.data.TestCatalog;
import com.easyword.learn.databinding.ActivityGradesBinding;
import com.easyword.learn.databinding.ItemGradeRowBinding;
import com.easyword.learn.viewmodel.WordViewModel;

/**
 * 全部字表：按年级（批次）列出，显示每个年级已识多少个字；
 * 点进某个年级就是那个批次的全部汉字。
 */
public class GradesActivity extends AppCompatActivity {

    private ActivityGradesBinding binding;
    private WordViewModel viewModel;

    public static Intent intent(Context context) {
        return new Intent(context, GradesActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGradesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.bar.btnBack.setOnClickListener(v -> finish());
        binding.bar.textBarTitle.setText("全部字表");

        viewModel = new ViewModelProvider(this).get(WordViewModel.class);
        viewModel.init();
        viewModel.getStats().observe(this, stats -> {
            if (stats != null) {
                render(stats);
            }
        });
    }

    private void render(WordViewModel.Stats stats) {
        binding.gradeBox.removeAllViews();
        for (int batch = 1; batch <= 10; batch++) {
            int total = (batch == 10) ? 50 : 100;
            ItemGradeRowBinding row = ItemGradeRowBinding.inflate(
                    getLayoutInflater(), binding.gradeBox, false);
            String name = TestCatalog.batchName(batch);
            int known = stats.knownOfBatch(batch);
            int percent = stats.percentOfBatch(batch);
            row.textGradeName.setText(name);
            row.textGradeDetail.setText("已识 " + known + " / " + total
                    + " · 学习进度 " + percent + "%");
            row.textGradeCount.setText(total + " 字");
            row.textGradeCount.setTextColor(percent >= 80 ? 0xFFFFEB3B : 0xFF9E9E9E);
            final int b = batch;
            row.getRoot().setOnClickListener(v -> startActivity(
                    WordListActivity.intentForBatch(this, b, name + " · 全部汉字")));
            binding.gradeBox.addView(row.getRoot());
        }
    }
}
