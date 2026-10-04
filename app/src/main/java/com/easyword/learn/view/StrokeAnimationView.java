package com.easyword.learn.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.PathParser;

import com.easyword.learn.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 笔画描红动画视图（真实手写笔顺 + 同字体范字）。
 *
 * <p>读取 {@code assets/strokes/{字}.json}（开源汉字笔顺数据）。背景“范字”用同一笔画数据以
 * 白色填充展示；描红时按规范笔顺逐笔、每笔严格沿其中心线(medians)渐进填充为黄色笔迹——
 * 横从左到右、竖从上到下、撇/捺/点/横折/钩都跟着各自走势拐弯，笔尖带圆点，写完整字无限循环。
 * 描红的字与背景范字来自同一数据源，字形字体完全一致。</p>
 */
public class StrokeAnimationView extends View {

    /** 单笔：形状路径 + 中心线折线（数据坐标）。 */
    private static class Stroke {
        final Path path;
        final List<float[]> pts;
        final float halfW;

        Stroke(Path path, List<float[]> pts, float halfW) {
            this.path = path;
            this.pts = pts;
            this.halfW = halfW;
        }
    }

    private final Paint ghostPaint;   // 白色范字（与描红同字体）
    private final Paint strokePaint;  // 黄色笔迹
    private final Paint penTipPaint;  // 笔头圆点
    private final List<Stroke> strokes = new ArrayList<>();
    private final RectF dataBounds = new RectF();

    private TextView targetText;
    private String word = "";
    private boolean loaded;
    private float scale = 1f;
    private float offsetX;
    private float offsetY;
    private float penRadius = 4f;   // 笔头圆点半径（整字统一，避免随笔画忽大忽小）
    private ValueAnimator animator;
    private int activeStroke = -1;
    private float activeFraction;

    public StrokeAnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);

        ghostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ghostPaint.setStyle(Paint.Style.FILL);
        ghostPaint.setColor(0xFFFFFFFF);

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.FILL);
        strokePaint.setColor(ContextCompat.getColor(context, R.color.stroke_red));

        penTipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        penTipPaint.setStyle(Paint.Style.FILL);
        penTipPaint.setColor(ContextCompat.getColor(context, R.color.stroke_red));

        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    /**
     * 绑定背后的大字容器并加载该字笔顺数据开始描红。
     *
     * @param target 卡片上的 {@code textWord}（仅用于尺寸/位置参考，写好后自动隐藏）
     * @param text   单个汉字
     */
    public void bindWord(TextView target, String text) {
        targetText = target;
        String t = text == null ? "" : text.trim();
        if (t.length() > 1) {
            t = t.substring(0, 1);
        }
        if (word.equals(t) && targetText != null && !strokes.isEmpty()) {
            return;
        }
        word = t;
        stopAnimator();
        animator = null;
        loaded = false;
        invalidate();
        rebuild();
        // 布局稳定后再对齐一次，确保与背后大字的最终位置完全重合
        post(this::rebuild);
    }

    /** 从第一笔重新描（朗读到“字”这一段时调用，与发音同步）。 */
    public void replay() {
        if (!loaded || getWidth() <= 0) {
            return;
        }
        stopAnimator();
        rebuild();
        startAnimator();
    }

    /** 描红是否正在播放。 */
    public boolean isAnimating() {
        return animator != null && animator.isRunning();
    }

    /**
     * 点一下范字的效果：正在描红就停在当前进度（已经描出来的部分留在屏幕上），
     * 已经停了就从第一笔重新描一遍。
     */
    public void toggleAnimation() {
        if (isAnimating()) {
            stopAnimator();
        } else {
            replay();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (word.isEmpty()) {
            return;
        }
        stopAnimator();
        rebuild();
        startAnimator();
    }

    /** 读取笔画数据并构建变换。 */
    private void rebuild() {
        strokes.clear();
        activeStroke = -1;
        activeFraction = 0f;
        loaded = false;
        if (word.isEmpty() || getWidth() <= 0 || getHeight() <= 0) {
            updateTargetVisibility();
            return;
        }
        try {
            loadFromAsset();
        } catch (Exception e) {
            // 缺数据：回退——只显示背后大字的系统字体
            updateTargetVisibility();
            return;
        }
        if (strokes.isEmpty()) {
            updateTargetVisibility();
            return;
        }
        loaded = true;
        updateTargetVisibility();
        computeTransform();
        startAnimator();
    }

    /** 数据加载成功时隐藏 textWord（改由本视图画同字体的白色范字）；失败时恢复显示。 */
    private void updateTargetVisibility() {
        if (targetText != null) {
            targetText.setVisibility(loaded ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /** 解析 assets/strokes/{字}.json 的 strokes 与 medians。 */
    private void loadFromAsset() throws Exception {
        InputStream in = null;
        try {
            in = getResources().getAssets().open("strokes/" + word + ".json");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject json = new JSONObject(sb.toString());
            JSONArray strokeArr = json.getJSONArray("strokes");
            JSONArray medianArr = json.getJSONArray("medians");

            dataBounds.setEmpty();
            for (int i = 0; i < strokeArr.length(); i++) {
                Path path = PathParser.createPathFromPathData(strokeArr.getString(i));
                // 数据源只镜像 Y：加载时把笔画路径镜像回正（X 与屏幕一致）
                Matrix mirror = new Matrix();
                mirror.setScale(1f, -1f);
                path.transform(mirror);

                RectF b = new RectF();
                path.computeBounds(b, true);
                dataBounds.union(b);

                // 中心线折线（同步镜像 Y）
                List<float[]> pts = new ArrayList<>();
                JSONArray mArr = medianArr.getJSONArray(i);
                for (int j = 0; j < mArr.length(); j++) {
                    JSONArray pt = mArr.getJSONArray(j);
                    pts.add(new float[]{(float) pt.getDouble(0), (float) -pt.getDouble(1)});
                }
                // 个别笔只有 1 个中心点：补一条从左到右的水平中心线兜底
                if (pts.size() == 1) {
                    float y = pts.get(0)[1];
                    pts.set(0, new float[]{b.left, y});
                    pts.add(new float[]{b.right, y});
                }
                float halfW = Math.max(2f, Math.min(b.width(), b.height()) * 0.45f);

                strokes.add(new Stroke(path, pts, halfW));
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /** 计算数据坐标 → 卡片坐标的缩放与偏移，使笔画贴合背后大字区域。 */
    private void computeTransform() {
        RectF target = targetBox();
        float s = Math.min(target.width() / dataBounds.width(),
                target.height() / dataBounds.height());
        scale = s;
        offsetX = target.centerX() - dataBounds.centerX() * s;
        offsetY = target.centerY() - dataBounds.centerY() * s;
        // 笔头大小按整字最小边统一计算
        penRadius = Math.max(3f,
                Math.min(dataBounds.width(), dataBounds.height()) * 0.045f);
    }

    /**
     * 在卡片之间切换时控制描红启停：
     * 当前页调用 {@code active=true} 从第一笔重新开始；离屏页 {@code active=false} 暂停。
     */
    public void setActive(boolean active) {
        if (!active) {
            stopAnimator();
            return;
        }
        if (!loaded || getWidth() <= 0) {
            return;
        }
        stopAnimator();
        activeStroke = 0;
        activeFraction = 0f;
        startAnimator();
    }

    /** 背后大字的字形包围盒（数据坐标被映射的目标区域）。 */
    private RectF targetBox() {
        RectF box = new RectF();
        if (targetText != null && targetText.getTextSize() > 0) {
            Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
            glyph.setTypeface(targetText.getTypeface());
            glyph.setTextSize(targetText.getTextSize());
            Rect b = new Rect();
            glyph.getTextBounds(word, 0, 1, b);

            float w = getWidth();
            float h = getHeight();
            float padL = targetText.getPaddingLeft();
            float padR = targetText.getPaddingRight();
            float padT = targetText.getPaddingTop();
            float padB = targetText.getPaddingBottom();
            float availW = w - padL - padR;
            float availH = h - padT - padB;

            Paint.FontMetrics fm = glyph.getFontMetrics();
            float x = padL + (availW - b.width()) / 2f - b.left;
            float baselineY = padT + availH / 2f - (fm.ascent + fm.descent) / 2f;

            Path p = new Path();
            glyph.getTextPath(word, 0, 1, x, baselineY, p);
            p.computeBounds(box, true);
        } else {
            float size = Math.min(getWidth(), getHeight()) * 0.7f;
            box.set((getWidth() - size) / 2f, (getHeight() - size) / 2f,
                    (getWidth() + size) / 2f, (getHeight() + size) / 2f);
        }
        return box;
    }

    /** 慢速无限循环：按笔顺一笔一笔写（越慢长辈越看得清）。 */
    private void startAnimator() {
        if (strokes.isEmpty()) {
            return;
        }
        stopAnimator();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(28000L);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.addUpdateListener(animation -> {
            float progress = animation.getAnimatedFraction();
            float pos = progress * strokes.size();
            int idx = Math.min(strokes.size() - 1, (int) pos);
            activeStroke = idx;
            activeFraction = pos - idx;
            invalidate();
        });
        animator.start();
    }

    private void stopAnimator() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (strokes.isEmpty() || activeStroke < 0) {
            return;
        }
        canvas.save();
        canvas.translate(offsetX, offsetY);
        canvas.scale(scale, scale);

        // 白色范字：用同一笔顺数据画背景字，保证与描红同字体同形状
        for (Stroke s : strokes) {
            canvas.drawPath(s.path, ghostPaint);
        }
        // 已写好的笔画：黄色完整填充
        for (int i = 0; i < activeStroke; i++) {
            canvas.drawPath(strokes.get(i).path, strokePaint);
        }
        // 正在写的笔画：严格沿中心线方向渐进填充（横/竖/撇/捺/折/钩都跟走势）
        Stroke stroke = strokes.get(activeStroke);
        drawStrokeProgress(canvas, stroke, activeFraction);
        drawPenTip(canvas, stroke, activeFraction);

        canvas.restore();
    }

    /** 当前笔画沿中心线折线写到进度 f 处，用“笔刷带宽”剪出已写部分。 */
    private void drawStrokeProgress(Canvas canvas, Stroke s, float f) {
        List<float[]> pts = s.pts;
        float target = pathLength(pts) * Math.max(0f, Math.min(1f, f));
        int frontIdx = 0;
        float frontX = pts.get(0)[0];
        float frontY = pts.get(0)[1];
        float acc = 0f;
        for (int i = 0; i < pts.size() - 1; i++) {
            float dx = pts.get(i + 1)[0] - pts.get(i)[0];
            float dy = pts.get(i + 1)[1] - pts.get(i)[1];
            float segLen = (float) Math.hypot(dx, dy);
            if (acc + segLen >= target) {
                float t = segLen == 0f ? 0f : (target - acc) / segLen;
                frontIdx = i;
                frontX = pts.get(i)[0] + dx * t;
                frontY = pts.get(i)[1] + dy * t;
                break;
            }
            acc += segLen;
        }

        Path clip = buildBand(pts, frontIdx, frontX, frontY, s.halfW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawPath(s.path, strokePaint);
        canvas.restore();
    }

    /** 沿中心线折线构建“笔刷带”剪裁多边形（前端跟随笔画走向）。 */
    private Path buildBand(List<float[]> pts, int frontIdx, float frontX, float frontY, float halfW) {
        Path band = new Path();
        boolean firstPoint = true;
        // 左边缘：从起点到前端（+法向）
        for (int i = 0; i <= frontIdx; i++) {
            float[] p = pts.get(i);
            float[] n = normalAt(pts, i, frontIdx);
            float lx = p[0] + n[0] * halfW;
            float ly = p[1] + n[1] * halfW;
            if (firstPoint) {
                band.moveTo(lx, ly);
                firstPoint = false;
            } else {
                band.lineTo(lx, ly);
            }
        }
        // 前端点
        float[] nf = normalAt(pts, frontIdx, frontIdx);
        band.lineTo(frontX + nf[0] * halfW, frontY + nf[1] * halfW);
        band.lineTo(frontX - nf[0] * halfW, frontY - nf[1] * halfW);
        // 右边缘：从前端回到起点（-法向）
        for (int i = frontIdx; i >= 0; i--) {
            float[] p = pts.get(i);
            float[] n = normalAt(pts, i, frontIdx);
            band.lineTo(p[0] - n[0] * halfW, p[1] - n[1] * halfW);
        }
        band.close();
        return band;
    }

    /** 折线上第 i 点的单位法向（垂直于该局部段方向）。 */
    private float[] normalAt(List<float[]> pts, int i, int frontIdx) {
        float[] p = pts.get(i);
        int next = Math.min(i + 1, pts.size() - 1);
        int prev = Math.max(i - 1, 0);
        float dx = pts.get(next)[0] - p[0];
        float dy = pts.get(next)[1] - p[1];
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) {
            dx = p[0] - pts.get(prev)[0];
            dy = p[1] - pts.get(prev)[1];
            len = (float) Math.hypot(dx, dy);
        }
        if (len < 1e-4f) {
            return new float[]{0f, 1f};
        }
        return new float[]{-dy / len, dx / len};
    }

    private float pathLength(List<float[]> pts) {
        float len = 0f;
        for (int i = 0; i < pts.size() - 1; i++) {
            len += (float) Math.hypot(
                    pts.get(i + 1)[0] - pts.get(i)[0],
                    pts.get(i + 1)[1] - pts.get(i)[1]);
        }
        return len;
    }

    /** 在笔尖（前端）画一个笔头圆点，增强书写感。 */
    private void drawPenTip(Canvas canvas, Stroke s, float f) {
        List<float[]> pts = s.pts;
        float target = pathLength(pts) * Math.max(0f, Math.min(1f, f));
        float acc = 0f;
        for (int i = 0; i < pts.size() - 1; i++) {
            float dx = pts.get(i + 1)[0] - pts.get(i)[0];
            float dy = pts.get(i + 1)[1] - pts.get(i)[1];
            float segLen = (float) Math.hypot(dx, dy);
            if (acc + segLen >= target) {
                float t = segLen == 0f ? 0f : (target - acc) / segLen;
                float tx = pts.get(i)[0] + dx * t;
                float ty = pts.get(i)[1] + dy * t;
                canvas.drawCircle(tx, ty, penRadius, penTipPaint);
                return;
            }
            acc += segLen;
        }
        float[] last = pts.get(pts.size() - 1);
        canvas.drawCircle(last[0], last[1], penRadius, penTipPaint);
    }

    /** 大字区手势接口（单击朗读 / 双击与左滑收藏）。擦除只走“擦除”按钮。 */
    public interface OnCardGestureListener {
        void onSingleTap();

        void onDoubleTap();

        void onSwipeLeft();
    }

    private GestureDetector gestureDetector;
    private OnCardGestureListener gestureListener;

    /** 大字区域手势（单击朗读 / 双击或左滑收藏）。 */
    public void setGestureListener(OnCardGestureListener listener) {
        gestureListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        GestureDetector detector = gestureDetector;
        if (detector == null) {
            detector = new GestureDetector(getContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            if (gestureListener != null) {
                                gestureListener.onSingleTap();
                            }
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            if (gestureListener != null) {
                                gestureListener.onDoubleTap();
                            }
                            return true;
                        }

                        @Override
                        public boolean onFling(MotionEvent e1, MotionEvent e2,
                                               float velocityX, float velocityY) {
                            if (e1 == null || e2 == null || gestureListener == null) {
                                return false;
                            }
                            float dx = e2.getX() - e1.getX();
                            float dy = e2.getY() - e1.getY();
                            float flingSlop = getResources().getDisplayMetrics().density * 24f;
                            // 只保留左滑收藏；右滑不再擦除笔迹（擦除只走“擦除”按钮）
                            if (dx < 0 && Math.abs(dx) > Math.abs(dy) * 2f
                                    && Math.abs(dx) > flingSlop) {
                                gestureListener.onSwipeLeft();
                                return true;
                            }
                            return false;
                        }
                    });
            gestureDetector = detector;
        }
        detector.onTouchEvent(event);
        // 自己接管这一串手势（单击朗读 / 双击或左滑收藏）；没有调用
        // requestDisallowInterceptTouchEvent，所以上下滑动仍会被外层 ViewPager
        // 拦截去翻页，两种操作互不打架。
        return true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimator();
    }
}
