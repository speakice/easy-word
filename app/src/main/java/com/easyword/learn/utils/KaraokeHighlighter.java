package com.easyword.learn.utils;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

/**
 * 逐字跟读：读到哪里，从开头到那个字的文字就变成红色（只改文字颜色，不加底色）。
 *
 * <p>例：读到「我爱你」的「爱」时，「我爱」两个字是红的，后面还没读的保持原来的颜色。
 * 优先用 TTS 引擎上报的 onRangeStart 精确定位；引擎不上报时按中文朗读速度估算推进。</p>
 */
public final class KaraokeHighlighter {

    /** 已经读过的文字的颜色：跟描红笔迹同一个红（= {@code R.color.stroke_red}）。 */
    private static final int READ_COLOR = 0xFFFF3B30;

    /** 估算参数：引擎起播有一点延迟，之后约每 170ms 一个字。 */
    private static final long TICK_MS = 50L;
    private static final long LEAD_IN_MS = 260L;
    private static final long MS_PER_CHAR = 170L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView view;
    private final Runnable ticker = this::tick;

    private CharSequence plain;
    /** 变色从这个下标开始（跳过“词组：”这类前缀）。 */
    private int colorFrom;
    /** 本段要朗读的区间 [segmentStart, segmentEnd)。 */
    private int segmentStart;
    private int segmentEnd;
    /** 已经读到的最远位置，保证只往前走、不回退。 */
    private int furthest;
    private int lastPainted = -1;
    private long startedAt;
    private boolean precise;

    public KaraokeHighlighter(TextView view) {
        this.view = view;
    }

    /**
     * 开始/继续跟读一段。
     *
     * @param text         界面上显示的完整文字（含“词组：”这类前缀）
     * @param colorFrom    从哪个下标开始变黄（内容开头）
     * @param segmentStart 这一段朗读的起始下标
     * @param segmentEnd   这一段朗读的结束下标（不含）
     */
    public void start(CharSequence text, int colorFrom, int segmentStart, int segmentEnd) {
        if (view == null || text == null || text.length() == 0) {
            return;
        }
        boolean sameLine = plain != null && plain.toString().contentEquals(text)
                && this.colorFrom == colorFrom;
        if (!sameLine) {
            stop();
            plain = text;
            this.colorFrom = Math.max(0, Math.min(colorFrom, text.length()));
            furthest = this.colorFrom - 1;
            lastPainted = -1;
        }
        this.segmentStart = Math.max(0, Math.min(segmentStart, text.length()));
        this.segmentEnd = Math.max(this.segmentStart, Math.min(segmentEnd, text.length()));
        precise = false;
        startedAt = SystemClock.uptimeMillis();
        handler.removeCallbacks(ticker);
        paint(Math.max(furthest, this.segmentStart - 1));
        handler.postDelayed(ticker, TICK_MS);
    }

    /** TTS 上报的朗读位置（相对本段文本的字符下标）。 */
    public void onRange(int start, int end) {
        if (plain == null) {
            return;
        }
        precise = true;
        paint(segmentStart + Math.max(0, start));
    }

    /** 停止跟读并恢复成原来的文字颜色。 */
    public void stop() {
        handler.removeCallbacks(ticker);
        precise = false;
        furthest = -1;
        lastPainted = -1;
        if (plain != null && view != null) {
            view.setText(plain);
        }
        plain = null;
    }

    private void tick() {
        if (plain == null) {
            return;
        }
        if (!precise) {
            long elapsed = SystemClock.uptimeMillis() - startedAt - LEAD_IN_MS;
            int step = (int) Math.max(0, elapsed / MS_PER_CHAR);
            int position = Math.min(segmentStart + step, Math.max(segmentStart, segmentEnd - 1));
            paint(position);
        }
        handler.postDelayed(ticker, TICK_MS);
    }

    /** 把 [colorFrom, position] 这段文字染成红色，后面的字保持原色。 */
    private void paint(int position) {
        if (view == null || plain == null) {
            return;
        }
        int target = Math.max(furthest, position);
        if (target < colorFrom || target == lastPainted) {
            return;
        }
        furthest = target;
        lastPainted = target;
        SpannableStringBuilder sb = new SpannableStringBuilder(plain);
        int end = Math.min(target + 1, plain.length());
        if (end > colorFrom) {
            sb.setSpan(new ForegroundColorSpan(READ_COLOR), colorFrom, end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        view.setText(sb);
    }
}
