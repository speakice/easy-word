<div align="center">

<img src="docs/screenshots/icon.png" width="120" alt="轻松识字">

# 轻松识字 EasyWord

**给家里长辈做的汉字学习 App**：950 个常用汉字，会认、会写、会读，还能考试拿毕业证。

纯离线 · 无广告 · 无账号 · 不联网

</div>

---

## 下载安装

不用自己编译，直接下 APK 装到手机上（Android 7.0 及以上）：

**[⬇️ 下载最新版 APK](https://github.com/speakice/easy-word/releases/latest)**

安装时如果系统提示"未知来源"，在弹窗里允许一次即可。装好打开就能用，全程离线。

<sub>也可以直接下这个版本：[EasyWord-v1.0.apk](https://github.com/speakice/easy-word/releases/download/v1.0/EasyWord-v1.0.apk)（5.6 MB）</sub>

---

## 这是什么

家里老人想认字，市面上的识字 App 大多面向小孩：字小、按钮小、操作复杂、还带各种弹窗。
这个 App 是照着"教我妈认字"这个目标做的，所以：

- **字大、按钮大、全屏沉浸**，没有花哨动画和干扰
- **一次只学一个字**：全屏大字 → 笔顺描红 → 田字格练写 → 词组 / 巧记 / 场景
- **全程有声音**：点哪读哪，朗读时文字会**逐字变黄**跟着读
- **有目标感**：每 100 字一个年级，学完考试、及格毕业，600 字小学毕业、950 字初中毕业

目标识字量 **950 字**，按常用度分成 10 个批次（每批 100 字，最后一批 50 字）。

## 功能

### 1. 识字（首页）

- 全屏滑动刷字，**笔顺描红动画**（950 个字都有真实笔顺数据，按规范笔顺逐笔书写）
- **田字格练写区**：手指直接写，写多粗的笔迹都看得清；写字时不会误翻页；格子右侧有扫把图标一键擦除
- 每个字配 **词组 / 巧记 / 场景** 三行，点行首的小喇叭就能单独听

<img src="docs/screenshots/01-learn.png" width="240"> <img src="docs/screenshots/02-write.png" width="240">

### 2. 考试（测验）

- **听发音选字**：播放读音，从 4 个字里选出听到的那个 —— 不认识拼音也能考
- **10 个年级期末考试**（每 100 字一个年级）+ **小升初** + **中考**，100 分制
- 解锁规则：本批学习进度到 **80%** 才能考该年级；**小升初**要六年级期末考及格；**中考**要初三期末考及格
- 不限次数，随时可以重考
- 结果页是**红笔手写的分数** + 手绘横线，跟真的改卷子一样
- 成绩按日期逐次记录（同一天同一场多次考试合并取最好）

<img src="docs/screenshots/03-quiz-list.png" width="240"> <img src="docs/screenshots/04-test-question.png" width="240"> <img src="docs/screenshots/05-test-result.png" width="240">

### 3. 阅读

- 22 篇短文，**30sp 大字号**，每篇都带重复句，读起来有节奏
- 点「朗读全文」整篇跟读，**读到哪里前面的字变黄**
- 按"和你已识字表的契合度"排序，识字少时也能先跟着读

<img src="docs/screenshots/06-reading.png" width="240">

### 4. 我的

- 顶部显示识字量、学习进度条
- **全年学习热力图**：365 格、白底绿格，一眼看出哪天学了多久
- **我的成绩**：每一场考试单独记录（日期 + 年级 + 分数）
- **毕业证书**：小升初 / 中考成绩出来后可申请毕业，60 分以上颁发毕业证（成绩合格 / 良好 / 优秀），之后考得更好还能更新成绩
- **字表**：全部字表按年级浏览（显示每个年级已识多少字）→ 点进年级看该批全部汉字 → 点字直接回首页练写；也能按已学 / 已识 / 未识 / 已收藏查看，每类都显示字数

<img src="docs/screenshots/07-profile.png" width="240"> <img src="docs/screenshots/08-grades.png" width="240"> <img src="docs/screenshots/09-batch.png" width="240">

<img src="docs/screenshots/10-certificate.png" width="240">

## 学习机制

- **批次解锁**：前一批识字率达到 80% 自动解锁下一批
- **记忆曲线**：每个字有 9 个复习阶段（5 分钟 → 30 分钟 → 12 小时 → 1 天 → … → 15 天），到期的字会自动回到首页
- **考完自动进复习**：考试答错的字会加入"未识表"并提高首页推送权重，答对的降低权重

## 构建

需要 Android Studio（或 JDK 17 + Android SDK 34）。

```bash
git clone https://github.com/speakice/easy-word.git
cd easy-word
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

或直接用 Android Studio 打开工程，点 Run。最低支持 Android 7.0（API 24）。

打正式包（发布用，需要签名）：

```bash
# 1) 准备签名文件（首次）
keytool -genkeypair -v -keystore keystore/easyword.jks -alias easyword \
        -keyalg RSA -keysize 2048 -validity 10000
# 2) 在项目根目录建 keystore.properties（不进版本库）
#    storeFile=keystore/easyword.jks
#    storePassword=…
#    keyAlias=easyword
#    keyPassword=…
# 3) 打包
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

没有 `keystore.properties` 时会自动退回 debug 签名，保证 clone 下来就能构建。
另外：本机若只有 JDK 25（Android Studio 自带的 JBR），AGP 8.7 的 lint 会失败，
已在 `app/build.gradle` 里关掉 release 的 lint 检查；想跑 lint 请用 JDK 17。

## 内容与数据来源

字库内容由开放数据生成（脚本见 [`tools/`](tools/)）：

| 用途 | 数据源 | 许可 |
|---|---|---|
| 判断哪些组合是真实的词 | [CC-CEDICT](https://cc-cedict.org/wiki/) | CC BY-SA 4.0 |
| 词频 / 词性（排常用词、剔除人名地名） | [jieba](https://github.com/fxsjy/jieba) 词典 | MIT |
| 场景例句 | [Tatoeba](https://tatoeba.org/) 中文句子 | CC BY 2.0 FR |
| 字形拆解、部首、笔画 | [makemeahanzi](https://github.com/skishore/makemeahanzi) | 见项目说明 |
| 笔顺描红数据 | [hanzi-writer-data](https://github.com/chanind/hanzi-writer-data) | MIT |

重新生成字库内容 / 补齐笔顺数据：

```bash
python3 tools/build_library.py --data <开放数据目录>   # 词组 / 巧记 / 场景
python3 tools/fetch_strokes.py                          # 笔顺数据
```

## 目录结构

```
app/src/main/java/com/easyword/learn/
├── MainActivity.java          四个 Tab 的壳
├── ui/                        识字 / 考试 / 阅读 / 我的 / 字表 / 证书 …
├── view/                      DrawingView（练写）、StrokeAnimationView（笔顺描红）
├── adapter/                   ViewPager 卡片、字表、考试列表
├── data/                      Room 数据库、字库、考试科目表、成绩记录
├── utils/                     TTS、朗读逐字高亮、字库加载
└── assets/library/            950 字字库、短文、笔顺数据
```

## 授权

**非商业使用免费；商业使用需获得授权。**

- ✅ 个人学习、家庭教育、课堂与公益教学等**非商业**用途：免费使用、修改、分发（须保留版权声明与许可）
- ❌ 任何**商业用途**（销售、内置到付费产品、商业培训、SaaS、广告变现等）：**必须事先取得著作权人书面授权**

详见 [LICENSE](LICENSE)。商业授权请联系仓库 Issue 或 `907203644@qq.com`。

---

<div align="center">
如果这个项目帮到了你家里的长辈，欢迎点个 ⭐
</div>
