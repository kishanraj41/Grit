# Prep Tracker — Cursor build spec

Drop this in the repo root next to `README.md`. It is written to be handed to
Cursor a phase at a time, not all at once.

**Goal:** take the existing scaffold from "never compiled" to a working app that
adapts to real health data the way MacroFactor adapts calories and the way a
training app like Gravl adapts loads — instead of following the fixed numbers
currently hard-coded in `domain/Program.kt`.

---

## 0. Ground rules for Cursor

Paste this block into Cursor's rules (`.cursorrules`) or the top of the chat:

```
This is an Android app (Kotlin, Compose, Room, Health Connect) that has never
been compiled. Work in phases; do not attempt the whole spec at once.

- Run `./gradlew test` before and after every change. The tests in
  app/src/test/ encode intended behaviour. If a test fails, the test is right
  and the code is wrong, unless the spec explicitly says to change the test.
- Do not modify files in domain/ except where a phase says to. That logic was
  reviewed against published research and is deliberate.
- Never invent a number. Every threshold, coefficient and constant must either
  come from this spec or be surfaced in the Plan screen as a user-editable
  setting. No magic numbers buried in a composable.
- When something cannot be verified (a Health Connect field that may not exist
  on this device, a tracker that may not report steps), write the code to
  degrade to null and show "—" in the UI. Never fabricate a fallback value.
- Prefer small commits with a one-line message describing the behaviour change.
```

---

## Phase 0 — make it compile

Nothing in this phase changes behaviour. Expect 10–30 errors.

1. `./gradlew test` first. These run without a device and should mostly pass;
   they exercise `RulesEngine` and `Progression`. Fix compile errors in
   `app/src/test/` and `domain/` only.
2. `./gradlew assembleDebug`. Known likely breakages, in order of probability:
   - **`SegmentedButton` / `SingleChoiceSegmentedButtonRow`** in
     `ui/screens/TrainScreen.kt` and `PlanScreen.kt` — signature moved between
     Compose BOM versions and may need `@OptIn(ExperimentalMaterial3Api::class)`.
     If it fights back, replace with two `FilterChip`s; the behaviour is identical.
   - **`LinearProgressIndicator`** in `ui/components/Components.kt` — the
     `progress` lambda overload replaced a `Float` parameter in 2024 BOMs.
   - **Health Connect aggregate metrics** in `health/HealthConnectManager.kt` —
     `RestingHeartRateRecord.BPM_AVG`, `NutritionRecord.PROTEIN_TOTAL`,
     `SleepSessionRecord.SLEEP_DURATION_TOTAL` have all moved across alpha
     versions. Check the actual `androidx.health.connect:connect-client` version
     resolved in `gradle/libs.versions.toml` and fix names against that source.
   - **`HeartRateVariabilityRmssdRecord`** — the property is
     `heartRateVariabilityMillis` on recent versions; older ones differ.
   - Missing launcher icon: either add one or confirm the manifest has no
     `android:icon` (it currently does not).
3. Install on a **physical phone**. Health Connect is absent from most emulator
   images. Grant permissions via `Plan → Connect`, then `Backfill 30 days`.

**Acceptance:** app launches, all five tabs render, `Today` shows real steps and
sleep pulled from Health Connect, `./gradlew test` is green.

---

## Phase 1 — adaptive energy (the MacroFactor-style part)

This is the biggest gap. Right now the app compares your loss rate to a fixed
target and nags you. It does not know what you actually burn, and the 2,050 kcal
target is hard-coded prose, not a computed number.

### 1.1 What MacroFactor actually does

Three ideas, none of them secret, though the exact implementation is proprietary
— what follows is a defensible reconstruction, not their algorithm:

1. **Trend weight**, not scale weight. Daily weight is mostly water noise; an
   exponentially weighted moving average extracts the signal.
2. **Energy balance TDEE.** If you know intake and you know the rate the trend
   weight moved, expenditure falls out of arithmetic — no formula, no activity
   multiplier, no guessing.
3. **Weekly target recalculation.** Targets follow the measured TDEE so the
   deficit stays the size you chose as metabolism and activity drift.

### 1.2 New file: `domain/TrendWeight.kt`

```kotlin
package com.kishan.preptracker.domain

import java.time.LocalDate

/**
 * Exponentially weighted moving average of scale weight.
 * alpha 0.15 ≈ a 12–13 day effective window: responsive enough to catch a real
 * change inside two weeks, damped enough to ignore a refeed or a salty meal.
 * Exposed as a setting so it can be tuned; do not hard-code at the call site.
 */
object TrendWeight {
    const val DEFAULT_ALPHA = 0.15

    /** Returns one trend value per day that has a scale weight, in date order. */
    fun compute(weights: List<Pair<LocalDate, Double>>, alpha: Double = DEFAULT_ALPHA): List<Pair<LocalDate, Double>> {
        if (weights.isEmpty()) return emptyList()
        val sorted = weights.sortedBy { it.first }
        var trend = sorted.first().second
        return sorted.map { (d, w) ->
            trend += alpha * (w - trend)
            d to trend
        }
    }

    /** kg per week the trend moved over [days], positive = losing. Null if too little data. */
    fun rateKgPerWeek(trend: List<Pair<LocalDate, Double>>, days: Int = 14): Double? {
        if (trend.size < 2) return null
        val end = trend.last()
        val start = trend.lastOrNull { it.first <= end.first.minusDays(days.toLong()) } ?: return null
        val elapsed = java.time.temporal.ChronoUnit.DAYS.between(start.first, end.first)
        if (elapsed < 7) return null
        return (start.second - end.second) / elapsed * 7.0
    }
}
```

**Replace the simple 7-day average in `RulesEngine.weeklyReview` with this** —
but keep the 7-day average visible on screen too, because that is the number he
has been reasoning with for months and a silent switch would be confusing.

### 1.3 New file: `domain/AdaptiveEnergy.kt`

```kotlin
package com.kishan.preptracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** kcal per kg of body mass change. 7700 is the standard fat figure. */
const val KCAL_PER_KG = 7700.0

data class EnergyEstimate(
    val tdee: Double?,          // kcal/day
    val windowDays: Int,
    val intakeDays: Int,        // complete intake days inside the window
    val confidence: Confidence,
    val note: String,
) {
    enum class Confidence { NONE, LOW, MEDIUM, HIGH }
}

object AdaptiveEnergy {

    /** A day counts as logged only if intake looks real. Below this, assume a missed log. */
    const val MIN_PLAUSIBLE_KCAL = 800

    /**
     * TDEE from energy balance:
     *     TDEE = meanIntake + (trendWeightLostKg * KCAL_PER_KG) / days
     *
     * Requires complete intake logging. Missing days are excluded from the mean
     * but still count in the elapsed period, which biases TDEE downward — so
     * confidence drops sharply as logged days fall below the window.
     */
    fun estimate(
        days: Map<LocalDate, DayLogModel>,
        ref: LocalDate,
        windowDays: Int = 21,
    ): EnergyEstimate {
        val window = (0 until windowDays).map { ref.minusDays(it.toLong()) }
        val intakes = window.mapNotNull { days[it]?.kcal?.takeIf { k -> k >= MIN_PLAUSIBLE_KCAL } }
        val weights = window.mapNotNull { d -> days[d]?.weight?.let { d to it } }
        if (intakes.size < 10 || weights.size < 8)
            return EnergyEstimate(null, windowDays, intakes.size, EnergyEstimate.Confidence.NONE,
                "Need ~10 logged intake days and ~8 weigh-ins in the window.")

        val trend = TrendWeight.compute(weights)
        val start = trend.first(); val end = trend.last()
        val elapsed = ChronoUnit.DAYS.between(start.first, end.first).toDouble()
        if (elapsed < 10) return EnergyEstimate(null, windowDays, intakes.size,
            EnergyEstimate.Confidence.NONE, "Window too short.")

        val lostKg = start.second - end.second
        val tdee = intakes.average() + (lostKg * KCAL_PER_KG) / elapsed

        val coverage = intakes.size.toDouble() / windowDays
        val confidence = when {
            coverage >= 0.9 && weights.size >= 15 -> EnergyEstimate.Confidence.HIGH
            coverage >= 0.75 -> EnergyEstimate.Confidence.MEDIUM
            else -> EnergyEstimate.Confidence.LOW
        }
        val note = when (confidence) {
            EnergyEstimate.Confidence.HIGH -> "Based on ${intakes.size} logged days and ${weights.size} weigh-ins."
            EnergyEstimate.Confidence.MEDIUM -> "Some days unlogged — treat as approximate."
            else -> "Logging gaps make this unreliable. Do not act on it yet."
        }
        return EnergyEstimate(tdee, windowDays, intakes.size, confidence, note)
    }
}
```

**Two things to tell the user in the UI, not bury:**

- Early in a cut, glycogen and water depletion inflate apparent weight loss, so
  TDEE reads high. The estimate is only trustworthy after ~3 weeks of steady
  logging. Show the window and the confidence next to the number, always.
- **MacroFactor is already computing its own adaptive TDEE from the same inputs.**
  This app cannot read that number — Health Connect has no field for it — so it
  recomputes independently and the two will disagree by 100–200 kcal. Label this
  one clearly as "Prep Tracker estimate" so it is never mistaken for
  MacroFactor's. Where they conflict, MacroFactor's has more complete intake
  data and should win.

### 1.4 New file: `domain/NutritionTargets.kt`

```kotlin
package com.kishan.preptracker.domain

data class MacroTarget(
    val kcal: Int, val proteinG: Int, val fatG: Int, val carbG: Int,
    val source: String,   // "adaptive" or "fixed"
    val rationale: String,
)

object NutritionTargets {
    // Guardrails — all editable in Plan, none hard-coded at call sites.
    const val PROTEIN_LOW = 190; const val PROTEIN_HIGH = 210
    const val FAT_FLOOR_G = 50
    const val KCAL_FLOOR = 1900          // below measured RMR 2029 is already aggressive
    const val MAX_WEEKLY_CHANGE = 150    // kcal; never move the target more than this at once

    fun compute(energy: EnergyEstimate, p: Profile, currentTarget: Int?): MacroTarget {
        val goalRate = Program.GOAL_RATE[p.goal] ?: 0.45
        val dailyDeficit = goalRate * KCAL_PER_KG / 7.0

        if (energy.tdee == null || energy.confidence == EnergyEstimate.Confidence.NONE ||
            energy.confidence == EnergyEstimate.Confidence.LOW) {
            return fixed(p, "Not enough clean data — holding the planned 2,050 kcal. ${energy.note}")
        }

        val raw = (energy.tdee - dailyDeficit).toInt()
        val capped = currentTarget?.let { raw.coerceIn(it - MAX_WEEKLY_CHANGE, it + MAX_WEEKLY_CHANGE) } ?: raw

        if (capped < KCAL_FLOOR) {
            return fixed(p, "Hitting ${goalRate} kg/wk would need ${capped} kcal, below the ${KCAL_FLOOR} floor. " +
                "Do not cut further — pull the cardio lever instead (Review tab).")
        }

        val protein = PROTEIN_HIGH
        val fat = maxOf(FAT_FLOOR_G, (capped * 0.25 / 9).toInt())
        val carbs = ((capped - protein * 4 - fat * 9) / 4).coerceAtLeast(0)
        return MacroTarget(capped, protein, fat, carbs, "adaptive",
            "TDEE ${energy.tdee.toInt()} kcal (${energy.windowDays}-day window, ${energy.confidence}) " +
            "minus ${dailyDeficit.toInt()} kcal for ${goalRate} kg/wk. ${energy.note}")
    }

    private fun fixed(p: Profile, why: String) =
        MacroTarget(2050, 205, 55, 165, "fixed", why)
}
```

### 1.5 Wire it up

- `AppViewModel`: add `energy: StateFlow<EnergyEstimate>` and
  `target: StateFlow<MacroTarget>`, both derived from `days` + `profile`.
- `Review` screen: new card at the top — TDEE, confidence, window, and the
  resulting target with its rationale string. If `source == "fixed"`, say so
  plainly rather than showing a number that looks computed.
- **Recalculate weekly, not daily.** Store `lastTargetChange` in `Profile` and
  only move the target if ≥7 days have passed. A target that moves every morning
  is unusable.
- Keep the existing lever ladder. The adaptive engine sets the *number*; the
  lever ladder still decides *whether food is the right thing to change at all*
  — and per the guardrail above, when the required target falls under the floor
  the answer is cardio, not food.

### 1.6 Tests to add (`app/src/test/`)

```
trend weight tracks a linear loss within 0.1 kg after 21 days
trend weight ignores a single 1.5 kg refeed spike (moves < 0.3 kg)
TDEE recovers a known expenditure from synthetic data: intake 2000, true TDEE 2700,
  expect 0.64 kg/wk loss and an estimate within 75 kcal
TDEE returns NONE confidence with fewer than 10 logged intake days
target never moves more than 150 kcal from the previous target in one step
target refuses to go below the 1900 floor and returns the cardio-lever rationale
days below 800 kcal are excluded as missed logs, not counted as fasting
```

---

## Phase 2 — Gravl-style training

*(Assumed, not verified: I have not used Gravl. The features below are what a
modern logging app of that type does; treat the list as a target, not a clone.)*

Already in the scaffold: per-exercise prescriptions, double progression,
previous-session ghosting, rest timer, est-1RM charts, Standard/Compressed modes.

Add, in this order:

1. **Plate calculator.** Tap a load → show the plates per side for a 20 kg bar.
   Bar weight and available plates go in Plan settings. New file
   `ui/components/PlateCalculator.kt`.
2. **PR detection.** On finishing a session, compare each exercise's best e1RM
   against all history; show a badge and a one-line summary
   ("Close-grip bench: new best est. 1RM, 99 → 101 kg").
3. **Exercise history sheet.** Long-press an exercise name → every past session
   for it, with e1RM sparkline and total volume per session.
4. **Warm-up sets.** For anchors only, generate ramp sets from the working load
   (40% × 5, 60% × 3, 80% × 1) — untracked, not counted in weekly volume.
5. **Session notes and per-exercise RPE feel** (easy / normal / hard). Feed
   "hard" flags into the Review screen as a soft fatigue signal alongside HRV.
6. **Quick-edit for set counts.** Add or remove a set inline without switching
   Standard/Compressed for the whole session.
7. **Swap exercise.** Equipment-busy fallback: each exercise gets an
   `alternatives: List<String>` field in `Exercise`; swapping keeps the history
   thread by logging under the original id with a `variant` tag.

**Do not** add: social feed, streak gamification, or anything that turns a
missed day into a penalty. The plan explicitly treats one bad day as noise.

---

## Phase 3 — adaptive training (where the two halves meet)

The `RulesEngine` already reads sleep, HRV, RHR and anchor drops. Extend it with
what Phase 1 now knows:

- If measured TDEE drops >8% over three weeks while training volume held steady,
  flag it as likely reduced NEAT (subconscious movement drops in a deficit) and
  recommend checking steps before touching food.
- If the trend weight has been flat >10 days *and* waist is falling *and* the
  adaptive TDEE is stable, show the water-retention hold explicitly instead of
  letting the lever ladder advance.
- Surface a single "today" line on the Today screen that combines mode, target
  and the one thing that matters most. One sentence, no dashboard.

---

## Verified vs assumed — read before trusting any of this

**Verified (I executed or computed it):**
- Set totals in `domain/Program.kt`: Standard 98, Compressed 72, side delts 16,
  biceps 15, triceps 15, chest 6, back 9. Computed from the source, test-covered.
- The rules-engine logic and double-progression behaviour — ported from a web
  version that was run against synthetic data and produced the expected triggers.
- MacroFactor exports nutrition and weight to Health Connect and syncs as you
  log; its own food log takes priority over data synced into it. Confirmed from
  MacroFactor's own documentation.
- Google Fit REST API shut down; Health Connect is the on-device replacement.

**Assumed (not verified — check before relying on it):**
- That the app compiles. It has never been built.
- Exact Health Connect aggregate metric names for the resolved client version.
- That your tracker writes HRV to Health Connect at all — many only write it to
  their own app. If HRV stays null, delete that flag rather than faking it.
- Gravl's actual feature set. Phase 2 is a reconstruction from typical apps.
- `KCAL_PER_KG = 7700` — correct for fat tissue, wrong during the first two
  weeks of a cut when water and glycogen dominate the change. This is why the
  window is 21 days and the confidence label exists.
- `alpha = 0.15` for trend weight. A tuning parameter, not a finding.
- That MacroFactor's TDEE and this app's will agree. They will not, exactly.
