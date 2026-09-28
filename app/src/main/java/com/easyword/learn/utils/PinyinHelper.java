package com.easyword.learn.utils;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 汉语拼音小工具：声母 / 韵母表、把音节拆成「声母 + 韵母」（中间加空格显示），
 * 以及"拼读"要念的内容（会 → hui，喝，威，hui）。
 */
public final class PinyinHelper {

    /** 23 个声母。 */
    public static final String[] INITIALS = {
            "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x",
            "zh", "ch", "sh", "r", "z", "c", "s", "y", "w"};

    /** 声母的读音（汉语拼音字母名称）。 */
    public static final String[] INITIAL_SOUNDS = {
            "玻", "坡", "摸", "佛", "得", "特", "讷", "勒", "哥", "科", "喝", "基", "欺", "希",
            "知", "蚩", "诗", "日", "资", "雌", "思", "衣", "乌"};

    /** 24 个韵母。 */
    public static final String[] FINALS = {
            "a", "o", "e", "i", "u", "ü",
            "ai", "ei", "ui", "ao", "ou", "iu", "ie", "üe", "er",
            "an", "en", "in", "un", "ün",
            "ang", "eng", "ing", "ong"};

    /** 韵母单独读出来时的字。 */
    public static final String[] FINAL_SOUNDS = {
            "啊", "喔", "鹅", "衣", "乌", "迂",
            "哀", "诶", "威", "熬", "欧", "优", "耶", "约", "儿",
            "安", "恩", "因", "温", "晕",
            "昂", "鞥", "英", "轰"};

    /** 整体认读音节：这些不拆声母韵母。 */
    private static final String[] WHOLE = {
            "zhi", "chi", "shi", "ri", "zi", "ci", "si",
            "yi", "wu", "yu", "ye", "yue", "yuan", "yin", "yun", "ying"};

    private static final Map<Character, Character> PLAIN = new HashMap<>();

    static {
        String[] toned = {"āáǎà", "ōóǒò", "ēéěè", "īíǐì", "ūúǔù", "ǖǘǚǜ"};
        char[] base = {'a', 'o', 'e', 'i', 'u', 'v'};
        for (int i = 0; i < toned.length; i++) {
            for (char c : toned[i].toCharArray()) {
                PLAIN.put(c, base[i]);
            }
        }
    }

    private PinyinHelper() {
    }

    /** 去掉声调（ü 统一成 v 便于比较）。 */
    private static String plain(String pinyin) {
        if (pinyin == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : pinyin.toLowerCase().toCharArray()) {
            Character p = PLAIN.get(c);
            char out = p != null ? p : c;
            sb.append(out == 'ü' ? 'v' : out);
        }
        return sb.toString();
    }

    /** 这个音节的声母；没有声母（含整体认读音节）返回空串。 */
    public static String initialOf(String pinyin) {
        String value = plain(pinyin);
        if (value.isEmpty()) {
            return "";
        }
        for (String whole : WHOLE) {
            if (value.equals(whole)) {
                return "";                     // 整体认读，不拆
            }
        }
        for (String initial : INITIALS) {
            if (value.startsWith(initial)) {
                return pinyin.substring(0, initial.length());
            }
        }
        return "";
    }

    /** 音节的韵母；拆不出来返回原音节。 */
    public static String finalOf(String pinyin) {
        String initial = initialOf(pinyin);
        if (initial.isEmpty()) {
            return pinyin == null ? "" : pinyin;
        }
        return pinyin.substring(initial.length());
    }

    /** 卡片上显示的拼音：声母和韵母中间加空格，例如 hui → "h ui"。 */
    public static String display(String pinyin) {
        if (pinyin == null || pinyin.trim().isEmpty()) {
            return "";
        }
        String value = pinyin.trim();
        String initial = initialOf(value);
        if (initial.isEmpty()) {
            return value;
        }
        return initial + " " + value.substring(initial.length());
    }

    /** 声母单独读出来时的字。 */
    public static String soundOfInitial(String initial) {
        String value = plain(initial);
        for (int i = 0; i < INITIALS.length; i++) {
            if (INITIALS[i].equals(value)) {
                return INITIAL_SOUNDS[i];
            }
        }
        return "";
    }

    /** 韵母单独读出来时的字。 */
    public static String soundOfFinal(String finals) {
        String value = plain(finals);
        for (int i = 0; i < FINALS.length; i++) {
            if (plain(FINALS[i]).equals(value)) {
                return FINAL_SOUNDS[i];
            }
        }
        return "";
    }

    /**
     * 韵母部分怎么念：二拼（b + an）读一个音，三拼（b + i + an）读两个音。
     */
    private static List<String> finalReadings(String finals) {
        List<String> out = new ArrayList<>();
        String whole = soundOfFinal(finals);
        if (!whole.isEmpty()) {
            out.add(whole);
            return out;
        }
        // 三拼音节：介母（i / u / ü）+ 韵母，例如 ian → 衣 + 安
        if (finals.length() > 1) {
            String head = finals.substring(0, 1);
            String tail = finals.substring(1);
            String headSound = soundOfFinal(head);
            String tailSound = soundOfFinal(tail);
            if (!headSound.isEmpty() && !tailSound.isEmpty()) {
                out.add(headSound);
                out.add(tailSound);
                return out;
            }
        }
        return out;
    }

    /**
     * 拼读要念的内容：会 → "hui，喝，威，hui"；扁 → "biǎn，玻，衣，安，biǎn"
     * （先读整个音节，再读声母和韵母，最后再读一遍）。整体认读音节只连读两遍。
     */
    public static String spelling(String pinyin) {
        if (pinyin == null || pinyin.trim().isEmpty()) {
            return "";
        }
        String value = pinyin.trim();
        String initial = initialOf(value);
        if (initial.isEmpty()) {
            return value + "，" + value;
        }
        String finals = value.substring(initial.length());
        String initialSound = soundOfInitial(initial);
        StringBuilder sb = new StringBuilder(value).append("，");
        if (!initialSound.isEmpty()) {
            sb.append(initialSound).append("，");
        }
        for (String part : finalReadings(finals)) {
            sb.append(part).append("，");
        }
        sb.append(value);
        return sb.toString();
    }
}
