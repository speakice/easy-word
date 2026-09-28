#!/usr/bin/env python3
"""补齐 assets/strokes/*.json —— 每个字的笔顺（描红动画）数据。

数据来自 hanzi-writer-data（MIT，字形笔顺来自 makemeahanzi / 开源字体），
格式与 App 里 StrokeAnimationView 读取的完全一致：{"strokes": [...], "medians": [...]}。

用法:
    python3 tools/fetch_strokes.py

已经存在的文件默认跳过，加 --force 可重新下载。
"""

import argparse
import concurrent.futures
import json
import os
import urllib.parse
import urllib.request

CDN = "https://cdn.jsdelivr.net/npm/hanzi-writer-data@2.0.1/{}.json"


def fetch(char):
    url = CDN.format(urllib.parse.quote(char))
    try:
        with urllib.request.urlopen(url, timeout=20) as resp:
            raw = json.loads(resp.read().decode("utf-8"))
    except Exception:
        return char, None
    data = {"strokes": raw.get("strokes", []), "medians": raw.get("medians", [])}
    if not data["strokes"] or not data["medians"]:
        return char, None
    return char, data


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--words", default="app/src/main/assets/library/words.json")
    ap.add_argument("--out", default="app/src/main/assets/strokes")
    ap.add_argument("--force", action="store_true", help="已存在的文件也重新下载")
    ap.add_argument("--workers", type=int, default=8)
    args = ap.parse_args()

    with open(args.words, encoding="utf-8") as fh:
        chars = [w["c"] for w in json.load(fh)["words"]]
    os.makedirs(args.out, exist_ok=True)

    todo = [c for c in chars
            if args.force or not os.path.exists(os.path.join(args.out, c + ".json"))]
    print(f"共 {len(chars)} 字，需要下载 {len(todo)} 个")

    ok, missing = 0, []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        for char, data in pool.map(fetch, todo):
            if data is None:
                missing.append(char)
                continue
            path = os.path.join(args.out, char + ".json")
            with open(path, "w", encoding="utf-8") as fh:
                json.dump(data, fh, ensure_ascii=False, separators=(",", ":"))
            ok += 1

    have = len([c for c in chars if os.path.exists(os.path.join(args.out, c + ".json"))])
    print(f"下载成功 {ok} 个；现在字库覆盖 {have}/{len(chars)}")
    if missing:
        print("没有笔顺数据的字：", "".join(missing))


if __name__ == "__main__":
    main()
