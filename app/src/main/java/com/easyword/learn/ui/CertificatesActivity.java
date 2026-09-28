package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.easyword.learn.data.TestScores;
import com.easyword.learn.databinding.ActivityCertificatesBinding;
import com.easyword.learn.databinding.ItemCertificateBinding;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 毕业证书：小升初 / 中考成绩出来以后，这里会出现「申请毕业」按钮；
 * 成绩 60 分以上申请成功 → 颁发小学 / 初中毕业证书；没到 60 分就申请不下来，
 * 证书区保持为空。还没考的科目不列出来。
 */
public class CertificatesActivity extends AppCompatActivity {

    private ActivityCertificatesBinding binding;
    private TestScores scores;

    public static Intent intent(Context context) {
        return new Intent(context, CertificatesActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCertificatesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.bar.btnBack.setOnClickListener(v -> finish());
        binding.bar.textBarTitle.setText("毕业证书");
        scores = new TestScores(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        binding.certBox.removeAllViews();
        int shown = 0;
        shown += addCertificate("final_primary", "小学毕业证书",
                "已学完小学阶段 600 个汉字", "小升初考试");
        shown += addCertificate("final_junior", "初中毕业证书",
                "已学完初中阶段 950 个汉字", "中考");
        if (shown == 0) {
            TextView empty = new TextView(this);
            empty.setText("还没有可以申请的证书。\n先考「小升初」或「中考」，"
                    + "成绩出来就能在这里申请毕业证书。");
            empty.setTextColor(0xFF9E9E9E);
            empty.setTextSize(16f);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            binding.certBox.addView(empty);
        }
    }

    /** @return 这一项是否展示（没考过的科目不展示） */
    private int addCertificate(String testId, String title, String body, String examName) {
        int best = scores.best(testId);
        if (best < 0) {
            return 0;
        }
        boolean applied = scores.applied(testId);
        ItemCertificateBinding row = ItemCertificateBinding.inflate(
                getLayoutInflater(), binding.certBox, false);
        row.textCertTitle.setText(title);
        row.textCertBody.setText(body);

        if (applied) {
            // 已经颁发：金边证书 + 红印章；后来考得更好可以更新成绩
            // 证书上写的是"申请毕业那一刻"的成绩；后来考得更高，由下面的按钮手动更新
            int recorded = scores.diplomaScore(testId);
            int diplomaScore = recorded > 0 ? recorded : best;
            row.textCertTitle.setTextColor(0xFFFFD54F);
            row.textCertScore.setText(examName + " " + diplomaScore + " 分 · "
                    + TestScores.gradeWord(diplomaScore));
            row.textCertScore.setTextColor(0xFFE53935);
            long issued = scores.issuedAt(testId);
            String when = issued > 0
                    ? new SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(new Date(issued))
                    : "";
            row.textCertDate.setText(when.isEmpty() ? "轻松识字" : "轻松识字 · " + when + " 颁发");
            row.textCertSeal.setVisibility(View.VISIBLE);
            if (best > diplomaScore) {
                // 重新考出了更高的分数，让证书上的成绩也能跟上
                row.btnApply.setVisibility(View.VISIBLE);
                row.btnApply.setText("更新毕业成绩（新成绩 " + best + " 分）");
                row.btnApply.setOnClickListener(v -> {
                    scores.updateDiplomaScore(testId, best);
                    Toast.makeText(this, "毕业成绩已更新为 " + best + " 分", Toast.LENGTH_LONG).show();
                    render();
                });
            } else {
                row.btnApply.setVisibility(View.GONE);
            }
        } else {
            // 成绩出来了但还没申请：显示成绩 + 申请按钮
            row.textCertTitle.setTextColor(0xFF9E9E9E);
            row.textCertScore.setText(best >= 60
                    ? examName + " " + best + " 分 · " + TestScores.gradeWord(best)
                    : examName + " " + best + " 分");
            row.textCertScore.setTextColor(best >= 60 ? 0xFFE53935 : 0xFF9E9E9E);
            row.textCertDate.setText(best >= 60
                    ? "成绩合格，点下面申请毕业证书"
                    : "60 分以上才能申请毕业证书");
            row.textCertSeal.setVisibility(View.GONE);
            row.btnApply.setVisibility(View.VISIBLE);
            row.btnApply.setOnClickListener(v -> apply(testId, title, best));
        }
        binding.certBox.addView(row.getRoot());
        return 1;
    }

    private void apply(String testId, String title, int best) {
        if (best < 60) {
            Toast.makeText(this, title.substring(0, 2) + "成绩 " + best
                    + " 分，还没到 60 分，再考一次吧", Toast.LENGTH_LONG).show();
            return;
        }
        scores.applyForDiploma(testId, best);
        Toast.makeText(this, "申请成功，已颁发" + title + "！", Toast.LENGTH_LONG).show();
        render();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
