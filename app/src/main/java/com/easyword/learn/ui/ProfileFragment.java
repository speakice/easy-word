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
import com.easyword.learn.utils.HalfYear;
import com.easyword.learn.utils.InstallInfo;
import com.easyword.learn.utils.Settings;
import com.easyword.learn.viewmodel.WordViewModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final List<HalfYear> periods = new ArrayList<>();

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
        addRow(binding.rowBoxMine, "⚙️", "设置", "",
                () -> startActivity(SettingsActivity.intent(requireContext())));

        allListsValue = addRow(binding.rowBoxLists, "📚", "全部字表", "",
                () -> startActivity(GradesActivity.intent(requireContext())));
        learnedValue = addRow(binding.rowBoxLists, "📖", "已学字表", "",
                () -> openList(WordViewModel.TYPE_LEARNED, "已学字表"));
        knownValue = addRow(binding.rowBoxLists, "✅", "已识字表", "",
                () -> openList(WordViewModel.TYPE_KNOWN, "已识字表"));
        unknownValue = addRow(binding.rowBoxLists, "❓", "错字集", "",
                () -> openList(WordViewModel.TYPE_UNKNOWN, "错字集"));
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
        setupPeriods();
        refreshHeatmap();
    }

    @Override
    public void onResume() {
        super.onResume();
        applyProfile();
        refreshScoresSummary();
        refreshListCounts();
    }

    /** 昵称和头像跟着设置走。 */
    private void applyProfile() {
        binding.textNickname.setText(Settings.nickname(requireContext()));
        String path = Settings.avatarPath(requireContext());
        if (path != null && new java.io.File(path).exists()) {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(path);
            if (bitmap != null) {
                binding.imgAvatar.setImageBitmap(bitmap);
                binding.imgAvatar.setClipToOutline(true);
                binding.imgAvatar.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                    }
                });
                binding.imgAvatar.setVisibility(View.VISIBLE);
                binding.textAvatar.setVisibility(View.GONE);
                return;
            }
        }
        binding.imgAvatar.setVisibility(View.GONE);
        binding.textAvatar.setVisibility(View.VISIBLE);
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

    /** 时间切换：从安装所在的半年开始，每过半年多一个选项。 */
    private void setupPeriods() {
        HalfYear from = HalfYear.of(InstallInfo.installTime(requireContext()));
        HalfYear now = HalfYear.of(System.currentTimeMillis());
        periods.clear();
        periods.addAll(HalfYear.between(from, now));

        binding.periodBox.removeAllViews();
        // 只有一个半年时不用切换，整行不显示
        boolean needSwitch = periods.size() > 1;
        binding.periodScroll.setVisibility(needSwitch ? View.VISIBLE : View.GONE);
        if (needSwitch) {
            for (HalfYear period : periods) {
                TextView chip = new TextView(requireContext());
                chip.setText(period.label());
                chip.setTextSize(14f);
                chip.setGravity(android.view.Gravity.CENTER);
                int padH = dp(14), padV = dp(7);
                chip.setPadding(padH, padV, padH, padV);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.rightMargin = dp(8);
                chip.setLayoutParams(lp);
                chip.setOnClickListener(v -> selectPeriod(period));
                binding.periodBox.addView(chip);
            }
        }
        selectPeriod(periods.get(periods.size() - 1));   // 默认看最近的半年
    }

    private void selectPeriod(HalfYear period) {
        for (int i = 0; i < binding.periodBox.getChildCount() && i < periods.size(); i++) {
            TextView chip = (TextView) binding.periodBox.getChildAt(i);
            boolean active = periods.get(i).equals(period);
            chip.setBackgroundResource(active
                    ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(active ? 0xFF000000 : 0xFFCCCCCC);
        }
        // 月份标签跟着区间走
        binding.heatmap.setRange(period.startMillis(), period.endMillis());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
