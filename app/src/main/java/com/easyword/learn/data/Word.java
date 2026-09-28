package com.easyword.learn.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 汉字实体的 Room 表结构。
 *
 * <p>对应数据库表 {@code words}，记录一个汉字的字形、拼音、分类
 * 以及学习状态（是否学习过、是否收藏）。</p>
 */
@Entity(tableName = "words")
public class Word {

    /** 自增主键。 */
    @PrimaryKey(autoGenerate = true)
    private int id;

    /** 汉字本身。 */
    @ColumnInfo(name = "word")
    private String word;

    /** 拼音（含声调，如 jiā）。 */
    @ColumnInfo(name = "pinyin")
    private String pinyin;

    /** 分类，如：家庭、自然、颜色、方位等。 */
    @ColumnInfo(name = "category")
    private String category;

    /** 顺口溜（记忆口诀）。 */
    @ColumnInfo(name = "rhyme")
    private String rhyme;

    /** 组词，多个以空格分隔。 */
    @ColumnInfo(name = "words")
    private String words;

    /** 使用场景例句。 */
    @ColumnInfo(name = "usage")
    private String usage;

    /** 是否学习过。 */
    @ColumnInfo(name = "is_learned")
    private boolean isLearned;

    /** 是否已收藏。 */
    @ColumnInfo(name = "is_favorite")
    private boolean isFavorite;

    /** 批次（第几组，1~10，每批 100 字）。 */
    @ColumnInfo(name = "batch", defaultValue = "0")
    private int batch;

    /** 推荐权重：测验加减分影响首页推荐顺序。 */
    @ColumnInfo(name = "weight", defaultValue = "0")
    private int weight;

    /** 学习次数。 */
    @ColumnInfo(name = "learnCount", defaultValue = "0")
    private int learnCount;

    /** 单字累计停留时长（毫秒）。 */
    @ColumnInfo(name = "studyMillis", defaultValue = "0")
    private long studyMillis;

    /** 上次学习时间戳（毫秒）。 */
    @ColumnInfo(name = "lastStudyAt", defaultValue = "0")
    private long lastStudyAt;

    /** 记忆复习阶段（间隔递增）。 */
    @ColumnInfo(name = "reviewStage", defaultValue = "0")
    private int reviewStage;

    /** 是否已识字（进入识字表）。 */
    @ColumnInfo(name = "is_known", defaultValue = "0")
    private boolean isKnown;

    public Word() {
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getPinyin() {
        return pinyin;
    }

    public void setPinyin(String pinyin) {
        this.pinyin = pinyin;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isLearned() {
        return isLearned;
    }

    public void setLearned(boolean learned) {
        isLearned = learned;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public String getRhyme() {
        return rhyme;
    }

    public void setRhyme(String rhyme) {
        this.rhyme = rhyme;
    }

    public String getWords() {
        return words;
    }

    public void setWords(String words) {
        this.words = words;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }

    public int getBatch() {
        return batch;
    }

    public void setBatch(int batch) {
        this.batch = batch;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public int getLearnCount() {
        return learnCount;
    }

    public void setLearnCount(int learnCount) {
        this.learnCount = learnCount;
    }

    public long getStudyMillis() {
        return studyMillis;
    }

    public void setStudyMillis(long studyMillis) {
        this.studyMillis = studyMillis;
    }

    public long getLastStudyAt() {
        return lastStudyAt;
    }

    public void setLastStudyAt(long lastStudyAt) {
        this.lastStudyAt = lastStudyAt;
    }

    public int getReviewStage() {
        return reviewStage;
    }

    public void setReviewStage(int reviewStage) {
        this.reviewStage = reviewStage;
    }

    public boolean isKnown() {
        return isKnown;
    }

    public void setKnown(boolean known) {
        isKnown = known;
    }
}