package com.easyword.learn.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.easyword.learn.R;
import com.easyword.learn.WordListActivity;
import com.easyword.learn.data.DailyRecord;
import com.easyword.learn.data.TestCatalog;
import com.easyword.learn.data.TestScores;
import com.easyword.learn.databinding.FragmentProfileBinding;
import com.easyword.learn.viewmodel.WordViewModel;

import java.util.HashMap;
import java.util.Map;

/**
 * 我的：常见的个人中心样式 —— 顶部头像 + 学习进度，下面分组列出
 * 我的成绩 / 我的证书 / 各类字表，最后是学习热力图。
 */
public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private WordViewModel viewModel;
    private TextView scoresValue;
    private TextView certificatesValue;
    private TextView allListsValue;
    private TextView learnedValue;
    private TextView knownValue;
    private TextView unknownValue;
    private TextView favoriteValue;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(WordViewModel.class);

        scoresValue = addRow(binding.rowBoxMine, "📊", "我的成绩", "",
                () -> startActivity(ScoresActivity.intent(requireContext())));
        certificatesValue = addRow(binding.rowBoxMine, "🎖", "毕业证书", "",
                () -> startActivity(CertificatesActivity.intent(requireContext())));

        allListsValue = addRow(binding.rowBoxLists, "📚", "全部字表", "",
                () -> startActivity(GradesActivity.intent(requireContext())));
        learnedValue = addRow(binding.rowBoxLists, "📖", "已学字表", "",
                () -> openList(WordViewModel.TYPE_LEARNED, "已学字表"));
        knownValue = addRow(binding.rowBoxLists, "✅", "已识字表", "",
                () -> openList(WordViewModel.TYPE_KNOWN, "已识字表"));
        unknownValue = addRow(binding.rowBoxLists, "❓", "未识字表", "",
                () -> openList(WordViewModel.TYPE_UNKNOWN, "未识字表"));
        favoriteValue = addRow(binding.rowBoxLists, "⭐", "已收藏字表", "",
                () -> openList(WordViewModel.TYPE_FAVORITE, "已收藏字表"));

        viewModel.getStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats == null) {
                return;
            }
            binding.textStats.setText("识字 " + stats.known + " · 已学 " + stats.learned
                    + " · 未识 " + stats.unknown);
            int percent = stats.total <= 0 ? 0 : stats.known * 100 / stats.total;
            binding.textTotalProgress.setText(stats.total + " 字学习进度：" + percent + "%");
            binding.progressLearn.setMax(Math.max(1, stats.total));
            binding.progressLearn.setProgress(stats.known);
            if (allListsValue != null) {
                allListsValue.setText(stats.total + " 字");
            }
        });

        refreshScoresSummary();
        refreshListCounts();
        refreshHeatmap();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshScoresSummary();
        refreshListCounts();
    }

    /** 每个字表分类显示当前有多少个字。 */
    private void refreshListCounts() {
        setCount(learnedValue, WordViewModel.TYPE_LEARNED);
        setCount(knownValue, WordViewModel.TYPE_KNOWN);
        setCount(unknownValue, WordViewModel.TYPE_UNKNOWN);
        setCount(favoriteValue, WordViewModel.TYPE_FAVORITE);
    }

    private void setCount(TextView view, int type) {
        if (view == null) {
            return;
        }
        viewModel.loadList(type, list -> {
            if (view != null) {
                view.setText((list == null ? 0 : list.size()) + " 字");
            }
        });
    }

    /** 顶部两行右侧显示成绩 / 证书数量。 */
    private void refreshScoresSummary() {
        TestScores scores = new TestScores(requireContext());
        if (scoresValue != null) {
            scoresValue.setText(scores.summary());
        }
        if (certificatesValue != null) {
            int issued = 0;
            boolean canApply = false;
            for (TestCatalog.TestSpec spec : TestCatalog.ALL) {
                if (spec.diploma == null) {
                    continue;
                }
                if (scores.applied(spec.id)) {
                    issued++;
                } else if (scores.best(spec.id) >= 60) {
                    canApply = true;
                }
            }
            certificatesValue.setText(issued > 0 ? "已领 " + issued + " 张"
                    : (canApply ? "可以申请" : ""));
        }
    }

    private TextView addRow(LinearLayout parent, String icon, String title, String value,
                            Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_profile_row, parent, false);
        ((TextView) row.findViewById(R.id.textRowIcon)).setText(icon);
        ((TextView) row.findViewById(R.id.textRowTitle)).setText(title);
        TextView valueView = row.findViewById(R.id.textRowValue);
        valueView.setText(value);
        row.setOnClickListener(v -> action.run());
        parent.addView(row);
        return valueView;
    }

    private void openList(int type, String title) {
        startActivity(WordListActivity.intent(requireContext(), type, title));
    }

    private void refreshHeatmap() {
        viewModel.loadDaily(records -> {
            if (binding == null || !isAdded() || getView() == null) {
                return;
            }
            Map<String, Long> map = new HashMap<>();
            for (DailyRecord r : records) {
                map.put(r.getDate(), r.getMillis());
            }
            binding.heatmap.setData(map);
        });
    }
}
