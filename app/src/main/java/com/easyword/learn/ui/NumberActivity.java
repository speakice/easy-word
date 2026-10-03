package com.easyword.learn.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.easyword.learn.BaseActivity;
import com.easyword.learn.R;
import com.easyword.learn.databinding.ActivityNumberBinding;

/**
 * 数字的二级菜单：整数 / 小数 / 计算，点进去分别是各自的点读页。
 * 内容太多，摊在一页要滑很久，所以先选大类。
 */
public class NumberActivity extends BaseActivity {

    private ActivityNumberBinding binding;

    public static Intent intent(Context context) {
        return new Intent(context, NumberActivity.class);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityNumberBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.bar.btnBack.setOnClickListener(v -> finish());
        binding.bar.textBarTitle.setText(R.string.number_title);
        addRow("123", getString(R.string.number_integer),
                getString(R.string.number_menu_integer_value), NumberTilesActivity.KEY_INTEGER);
        addRow("1.0", getString(R.string.number_decimal),
                getString(R.string.number_menu_decimal_value), NumberTilesActivity.KEY_DECIMAL);
        addRow("＋", getString(R.string.number_add),
                getString(R.string.number_menu_add_value), NumberTilesActivity.KEY_ADD);
        addRow("×", getString(R.string.number_multiply),
                getString(R.string.number_menu_multiply_value), NumberTilesActivity.KEY_MULTIPLY);
    }

    private void addRow(String icon, String title, String value, String key) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_profile_row,
                binding.rowBox, false);
        ((TextView) row.findViewById(R.id.textRowIcon)).setText(icon);
        ((TextView) row.findViewById(R.id.textRowTitle)).setText(title);
        ((TextView) row.findViewById(R.id.textRowValue)).setText(value);
        row.setOnClickListener(v -> startActivity(NumberTilesActivity.intent(this, key)));
        binding.rowBox.addView(row);
    }
}
