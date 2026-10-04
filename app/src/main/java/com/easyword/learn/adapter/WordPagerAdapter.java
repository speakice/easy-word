package com.easyword.learn.adapter;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.easyword.learn.R;
import com.easyword.learn.data.Word;
import com.easyword.learn.databinding.ItemWordCardBinding;
import com.easyword.learn.utils.EmphasisText;
import com.easyword.learn.utils.KaraokeHighlighter;
import com.easyword.learn.utils.TTSManager;
import com.easyword.learn.view.DrawingView;
import com.easyword.learn.view.StrokeAnimationView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 全屏滑动刷字页的 Pager 适配器。
 *
 * <p>基于 {@link ListAdapter}，通过 {@link DiffUtil} 只刷新确实变化的位置；
 * 每个卡片视图负责绑定汉字/拼音、描红笔画层、练写区、清空按钮、逐行播放按钮，
 * 并把交互（单击朗读/双击收藏/左滑收藏/右滑擦除）转发给宿主界面。</p>
 */
public class WordPagerAdapter extends ListAdapter<Word, WordPagerAdapter.CardViewHolder> {

    /** 卡片交互事件回调，由宿主界面（首页 / 列表学习页）实现。 */
    public interface CardListener {
        /** 单击卡片：朗读。 */
        void onWordTap(Word word);

        /** 双击/左滑卡片：收藏。 */
        void onWordDoubleTap(Word word);

        /** 右滑/清空按钮：擦除笔迹。 */
        void onClearStroke(Word word);

        /** 保存按钮：把手写内容存成图片。 */
        void onSaveDrawing(android.view.View drawingView, Word word);

        /** 播放按钮：朗读一段文本（字 / 词组 / 顺口溜 / 场景）。 */
        void onSpeak(String text);
    }

    private final CardListener cardListener;
    private final TTSManager ttsManager;

    /** 朗读时的跟读高亮色：跟描红笔迹同一个红（= {@code R.color.stroke_red}）。 */
    private static final int READ_HIGHLIGHT = 0xFFFF3B30;

    // ---- 自动朗读（歌词跟随）状态 ----
    private static final int TYPE_CHAR = 0;
    private static final int TYPE_SPELL = 4;
    private static final int TYPE_WORD = 1;
    private static final int TYPE_RHYME = 2;
    private static final int TYPE_USAGE = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ItemWordCardBinding readingBinding;
    private Word readingWord;
    private int readingToken;
    private String wordsDisplayText;

    // 逐字跟随朗读的高亮器（同一时刻只有一张卡片在朗读）
    private ItemWordCardBinding karaokeBinding;
    private KaraokeHighlighter karaokeWords;
    private KaraokeHighlighter karaokeRhyme;
    private KaraokeHighlighter karaokeUsage;

    /** 一个朗读片段：类型 + 朗读用的文本；词组片段附带单词本身用于高亮。 */
    private static class Segment {
        final int type;
        final String say;
        final String word;
        final int wordIndex;
        final int[] wordStarts;

        Segment(int type, String say, String word, int wordIndex, int[] wordStarts) {
            this.type = type;
            this.say = say;
            this.word = word;
            this.wordIndex = wordIndex;
            this.wordStarts = wordStarts;
        }
    }

    /**
     * 构造 Pager 适配器。
     *
     * @param cardListener 卡片交互回调
     * @param ttsManager   朗读引擎（自动朗读 + 点击朗读共用）
     */
    public WordPagerAdapter(CardListener cardListener, TTSManager ttsManager) {
        super(new WordDiffCallback());
        this.cardListener = cardListener;
        this.ttsManager = ttsManager;
    }

    /**
     * 自动朗读当前卡片：字 → 拼读（hui，喝，威，hui）→ 词组(xx的x) → 场景 → 巧记，
     * 并逐行高亮跟随。
     */
    public void startReading(ItemWordCardBinding binding, Word word) {
        stopReading();
        if (binding == null || word == null) {
            return;
        }
        if (!ttsManager.isReady()) {
            // 引擎不可用（没装语音包 / 没引擎）时说清楚原因，别让长辈对着没声的手机干等
            ttsManager.warnIfUnavailable();
            final int token = readingToken;
            handler.postDelayed(() -> {
                if (token == readingToken && ttsManager.isReady()) {
                    beginReading(binding, word);
                }
            }, 150);
            return;
        }
        beginReading(binding, word);
    }

    /** 停止当前自动朗读，并清除所有高亮。 */
    public void stopReading() {
        readingToken++;
        ttsManager.stop();
        resetHighlights(readingBinding);
        readingBinding = null;
        readingWord = null;
    }

    /** 构建朗读片段列表并逐段播放。 */
    private void beginReading(ItemWordCardBinding binding, Word word) {
        final ItemWordCardBinding b = binding;
        final int token = readingToken;
        readingBinding = binding;
        readingWord = word;

        List<Segment> segments = buildSegments(b, word);
        if (segments.isEmpty()) {
            return;
        }
        playNext(b, segments, 0, token);
    }

    /** 顺序播放：每次一段，开始时高亮，结束后播下一段。 */
    private void playNext(ItemWordCardBinding b, List<Segment> segments, int index, final int token) {
        if (token != readingToken) {
            return;
        }
        if (index >= segments.size()) {
            resetHighlights(b);
            if (readingBinding == b) {
                readingBinding = null;
            }
            return;
        }
        Segment segment = segments.get(index);
        ttsManager.speak(segment.say, TextToSpeech.QUEUE_ADD, new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // TTS 回调来自 Binder 线程，必须切回主线程才能改 View / 动 ValueAnimator
                handler.post(() -> {
                    if (token == readingToken) {
                        applyHighlight(b, segment);
                    }
                });
            }

            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) {
                // 引擎能上报朗读位置时，用精确位置驱动逐字变色
                handler.post(() -> {
                    if (token == readingToken) {
                        applyRange(segment, start, end);
                    }
                });
            }

            @Override
            public void onDone(String utteranceId) {
                handler.post(() -> playNext(b, segments, index + 1, token));
            }

            @Override
            public void onError(String utteranceId) {
                handler.post(() -> playNext(b, segments, index + 1, token));
            }
        });
    }

    /** 根据卡片内容生成朗读片段（字、每个词组、场景、巧记），顺序和面板从上到下一致。 */
    private List<Segment> buildSegments(ItemWordCardBinding b, Word word) {
        List<Segment> segments = new ArrayList<>();
        Context ctx = b.getRoot().getContext();

        segments.add(new Segment(TYPE_CHAR, word.getWord(), word.getWord(), -1, null));

        // 拼读：先读整个音节，再读声母、韵母，最后再读一遍（会 → hui，喝，威，hui）
        String spelling = com.easyword.learn.utils.PinyinHelper.spelling(word.getPinyin());
        if (!spelling.isEmpty()) {
            segments.add(new Segment(TYPE_SPELL, spelling, null, -1, null));
        }

        String[] wordArr = word.getWords() == null ? new String[0]
                : word.getWords().trim().split("\\s+");
        int[] starts = new int[wordArr.length];
        int cursor = 0;
        for (int i = 0; i < wordArr.length; i++) {
            starts[i] = cursor;
            cursor += wordArr[i].length();
            if (i < wordArr.length - 1) {
                cursor += " · ".length();
            }
        }
        wordsDisplayText = wordsDisplayText(word);
        b.textWords.setText(EmphasisText.mark(wordsDisplayText, word.getWord()));
        for (int i = 0; i < wordArr.length; i++) {
            segments.add(new Segment(TYPE_WORD, wordArr[i] + "的" + word.getWord(),
                    wordArr[i], i, starts));
        }

        // 巧记排在最后：面板里它也在最后一行，高亮就不会来回跳
        segments.add(new Segment(TYPE_USAGE, word.getUsage(), null, -1, null));
        segments.add(new Segment(TYPE_RHYME, word.getRhyme(), null, -1, null));
        return segments;
    }

    /** 高亮当前正在朗读的段落（歌词跟随效果）。 */
    private void applyHighlight(ItemWordCardBinding b, Segment segment) {
        prepareKaraoke(b);
        switch (segment.type) {
            case TYPE_CHAR:
                b.textWord.setTextColor(READ_HIGHLIGHT);
                b.strokeView.replay();
                break;
            case TYPE_SPELL:
                // 拼读时把拼音标红（跟朗读高亮同色），方便跟着念
                b.textPinyin.setTextColor(READ_HIGHLIGHT);
                break;
            case TYPE_WORD:
                // 拼读读完了，拼音恢复成灰色
                b.textPinyin.setTextColor(0xFFB0B0B0);
                if (segment.wordStarts != null && segment.wordIndex >= 0 && wordsDisplayText != null) {
                    int start = segment.wordStarts[segment.wordIndex];
                    // 连词与词之间的「·」一起染黄，看起来才是连续推进的进度
                    int end = segment.wordIndex + 1 < segment.wordStarts.length
                            ? segment.wordStarts[segment.wordIndex + 1]
                            : b.textWords.length();
                    if (karaokeWords != null) {
                        karaokeWords.start(b.textWords.getText(), 0, start, end);
                    }
                }
                break;
            case TYPE_RHYME:
                b.textPinyin.setTextColor(0xFFB0B0B0);
                if (karaokeRhyme != null) {
                    karaokeRhyme.start(b.textRhyme.getText(), 0, 0, b.textRhyme.length());
                }
                break;
            case TYPE_USAGE:
                b.textPinyin.setTextColor(0xFFB0B0B0);
                if (karaokeUsage != null) {
                    karaokeUsage.start(b.textUsage.getText(), 0, 0, b.textUsage.length());
                }
                break;
            default:
                break;
        }
    }

    /** TTS 上报的位置 → 交给对应的高亮器。 */
    private void applyRange(Segment segment, int start, int end) {
        if (segment.type == TYPE_RHYME && karaokeRhyme != null) {
            karaokeRhyme.onRange(start, end);
        } else if (segment.type == TYPE_USAGE && karaokeUsage != null) {
            karaokeUsage.onRange(start, end);
        }
        // 词组是逐个词朗读（“X的Y”），时间轴和显示文本对不上，交给估算计时
    }

    private void prepareKaraoke(ItemWordCardBinding b) {
        if (karaokeBinding == b) {
            return;
        }
        stopKaraoke();
        karaokeBinding = b;
        karaokeWords = new KaraokeHighlighter(b.textWords);
        karaokeRhyme = new KaraokeHighlighter(b.textRhyme);
        karaokeUsage = new KaraokeHighlighter(b.textUsage);
    }

    private void stopKaraoke() {
        if (karaokeWords != null) {
            karaokeWords.stop();
            karaokeRhyme.stop();
            karaokeUsage.stop();
        }
        karaokeWords = null;
        karaokeRhyme = null;
        karaokeUsage = null;
        karaokeBinding = null;
    }

    /** 词组一行的显示文本（用「·」分隔）；朗读高亮的字符下标就是按这个字符串算的。 */
    private static String wordsDisplayText(Word word) {
        if (word == null || word.getWords() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String part : word.getWords().trim().split("\\s+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    /** 清除所有朗读高亮，恢复默认样式。 */
    private void resetHighlights(ItemWordCardBinding b) {
        if (b == null) {
            return;
        }
        stopKaraoke();
        b.textWord.setTextColor(Color.WHITE);
        b.textPinyin.setTextColor(0xFFB0B0B0);
        b.textRhyme.setBackground(null);
        b.textUsage.setBackground(null);
        b.textWords.setBackground(null);
        String plain = wordsDisplayText != null ? wordsDisplayText : b.textWords.getText().toString();
        b.textWords.setText(readingWord == null ? plain
                : EmphasisText.mark(plain, readingWord.getWord()));
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWordCardBinding binding =
                ItemWordCardBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        CardViewHolder holder = new CardViewHolder(binding);

        bindDrawingGestures(binding, holder);
        bindStrokeGestures(binding, holder);

        binding.btnClear.setOnClickListener(v -> {
            binding.drawingView.clear();
            clearStrokeAt(holder);
        });
        binding.btnSave.setOnClickListener(v -> {
            Word word = wordOf(holder);
            if (word != null) {
                cardListener.onSaveDrawing(binding.drawingView, word);
            }
        });

        // 大字旁边的喇叭：跟着读一遍（认字 → 拼读 → 词组 → 场景 → 巧记）
        binding.btnPlayWord.setOnClickListener(v -> {
            Word word = wordOf(holder);
            if (word != null) {
                // 只读这一个字的音。整串"认字 → 拼读 → 词组 → 场景 → 巧记"是翻到这一页时
                // 自动朗读做的事，点这里再念一遍会显得没完没了。
                stopReading();
                cardListener.onSpeak(word.getWord());
            }
        });
        // 点某一行的喇叭：朗读这一行，同时逐字跟着变色
        binding.btnPlayWords.setOnClickListener(v -> {
            Word word = wordOf(holder);
            if (word != null) {
                speakLine(holder, holder.binding.textWords, word.getWords());
            }
        });
        binding.btnPlayRhyme.setOnClickListener(v -> {
            Word word = wordOf(holder);
            if (word != null) {
                speakLine(holder, holder.binding.textRhyme, word.getRhyme());
            }
        });
        binding.btnPlayUsage.setOnClickListener(v -> {
            Word word = wordOf(holder);
            if (word != null) {
                speakLine(holder, holder.binding.textUsage, word.getUsage());
            }
        });

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        Word word = getItem(position);
        holder.binding.textWord.setText(word.getWord());
        holder.binding.textWord.setTextColor(Color.WHITE);
        holder.binding.strokeView.bindWord(holder.binding.textWord, word.getWord());
        // 拼音把声母和韵母分开写：hui → "h ui"
        holder.binding.textPinyin.setText(
                com.easyword.learn.utils.PinyinHelper.display(word.getPinyin()));
        // 行首的喇叭就是这三行的标记，所以不再显示“词组/巧记/场景”这些字
        holder.binding.textRhyme.setText(word.getRhyme());
        // 词组和例句里把当前学的字标出来（加粗 + 着重号），读的时候跟着变色
        holder.binding.textWords.setText(
                EmphasisText.mark(wordsDisplayText(word), word.getWord()));
        holder.binding.textUsage.setText(EmphasisText.mark(word.getUsage(), word.getWord()));
        holder.binding.textRhyme.setBackground(null);
        holder.binding.textUsage.setBackground(null);
        holder.binding.textWords.setBackground(null);
        if (holder.lastBoundPosition != position) {
            holder.binding.drawingView.clear();
            holder.lastBoundPosition = position;
        }
    }

    /** 双击/左滑收藏时播放“飘心”动画。 */
    private void animateHeart(View heartView) {
        heartView.setVisibility(View.VISIBLE);
        heartView.setAlpha(1f);
        heartView.setScaleX(0.2f);
        heartView.setScaleY(0.2f);

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(heartView, View.SCALE_X, 0.2f, 1.3f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(heartView, View.SCALE_Y, 0.2f, 1.3f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(heartView, View.ALPHA, 1f, 0f);
        ObjectAnimator translateY = ObjectAnimator.ofFloat(heartView, View.TRANSLATION_Y, 0f, -120f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY, alpha, translateY);
        animatorSet.setDuration(900);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                heartView.setVisibility(View.GONE);
                heartView.setTranslationY(0f);
            }
        });
        animatorSet.start();
    }

    /** 底部画笔区手势：单击朗读、双击与左滑收藏、右滑擦除。 */
    private void bindDrawingGestures(ItemWordCardBinding binding, CardViewHolder holder) {
        binding.drawingView.setGestureListener(new DrawingView.OnCardGestureListener() {
            @Override
            public void onSingleTap() {
                tapAt(holder);
            }

            @Override
            public void onDoubleTap() {
                favoriteAt(holder);
            }
        });
    }

    /** 大字区手势（笔画覆盖层）：单击暂停/继续描红、双击/左滑收藏；擦除只用“擦除”按钮。 */
    private void bindStrokeGestures(ItemWordCardBinding binding, CardViewHolder holder) {
        binding.strokeView.setGestureListener(new StrokeAnimationView.OnCardGestureListener() {
            @Override
            public void onSingleTap() {
                // 点范字：正在描红就停在当前笔画，已经停了就从第一笔重新描
                binding.strokeView.toggleAnimation();
            }

            @Override
            public void onDoubleTap() {
                favoriteAt(holder);
            }

            @Override
            public void onSwipeLeft() {
                favoriteAt(holder);
            }
        });
    }

    private Word wordOf(CardViewHolder holder) {
        int pos = holder.getBindingAdapterPosition();
        return pos == RecyclerView.NO_POSITION ? null : getItem(pos);
    }

    private void tapAt(CardViewHolder holder) {
        Word word = wordOf(holder);
        if (word != null) {
            // 点范字只念这一个字：先把正在自动朗读的整串（拼读/词组/例句/巧记）停掉，
            // 不然单字读完后这段朗读会被监听器接着往下念
            stopReading();
            cardListener.onWordTap(word);
        }
    }

    private void favoriteAt(CardViewHolder holder) {
        Word word = wordOf(holder);
        if (word != null) {
            animateHeart(holder.binding.heartOverlay);
            cardListener.onWordDoubleTap(word);
        }
    }

    private void clearStrokeAt(CardViewHolder holder) {
        holder.binding.drawingView.clear();
        Word word = wordOf(holder);
        if (word != null) {
            cardListener.onClearStroke(word);
        }
    }

    private void speakAt(CardViewHolder holder, String text) {
        if (text != null && !text.trim().isEmpty()) {
            cardListener.onSpeak(text);
        }
    }

    /** 朗读知识面板的某一行，并让该行文字逐字跟着变色。 */
    private void speakLine(CardViewHolder holder, android.widget.TextView view, String say) {
        if (say == null || say.trim().isEmpty()) {
            return;
        }
        ItemWordCardBinding b = holder.binding;
        prepareKaraoke(b);
        final KaraokeHighlighter karaoke;
        if (view == b.textWords) {
            karaoke = karaokeWords;
        } else if (view == b.textRhyme) {
            karaoke = karaokeRhyme;
        } else {
            karaoke = karaokeUsage;
        }
        if (karaoke == null) {
            speakAt(holder, say);
            return;
        }
        karaoke.start(view.getText(), 0, 0, view.length());
        ttsManager.speak(say, TextToSpeech.QUEUE_FLUSH, new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onRangeStart(String utteranceId, int start, int end, int frame) {
                handler.post(() -> karaoke.onRange(start, end));
            }

            @Override
            public void onDone(String utteranceId) {
                handler.post(karaoke::stop);
            }

            @Override
            public void onError(String utteranceId) {
                handler.post(karaoke::stop);
            }
        });
    }

    /** Item ViewHolder。 */
    public static class CardViewHolder extends RecyclerView.ViewHolder {
        public final ItemWordCardBinding binding;
        public int lastBoundPosition = -1;

        CardViewHolder(@NonNull ItemWordCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    /** DiffUtil：主键相同则视为同一项；内容变化（收藏/学习状态）才刷新。 */
    private static class WordDiffCallback extends DiffUtil.ItemCallback<Word> {
        @Override
        public boolean areItemsTheSame(@NonNull Word oldItem, @NonNull Word newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull Word oldItem, @NonNull Word newItem) {
            return Objects.equals(oldItem.getWord(), newItem.getWord())
                    && Objects.equals(oldItem.getPinyin(), newItem.getPinyin())
                    && oldItem.isFavorite() == newItem.isFavorite()
                    && oldItem.isLearned() == newItem.isLearned();
        }
    }
}
