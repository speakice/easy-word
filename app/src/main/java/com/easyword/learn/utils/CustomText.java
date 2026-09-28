package com.easyword.learn.utils;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户自己填的常用写字内容（姓名、性别、籍贯、住址…），用于把这辈子最常写的字
 * 加进字库。以 JSON 存在本机。
 */
public final class CustomText {

    /** 一条资料：名称 + 内容。 */
    public static class Entry {
        public String label;
        public String value;

        public Entry(String label, String value) {
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
        }
    }

    /** 推荐添加的项目（签字、填表常写）。 */
    public static final String[] PRESETS = {"姓名", "性别", "籍贯", "住址", "单位", "电话"};

    private static final String FILE = "easyword_custom_text";
    private static final String KEY = "entries";

    private CustomText() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static List<Entry> entries(Context context) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) {
                    out.add(new Entry(o.optString("l"), o.optString("v")));
                }
            }
        } catch (Exception ignored) {
            // 数据坏了就当空的
        }
        return out;
    }

    public static void save(Context context, List<Entry> entries) {
        JSONArray arr = new JSONArray();
        for (Entry e : entries) {
            if ((e.label == null || e.label.trim().isEmpty())
                    && (e.value == null || e.value.trim().isEmpty())) {
                continue;
            }
            JSONObject o = new JSONObject();
            try {
                o.put("l", e.label);
                o.put("v", e.value);
            } catch (Exception ignored) {
                continue;
            }
            arr.put(o);
        }
        prefs(context).edit().putString(KEY, arr.toString()).apply();
    }

    /** 所有内容拼起来（名称 + 内容），用来找需要学的字。 */
    public static String allText(Context context) {
        StringBuilder sb = new StringBuilder();
        for (Entry e : entries(context)) {
            sb.append(e.label).append(e.value);
        }
        return sb.toString();
    }

    /** 名称里含这个字的资料（用于生成"词组 / 场景"）。 */
    public static List<Entry> entriesContaining(Context context, String ch) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries(context)) {
            String text = e.label + e.value;
            if (text.contains(ch)) {
                out.add(e);
            }
        }
        return out;
    }
}
