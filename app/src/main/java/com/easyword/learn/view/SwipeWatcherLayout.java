package com.easyword.learn.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * 包在 ViewPager 外面的观察容器：只记「向上滑」这一种手势，不消费事件。
 *
 * <p>竖直手势会被 ViewPager 内部的 RecyclerView 先拦走，页内容器收不到 UP；包在外层就一定能拿到。
 * 事件照常往下传，翻页、点击都不受影响。是否处理由宿主判断（目前只有「学完页」用它回第一张复习）。</p>
 */
public class SwipeWatcherLayout extends FrameLayout {

    /** 上滑回调。 */
    public interface OnSwipeUpListener {
        void onSwipeUp();
    }

    private final int touchSlop;
    private float downX;
    private float downY;

    private OnSwipeUpListener listener;

    public SwipeWatcherLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnSwipeUpListener(OnSwipeUpListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                break;
            case MotionEvent.ACTION_UP:
                float dy = downY - event.getY();
                float dx = Math.abs(event.getX() - downX);
                if (listener != null && dy > touchSlop && dy > dx) {
                    listener.onSwipeUp();
                }
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(event);
    }
}
