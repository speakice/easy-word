package com.easyword.learn.ui;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.easyword.learn.R;
import com.easyword.learn.adapter.WordPagerAdapter;
import com.easyword.learn.data.Word;
import com.easyword.learn.databinding.FragmentLearnBinding;
import com.easyword.learn.utils.TTSManager;
import com.easyword.learn.utils.VibrateUtil;
import com.easyword.learn.viewmodel.WordViewModel;

import java.util.List;

/** 识字 Tab：全屏上下滑动背字，配合描红、练写、逐行播放与记忆曲线顺序。 */
public class HomeFragment extends Fragment implements WordPagerAdapter.CardListener {

    private FragmentLearnBinding binding;
    private WordViewModel viewModel;
    private TTSManager tts;
    private WordPagerAdapter pagerAdapter;
    private CardActions cardActions;

    private int lastReadingPage = -1;
    private long pageShownAt;
    private int activePage;

    /** 从字表点某个字进来时，定位到那个字。 */
    public static final String ARG_FOCUS_CHAR = "focus_char";
    private String focusChar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLearnBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(WordViewModel.class);
        tts = TTSManager.getInstance(requireContext());
        cardActions = new CardActions(viewModel, tts, requireContext());

        pagerAdapter = new WordPagerAdapter(this, tts);
        binding.viewPager.setAdapter(pagerAdapter);
        binding.viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        binding.viewPager.setOffscreenPageLimit(1);

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                recordDwellAndStudied(pageShownAt, activePage);
                activePage = position;
                pageShownAt = SystemClock.elapsedRealtime();
                startReadingPage(position);
                updateBatchHeader();
            }
        });

        viewModel.getStudyOrder().observe(getViewLifecycleOwner(), pagerAdapter::submitList);
        viewModel.getStats().observe(getViewLifecycleOwner(), stats -> updateBatchHeader());
        viewModel.init();

        if (getArguments() != null) {
            focusChar = getArguments().getString(ARG_FOCUS_CHAR);
        }
        if (focusChar != null && !focusChar.isEmpty()) {
            focusWord(focusChar, 0);
        }
    }

    /** 在当前学习顺序里找到这个字并翻到那一页；找不到就提示（可能还没学到）。 */
    private void focusWord(String ch, int attempt) {
        binding.viewPager.postDelayed(() -> {
            if (binding == null || !isAdded()) {
                return;
            }
            List<Word> words = pagerAdapter.getCurrentList();
            if (words != null) {
                for (int i = 0; i < words.size(); i++) {
                    if (ch.equals(words.get(i).getWord())) {
                        binding.viewPager.setCurrentItem(i, false);
                        return;
                    }
                }
            }
            if (attempt < 3) {
                focusWord(ch, attempt + 1);   // 列表可能还没加载好，再等一会儿
            } else {
                Toast.makeText(requireContext(),
                        "「" + ch + "」还没学到，先学完前面的批次", Toast.LENGTH_SHORT).show();
            }
        }, 350);
    }

    private void updateBatchHeader() {
        if (binding == null) {
            return;
        }
        WordViewModel.Stats stats = viewModel.getStats().getValue();
        if (stats == null) {
            return;
        }
        // 顶部只显示当前学到的最高年级
        binding.textBatchHeader.setText(
                com.easyword.learn.data.TestCatalog.batchName(stats.currentBatch));
    }

    @Override
    public void onResume() {
        super.onResume();
        // 刚考完试回来时，年级（考试通过的最高年级 + 1）可能变了，变了才重排卡片
        viewModel.refreshGradeIfNeeded();
        if (binding != null) {
            startReadingPage(activePage);
        }
    }

    @Override
    public void onPause() {
        recordDwellAndStudied(pageShownAt, activePage);
        pageShownAt = SystemClock.elapsedRealtime();
        super.onPause();
    }

    private void recordDwellAndStudied(long startedAt, int page) {
        if (startedAt <= 0 || page < 0) {
            return;
        }
        long millis = SystemClock.elapsedRealtime() - startedAt;
        List<Word> words = pagerAdapter.getCurrentList();
        if (words != null && page >= 0 && page < words.size()) {
            Word word = words.get(page);
            if (word.isLearned()) {
                viewModel.recordReview(word, Math.min(millis, 300_000));
            } else {
                viewModel.recordStudy(word, Math.min(millis, 300_000));
            }
        }
    }

    /** 切页：只让当前页描红，并自动朗读该页。 */
    private void startReadingPage(int position) {
        if (lastReadingPage == position) {
            return;
        }
        List<Word> words = pagerAdapter.getCurrentList();
        if (words == null || position < 0 || position >= words.size()) {
            return;
        }
        lastReadingPage = position;
        RecyclerView recycler = (RecyclerView) binding.viewPager.getChildAt(0);
        if (recycler == null) {
            return;
        }
        for (int i = 0; i < recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = recycler.getChildViewHolder(recycler.getChildAt(i));
            if (vh instanceof WordPagerAdapter.CardViewHolder) {
                WordPagerAdapter.CardViewHolder card = (WordPagerAdapter.CardViewHolder) vh;
                card.binding.strokeView.setActive(card.getBindingAdapterPosition() == position);
            }
        }
        Word word = words.get(position);
        WordPagerAdapter.CardViewHolder holder = (WordPagerAdapter.CardViewHolder)
                recycler.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            pagerAdapter.startReading(holder.binding, word);
        } else {
            binding.viewPager.post(() -> startReadingPage(position));
        }
    }

    // ---------- 卡片交互 ----------

    @Override
    public void onWordTap(Word word) {
        tts.speak(word.getWord());
    }

    @Override
    public void onWordDoubleTap(Word word) {
        viewModel.toggleFavorite(word);
        VibrateUtil.vibrate(requireContext(), 30);
    }

    @Override
    public void onClearStroke(Word word) {
        VibrateUtil.vibrate(requireContext(), 20);
    }

    @Override
    public void onSaveDrawing(android.view.View drawingView, Word word) {
        com.easyword.learn.utils.WritingExporter.save(
                requireActivity(), drawingView, word.getWord());
    }

    @Override
    public void onSpeak(String text) {
        tts.speak(text);
    }
}
