package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.easyword.learn.R;
import com.easyword.learn.data.TestCatalog;
import com.easyword.learn.data.TestQuestion;
import com.easyword.learn.data.TestScores;
import com.easyword.learn.data.Word;
import com.easyword.learn.databinding.ActivityTestBinding;
import com.easyword.learn.utils.TTSManager;
import com.easyword.learn.viewmodel.WordViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 期末考试 / 小升初 / 中考：**听发音选字**（不认识拼音也能考），100 分制；
 * 交卷后进结果页，用红笔手写的分数（{@link ScoreStampView}）+ 评语，
 * 小升初和中考及格还会给毕业称号。
 */
public class TestActivity extends AppCompatActivity {

    private static final String EXTRA_ID = "test_id";
    private static final long FEEDBACK_MS = 1400L;

    private ActivityTestBinding binding;
    private WordViewModel viewModel;
    private TTSManager tts;
    private TestScores scores;
    private TestCatalog.TestSpec spec;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private List<TestQuestion> questions = new ArrayList<>();
    private final List<Word> correctWords = new ArrayList<>();
    private final List<Word> wrongWords = new ArrayList<>();
    private int index;
    private int correctCount;
    private boolean answering;
    private TestQuestion current;

    public static Intent intent(Context context, String testId) {
        Intent i = new Intent(context, TestActivity.class);
        i.putExtra(EXTRA_ID, testId);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTestBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        spec = TestCatalog.byId(getIntent().getStringExtra(EXTRA_ID));
        if (spec == null) {
            finish();
            return;
        }
        scores = new TestScores(this);
        tts = TTSManager.getInstance(this);
        viewModel = new ViewModelProvider(this).get(WordViewModel.class);
        viewModel.init();

        binding.textResultTitle.setText(spec.title + " · " + spec.range);
        binding.btnQuit.setOnClickListener(v -> finish());
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnAgain.setOnClickListener(v -> startTest());
        binding.btnReplay.setOnClickListener(v -> speakCurrent());

        startTest();
    }

    private void startTest() {
        correctWords.clear();
        wrongWords.clear();
        index = 0;
        correctCount = 0;
        answering = false;
        binding.quizView.setVisibility(View.VISIBLE);
        binding.resultView.setVisibility(View.GONE);

        if (spec.fromBatch == spec.toBatch) {
            viewModel.loadBatchWords(spec.toBatch, this::onWordsLoaded);
        } else {
            viewModel.loadWordsUpToBatch(spec.toBatch, this::onWordsLoaded);
        }
    }

    private void onWordsLoaded(List<Word> pool) {
        questions = TestQuestion.build(pool, spec.questions, random);
        if (questions.isEmpty()) {
            finish();
            return;
        }
        showQuestion();
    }

    private void showQuestion() {
        if (index >= questions.size()) {
            showResult();
            return;
        }
        answering = false;
        current = questions.get(index);
        binding.textProgress.setText(getString(R.string.test_progress, index + 1, questions.size()));
        binding.textScoreSoFar.setText(getString(R.string.test_score_now, correctCount));
        binding.textFeedback.setText("");

        binding.optionBox.removeAllViews();
        for (String option : current.options) {
            Button b = new Button(this);
            b.setText(option);
            b.setTextSize(44f);
            b.setTextColor(getColor(R.color.white));
            b.setBackgroundResource(R.drawable.bg_semi_black);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(74));
            lp.bottomMargin = dp(8);
            b.setLayoutParams(lp);
            b.setGravity(Gravity.CENTER);
            b.setAllCaps(false);
            b.setOnClickListener(v -> answer(b, option));
            binding.optionBox.addView(b);
        }
        speakCurrent();
    }

    /** 读题：把当前这个字念出来。 */
    private void speakCurrent() {
        if (current != null) {
            tts.speak(current.word.getWord());
        }
    }

    private void answer(Button clicked, String option) {
        if (answering || current == null) {
            return;
        }
        answering = true;
        boolean right = option.equals(current.correctOption());
        if (right) {
            correctCount++;
            correctWords.add(current.word);
            clicked.setTextColor(getColor(R.color.yellow));
            binding.textFeedback.setText(R.string.test_correct);
        } else {
            wrongWords.add(current.word);
            clicked.setTextColor(0xFFFF6B6B);
            binding.textFeedback.setText(
                    getString(R.string.test_wrong, current.correctOption()));
        }
        // 把正确答案标出来，并再念一次加深印象
        for (int i = 0; i < binding.optionBox.getChildCount(); i++) {
            Button b = (Button) binding.optionBox.getChildAt(i);
            if (b.getText().toString().equals(current.correctOption())) {
                b.setTextColor(getColor(R.color.yellow));
            }
            b.setEnabled(false);
        }
        tts.speak(current.word.getWord());
        handler.postDelayed(() -> {
            index++;
            showQuestion();
        }, FEEDBACK_MS);
    }

    private void showResult() {
        int total = questions.size();
        int score = Math.round(correctCount * 100f / total);
        scores.save(spec.id, score);
        viewModel.applyTestResults(correctWords, wrongWords);

        binding.quizView.setVisibility(View.GONE);
        binding.resultView.setVisibility(View.VISIBLE);
        binding.scoreStamp.setScore(score);
        binding.textResultDetail.setText(getString(R.string.test_result_detail,
                correctCount, total, TestScores.comment(score)));

        if (spec.diploma != null && score >= 60) {
            binding.textDiploma.setVisibility(View.VISIBLE);
            binding.textDiploma.setText("🎓 " + spec.diploma + "！");
        } else {
            binding.textDiploma.setVisibility(View.GONE);
        }
        binding.textAdvice.setText(getAdvice(score));
    }

    private String getAdvice(int score) {
        if (score >= 90) {
            return "认得又准又牢！这些字可以放心了。";
        }
        if (score >= 80) {
            return "不错，把答错的那几个字多看两眼就更稳了。";
        }
        if (score >= 60) {
            return "通过了。答错的字会回到首页，多练几次。";
        }
        return "别急，答错的字已经放回首页，听熟认熟再来考一次。";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tts != null) {
            tts.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
