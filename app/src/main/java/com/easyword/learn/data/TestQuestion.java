package com.easyword.learn.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 一道听力题：听发音，从 4 个汉字里选出听到的那个。
 *
 * <p>不认识拼音也能考，正好适合零基础的老人。</p>
 */
public class TestQuestion {

    /** 正确答案（听到的字）。 */
    public final Word word;
    /** 4 个备选汉字。 */
    public final String[] options;
    public final int correctIndex;

    TestQuestion(Word word, String[] options, int correctIndex) {
        this.word = word;
        this.options = options;
        this.correctIndex = correctIndex;
    }

    public String correctOption() {
        return options[correctIndex];
    }

    /**
     * 从题库里抽题。
     *
     * @param pool  本次考试范围的字
     * @param count 出几道题
     */
    public static List<TestQuestion> build(List<Word> pool, int count, Random random) {
        List<Word> usable = new ArrayList<>();
        for (Word w : pool) {
            if (w.getWord() != null && !w.getWord().trim().isEmpty()) {
                usable.add(w);
            }
        }
        if (usable.isEmpty()) {
            return new ArrayList<>();
        }
        Collections.shuffle(usable, random);

        List<TestQuestion> questions = new ArrayList<>();
        int total = Math.min(count, usable.size());
        for (int i = 0; i < total; i++) {
            Word answer = usable.get(i);
            // 干扰项：同范围里读音不同的字（读音相同的话会有两个正确答案）
            List<Word> candidates = new ArrayList<>();
            for (Word other : usable) {
                if (other.getId() == answer.getId()) {
                    continue;
                }
                if (sameReading(other, answer)) {
                    continue;
                }
                candidates.add(other);
            }
            Collections.shuffle(candidates, random);

            List<String> options = new ArrayList<>();
            options.add(answer.getWord());
            for (Word other : candidates) {
                if (options.size() == 4) {
                    break;
                }
                if (!options.contains(other.getWord())) {
                    options.add(other.getWord());
                }
            }
            Collections.shuffle(options, random);
            questions.add(new TestQuestion(answer, options.toArray(new String[0]),
                    options.indexOf(answer.getWord())));
        }
        return questions;
    }

    private static boolean sameReading(Word a, Word b) {
        String pa = a.getPinyin() == null ? "" : a.getPinyin().trim().toLowerCase();
        String pb = b.getPinyin() == null ? "" : b.getPinyin().trim().toLowerCase();
        return !pa.isEmpty() && pa.equals(pb);
    }
}
