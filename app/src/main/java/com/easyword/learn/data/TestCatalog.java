package com.easyword.learn.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 考试科目表：10 个批次各一次期末考试（一个批次 = 一个年级），
 * 外加小升初考试（前 6 批 = 600 字）和中考（全部 950 字）。
 *
 * <p>100 分制：年级期末考 10 道题（每题 10 分），小升初 / 中考 20 道题（每题 5 分）。</p>
 */
public final class TestCatalog {

    /** 一次考试的定义。 */
    public static class TestSpec {
        public final String id;
        public final String title;
        public final String range;
        public final int fromBatch;
        public final int toBatch;
        public final int questions;
        /** 通过后拿到的毕业称号，没有就是 null。 */
        public final String diploma;
        /** 里程碑考试（小升初 / 中考），列表里要用颜色区分。 */
        public final boolean milestone;

        TestSpec(String id, String title, String range, int fromBatch, int toBatch,
                 int questions, String diploma, boolean milestone) {
            this.id = id;
            this.title = title;
            this.range = range;
            this.fromBatch = fromBatch;
            this.toBatch = toBatch;
            this.questions = questions;
            this.diploma = diploma;
            this.milestone = milestone;
        }

        /** 这个考试要求已经解锁到第几批。 */
        public int requiredBatch() {
            return toBatch;
        }
    }

    private static final String[] GRADE_NAMES = {
            "一年级", "二年级", "三年级", "四年级", "五年级", "六年级",
            "初中一年级", "初中二年级", "初中三年级",
    };

    public static final List<TestSpec> ALL;

    static {
        List<TestSpec> list = new ArrayList<>();
        // 小学一到六年级期末考试
        for (int batch = 1; batch <= 6; batch++) {
            list.add(new TestSpec("unit_" + batch, GRADE_NAMES[batch - 1] + "期末考试",
                    "第 " + batch + " 批 · 100 字", batch, batch, 10, null, false));
        }
        // 六年级之后就是小升初
        list.add(new TestSpec("final_primary", "小升初考试",
                "第 1~6 批 · 600 字", 1, 6, 20, "小学毕业", true));
        // 初中一到三年级期末考试
        for (int batch = 7; batch <= 9; batch++) {
            list.add(new TestSpec("unit_" + batch, GRADE_NAMES[batch - 1] + "期末考试",
                    "第 " + batch + " 批 · 100 字", batch, batch, 10, null, false));
        }
        list.add(new TestSpec("unit_10", "初中总复习期末考试",
                "第 10 批 · 50 字", 10, 10, 10, null, false));
        list.add(new TestSpec("final_junior", "中考",
                "全部 10 批 · 950 字", 1, 10, 20, "初中毕业", true));
        ALL = Collections.unmodifiableList(list);
    }

    private TestCatalog() {
    }

    public static TestSpec byId(String id) {
        for (TestSpec spec : ALL) {
            if (spec.id.equals(id)) {
                return spec;
            }
        }
        return null;
    }

    /** 批次对应的年级名字（字表按年级展示时用）。 */
    public static String batchName(int batch) {
        if (batch >= 1 && batch <= GRADE_NAMES.length) {
            return GRADE_NAMES[batch - 1];
        }
        return "初中总复习";
    }

}
