<div align="center">

```
 ██████╗ ██████╗ ██╗████████╗
██╔════╝ ██╔══██╗██║╚══██╔══╝
██║  ███╗██████╔╝██║   ██║
██║   ██║██╔══██╗██║   ██║
╚██████╔╝██║  ██║██║   ██║
 ╚═════╝ ╚═╝  ╚═╝╚═╝   ╚═╝
```

# Native Android prep tracker

**Log the session. Trust the week. Cut on rules, not vibes.**

Orange on charcoal. Loads in **lb**. Scale in **kg**. Data stays on the phone.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-E4572E?style=for-the-badge&logo=kotlin&logoColor=white)](#build)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-0E0E0E?style=for-the-badge&logo=jetpackcompose&logoColor=E4572E)](#build)
[![Health Connect](https://img.shields.io/badge/Health-Connect-E4572E?style=for-the-badge)](#data)
[![License](https://img.shields.io/badge/License-MIT-0E0E0E?style=for-the-badge)](LICENSE)

</div>

---

<table>
<tr>
<td align="center" width="20%"><b>Today</b><br/>one call, then the log</td>
<td align="center" width="20%"><b>Train</b><br/>v4 week in pounds</td>
<td align="center" width="20%"><b>Trends</b><br/>tap a day, read it</td>
<td align="center" width="20%"><b>Review</b><br/>the protocol speaks</td>
<td align="center" width="20%"><b>Plan</b><br/>sync, backup, import</td>
</tr>
</table>

```mermaid
flowchart LR
  subgraph phone [Phone]
    HC[Health Connect]
    ROOM[(Room prep.db)]
    UI[Grit UI]
  end
  Watch[Any watch / band]
  Diet[Any food log]
  Scale[Any scale]
  Watch -->|steps sleep HRV RHR| HC
  Diet -->|kcal protein| HC
  Scale -->|weight| HC
  HC -->|one writer per metric| ROOM
  UI <--> ROOM
  UI -->|Train sessions stay local| ROOM
```

## Five rooms

| | Tab | You get |
| :--- | :--- | :--- |
| 01 | **Today** | One line of advice. Weight, kcal, protein, sleep, steps, RHR/HRV. 7-day avg and trend. |
| 02 | **Train** | Standard or Compressed start. `-5` / `+5` lb. Tap to type. US plates on the bar you pick (45 / EZ 25 / trap 55 / smith 30). Warm-up, drop, rest-pause, timed, cardio, RPE, L/R, fail, supersets. Copy a set, move or clone a day, reopen a finished session. Orange flash when rest ends. Screen stays awake. |
| 03 | **Trends** | Drag a chart. Calendar-true gaps. 52-week heatmap. Muscle Recover / Dose / Quiet. Balance vs close-grip bench. Weight chart goal line. Anchor 1RMs in lb. |
| 04 | **Review** | Sleep, anchors, rate of loss. Compressed vs Standard. Diet levers. |
| 05 | **Plan** | Health Connect. Week shape (v4, PPL, upper/lower, full body) on a weekday clock or next-in-queue. JSON backup. Strong / Hevy / FitNotes CSV. Ladder policy. 1RM formula. |

## Signature

```
LOADS     225 lb     gym plates, 5 lb jumps
SCALE      86.4 kg   Health Connect / tape
STEPS      Auto      one writer per day, never two apps summed
LOGIC      domain/   pure Kotlin, unit-tested
STORE      Room      prep.db on device, not the cloud
```

**Retention-Recomp Protocol v4** is the default week: arms and side-delts, Standard or Compressed by trigger. Swap the clock to Push/Pull/Legs, Upper/Lower, or Full body without a second exercise library. Energy math is intake vs trend weight (7700 kcal/kg), not a copied TDEE from another app.

## Data

| In | Out |
| :--- | :--- |
| Whatever your watch, band, scale, or food log writes to Health Connect | Train sessions: **local only** |
| Manual tags beat sync | JSON backup you export |
| Pin a writer in Plan, or leave Auto | CSV in from Strong / Hevy / FitNotes |

Connect path: **Plan → Connect → Backfill 30 days**, then pick Movement and Food writers if you run more than one.

Grit never sums two apps for the same metric. Auto keeps the largest single writer that day.

## Build

JDK **17**. Android Studio, or:

```bash
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

```
app/src/main/java/com/kishan/preptracker/
├── domain/     rules, ladders, 1RM, muscles, heatmap
├── health/     Health Connect + step source pick
├── data/       Room + backup codec
├── train/      rest notification
└── ui/         five tabs, orange theme
```

Tests live next to the ideas they pin down in `app/src/test/`.

<div align="center">

**Grit** · MIT · yours to run, fork, and log against

</div>
