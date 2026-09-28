package com.easyword.learn;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.easyword.learn.adapter.WordListAdapter;
import com.easyword.learn.data.Word;
import com.easyword.learn.ui.StudyPagerActivity;
import com.easyword.learn.databinding.ActivityListBinding;
import com.easyword.learn.utils.TTSManager;
import com.easyword.learn.viewmodel.WordViewModel;

/** 字表页（我的 → 已学/已识/未识/收藏）：网格列出字+喇叭；点字进入该字表的上下滑动学习页。 */
public class WordListActivity extends AppCompatActivity implements WordListAdapter.OnListAction {

    private static final String EXTRA_TYPE = "list_type";
    private static final String EXTRA_TITLE = "list_title";
    private static final String EXTRA_BATCH = "list_batch";

    private ActivityListBinding binding;
    private WordViewModel viewModel;
    private TTSManager tts;
    private String title = "字表";
    private int type = WordViewModel.TYPE_LEARNED;
    /** 大于 0 表示这是"某个年级的全部汉字"字表。 */
    private int batch;

    public static Intent intent(Context context, int type, String title) {
        Intent i = new Intent(context, WordListActivity.class);
        i.putExtra(EXTRA_TYPE, type);
        i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    /** 某个年级（批次）的全部汉字字表。 */
    public static Intent intentForBatch(Context context, int batch, String title) {
        Intent i = new Intent(context, WordListActivity.class);
        i.putExtra(EXTRA_BATCH, batch);
        i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        type = getIntent().getIntExtra(EXTRA_TYPE, WordViewModel.TYPE_LEARNED);
        batch = getIntent().getIntExtra(EXTRA_BATCH, 0);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title == null || title.isEmpty()) {
            title = "字表";
        }

        tts = TTSManager.getInstance(this);
        viewModel = new ViewModelProvider(this).get(WordViewModel.class);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.textTitle.setText(title);

        // 3 列：每格放得下“字 + 喇叭”，4 列会挤在一起
        binding.recycler.setLayoutManager(new GridLayoutManager(this, 3));
        WordListAdapter adapter = new WordListAdapter(this);
        binding.recycler.setAdapter(adapter);

        java.util.function.Consumer<java.util.List<Word>> show = list -> {
            binding.textCount.setText((list == null ? 0 : list.size()) + " 字");
            adapter.submit(list);
        };
        if (batch > 0) {
            binding.textListHint.setText("点字回首页练写 · 点喇叭教读");
            viewModel.loadBatchWords(batch, show);
        } else {
            viewModel.loadList(type, show);
        }
    }

    @Override
    public void onWordClick(Word word) {
        if (batch > 0) {
            // 年级字表里的字：回首页练写
            startActivity(MainActivity.intentForChar(this, word.getWord()));
        } else {
            startActivity(StudyPagerActivity.intent(this, type, title));
        }
    }

    @Override
    public void onSpeak(Word word) {
        tts.speak(word.getWord());
    }
}
