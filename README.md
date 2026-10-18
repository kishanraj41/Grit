# Grit

Native Android prep and training tracker. Local Room database, Health Connect in, orange-on-black UI. Workout loads in **lb**. Scale weight stays **kg**.

Grit is Kishan's app for the Retention-Recomp Protocol v4: MacroFactor-style energy, Fitbit steps through Health Connect, and a Train screen that actually logs the session on the phone.

## What it does

- **Today** — one line of advice, Health Connect log, 7-day weight/sleep/steps.
- **Train** — v4 week (Standard / Compressed), lb steppers, plates (US 45s), warm-ups, drop sets, rest-pause, timed holds, L/R, supersets, rest timer + shade notification, screen stays awake while you train.
- **Trends** — interactive charts, training heatmap, muscle ready/recovering/tired, balance vs close-grip bench, lift 1RMs in lb.
- **Review** — weekly rules (sleep, anchors, rate of loss).
- **Plan** — Health Connect, JSON backup, Strong/Hevy/FitNotes CSV import, ladder policy, 1RM formula.

Training logic lives in `app/src/main/java/com/kishan/preptracker/domain/` with JUnit tests beside it.

## Build

JDK 17. Open this folder in Android Studio, or:

```
.\gradlew.bat testDebugUnitTest assembleDebug
```

First run on a phone: **Plan → Connect** (Health Connect), then **Backfill 30 days**. Fitbit should be the step writer; Grit picks one source per day so Samsung Health is not added on top.

## Design notes

Inspired by the *kind of product* [openGym](https://github.com/DuarteSantos8/openGym) is — a serious gym logger with named ladders, set shapes, recovery, and imports — but this is **not a fork**. No openGym source, catalogue, or gymvisual media is in this tree. Grit is Kotlin + Compose + Room + Health Connect, branded, and built around a fixed v4 program plus Kishan's prep rules.

See [NOTICE.md](NOTICE.md).
