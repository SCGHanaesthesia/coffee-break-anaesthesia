#!/usr/bin/env python3
"""Validate every issue in issues/ and rebuild feed.json (newest 12 issues).

Run from the repo root:  python3 tools/build_feed.py
Exits non-zero, printing every problem, if any issue file is invalid.
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FEED_ISSUES = 12
APP = {
    "latestVersion": 1,
    "apkUrl": "https://github.com/SCGHanaesthesia/coffee-break-anaesthesia/raw/main/app/coffee-break.apk",
}
VERDICTS = {"act", "change", "consider", "reassure", "know"}
DATE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def check_issue(path, issue, errors):
    def err(msg):
        errors.append(f"{os.path.basename(path)}: {msg}")

    stem = os.path.splitext(os.path.basename(path))[0]
    if issue.get("id") != stem:
        err(f"id {issue.get('id')!r} must match the file name {stem!r}")
    if not DATE.match(str(issue.get("id", ""))):
        err("id must be YYYY-MM-DD")
    for f in ("number", "title", "published", "window", "tldr", "sections"):
        if f not in issue:
            err(f"missing '{f}'")
    if not isinstance(issue.get("number"), int):
        err("number must be an integer")
    w = issue.get("window") or {}
    if not (DATE.match(str(w.get("from", ""))) and DATE.match(str(w.get("to", "")))):
        err("window.from and window.to must be YYYY-MM-DD")
    if not (1 <= len(issue.get("tldr") or []) <= 4):
        err("tldr needs 1–4 lines")
    seen = set()
    for s in issue.get("sections") or []:
        if not s.get("title"):
            err(f"section {s.get('id')!r} has no title")
        for it in s.get("items") or []:
            iid = it.get("id", "")
            where = f"item {iid!r}"
            if not re.match(r"^[a-z0-9][a-z0-9-]*$", iid):
                err(f"{where}: id must be lowercase-kebab-case")
            if iid in seen:
                err(f"{where}: duplicate id")
            seen.add(iid)
            for f in ("title", "source", "date", "finding", "soWhat", "verdict", "url"):
                if not it.get(f):
                    err(f"{where}: missing '{f}'")
            if it.get("verdict") and it["verdict"] not in VERDICTS:
                err(f"{where}: verdict must be one of {sorted(VERDICTS)}")
            if it.get("date") and not DATE.match(it["date"]):
                err(f"{where}: date must be YYYY-MM-DD")
            if it.get("url") and not it["url"].startswith("https://"):
                err(f"{where}: url must start with https://")
    for q in issue.get("quickHits") or []:
        if not q.get("text"):
            err("quick hit with no text")
        if q.get("url") and not q["url"].startswith("https://"):
            err(f"quick hit url must start with https:// ({q.get('url')})")
    for d in issue.get("dates") or []:
        if not DATE.match(str(d.get("date", ""))) or not d.get("label"):
            err(f"bad date entry {d!r}")


def main():
    errors = []
    issues = []
    for path in sorted(glob.glob(os.path.join(ROOT, "issues", "*.json"))):
        try:
            with open(path, encoding="utf-8") as fh:
                issue = json.load(fh)
        except Exception as e:  # noqa: BLE001
            errors.append(f"{os.path.basename(path)}: not valid JSON ({e})")
            continue
        check_issue(path, issue, errors)
        issues.append(issue)

    numbers = [i.get("number") for i in sorted(issues, key=lambda i: i.get("id", ""))]
    if numbers and numbers != list(range(numbers[0], numbers[0] + len(numbers))):
        errors.append(f"issue numbers should run consecutively by date, got {numbers}")

    if errors:
        print("Feed NOT rebuilt. Problems:")
        for e in errors:
            print("  -", e)
        sys.exit(1)

    issues.sort(key=lambda i: i["id"], reverse=True)
    feed = {
        "schema": 1,
        "updated": issues[0]["published"] if issues else None,
        "app": APP,
        "issues": issues[:FEED_ISSUES],
    }
    with open(os.path.join(ROOT, "feed.json"), "w", encoding="utf-8") as fh:
        json.dump(feed, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    print(f"feed.json rebuilt: {len(feed['issues'])} issue(s), latest #{issues[0]['number']} ({issues[0]['id']})")


if __name__ == "__main__":
    main()
