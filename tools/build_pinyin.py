#!/usr/bin/env python3
"""生成 assets/library/pinyin.txt —— 汉字 → 拼音的小字典。

用于「用户自己添加的字」（姓名、籍贯等）：这些字不在 950 字库里，没有现成拼音，
这里用 makemeahanzi 的开放数据（dictionary.txt）生成一份常用字拼音表随包发出。

用法:
    python3 tools/build_pinyin.py --data /tmp/ewdata
"""

import argparse
import json
import os


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="/tmp/ewdata")
    ap.add_argument("--out", default="app/src/main/assets/library/pinyin.txt")
    args = ap.parse_args()

    pairs = []
    with open(os.path.join(args.data, "dictionary.txt"), encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            ch = obj.get("character") or ""
            pinyin = obj.get("pinyin") or []
            if len(ch) != 1 or not pinyin:
                continue
            if not ("\u4e00" <= ch <= "\u9fff"):
                continue
            py = pinyin[0].strip()
            if py:
                pairs.append((ch, py))

    pairs.sort(key=lambda p: p[0])
    with open(args.out, "w", encoding="utf-8") as fh:
        for ch, py in pairs:
            fh.write(f"{ch}\t{py}\n")
    print(f"写入 {len(pairs)} 个字的拼音 -> {args.out}")


if __name__ == "__main__":
    main()
