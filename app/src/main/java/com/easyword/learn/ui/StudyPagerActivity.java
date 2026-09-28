package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.easyword.learn.R;
import com.easyword.learn.adapter.WordPagerAdapter;
import com.easyword.learn.data.Word;
import com.easyword.learn.databinding.ActivityStudyBinding;
import com.easyword.learn.utils.TTSManager;
import com.easyword.learn.utils.VibrateUtil;
import com.easyword.learn.viewmodel.WordViewModel;

import java.util.List;

/**
 * 字表学习页：进入后上下滑动浏览「当前字表」内容，支持描红、练写、逐行朗读、
 * 停留计时与记忆阶段推进，与识字首页共用同一套卡片交互。
 */
public class StudyPagerActivity extends AppCompatActivity implements WordPagerAdapter.CardListener {

    private static final String EXTRA_TYPE = "study_type";
    private static final String EXTRA_TITLE = "study_title";

    private ActivityStudyBinding binding;
    private WordViewModel viewModel;
    private TTSManager tts;
    private WordPagerAdapter pagerAdapter;
    private int type = WordViewModel.TYPE_LEARNED;
    private int lastReadingPage = -1;
    private int activePage;
    private long pageShownAt;

    public static Intent intent(Context context, int type, String title) {
        Intent i = new Intent(context, StudyPagerActivity.class);
        i.putExtra(EXTRA_TYPE, type);
        i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityStudyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        type = getIntent().getIntExtra(EXTRA_TYPE, WordViewModel.TYPE_LEARNED);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.isEmpty()) {
            title = "字表";
        }
        binding.textTitle.setText(title);

        tts = TTSManager.getInstance(this);
        viewModel = new ViewModelProvider(this).get(WordViewModel.class);

        pagerAdapter = new WordPagerAdapter(this, tts);
        binding.viewPager.setAdapter(pagerAdapter);
        binding.viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        binding.viewPager.setOffscreenPageLimit(1);

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                recordDwell();
                activePage = position;
                pageShownAt = SystemClock.elapsedRealtime();
                startReadingPage(position);
            }
        });

        binding.btnBack.setOnClickListener(v -> finish());

        viewModel.loadList(type, pagerAdapter::submitList);
        viewModel.init();
    }

    @Override
    public void onPause() {
        recordDwell();
        super.onPause();
    }

    private void recordDwell() {
        if (pageShownAt <= 0) {
            return;
        }
        long millis = SystemClock.elapsedRealtime() - pageShownAt;
        List<Word> words = pagerAdapter.getCurrentList();
        if (words != null && activePage >= 0 && activePage < words.size()) {
            Word word = words.get(activePage);
            if (word.isLearned()) {
                viewModel.recordReview(word, Math.min(millis, 300_000));
            } else {
                viewModel.recordStudy(word, Math.min(millis, 300_000));
            }
        }
        pageShownAt = 0;
    }

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
        VibrateUtil.vibrate(this, 30);
    }

    @Override
    public void onClearStroke(Word word) {
        VibrateUtil.vibrate(this, 20);
    }

    @Override
    public void onSpeak(String text) {
        tts.speak(text);
    }
}