package com.easyword.learn.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.Random;

/**
 * 红笔手写分数：用偏手写的字体（casual，设备没有就退回默认字体），
 * 微微倾斜，分数下面再画两条手绘的横线，像老师用红笔批在卷子上。
 */
public class ScoreStampView extends View {

    private static final int INK = 0xFFE53935;

    private final Paint inkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private int score = -1;

    public ScoreStampView(Context context, AttributeSet attrs) {
        super(context, attrs);
        inkPaint.setColor(INK);
        inkPaint.setTypeface(Typeface.create("casual", Typeface.BOLD));
        inkPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setScore(int score) {
        this.score = score;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (score < 0) {
            return;
        }
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;

        // 分数：微微倾斜
        inkPaint.setStyle(Paint.Style.FILL);
        inkPaint.setTextAlign(Paint.Align.CENTER);
        canvas.save();
        canvas.rotate(-7f, cx, cy);
        inkPaint.setTextSize(h * 0.56f);
        canvas.drawText(String.valueOf(score), cx - h * 0.10f, cy + h * 0.10f, inkPaint);
        inkPaint.setTextSize(h * 0.26f);
        canvas.drawText("分", cx + h * 0.42f, cy + h * 0.10f, inkPaint);
        canvas.restore();

        // 下面两条手绘横线（第二条稍短一点，像随手划的）
        inkPaint.setStyle(Paint.Style.STROKE);
        inkPaint.setStrokeWidth(h * 0.028f);
        float textWidth = h * 0.92f;
        float left = cx - textWidth / 2f;
        float right = cx + textWidth / 2f;
        drawHandLine(canvas, left, right, cy + h * 0.26f, 11L);
        drawHandLine(canvas, left + w * 0.04f, right - w * 0.02f, cy + h * 0.36f, 29L);
    }

    /** 一条带轻微起伏的手绘横线。 */
    private void drawHandLine(Canvas canvas, float left, float right, float y, long seed) {
        final int segments = 6;
        Random random = new Random(score * 31L + seed);
        float wobble = getHeight() * 0.018f;
        linePath.reset();
        linePath.moveTo(left, y);
        for (int i = 1; i <= segments; i++) {
            float x = left + (right - left) * i / segments;
            float dy = (random.nextFloat() - 0.5f) * 2f * wobble;
            linePath.lineTo(x, y + dy);
        }
        canvas.drawPath(linePath, inkPaint);
    }
}
