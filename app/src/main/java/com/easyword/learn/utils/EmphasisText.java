package com.easyword.learn.utils;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StyleSpan;

/**
 * 把词组 / 例句里“当前正在学的那个字”标出来：字加粗，字下面点三个点（着重号）。
 *
 * <p>只做标记、不改文字内容（不改字、不加字），所以朗读高亮（逐字变色）用的下标和原文字符一一对应。</p>
 */
public final class EmphasisText {

    /** 着重号颜色：和 App 的黄色主色一致。 */
    private static final int DOT_COLOR = 0xFFFFEB3B;

    /** 着重号中心离基线的距离（相对字号）。 */
    private static final float DOT_OFFSET_EM = 0.30f;

    /** 基线下面要留出的高度，够放下着重号，否则会被行高裁掉。 */
    private static final float DOT_ROOM_EM = 0.44f;

    private EmphasisText() {
    }

    /**
     * 给 {@code text} 里每一处 {@code target} 加上“加粗 + 着重号”。
     *
     * @param text   要显示的整段文字（词组一行或例句）
     * @param target 当前学习的字，只标这一个字
     * @return 带标记的文本；没有命中时原样返回
     */
    public static CharSequence mark(String text, String target) {
        if (text == null || text.isEmpty() || target == null || target.isEmpty()) {
            return text;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        boolean hit = false;
        int from = 0;
        while (from <= text.length() - target.length()) {
            int at = text.indexOf(target, from);
            if (at < 0) {
                break;
            }
            sb.setSpan(new StyleSpan(Typeface.BOLD), at, at + target.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new DotSpan(), at, at + target.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            hit = true;
            from = at + target.length();
        }
        return hit ? sb : text;
    }

    /**
     * 字下面的着重号。
     *
     * <p>Android 没有现成的“着重号”样式，用 {@link ReplacementSpan} 把字自己画一遍，
     * 再在基线下面点三个点——线上下划线做得到，基线下面加点只有这条路径。</p>
     */
    private static final class DotSpan extends ReplacementSpan {

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end,
                           Paint.FontMetricsInt fm) {
            if (fm != null) {
                // 行框按字体度量算高度，着重号画在基线下面，得先把这块地方要出来
                int room = (int) Math.ceil(paint.getTextSize() * DOT_ROOM_EM);
                fm.descent = Math.max(fm.descent, room);
                fm.bottom = Math.max(fm.bottom, room);
            }
            return (int) Math.ceil(paint.measureText(text, start, end));
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, Paint paint) {
            // ReplacementSpan 拿到的画笔只有“度量类”样式（加粗等），
            // 逐字高亮用的 ForegroundColorSpan 不在里面（见 TextLine.draw），
            // 所以朗读时的颜色要自己从文字上取
            int color = paint.getColor();
            paint.setColor(highlightColor(text, start, end, color));
            canvas.drawText(text, start, end, x, y, paint);

            float width = paint.measureText(text, start, end);
            float size = paint.getTextSize();
            float radius = Math.max(1.6f, size * 0.045f);
            float step = Math.min(width / 3.4f, radius * 4.2f);
            float cx = x + width / 2f - step;
            float cy = y + size * DOT_OFFSET_EM;

            Paint.Style style = paint.getStyle();
            paint.setColor(DOT_COLOR);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < 3; i++) {
                canvas.drawCircle(cx + i * step, cy, radius, paint);
            }
            paint.setColor(color);
            paint.setStyle(style);
        }

        /** 文字上盖着 ForegroundColorSpan（朗读高亮）时用它，没有就用画笔本来的颜色。 */
        private static int highlightColor(CharSequence text, int start, int end, int fallback) {
            if (text instanceof Spanned) {
                ForegroundColorSpan[] spans = ((Spanned) text)
                        .getSpans(start, end, ForegroundColorSpan.class);
                if (spans.length > 0) {
                    return spans[spans.length - 1].getForegroundColor();
                }
            }
            return fallback;
        }
    }
}
