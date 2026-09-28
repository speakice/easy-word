package com.easyword.learn.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

import java.util.ArrayList;
import java.util.List;

/**
 * 手写画笔覆盖层。
 * <p>
 * 叠加在卡片之上，透明绘制。用户手指移动超过“双击/单击判定阈值”后开始描红书写，
 * 使用贝塞尔曲线（quadTo）让笔画平滑。
 *
 * <p>这一层只负责“写字 + 单击朗读”：没有左右滑手势。手写的横、竖、撇、捺本来就会
 * 快速横向/纵向移动，若在这里识别滑动，写横会被当成“右滑擦除”，一写就被清空。</p>
 */
public class DrawingView extends View {

    /** 卡片交互回调。 */
    public interface OnCardGestureListener {
        /** 单击卡片：朗读。 */
        void onSingleTap();

        /** 双击卡片：收藏。 */
        void onDoubleTap();
    }

    private final Paint strokePaint;
    private final Paint guidePaint;
    private final List<Path> strokes = new ArrayList<>();
    private final Path currentPath = new Path();
    private final Path guidePath = new Path();
    private final int touchSlop;

    private float lastX;
    private float lastY;
    private float downX;
    private float downY;
    private boolean isDrawing = false;
    /** 本次手势是否已经写出了笔画（用来区分“连笔写字”和“双击收藏”）。 */
    private boolean strokeInGesture = false;

    private final GestureDetector gestureDetector;
    private OnCardGestureListener gestureListener;

    /**
     * 构造函数（XML inflate 时调用）。
     *
     * @param context 上下文
     * @param attrs   XML 属性
     */
    public DrawingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        // 笔迹按 dp 走：老人手劲大、眼睛花，太细的线看不清（旧值是写死的 12px，约 4.5dp）
        strokePaint.setStrokeWidth(9f * getResources().getDisplayMetrics().density);
        strokePaint.setColor(0xFFFFEB3B); // 亮黄色，黑色背景下清晰可见
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        // 田字格辅助线：淡淡的虚线，帮助老人把字写在格子中央
        guidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guidePaint.setStyle(Paint.Style.STROKE);
        guidePaint.setStrokeWidth(2f);
        guidePaint.setColor(0x40FFFFFF);
        guidePaint.setPathEffect(new DashPathEffect(new float[]{16f, 14f}, 0f));

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (gestureListener != null) {
                    gestureListener.onSingleTap();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // 上一笔刚写完马上落在附近，是连笔写字，不是双击收藏
                if (strokeInGesture) {
                    return true;
                }
                if (gestureListener != null) {
                    gestureListener.onDoubleTap();
                }
                return true;
            }
        });
    }

    /** 设置卡片交互监听器。 */
    public void setGestureListener(OnCardGestureListener listener) {
        this.gestureListener = listener;
    }

    /** 清除当前卡片的所有笔迹。 */
    public void clear() {
        strokes.clear();
        currentPath.reset();
        isDrawing = false;
        strokeInGesture = false;
        invalidate();
    }

    /**
     * 练写格永远保持正方形（边长取宽高里较小的一边），父容器会把它居中。
     * 这样田字格看上去是“方正”的，而不是一条扁长的横条。
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height);
        if (size <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        setMeasuredDimension(size, size);
    }

    /** 手指一落到练写格上就独占手势，避免被外层 ViewPager 抢去翻页。 */
    private void holdGesture(boolean hold) {
        ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(hold);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawGuides(canvas);
        for (Path p : strokes) {
            canvas.drawPath(p, strokePaint);
        }
        canvas.drawPath(currentPath, strokePaint);
    }

    /** 画田字格：十字虚线（横中线 + 竖中线）。 */
    private void drawGuides(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0f || h <= 0f) {
            return;
        }
        guidePath.reset();
        guidePath.moveTo(0f, h / 2f);
        guidePath.lineTo(w, h / 2f);
        guidePath.moveTo(w / 2f, 0f);
        guidePath.lineTo(w / 2f, h);
        canvas.drawPath(guidePath, guidePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // 在练写格里写字时，禁止外层上下滑动翻页
                holdGesture(true);
                downX = x;
                downY = y;
                lastX = x;
                lastY = y;
                isDrawing = false;
                strokeInGesture = false;
                currentPath.moveTo(x, y);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!isDrawing && Math.hypot(x - downX, y - downY) > touchSlop) {
                    isDrawing = true;
                    strokeInGesture = true;
                }
                if (isDrawing) {
                    // 贝塞尔平滑：用中间点作为 quadTo 终点
                    float midX = (lastX + x) / 2f;
                    float midY = (lastY + y) / 2f;
                    currentPath.quadTo(lastX, lastY, midX, midY);
                    lastX = x;
                    lastY = y;
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDrawing) {
                    strokes.add(new Path(currentPath));
                }
                currentPath.reset();
                isDrawing = false;
                holdGesture(false);
                performClick();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
