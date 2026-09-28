package com.easyword.learn.utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 半年区间：上半年 1~6 月、下半年 7~12 月，用来给热力图分页。
 */
public final class HalfYear implements Comparable<HalfYear> {

    public final int year;
    /** 1 = 上半年，2 = 下半年。 */
    public final int half;

    private HalfYear(int year, int half) {
        this.year = year;
        this.half = half;
    }

    /** 某个时间点属于哪个半年。 */
    public static HalfYear of(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        int month = c.get(Calendar.MONTH);      // 0~11
        return new HalfYear(c.get(Calendar.YEAR), month < 6 ? 1 : 2);
    }

    /** 区间内所有的半年（含首尾，按时间先后）。 */
    public static List<HalfYear> between(HalfYear from, HalfYear to) {
        List<HalfYear> out = new ArrayList<>();
        HalfYear cursor = from;
        while (cursor.compareTo(to) <= 0) {
            out.add(cursor);
            cursor = cursor.next();
        }
        return out;
    }

    public HalfYear next() {
        return half == 1 ? new HalfYear(year, 2) : new HalfYear(year + 1, 1);
    }

    /** 例如「2026年上」。 */
    public String label() {
        return year + "年" + (half == 1 ? "上" : "下");
    }

    /** 区间第一天 00:00。 */
    public long startMillis() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(year, half == 1 ? Calendar.JANUARY : Calendar.JULY, 1);
        return c.getTimeInMillis();
    }

    /** 区间最后一天 23:59:59.999。 */
    public long endMillis() {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(half == 1 ? year : year + 1, half == 1 ? Calendar.JULY : Calendar.JANUARY, 1);
        c.add(Calendar.MILLISECOND, -1);
        return c.getTimeInMillis();
    }

    @Override
    public int compareTo(HalfYear o) {
        return year != o.year ? Integer.compare(year, o.year) : Integer.compare(half, o.half);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HalfYear && compareTo((HalfYear) o) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, half);
    }

    @Override
    public String toString() {
        return String.format(Locale.CHINA, "%d-%d", year, half);
    }
}
