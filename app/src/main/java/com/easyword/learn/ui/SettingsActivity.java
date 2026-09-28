package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.easyword.learn.R;
import com.easyword.learn.databinding.ActivitySettingsBinding;
import com.easyword.learn.utils.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 设置：换头像、改昵称，以及调考试门槛（解锁进度 / 及格 / 良好 / 优秀）。
 * 头像会复制到 App 私有目录，不依赖相册权限。
 */
public class SettingsActivity extends AppCompatActivity {

    private static final int REQ_PICK = 2001;
    private static final String AVATAR_FILE = "avatar.png";

    private ActivitySettingsBinding binding;

    public static Intent intent(Context context) {
        return new Intent(context, SettingsActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bar.btnBack.setOnClickListener(v -> finish());
        binding.bar.textBarTitle.setText(R.string.settings_title);

        // 圆形头像
        binding.imgAvatar.setClipToOutline(true);
        binding.imgAvatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        binding.imgAvatar.setOnClickListener(v -> pickImage());
        binding.rowAvatar.setOnClickListener(v -> pickImage());

        loadCurrent();
        binding.btnSaveSettings.setOnClickListener(v -> save());
    }

    private void loadCurrent() {
        binding.editNickname.setText(Settings.nickname(this));
        binding.editUnlock.setText(String.valueOf(Settings.unlockPercent(this)));
        binding.editPass.setText(String.valueOf(Settings.passScore(this)));
        binding.editGood.setText(String.valueOf(Settings.goodScore(this)));
        binding.editExcellent.setText(String.valueOf(Settings.excellentScore(this)));
        showAvatar();
    }

    private void showAvatar() {
        File file = new File(getFilesDir(), AVATAR_FILE);
        if (file.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                binding.imgAvatar.setImageBitmap(bitmap);
                return;
            }
        }
        binding.imgAvatar.setImageResource(R.drawable.bg_circle_gold);
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        try {
            startActivityForResult(intent, REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, R.string.settings_pick_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (copyAvatar(uri)) {
            showAvatar();
        } else {
            Toast.makeText(this, R.string.settings_pick_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 把选中的图片压到 512px 存进 App 私有目录，之后不依赖相册权限。 */
    private boolean copyAvatar(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return false;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, bounds);

            int sample = 1;
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            while (max / sample > 512) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap bitmap;
            try (InputStream in2 = getContentResolver().openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in2, null, options);
            }
            if (bitmap == null) {
                return false;
            }
            File file = new File(getFilesDir(), AVATAR_FILE);
            try (OutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bitmap.recycle();
            Settings.setAvatarPath(this, file.getAbsolutePath());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void save() {
        Settings.setNickname(this, binding.editNickname.getText().toString());
        Settings.setUnlockPercent(this, parseInt(binding.editUnlock.getText().toString(),
                Settings.DEFAULT_UNLOCK));
        Settings.setPassScore(this, parseInt(binding.editPass.getText().toString(),
                Settings.DEFAULT_PASS));
        Settings.setGoodScore(this, parseInt(binding.editGood.getText().toString(),
                Settings.DEFAULT_GOOD));
        Settings.setExcellentScore(this, parseInt(binding.editExcellent.getText().toString(),
                Settings.DEFAULT_EXCELLENT));
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
