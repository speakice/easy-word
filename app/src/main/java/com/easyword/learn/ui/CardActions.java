package com.easyword.learn.ui;

import android.content.Context;
import android.widget.Toast;

import com.easyword.learn.adapter.WordPagerAdapter;
import com.easyword.learn.data.Word;
import com.easyword.learn.utils.TTSManager;
import com.easyword.learn.utils.VibrateUtil;
import com.easyword.learn.viewmodel.WordViewModel;

/**
 * 卡片交互的通用实现：单击朗读、双击/左滑收藏、右滑擦除、播放按钮朗读。
 * 首页与“字表学习页”共用。
 */
public class CardActions implements WordPagerAdapter.CardListener {

    private final WordViewModel viewModel;
    private final TTSManager tts;
    private final Context context;

    public CardActions(WordViewModel viewModel, TTSManager tts, Context context) {
        this.viewModel = viewModel;
        this.tts = tts;
        this.context = context;
    }

    @Override
    public void onWordTap(Word word) {
        tts.speak(word.getWord());
    }

    @Override
    public void onWordDoubleTap(Word word) {
        viewModel.toggleFavorite(word);
        VibrateUtil.vibrate(context, 30);
    }

    @Override
    public void onClearStroke(Word word) {
        VibrateUtil.vibrate(context, 20);
    }

    @Override
    public void onSpeak(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!tts.isReady()) {
            Toast.makeText(context, "语音引擎尚未就绪，请稍后再试", Toast.LENGTH_SHORT).show();
        }
        tts.speak(text);
    }
}