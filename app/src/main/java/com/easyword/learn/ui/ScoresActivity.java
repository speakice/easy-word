package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

import com.easyword.learn.BaseActivity;
import com.easyword.learn.data.TestCatalog;
import com.easyword.learn.data.TestScores;
import com.easyword.learn.utils.Settings;
import com.easyword.learn.databinding.ActivityScoresBinding;
import com.easyword.learn.databinding.ItemScoreRowBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 我的成绩：**每一次考试都单独一条**，按时间从新到旧排列，
 * 读作「什么时间 · 哪个年级 · 考了多少分」。同一个年级在不同日期考的会各占一行。
 */
public class ScoresActivity extends BaseActivity {

    private ActivityScoresBinding binding;

    public static Intent intent(Context context) {
        return new Intent(context, ScoresActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScoresBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bar.btnBack.setOnClickListener(v -> finish());
        binding.bar.textBarTitle.setText("我的成绩");

        TestScores scores = new TestScores(this);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy年M月d日", Locale.CHINA);
        List<TestScores.Attempt> attempts = scores.allAttempts();
        for (TestScores.Attempt attempt : attempts) {
            TestCatalog.TestSpec spec = attempt.spec();
            if (spec == null) {
                continue;
            }
            ItemScoreRowBinding row = ItemScoreRowBinding.inflate(
                    getLayoutInflater(), binding.scoreBox, false);
            String when = attempt.time > 0 ? fmt.format(new Date(attempt.time)) : "时间不详";
            row.textScoreTitle.setText(when + "  " + (spec.milestone ? "🏁 " : "") + spec.title);
            row.textScoreValue.setText(attempt.score + " 分");
            row.textScoreValue.setTextColor(
                    attempt.score >= Settings.passScore(this) ? 0xFFFFEB3B : 0xFFFF6B6B);
            int best = scores.best(spec.id);
            String detail = spec.range + " · " + Settings.comment(this, attempt.score);
            if (attempt.count > 1) {
                detail += " · 当天考了 " + attempt.count + " 次，取最好";
            }
            if (attempt.score == best) {
                detail += " · 本年级最好成绩";
            } else {
                detail += " · 本年级最好 " + best + " 分";
            }
            row.textScoreDetail.setText(detail);
            binding.scoreBox.addView(row.getRoot());
        }

        if (attempts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("还没有考试记录。\n去「测验」里考一次，成绩就会出现在这里。");
            empty.setTextColor(0xFF9E9E9E);
            empty.setTextSize(16f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            binding.scoreBox.addView(empty);
        } else {
            TextView summary = new TextView(this);
            summary.setText(scores.summary() + "（每次考试都单独记录）");
            summary.setTextColor(0xFF9E9E9E);
            summary.setTextSize(13f);
            summary.setGravity(Gravity.CENTER);
            summary.setPadding(0, dp(6), 0, dp(4));
            binding.scoreBox.addView(summary, 0);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
