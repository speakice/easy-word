package com.easyword.learn.utils;

/**
 * 数字的中文读法：101 → 一百零一，1010 → 一千零一十，1001 → 一千零一，1.1 → 一点一。
 *
 * <p>朗读时用中文而不是直接丢「101」给引擎，读音才稳定（不会读成「一零一」）。</p>
 */
public final class NumberWords {

    private static final String[] DIGITS = {
            "零", "一", "二", "三", "四", "五", "六", "七", "八", "九"};

    private NumberWords() {
    }

    /** 整数的读法。 */
    public static String of(int n) {
        if (n < 0) {
            return "负" + of(-n);
        }
        if (n == 0) {
            return "零";
        }
        if (n < 100) {
            return under100(n, false);
        }
        if (n < 1000) {
            int hundred = n / 100;
            int rest = n % 100;
            String s = DIGITS[hundred] + "百";
            if (rest == 0) {
                return s;
            }
            // 一百零一 / 一百一十
            return s + (rest < 10 ? "零" + DIGITS[rest] : under100(rest, true));
        }
        if (n < 10000) {
            int thousand = n / 1000;
            int rest = n % 1000;
            String s = DIGITS[thousand] + "千";
            if (rest == 0) {
                return s;
            }
            if (rest < 100) {
                return s + "零" + under100(rest, true);   // 一千零一 / 一千零一十
            }
            return s + of(rest);                          // 一千一百
        }
        if (n < 100_000_000) {
            int tenThousand = n / 10000;
            int rest = n % 10000;
            String s = of(tenThousand) + "万";
            if (rest == 0) {
                return s;
            }
            return s + (rest < 1000 ? "零" : "") + of(rest);
        }
        return String.valueOf(n);
    }

    /** 小数的读法：1.0 → 一点零，1.25 → 一点二五。 */
    public static String ofDecimal(String decimal) {
        if (decimal == null || decimal.trim().isEmpty()) {
            return "";
        }
        String value = decimal.trim();
        int dot = value.indexOf('.');
        if (dot < 0) {
            try {
                return of(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                return value;
            }
        }
        String intPart = value.substring(0, dot);
        String decPart = value.substring(dot + 1);
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(of(Integer.parseInt(intPart)));
        } catch (NumberFormatException e) {
            sb.append(intPart);
        }
        sb.append("点");
        for (char c : decPart.toCharArray()) {
            if (c >= '0' && c <= '9') {
                sb.append(DIGITS[c - '0']);
            }
        }
        return sb.toString();
    }

    /** 100 以内的读法；{@code leadingYi} 为真时 10~19 读成「一十…」（用在百位/千位后面）。 */
    private static String under100(int n, boolean leadingYi) {
        if (n < 10) {
            return DIGITS[n];
        }
        int tens = n / 10;
        int units = n % 10;
        StringBuilder sb = new StringBuilder();
        if (tens == 1) {
            if (leadingYi) {
                sb.append("一");
            }
        } else {
            sb.append(DIGITS[tens]);
        }
        sb.append("十");
        if (units != 0) {
            sb.append(DIGITS[units]);
        }
        return sb.toString();
    }
}
