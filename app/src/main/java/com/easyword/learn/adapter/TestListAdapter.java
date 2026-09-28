package com.easyword.learn.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.easyword.learn.R;
import com.easyword.learn.data.TestCatalog;
import com.easyword.learn.data.TestScores;
import com.easyword.learn.databinding.ItemTestBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 考试列表：每个年级一次单元测试，外加小学 / 初中毕业考试。
 * 还没学到的批次显示成"锁住"，考过的显示最好成绩。
 */
public class TestListAdapter extends RecyclerView.Adapter<TestListAdapter.Holder> {

    /** 本批识字率达到这个百分比，年级期末考试才解锁。 */
    public static final int UNLOCK_PERCENT = 80;

    /** 点击回调。 */
    public interface OnTestClick {
        void onTestClick(TestCatalog.TestSpec spec);
    }

    private final List<TestCatalog.TestSpec> items = new ArrayList<>();
    private final OnTestClick listener;
    private int[] batchPercent = new int[11];
    private TestScores scores;

    public TestListAdapter(OnTestClick listener) {
        this.listener = listener;
    }

    public void setData(List<TestCatalog.TestSpec> specs, int[] batchPercent) {
        items.clear();
        if (specs != null) {
            items.addAll(specs);
        }
        if (batchPercent != null) {
            this.batchPercent = batchPercent;
        }
        notifyDataSetChanged();
    }

    /** 考试解锁规则：年级期末考看本批学习进度，毕业考试看前置年级期末考是否及格。 */
    public boolean isUnlocked(TestCatalog.TestSpec spec) {
        if (scores == null) {
            return false;
        }
        String prerequisite = prerequisiteTestId(spec);
        if (prerequisite != null) {
            return scores.best(prerequisite) >= 60;
        }
        return percentOfBatch(spec.toBatch) >= UNLOCK_PERCENT;
    }

    /** 毕业考试的前置：小升初要六年级期末及格，中考要初三期末及格。 */
    public static String prerequisiteTestId(TestCatalog.TestSpec spec) {
        if ("final_primary".equals(spec.id)) {
            return "unit_6";
        }
        if ("final_junior".equals(spec.id)) {
            return "unit_9";
        }
        return null;
    }

    private int percentOfBatch(int batch) {
        if (batchPercent == null || batch < 1 || batch >= batchPercent.length) {
            return 0;
        }
        return batchPercent[batch];
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTestBinding binding = ItemTestBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TestCatalog.TestSpec spec = items.get(position);
        if (scores == null) {
            scores = new TestScores(holder.itemView.getContext());
        }
        // 里程碑考试（小升初 / 中考）用金色卡片标出来
        holder.itemView.setBackgroundResource(spec.milestone
                ? R.drawable.bg_card_milestone : R.drawable.bg_card);
        holder.binding.textTestTitle.setTextColor(spec.milestone ? 0xFFFFD54F : 0xFFFFFFFF);
        holder.binding.textTestTitle.setText(spec.title);
        int percent = percentOfBatch(spec.toBatch);
        if (!isUnlocked(spec)) {
            holder.binding.textTestRange.setText(lockHint(spec, percent));
            holder.binding.textTestScore.setText("未解锁");
            holder.binding.textTestScore.setTextColor(
                    spec.milestone ? 0xCCFFB300 : 0xFF808080);
            holder.binding.textTestTitle.setTextColor(
                    spec.milestone ? 0x99FFD54F : 0xFF9E9E9E);
            holder.itemView.setAlpha(0.7f);
        } else {
            String range = spec.milestone
                    ? "🏁 " + spec.range
                    : spec.range + " · 学习进度 " + percent + "%";
            holder.binding.textTestRange.setText(range);
            holder.itemView.setAlpha(1f);
            int best = scores.best(spec.id);
            if (best < 0) {
                holder.binding.textTestScore.setText("还没考过");
                holder.binding.textTestScore.setTextColor(
                        spec.milestone ? 0xFFFFB300 : 0xFF9E9E9E);
            } else {
                holder.binding.textTestScore.setText("最好 " + best + " 分");
                holder.binding.textTestScore.setTextColor(best >= 60 ? 0xFFFFEB3B : 0xFFFF6B6B);
            }
        }
        holder.itemView.setOnClickListener(v -> listener.onTestClick(spec));
    }

    /** 没解锁时告诉用户差什么。 */
    private String lockHint(TestCatalog.TestSpec spec, int percent) {
        String prerequisite = prerequisiteTestId(spec);
        if ("unit_6".equals(prerequisite)) {
            return "🔒 六年级期末考试及格后解锁";
        }
        if ("unit_9".equals(prerequisite)) {
            return "🔒 初中三年级期末考试及格后解锁";
        }
        return "🔒 第 " + spec.toBatch + " 批学习进度 " + percent
                + "%（到 " + UNLOCK_PERCENT + "% 解锁这个考试）";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemTestBinding binding;

        Holder(ItemTestBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
