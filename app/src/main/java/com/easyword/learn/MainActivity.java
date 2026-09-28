package com.easyword.learn;

import android.os.Build;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.easyword.learn.databinding.ActivityMainBinding;
import com.easyword.learn.ui.HomeFragment;
import com.easyword.learn.ui.ProfileFragment;
import com.easyword.learn.ui.QuizFragment;
import com.easyword.learn.ui.ReadingFragment;
import com.easyword.learn.viewmodel.WordViewModel;
import com.easyword.learn.utils.InstallInfo;

/**
 * 主页：底部四 Tab（识字 / 测验 / 阅读 / 我的）切换，全屏沉浸式。
 */
public class MainActivity extends AppCompatActivity {

    private static final String STATE_TAB = "state_selected_tab";
    /** 从字表点某个字进来时，带到首页去练写。 */
    public static final String EXTRA_FOCUS_CHAR = "focus_char";

    private ActivityMainBinding binding;
    private Fragment current;

    /** 带着一个字打开首页（用于"点字回首页练写"）。 */
    public static Intent intentForChar(Context context, String ch) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra(EXTRA_FOCUS_CHAR, ch);
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 记下第一次打开的时间：热力图的半年区间从这里开始算
        InstallInfo.ensureRecorded(this);

        // 字库初始化放在 Activity 里：不管先从哪个 Tab 进来，数据都已就绪
        new ViewModelProvider(this).get(WordViewModel.class).init();

        binding.bottomNav.setOnItemSelectedListener(item -> {
            show(fragmentForTab(item.getItemId()));
            return true;
        });

        // 默认选中“识字”；进程重建后回到用户上次停留的 Tab
        int startTab = savedInstanceState == null
                ? R.id.tabLearn
                : savedInstanceState.getInt(STATE_TAB, R.id.tabLearn);
        binding.bottomNav.setSelectedItemId(startTab);

        // 兜底：万一没有任何 Fragment 被加上（例如系统恢复状态异常），这里补一个
        if (getSupportFragmentManager().findFragmentById(R.id.fragmentContainer) == null) {
            show(fragmentForTab(startTab));
        }

        focusCharIfNeeded(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        focusCharIfNeeded(intent);
    }

    /** 带着某个字进来时：切到"识字"页并定位到那个字。 */
    private void focusCharIfNeeded(Intent intent) {
        if (intent == null) {
            return;
        }
        String ch = intent.getStringExtra(EXTRA_FOCUS_CHAR);
        if (ch == null || ch.isEmpty()) {
            return;
        }
        intent.removeExtra(EXTRA_FOCUS_CHAR);   // 只生效一次
        binding.bottomNav.setSelectedItemId(R.id.tabLearn);
        Fragment home = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(HomeFragment.ARG_FOCUS_CHAR, ch);
        home.setArguments(args);
        current = null;      // 强制换成带参数的新实例
        show(home);
    }

    private Fragment fragmentForTab(int itemId) {
        if (itemId == R.id.tabQuiz) {
            return new QuizFragment();
        }
        if (itemId == R.id.tabRead) {
            return new ReadingFragment();
        }
        if (itemId == R.id.tabMe) {
            return new ProfileFragment();
        }
        return new HomeFragment();
    }

    private void show(Fragment fragment) {
        if (current != null && current.getClass() == fragment.getClass()) {
            return;
        }
        current = fragment;
        // 用 commitNow：点击后立刻完成替换，中间不会出现空屏的一帧
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commitNow();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, binding.bottomNav.getSelectedItemId());
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
    }

    /** 隐藏状态栏与导航栏（API 30+ 用 WindowInsetsController，兼容低版本）。 */
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
