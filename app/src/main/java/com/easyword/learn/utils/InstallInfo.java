package com.easyword.learn.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 记录 App 第一次打开的时间（安装日期），热力图的半年区间就从这个日期算起。
 */
public final class InstallInfo {

    private static final String FILE = "easyword_app";
    private static final String KEY_INSTALL = "install_time";

    private InstallInfo() {
    }

    /** 首次调用时把当前时间记为安装时间。 */
    public static void ensureRecorded(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        if (prefs.getLong(KEY_INSTALL, 0L) == 0L) {
            prefs.edit().putLong(KEY_INSTALL, System.currentTimeMillis()).apply();
        }
    }

    /** 安装时间；万一没记录（老版本升级上来），就按现在算。 */
    public static long installTime(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        long time = prefs.getLong(KEY_INSTALL, 0L);
        if (time == 0L) {
            time = System.currentTimeMillis();
            prefs.edit().putLong(KEY_INSTALL, time).apply();
        }
        return time;
    }
}
