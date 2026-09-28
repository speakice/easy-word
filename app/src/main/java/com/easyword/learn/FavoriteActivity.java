package com.easyword.learn;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;

import com.easyword.learn.adapter.FavoriteWordAdapter;
import com.easyword.learn.data.Word;
import com.easyword.learn.databinding.ActivityFavoriteBinding;
import com.easyword.learn.utils.VibrateUtil;
import com.easyword.learn.viewmodel.WordViewModel;

import java.util.List;

/**
 * 收藏本页面：展示所有被收藏的汉字（3 列网格）。
 * <p>单击条目 → 连同字 ID 返回主页面并定位；长按 → 取消收藏。
 * 返回时通过 {@link #EXTRA_WORD_ID} 带回所选字主键。</p>
 */
public class FavoriteActivity extends AppCompatActivity implements FavoriteWordAdapter.OnFavoriteWordAction {

    /** 返回给 MainActivity 的汉字主键。 */
    public static final String EXTRA_WORD_ID = "extra_word_id";

    private ActivityFavoriteBinding binding;
    private WordViewModel viewModel;
    private final FavoriteWordAdapter adapter = new FavoriteWordAdapter(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFavoriteBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(WordViewModel.class);

        binding.rvFavorite.setLayoutManager(new GridLayoutManager(this, 3));
        binding.rvFavorite.setAdapter(adapter);

        binding.btnBack.setOnClickListener(v -> finish());

        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });

        viewModel.getFavoriteWords().observe(this, this::onFavoriteChanged);
    }

    /** 收藏列表变化时刷新界面。 */
    private void onFavoriteChanged(List<Word> words) {
        adapter.submitList(words);
        boolean empty = words == null || words.isEmpty();
        binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvFavorite.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onWordClick(Word word) {
        VibrateUtil.vibrate(this, 15);
        Intent result = new Intent();
        result.putExtra(EXTRA_WORD_ID, word.getId());
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onWordLongClick(Word word) {
        viewModel.setFavorite(word.getId(), false);
        VibrateUtil.vibrate(this, 30);
        Toast.makeText(this, R.string.toast_unfavorited, Toast.LENGTH_SHORT).show();
    }
}