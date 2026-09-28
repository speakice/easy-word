package com.easyword.learn.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 每日学习时长记录（用于“我的”页日历热力图）。按日期累计当天学习毫秒数。
 */
@Entity(tableName = "daily")
public class DailyRecord {

    /** 日期，格式 YYYY-MM-DD。 */
    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "date")
    private String date;

    /** 当天累计学习毫秒数。 */
    @ColumnInfo(name = "millis")
    private long millis;

    public DailyRecord() {
    }

    /** 便捷构造，仅业务代码使用，Room 使用无参构造。 */
    @androidx.room.Ignore
    public DailyRecord(String date, long millis) {
        this.date = date;
        this.millis = millis;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public long getMillis() {
        return millis;
    }

    public void setMillis(long millis) {
        this.millis = millis;
    }
}