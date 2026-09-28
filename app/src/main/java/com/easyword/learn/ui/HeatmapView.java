package com.easyword.learn.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 学习热力图：白底卡片，一格一天，只画指定的半年区间（1~6 月或 7~12 月）。
 *
 * <p>列 = 周（从左到右按时间推进），行 = 星期（周一在最上面，周日最下面），
 * 所以每一列竖着看就是一周。绿色越深表示那天学得越久，没学是浅灰。
 * 上方月份标签跟着区间自动变化。</p>
 */
public class HeatmapView extends View {

    /** 学习时长对应的绿色深浅（分钟）。 */
    private static final long[] THRESHOLDS_MS = {60_000L, 5 * 60_000L, 15 * 60_000L, 30 * 60_000L};
    private static final int[] GREEN = {0xFFC8E6C9, 0xFF81C784, 0xFF4CAF50, 0xFF2E7D32};
    private static final int EMPTY = 0xFFE8E8E8;
    private static final int FUTURE = 0xFFF4F4F4;

    private final Map<String, Long> dailyMillis = new HashMap<>();
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private final RectF cardRect = new RectF();

    /** 方格边长：按可用宽度自动算，全年 365 天刚好铺满一行不滚动。 */
    private float cellSize;
    private float gapSize;
    private float padSize;
    private float headerHeight;
    private int columnCount = 53;

    /** 当前显示的区间（默认整天）。 */
    private long rangeStart = Long.MIN_VALUE;
    private long rangeEnd = Long.MAX_VALUE;

    public HeatmapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        cardPaint.setColor(0xFFFFFFFF);
        labelPaint.setColor(0xFF999999);
        labelPaint.setTextSize(getResources().getDisplayMetrics().density * 9f);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        gapSize = getResources().getDisplayMetrics().density * 1.2f;
        padSize = getResources().getDisplayMetrics().density * 9f;
        headerHeight = getResources().getDisplayMetrics().density * 12f;
    }

    /** 宽度撑满父容器，高度按方格数算出来（不滚动）。 */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = Math.max(1, Math.round(width - padSize * 2 - (columnCount - 1) * gapSize));
        cellSize = Math.max(1f, available / (float) columnCount);
        int height = Math.round(padSize * 2 + headerHeight + 7 * cellSize + 6 * gapSize);
        setMeasuredDimension(width, height);
    }

    /** 设置 {yyyy-MM-dd → 学习毫秒}。 */
    public void setData(Map<String, Long> data) {
        dailyMillis.clear();
        if (data != null) {
            dailyMillis.putAll(data);
        }
        invalidate();
    }

    /** 设置要显示的半年区间（start/end 都是毫秒时间戳）。 */
    public void setRange(long startMillis, long endMillis) {
        rangeStart = startMillis;
        rangeEnd = endMillis;
        columnCount = weeksBetween(startMillis, endMillis);
        requestLayout();
        invalidate();
    }

    /** 区间占几周（用于算方格宽度）。 */
    private static int weeksBetween(long start, long end) {
        Calendar first = Calendar.getInstance();
        first.setTimeInMillis(start);
        first.set(Calendar.HOUR_OF_DAY, 0);
        first.set(Calendar.MINUTE, 0);
        first.set(Calendar.SECOND, 0);
        first.set(Calendar.MILLISECOND, 0);
        first.add(Calendar.DAY_OF_YEAR, -((first.get(Calendar.DAY_OF_WEEK) + 5) % 7));

        Calendar last = Calendar.getInstance();
        last.setTimeInMillis(end);
        last.add(Calendar.DAY_OF_YEAR, 6 - (last.get(Calendar.DAY_OF_WEEK) + 5) % 7);

        long days = (last.getTimeInMillis() - first.getTimeInMillis()) / 86_400_000L + 1;
        return (int) Math.max(1, (days + 6) / 7);
    }

    private int colorFor(long millis, boolean isFuture) {
        if (isFuture) {
            return FUTURE;
        }
        if (millis <= 0) {
            return EMPTY;
        }
        for (int i = 0; i < THRESHOLDS_MS.length; i++) {
            if (millis < THRESHOLDS_MS[i]) {
                return GREEN[Math.max(0, i - 1)];
            }
        }
        return GREEN[GREEN.length - 1];
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        float pad = padSize;
        cardRect.set(0f, 0f, getWidth(), getHeight());
        float radius = 0f;   // 撑满整行，不要圆角外框
        canvas.drawRoundRect(cardRect, radius, radius, cardPaint);

        Calendar today = Calendar.getInstance();

        // 从区间第一天所在那一周的周一开始画，保证每一列都是完整的一周
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(rangeStart == Long.MIN_VALUE ? today.getTimeInMillis() : rangeStart);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        int mondayOffset = (day.get(Calendar.DAY_OF_WEEK) + 5) % 7;  // 周一 = 0
        day.add(Calendar.DAY_OF_YEAR, -mondayOffset);
        Calendar first = (Calendar) day.clone();
        long periodStart = startOfDay(day.getTimeInMillis() + mondayOffset * 86_400_000L);
        long periodEnd = rangeEnd == Long.MAX_VALUE ? Long.MAX_VALUE : rangeEnd;

        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(periodEnd == Long.MAX_VALUE ? today.getTimeInMillis() : periodEnd);
        end.add(Calendar.DAY_OF_YEAR, 6 - (end.get(Calendar.DAY_OF_WEEK) + 5) % 7);

        long totalDays = (end.getTimeInMillis() - first.getTimeInMillis()) / 86_400_000L + 1;
        int cols = (int) Math.max(1, (totalDays + 6) / 7);
        int rows = 7;
        columnCount = cols;

        float cell = cellSize;
        float gap = gapSize;
        float startX = pad;
        float startY = pad + headerHeight;

        long todayStart = startOfDay(today.getTimeInMillis());
        Map<String, Integer> monthsDrawn = new HashMap<>();
        String todayKey = fmt.format(today.getTime());

        Calendar cursor = (Calendar) first.clone();
        for (int i = 0; i < totalDays; i++) {
            int col = i / 7;
            int row = i % 7;
            long cursorStart = startOfDay(cursor.getTimeInMillis());
            boolean inPeriod = cursorStart >= periodStart && cursorStart <= periodEnd;
            if (inPeriod) {
                String key = fmt.format(cursor.getTime());
                long millis = dailyMillis.containsKey(key) ? dailyMillis.get(key) : 0L;
                boolean future = startOfDay(cursor.getTimeInMillis()) > todayStart;
                cellPaint.setColor(colorFor(millis, future));
                float left = startX + col * (cell + gap);
                float top = startY + row * (cell + gap);
                canvas.drawRoundRect(left, top, left + cell, top + cell,
                        cell * 0.25f, cell * 0.25f, cellPaint);
                if (key.equals(todayKey)) {
                    cellPaint.setStyle(Paint.Style.STROKE);
                    cellPaint.setStrokeWidth(dp(1.2f));
                    cellPaint.setColor(0xFF2E7D32);
                    canvas.drawRoundRect(left, top, left + cell, top + cell,
                            cell * 0.25f, cell * 0.25f, cellPaint);
                    cellPaint.setStyle(Paint.Style.FILL);
                }
                // 每月第一次出现时标一个月份
                int month = cursor.get(Calendar.MONTH);
                if (!monthsDrawn.containsKey(key.substring(0, 7))) {
                    monthsDrawn.put(key.substring(0, 7), col);
                    canvas.drawText((month + 1) + "月", left,
                            startY - dp(3f), labelPaint);
                }
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }
    }

    private static long startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
