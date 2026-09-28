package com.easyword.learn.utils;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 把练写框里的手写内容导出成 PNG，存到手机相册的「轻松识字」相册里。
 *
 * <p>Android 10 以上走 MediaStore（不需要权限）；Android 9 及以下需要
 * 存储权限，这里会就地申请一次，让用户点第二次完成保存。</p>
 */
public final class WritingExporter {

    private static final String ALBUM = "轻松识字";
    private static final int REQ_STORAGE = 1001;

    private WritingExporter() {
    }

    /** 把手写区域存成图片。 */
    public static void save(Activity activity, View drawingView, String word) {
        if (activity == null || drawingView == null
                || drawingView.getWidth() <= 0 || drawingView.getHeight() <= 0) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            Toast.makeText(activity, "请允许存储权限，再点一次保存", Toast.LENGTH_LONG).show();
            return;
        }

        Bitmap bitmap = render(drawingView);
        String name = fileName(word);
        try {
            write(activity, bitmap, name);
            Toast.makeText(activity, "已存到相册：" + name, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "保存失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
        } finally {
            bitmap.recycle();
        }
    }

    /** 练写框连同笔迹一起画到黑底上（黄色笔迹看得最清楚）。 */
    private static Bitmap render(View view) {
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);
        view.draw(canvas);
        return bitmap;
    }

    private static void write(Context context, Bitmap bitmap, String name) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + ALBUM);
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("相册不可用");
            }
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new IOException("写入失败");
                }
            }
            return;
        }
        File dir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                ALBUM);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建相册目录");
        }
        File file = new File(dir, name);
        try (OutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("写入失败");
            }
        }
        MediaScannerConnection.scanFile(context,
                new String[]{file.getAbsolutePath()}, new String[]{"image/png"}, null);
    }

    /** 文件名带上练的那个字，方便日后翻看。 */
    private static String fileName(String word) {
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA)
                .format(new Date());
        String ch = (word == null || word.trim().isEmpty()) ? "" : word.trim() + "_";
        return "轻松识字_" + ch + stamp + ".png";
    }
}
