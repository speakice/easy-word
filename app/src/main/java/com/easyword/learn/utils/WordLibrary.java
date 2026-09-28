package com.easyword.learn.utils;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 950 字字库加载器：从 assets/library/words.json 加载每个字的拼音、批次、组词、
 * 顺口溜、使用场景；从 assets/library/passages.json 加载阅读短文。全应用只初始化一次。
 */
public final class WordLibrary {

    /** 一个字的字库条目。 */
    public static class LibWord {
        public final String ch;
        public final String py;
        public final int batch;
        public final List<String> words;
        public final String rhyme;
        public final String usage;

        LibWord(String ch, String py, int batch, List<String> words,
                String rhyme, String usage) {
            this.ch = ch;
            this.py = py;
            this.batch = batch;
            this.words = words;
            this.rhyme = rhyme;
            this.usage = usage;
        }
    }

    /** 一篇阅读短文。 */
    public static class Passage {
        public final String title;
        public final String text;

        Passage(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    private static final List<LibWord> LIST = new ArrayList<>();
    private static final Map<String, LibWord> BY_CHAR = new HashMap<>();
    private static final List<Passage> PASSAGES = new ArrayList<>();
    /** 汉字 → 拼音（给用户自己添加的字用）。 */
    private static final Map<String, String> PINYIN = new HashMap<>();
    private static boolean loaded;

    private WordLibrary() {
    }

    /** 加载字库与短文（幂等，线程安全）。 */
    public static synchronized void ensureLoaded(Context context) {
        if (loaded) {
            return;
        }
        try {
            JSONObject root = new JSONObject(read(context, "library/words.json"));
            JSONArray arr = root.getJSONArray("words");
            LIST.clear();
            BY_CHAR.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String ch = o.getString("c");
                List<String> words = new ArrayList<>();
                JSONArray ws = o.getJSONArray("w");
                for (int j = 0; j < ws.length(); j++) {
                    words.add(ws.getString(j));
                }
                LibWord w = new LibWord(ch, o.optString("py", ""), o.getInt("g"),
                        words, o.optString("r", ""), o.optString("u", ""));
                LIST.add(w);
                BY_CHAR.put(ch, w);
            }

            JSONArray ps = new JSONArray(read(context, "library/passages.json"));
            PASSAGES.clear();
            for (int i = 0; i < ps.length(); i++) {
                JSONObject o = ps.getJSONObject(i);
                PASSAGES.add(new Passage(o.optString("title", ""), o.optString("text", "")));
            }

            PINYIN.clear();
            for (String line : readLines(context, "library/pinyin.txt")) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    PINYIN.put(line.substring(0, tab).trim(), line.substring(tab + 1).trim());
                }
            }
        } catch (Exception ignore) {
            // 资源缺失时保持空库，不阻塞启动
        }
        loaded = true;
    }

    private static String read(Context context, String path) throws Exception {
        InputStream in = context.getAssets().open(path);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    /** 按行读取（read() 会把换行丢掉，逐行解析的不能用它）。 */
    private static java.util.List<String> readLines(Context context, String path) throws Exception {
        java.util.List<String> lines = new ArrayList<>();
        InputStream in = context.getAssets().open(path);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** 全部字（950），按学习顺序（批次内为生成顺序）。 */
    public static List<LibWord> all(Context context) {
        ensureLoaded(context);
        return Collections.unmodifiableList(LIST);
    }

    /** 指定批次（1~10）的字。 */
    public static List<LibWord> batch(Context context, int batch) {
        ensureLoaded(context);
        List<LibWord> out = new ArrayList<>();
        for (LibWord w : LIST) {
            if (w.batch == batch) {
                out.add(w);
            }
        }
        return out;
    }

    /** 按字查条目。 */
    public static LibWord get(Context context, String ch) {
        ensureLoaded(context);
        return BY_CHAR.get(ch);
    }

    public static int size(Context context) {
        ensureLoaded(context);
        return LIST.size();
    }

    public static List<Passage> passages(Context context) {
        ensureLoaded(context);
        return Collections.unmodifiableList(PASSAGES);
    }

    /** 组词展示文本（用 · 分隔）。 */
    public static String wordsText(Context context, String ch) {
        LibWord w = get(context, ch);
        if (w == null || w.words.isEmpty()) {
            return ch + "字";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < w.words.size(); i++) {
            if (i > 0) {
                sb.append(" · ");
            }
            sb.append(w.words.get(i));
        }
        return sb.toString();
    }

    /** 某个字的拼音（查不到返回空串）。 */
    public static String pinyin(Context context, String ch) {
        ensureLoaded(context);
        String py = PINYIN.get(ch);
        return py == null ? "" : py;
    }
}
