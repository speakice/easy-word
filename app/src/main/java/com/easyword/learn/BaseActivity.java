package com.easyword.learn;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.easyword.learn.utils.TTSManager;

/**
 * 所有页面的基类，统一处理两件跟"长辈的手机"有关的全局事情。
 *
 * <p><b>1. 字号不跟随系统放大。</b>长辈常把系统的"字体大小"拉到最大，
 * 那会把首页的范字挤到看不见（范字区是按剩余高度分的权重块，下面几行字一变大就没它的位置了）。
 * 本 App 的字号本来就是照着"给长辈看"设计好的，所以这里把 fontScale 固定成 1.0，
 * 所有页面都按设计稿渲染，系统字体调多大都不影响。</p>
 *
 * <p><b>2. 登记当前前台页面。</b>手机缺少朗读引擎 / 中文语音包时，需要弹框引导用户去系统设置里装，
 * 但发起朗读的地方（适配器、工具类）拿不到界面，所以在这里登记一个弱引用给 {@link TTSManager} 用。</p>
 *
 * <p><b>3. 离开页面 / 退出 App 时停掉朗读。</b>点返回键或页面上的"返回"按钮退出时立刻停；
 * 整个 App 退到后台（回桌面、切到别的 App、息屏）时也停，不然人已经走了声音还在念。</p>
 */
public class BaseActivity extends AppCompatActivity {

    /** 本 App 固定使用的字体缩放系数：忽略系统"字体大小"设置。 */
    private static final float FIXED_FONT_SCALE = 1.0f;

    /** 处于"已启动"状态的页面数：减到 0 就说明整个 App 退到后台了。 */
    private static int startedCount;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        lockFontScale(getResources());
    }

    /**
     * 每次取 Resources 都把 fontScale 拉回 1.0。
     *
     * <p>放在这里（而不是只在 attachBaseContext）是因为 AppCompat 内部还会用自己的
     * Configuration 覆盖一次，只有最外层的 Activity 兜底才不会被绕过去。
     * 值已经是 1.0 时直接返回，没有额外开销。</p>
     */
    @Override
    public Resources getResources() {
        Resources resources = super.getResources();
        lockFontScale(resources);
        return resources;
    }

    private static void lockFontScale(Resources resources) {
        if (resources == null) {
            return;
        }
        Configuration current = resources.getConfiguration();
        if (current == null || current.fontScale == FIXED_FONT_SCALE) {
            return;
        }
        Configuration fixed = new Configuration(current);
        fixed.fontScale = FIXED_FONT_SCALE;
        resources.updateConfiguration(fixed, resources.getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TTSManager.getInstance(this).setHost(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        TTSManager tts = TTSManager.getInstance(this);
        tts.setHost(this);
        // 用户可能刚从系统设置里装好语音包回来，这里自动再试一轮
        tts.retryIfUnavailable();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startedCount++;
    }

    @Override
    protected void onStop() {
        startedCount = Math.max(0, startedCount - 1);
        if (startedCount == 0) {
            // 整个 App 退到后台：没人看屏幕了，把还在念的语音停掉
            TTSManager.getInstance(this).stop();
        }
        super.onStop();
    }

    @Override
    protected void onPause() {
        TTSManager tts = TTSManager.getInstance(this);
        // 点返回键或页面上的"返回"按钮退出本页时，立刻停掉朗读，别让声音留在身后
        if (isFinishing()) {
            tts.stop();
        }
        tts.clearHost(this);
        super.onPause();
    }
}
