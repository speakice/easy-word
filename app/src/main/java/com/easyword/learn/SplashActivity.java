package com.easyword.learn;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Random;

/**
 * 启动页：亮一下 App 名，配一句给长辈的话，停一小会儿自动进主页。
 * 轻触屏幕可以立刻跳过。
 */
public class SplashActivity extends AppCompatActivity {

    /** 启动页停留时间（毫秒）。 */
    private static final long HOLD_MS = 1800L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean left;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        String[] lines = getResources().getStringArray(R.array.splash_taglines);
        if (lines.length > 0) {
            TextView tagline = findViewById(R.id.textSplashTagline);
            tagline.setText(highlightEnding(lines[new Random().nextInt(lines.length)]));
        }

        findViewById(R.id.splashRoot).setOnClickListener(v -> goHome());
        handler.postDelayed(this::goHome, HOLD_MS);
    }

    private void goHome() {
        if (left) {
            return;
        }
        left = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /**
     * 「从“睁眼瞎”，到睁开眼」这句的重点在后半句：把「睁开眼」加粗提亮，
     * 让它读起来是「翻篇」而不是「揭伤疤」。其余句子原样返回。
     */
    private CharSequence highlightEnding(String line) {
        String key = "睁开眼";
        int at = line.indexOf(key);
        if (at < 0) {
            return line;
        }
        android.text.SpannableString s = new android.text.SpannableString(line);
        int end = at + key.length();
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, end, 0);
        s.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFF59D), at, end, 0);
        return s;
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    /** 隐藏状态栏与导航栏，跟主页保持一致（API 30+ 用 WindowInsetsController）。 */
    private void enterImmersiveMode() {
        Window window = getWindow();
        View decorView = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.setStatusBarColor(android.graphics.Color.BLACK);
            window.setNavigationBarColor(android.graphics.Color.BLACK);
        }
    }
}
