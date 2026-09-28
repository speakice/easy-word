package com.easyword.learn.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.easyword.learn.R;
import com.easyword.learn.utils.TTSManager;
import com.easyword.learn.utils.WordLibrary;
import com.easyword.learn.viewmodel.WordViewModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 阅读 Tab：把短文按难度从低到高列出（识字越少，靠前的越容易读），
 * 条目标题右边标出难度百分比（100% − 与识字表的契合度），点进去是大字号朗读页。
 */
public class ReadingFragment extends Fragment {

    private ListView listView;
    private PassageAdapter adapter;
    private WordViewModel viewModel;
    private TTSManager tts;
    private final List<String> titles = new ArrayList<>();
    private final List<Integer> levels = new ArrayList<>();
    private final List<String> texts = new ArrayList<>();
    private final List<String> rawTitles = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reading, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(WordViewModel.class);
        tts = TTSManager.getInstance(requireContext());
        listView = view.findViewById(R.id.readingList);
        // 自带配色 + 白字的条目布局，避免黑底黑字（系统主题不同会看不见）
        adapter = new PassageAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, v, pos, id) -> {
            // 点到短文就进大字号阅读页，那里可以逐字跟随朗读
            startActivity(ReadingActivity.intent(requireContext(),
                    rawTitles.get(pos), texts.get(pos)));
        });
        // 识字量变化时自动重算推荐
        viewModel.getStats().observe(getViewLifecycleOwner(), s -> refreshRecommendations());
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshRecommendations();
    }

    private void refreshRecommendations() {
        // 这里必须用 Application 级 Context：查询回来时 Fragment 可能已经切走/销毁，
        // 在回调里再调 requireContext() 会抛 IllegalStateException 直接把 App 搞崩。
        Context context = getContext();
        if (context == null) {
            return;
        }
        final Context appContext = context.getApplicationContext();
        viewModel.loadKnownChars(chars -> {
            if (!isAdded() || getView() == null || adapter == null) {
                return;
            }
            titles.clear();
            levels.clear();
            texts.clear();
            rawTitles.clear();
            // 按“难度”从低到高排：越好读的越靠前，识字少的时候也能先听读
            final List<String> passageTitles = new ArrayList<>();
            final List<String> passageTexts = new ArrayList<>();
            final List<Float> passageCov = new ArrayList<>();
            for (WordLibrary.Passage p : WordLibrary.passages(appContext)) {
                passageTitles.add(p.title);
                passageTexts.add(p.text);
                passageCov.add(coverage(p.text, chars));
            }
            Integer[] order = new Integer[passageTitles.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = i;
            }
            java.util.Arrays.sort(order, (a, b) ->
                    Float.compare(passageCov.get(b), passageCov.get(a)));
            for (int idx : order) {
                int pct = Math.round(passageCov.get(idx) * 100);
                rawTitles.add(passageTitles.get(idx));
                titles.add(passageTitles.get(idx));
                levels.add(Math.max(0, 100 - pct));
                texts.add(passageTexts.get(idx));
            }
            if (titles.isEmpty()) {
                titles.add("短文还没有准备好，先去识字页学几个字吧");
                levels.add(0);
                texts.add("");
                rawTitles.add("");
            }
            adapter.notifyDataSetChanged();
        });
    }

    /** 列表条目：标题 + 右侧的难度百分比。 */
    private class PassageAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return titles.size();
        }

        @Override
        public Object getItem(int position) {
            return titles.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_reading, parent, false);
            }
            TextView title = row.findViewById(R.id.textReading);
            TextView level = row.findViewById(R.id.textReadingLevel);
            title.setText(titles.get(position));
            level.setText("难度 " + levels.get(position) + "%");
            return row;
        }
    }

    private static float coverage(String text, Set<String> chars) {
        if (text == null || text.isEmpty() || chars == null || chars.isEmpty()) {
            return 0f;
        }
        Set<String> uniq = new HashSet<>();
        for (char c : text.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fff') {
                uniq.add(String.valueOf(c));
            }
        }
        int hit = 0;
        for (String ch : uniq) {
            if (chars.contains(ch)) {
                hit++;
            }
        }
        return uniq.isEmpty() ? 0f : hit / (float) uniq.size();
    }
}
