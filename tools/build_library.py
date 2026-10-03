#!/usr/bin/env python3
"""重建 assets/library/words.json 里的「组词 / 记字 / 场景」三项内容。

开放数据来源：
  * CC-CEDICT        判断哪些组合是真实存在的词（CC BY-SA 4.0）
  * jieba 词典       词频 + 词性（用来排除人名 / 地名 / 术语，MIT）
  * Tatoeba 中文句子  词频排序 + 场景例句（CC BY 2.0 FR）
  * makemeahanzi     字形拆解、部首、笔画数（https://github.com/skishore/makemeahanzi）

用法:
    python3 tools/build_library.py --data /tmp/ewdata

只重写 w / r / u 三个字段，汉字顺序、批次、拼音保持原样，
所以 App 里已学进度、收藏、批次解锁都不会变（字库回填按字覆盖内容字段）。
"""

import argparse
import bz2
import collections
import gzip
import json
import os
import re

CJK = re.compile(r"[\u4e00-\u9fff]")

IDS_OPS = {
    "⿰": "左右结构",
    "⿱": "上下结构",
    "⿲": "左中右结构",
    "⿳": "上中下结构",
    "⿴": "全包围结构",
    "⿵": "上三包围结构",
    "⿶": "下三包围结构",
    "⿷": "左三包围结构",
    "⿸": "左上包围结构",
    "⿹": "右上包围结构",
    "⿺": "左下包围结构",
    "⿻": "交叉结构",
}

# 拆字数据只拆到笔画级的独体字：它们的部首不是自己（如 不/五/年），
# 但按拆字结果说成“上下结构/全包围”会误导，这里单独点名。
SOLO_CHARS = set(
    "不上年也之头世已与才书乐万业及五巴久予由州乌甲丁农专乡丝击承"
)

# 例句里出现这些音译人名就跳过
NAME_BLACKLIST = (
    "汤姆", "玛丽", "约翰", "杰克", "大卫", "迈克", "彼得", "保罗", "比尔",
    "苏珊", "丽莎", "安娜", "罗伯特", "威廉", "查理", "珍妮", "凯特", "尼克",
    "爱丽丝", "哈利", "乔治", "弗兰克", "马克", "露西", "琳达", "詹姆斯",
    "萨米", "莱拉", "齐里", "丽玛", "米勒", "艾马提", "阿什兰", "索非亚",
    "奥克兰", "卡萨", "萨拉", "劳拉", "马里", "亚历", "尼古拉", "伊万",
    "汉斯", "彼得", "汤姆", "露西", "艾米", "丽莎",
)

# CC-CEDICT 释义里出现这些词，多半是专名 / 古语 / 生僻用法，不适合教初学者
DEF_BAD = (
    "surname ", "abbr.", "old variant", "variant of", "see also", "Japanese ",
    "place name", "county in", "city in", "province in", "capital of",
    "district of", "town in", "village in", "river in", "mountain in",
    "archaic", "literary", "vulgar", "slang", "Buddhist", "dialect",
    "curse", "swear", "profanity", "insult", "derogatory", "obscene",
    "(name)", "person name", "given name", "place name", "county-level",
)

# 骂人话 / 脏话，绝不进教材
WORD_BLACKLIST = {
    "妈的", "你妈", "他妈", "他妈的", "娘的", "妈的巴子", "滚蛋", "混蛋",
    "王八", "王八蛋", "该死", "去死", "死鬼", "傻瓜", "笨蛋", "屁话",
    "放屁", "狗屁", "鸟人", "贱人", "婊子", "妓女", "强奸", "淫", "屎",
    "尿", "屁股", "乳房", "操你", "傻逼", "装逼", "牛逼", "卧槽", "我操",
    "酒鬼", "烟鬼", "色鬼", "懒鬼", "赌鬼", "毒品", "贩毒", "吸毒",
    "特么", "特么的", "伟哥", "阴茎", "阴道", "鸡巴", "奶子", "骚货",
    "狗日", "杂种", "上吊", "吊死", "勒死", "该死", "妈的", "卖国",
    "嫖", "娼", "强奸", "自杀",
}

# 词性白名单：名词/动词/形容词/副词/数量词/代词/时间词/方位词/习用语/简称 等
OK_POS = {
    "n", "v", "a", "d", "m", "q", "r", "t", "f", "s", "i", "l", "j", "b",
    "z", "ad", "an", "vn", "vd", "ag", "ng", "vg", "tg", "nz", "ns",
}
# 人名、机构名、外来词、标点等一律不要
BAD_POS = {"nr", "nrt", "nt", "nw", "eng", "x", "w", "p", "c", "u", "y", "o", "e", "zg"}
# 地名 / 专名要足够常见才收（中国、北京、太阳 可以；万宁、密斯 不行）
MIN_FREQ = {"ns": 5000, "nz": 500}
DEFAULT_MIN_FREQ = 20

# 语料里实在找不到合适例句的几个字，手工补一句（保持和例句同样的口语化短句风格）
MANUAL_SENTENCES = {
    "么": "你在做什么呢？",
    "份": "我买了一份报纸。",
    "座": "这个座位有人吗？",
    "届": "这次比赛是第八届。",
    "宁": "这里很安静，也很宁静。",
    "凤": "凤凰是一种神鸟。",
    "昌": "这家店生意很昌盛。",
}

# 语料里这几句偏血腥或生硬，无条件替换成更适合给老人读的句子
MANUAL_SENTENCE_REPLACE = {
    "把": "请把门关上。",
    "无": "无论下雨还是晴天，我都去。",
    "杀": "杀虫剂可以杀死蚊子。",
    "虎": "老虎的力气很大。",
    "怀": "她怀里抱着一个孩子。",
    "枪": "水枪是小孩的玩具。",
    "川": "山上的冰川慢慢融化了。",
    "莱": "传说蓬莱是神仙住的地方。",
    "玛": "这块玛瑙很漂亮。",
}

# 这些字多用于人名地名，自动挑词会挑到「卡巴莱 / 德莱塞 / 二哈」之类，
# 这里手工指定更常见的组词（都是真实、常见的词）。
MANUAL_WORDS = {
    "她": ["她们"],
    "吗": ["是吗", "好吗", "行吗"],
    "呢": ["呢子", "毛呢"],
    "尔": ["偶尔", "尔后"],
    "罗": ["罗列", "罗盘", "张罗"],
    "陈": ["陈列", "陈旧", "陈述"],
    "阿": ["阿姨", "阿拉伯"],
    "苏": ["苏醒", "苏州", "苏打"],
    "兰": ["兰花", "兰草", "木兰"],
    "艾": ["艾草", "艾叶", "艾灸"],
    "洛": ["洛阳", "洛河"],
    "莱": ["蓬莱", "莱茵河"],
    "尼": ["尼龙", "尼姑"],
    "哈": ["哈哈", "哈欠", "哈密瓜"],
    "吉": ["吉利", "吉祥", "吉日"],
    "泰": ["泰山", "泰国", "安泰"],
    "伯": ["伯伯", "大伯", "伯父"],
    "欧": ["欧洲", "欧元", "北欧"],
    "玉": ["玉米", "玉石", "玉器"],
    "京": ["北京", "京剧", "京城"],
    "康": ["健康", "康复", "小康"],
    "恩": ["恩人", "恩情", "感恩"],
    "勒": ["勒紧", "勒令"],
    "狗": ["小狗", "热狗", "狗窝"],
    "李": ["李子", "行李", "桃李"],
    "派": ["派对", "派出", "学派"],
    "龙": ["龙头", "恐龙", "龙舟"],
    "亚": ["亚洲", "亚军", "亚麻"],
    "阳": ["太阳", "阳光", "阳台"],
    "花": ["花园", "花朵", "开花"],
    "门": ["门口", "大门", "出门"],
    "大": ["大家", "大学", "大人"],
    "心": ["开心", "小心", "心里"],
    "杀": ["杀菌", "杀死", "杀虫"],
    "死": ["死亡", "生死"],
}


def load_library(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def load_cedict(path):
    """返回 (简体词 -> 释义, 只作为繁体使用的字集合)。"""
    entries = {}
    trad_only = set()
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as fh:
        for line in fh:
            if not line or line.startswith("#"):
                continue
            try:
                trad, rest = line.split(" ", 1)
                simp = rest.split(" ", 1)[0]
            except ValueError:
                continue
            defs = "/".join(re.findall(r"/([^/]*)/", line))
            entries[simp] = defs
            if trad != simp:
                trad_only |= set(trad) - set(simp)
    return entries, trad_only


def load_sentences(path, trad_only):
    """Tatoeba 中文句子里挑出适合教学的长短与写法。"""
    out = []
    opener = bz2.open if path.endswith(".bz2") else open
    with opener(path, "rt", encoding="utf-8") as fh:
        for line in fh:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 3:
                continue
            text = parts[2].strip()
            if not 6 <= len(text) <= 22:
                continue
            if re.search(r"[A-Za-z0-9０-９]", text):
                continue
            if any(c.isspace() for c in text):
                continue
            if any(c in trad_only for c in text):
                continue
            if any(name in text for name in NAME_BLACKLIST):
                continue
            if sum(1 for c in text if CJK.match(c)) < 5:
                continue
            out.append(text)
    return out


def build_word_index(entries):
    """把候选词按“含哪个字”建索引，避免每个字都遍历全部词条。"""
    index = collections.defaultdict(list)
    for word, defs in entries.items():
        if not 2 <= len(word) <= 3:
            continue
        if not all(CJK.match(c) for c in word):
            continue
        if any(bad in defs for bad in DEF_BAD):
            continue
        if word in WORD_BLACKLIST or any(bad in word for bad in WORD_BLACKLIST):
            continue
        if re.search(r"\b[A-Z][a-zA-Z]{2,}", defs) and not re.search(
                r"\b(Chinese|China|English|Japan|Japanese|Mandarin|Cantonese|"
                r"America|American|Internet)\b", defs):
            # 释义里出现英文专名（Gejiu / Mike / Hitler…）说明是人名地名
            continue
        for ch in set(word):
            index[ch].append(word)
    return index


def load_frequencies(path):
    """jieba 词典：词 -> (词频, 词性)。"""
    table = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            parts = line.split()
            if len(parts) < 2:
                continue
            word, freq = parts[0], parts[1]
            pos = parts[2] if len(parts) > 2 else "n"
            try:
                table[word] = (int(freq), pos)
            except ValueError:
                continue
    return table


def acceptable(word, table):
    """词性 / 词频是否适合做初学者的组词。"""
    freq, pos = table.get(word, (0, ""))
    if pos in BAD_POS:
        return 0
    if pos not in OK_POS:
        return 0
    return freq if freq >= MIN_FREQ.get(pos, DEFAULT_MIN_FREQ) else 0


def build_sentence_index(sentences):
    index = collections.defaultdict(list)
    for text in sentences:
        for ch in set(CJK.findall(text)):
            index[ch].append(text)
    return index


SAFE_CAPS = re.compile(
    r"\b(Chinese|China|English|Japan|Japanese|Mandarin|Cantonese|"
    r"America|American|Internet)\b")


def name_like_chars(entries):
    """找出主要在音译人名地名里出现的字（洛/莱/玛/莎/姆…）。"""
    total, proper = collections.Counter(), collections.Counter()
    for word, defs in entries.items():
        if not 2 <= len(word) <= 4:
            continue
        is_proper = bool(re.search(r"\b[A-Z][a-zA-Z]{2,}", defs)) and not SAFE_CAPS.search(defs)
        for ch in set(word):
            total[ch] += 1
            if is_proper:
                proper[ch] += 1
    return {ch for ch in total
            if total[ch] >= 3 and proper[ch] / total[ch] >= 0.5}


def simple_usage(sentences):
    """口语语料里的出现次数：用来把“生活里常说的词”排在书面语前面。"""
    counts = collections.Counter()
    for text in sentences:
        for n in (2, 3):
            for i in range(len(text) - n + 1):
                counts[text[i:i + n]] += 1
    return counts


def pick_words(ch, candidates, charset, freq, simple, limit=3):
    """词频高的优先；频率太低的多半是地名 / 化学名 / 人名，直接不要。"""
    import math

    scored, relaxed = [], []
    for word in candidates.get(ch, ()):  # noqa: B905
        outside = sum(1 for c in word if c != ch and c not in charset)
        repeated = 1 if len(set(word)) == 1 else 0
        base = -outside * 8 - repeated * 10 - (len(word) - 2) * 6
        relaxed.append((base, word))
        f = acceptable(word, freq)
        if f:
            # 通用词频 + 口语语料出现次数：让「开门 / 火车 / 大家」这类
            # 生活里天天用的词排在「机关 / 行政 / 重大」这类书面词前面
            score = math.log10(f + 1) * 10 + math.log10(simple.get(word, 0) + 1) * 9 + base
            scored.append((score, f, word))
    scored.sort(key=lambda t: (-t[0], -t[1], t[2]))
    picked = []
    # 门槛从高到低放宽：先要常见词，凑不够再收冷僻一点的，保证每个字都有词可学
    for factor in (10, 3, 1):
        for _, f, word in scored:
            if f < DEFAULT_MIN_FREQ * factor or word in picked:
                continue
            if any(word in p or p in word for p in picked):
                continue
            picked.append(word)
            if len(picked) == limit:
                return picked
    for _, _, word in scored:
        if word not in picked:
            picked.append(word)
        if len(picked) == limit:
            break
    if len(picked) < limit:  # 常见词凑不够（虚词、多用于人名地名的字）用真实词兜底
        relaxed.sort(key=lambda t: (-t[0], t[1]))
        for _, word in relaxed:
            if len(picked) == limit:
                break
            if word not in picked:
                picked.append(word)
    return picked


def pick_sentence(ch, words, charset, rare, name_like, sent_index, used):
    """短、常见、尽量只用到识字表里的字，最好还能带上刚组的词。

    used 里记录已经用过的句子，尽量避免同一个句子出现在多个字下面。
    """
    best, best_score = None, None
    for text in sent_index.get(ch, ()):
        cjk = [c for c in text if CJK.match(c)]
        unknown = sum(1 for c in cjk if c not in charset)
        if unknown > 2:
            continue
        # 生僻字越多，句子越可能是在讲外国人名地名（"萨米和莱拉想去商场"）
        rare_chars = sum(1 for c in cjk if c in rare)
        names = sum(1 for c in cjk if c in name_like and c != ch)
        score = -unknown * 150
        score += 150 if unknown == 0 else 0
        score += 200 if any(w in text for w in words) else 0
        score -= rare_chars * 40
        score -= names * 120
        score -= abs(len(text) - 11) * 4
        score += 10 if text.endswith(("。", "！", "？")) else 0
        score -= 1000 if text in used else 0
        if best_score is None or score > best_score:
            best_score, best = score, text
    return best


def render_structure(ch, hanzi):
    """用拆字数据写一句“记字”提示；拆不开就退回部首 + 笔画。"""
    info = hanzi.get(ch) or {}
    radical = info.get("radical") or ch
    strokes = len(info.get("matches") or []) or None
    deco = info.get("decomposition") or ""

    def is_char(token):
        # 允许「夕＋寸」这种嵌套部件写法
        return bool(token) and all(CJK.match(c) or c == "＋" for c in token)

    if radical == ch or ch in SOLO_CHARS:
        # 独体字：拆字数据在笔画级打转，说“部首就是它自己”没有信息量
        return f"独体字：共 {strokes} 画，照着笔顺写一写" if strokes else ""

    if len(deco) >= 3 and deco[0] in IDS_OPS:
        rest, pieces, i = deco[1:], [], 0
        while i < len(rest):
            if rest[i] in IDS_OPS and i + 2 < len(rest):
                pieces.append(rest[i + 1] + "＋" + rest[i + 2])
                i += 3
            else:
                pieces.append(rest[i])
                i += 1
        if len(pieces) >= 2 and all(is_char(p) for p in pieces):
            shape = IDS_OPS[deco[0]]
            if len(pieces) == 2:
                a, b = pieces
                if deco[0] == "⿰":
                    return f"{shape}：左边是「{a}」，右边是「{b}」"
                if deco[0] == "⿱":
                    return f"{shape}：上面是「{a}」，下面是「{b}」"
                return f"{shape}：由「{a}」和「{b}」组成"
            if len(pieces) == 3:
                return f"{shape}：" + "、".join(f"「{p}」" for p in pieces)
        # 部件里有生僻部件（拆字数据写成「？」）时至少把结构说清楚，
        # 比“部首是「一」，一共 4 画”更有用
        return IDS_OPS[deco[0]] + (f"，一共 {strokes} 画" if strokes else "")
    return f"部首是「{radical}」，一共 {strokes} 画" if strokes else ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="/tmp/ewdata")
    ap.add_argument("--words", default="app/src/main/assets/library/words.json")
    ap.add_argument("--report", default="/tmp/ewdata/report.tsv")
    ap.add_argument("--jieba", default=None, help="jieba dict.txt 路径")
    args = ap.parse_args()

    library = load_library(args.words)
    chars = [w["c"] for w in library["words"]]
    charset = set(chars)
    # 最后三批（约 250 字）多是低频字，例句里出现得多半是外国人名地名
    rare = {w["c"] for w in library["words"] if w.get("g", 1) >= 8}

    entries, trad_only = load_cedict(os.path.join(args.data, "cc-cedict.gz"))
    sentences = load_sentences(os.path.join(args.data, "cmn.bz2"), trad_only)
    word_index = build_word_index(entries)
    sent_index = build_sentence_index(sentences)
    jieba_path = args.jieba or os.path.join(args.data, "jieba_dict.txt")
    freq = load_frequencies(jieba_path)
    simple = simple_usage(sentences)
    name_like = name_like_chars(entries)
    hanzi = {}
    with open(os.path.join(args.data, "dictionary.txt"), encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                obj = json.loads(line)
                hanzi[obj["character"]] = obj

    stats = collections.Counter()
    rows = []
    used_sentences = set()
    for item in library["words"]:
        ch = item["c"]
        words = MANUAL_WORDS.get(ch) or pick_words(ch, word_index, charset, freq, simple)
        sentence = MANUAL_SENTENCE_REPLACE.get(ch)
        if not sentence:
            sentence = pick_sentence(ch, words, charset, rare, name_like,
                                     sent_index, used_sentences)
        if not sentence:
            sentence = MANUAL_SENTENCES.get(ch)
        if sentence:
            used_sentences.add(sentence)
        hint = render_structure(ch, hanzi)
        stats["total"] += 1
        stats["words"] += len(words) >= 3
        stats["sentence"] += bool(sentence)
        stats["hint"] += bool(hint)
        item["w"] = words
        item["r"] = hint or item.get("r", "")
        item["u"] = sentence or ""
        # 拼音原来个别首字母大写（如 世 -> Shi），顺手修掉
        if item.get("py"):
            item["py"] = item["py"][0].lower() + item["py"][1:]
        rows.append((ch, " · ".join(words), hint, sentence or ""))

    with open(args.words, "w", encoding="utf-8") as fh:
        json.dump(library, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    with open(args.report, "w", encoding="utf-8") as fh:
        for row in rows:
            fh.write("\t".join(row) + "\n")

    print(f"语料 {len(sentences)} 句 / CC-CEDICT {len(entries)} 词条")
    print(f"覆盖：组词≥3 {stats['words']}/{stats['total']}，"
          f"例句 {stats['sentence']}/{stats['total']}，"
          f"记字 {stats['hint']}/{stats['total']}")
    print(f"明细 -> {args.report}")


if __name__ == "__main__":
    main()
