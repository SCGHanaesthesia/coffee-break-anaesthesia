# Coffee Break · Anaesthesia

A weekly, coffee-break-length digest of anaesthesia research, safety and supply news, delivered to an Android app.

- **`issues/`** has one JSON file per weekly issue.
- **`feed.json`** holds the newest 12 issues. The app downloads it from
  `https://raw.githubusercontent.com/SCGHanaesthesia/coffee-break-anaesthesia/main/feed.json`.
- **`app/coffee-break.apk`** is the Android app. Install it by opening
  [this link](https://github.com/SCGHanaesthesia/coffee-break-anaesthesia/raw/main/app/coffee-break.apk) on the phone.
- **`docs/EDITORIAL.md`** is the guide the scheduled weekly run follows to research and write each issue.
- **`tools/build_feed.py`** validates the issues and rebuilds `feed.json`.

## How it works

1. Every Monday morning (Perth time), a scheduled Claude task researches the previous week, writes
   `issues/<date>.json`, runs `tools/build_feed.py`, and pushes to `main`.
2. The app checks `feed.json` every few hours in the background. When a new issue appears, it saves it on the phone and
   posts a notification. Old issues stay on the phone, so the archive and search work offline.
3. If no new issue has arrived after 9 days, the app sends a low-priority "hasn't arrived" notification.

## Rebuilding the app

The app is plain Java plus a WebView UI (`app/assets/index.html`), built without Gradle:

```
ANDROID_JAR=/path/to/android-34.jar KEYSTORE=/path/to/coffee-break.keystore KS_PASS=... ./app/build.sh
```

This needs `aapt`, `dalvik-exchange`, `zipalign`, `apksigner` (Ubuntu packages) and a JDK. The signing keystore isn't in
this repo. Android only installs updates signed with the same key, so an update built with a new key means uninstalling
the old app first.

Summaries are machine-written from abstracts and public pages. Check the paper before changing practice.
