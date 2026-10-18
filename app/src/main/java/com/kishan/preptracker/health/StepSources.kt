package com.kishan.preptracker.health

/**
 * Health Connect stores a record from every app that writes that type.
 * Summing writers double-counts the same walk or the same meal.
 * Grit keeps one package per metric. Pin one in Plan, or Auto = the largest
 * single writer that day. No brand is hard-wired.
 */
object StepSources {
    fun pick(byOrigin: Map<String, Long>, prefer: String? = null): Pair<String, Long>? {
        val usable = byOrigin.filter { it.value > 0 }
        if (usable.isEmpty()) return null
        prefer?.takeIf { it in usable }?.let { return it to usable.getValue(it) }
        return usable.maxBy { it.value }.toPair()
    }

    fun pickDouble(byOrigin: Map<String, Double>, prefer: String? = null): Pair<String, Double>? {
        val usable = byOrigin.filter { it.value > 0 }
        if (usable.isEmpty()) return null
        prefer?.takeIf { it in usable }?.let { return it to usable.getValue(it) }
        return usable.maxBy { it.value }.toPair()
    }

    fun label(pkg: String?): String {
        if (pkg.isNullOrBlank() || pkg == "healthconnect") return "Health Connect"
        KNOWN[pkg]?.let { return it }
        KNOWN.entries.firstOrNull { pkg.startsWith(it.key) }?.let { return it.value }
        return pkg.substringAfterLast('.').replace('_', ' ')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun srcTag(pkg: String?): String = if (pkg.isNullOrBlank()) "hc" else "hc:$pkg"

    fun packageOf(src: String?): String? =
        src?.takeIf { it.startsWith("hc:") }?.removePrefix("hc:")?.takeIf { it.isNotBlank() }

    private val KNOWN = mapOf(
        "com.fitbit.FitbitMobile" to "Fitbit",
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.google.android.apps.fitness" to "Google Fit",
        "com.google.android.apps.healthdata" to "Health Connect",
        "com.garmin.android.apps.connectmobile" to "Garmin",
        "com.whoop.android" to "WHOOP",
        "com.ouraring.oura" to "Oura",
        "com.polar.polarflow" to "Polar",
        "com.strava" to "Strava",
        "com.peloton.android" to "Peloton",
        "com.myfitnesspal.android" to "MyFitnessPal",
        "com.cronometer" to "Cronometer",
        "com.sbs.diet" to "Carbon",
        "com.macrofactor" to "MacroFactor",
        "com.hevy" to "Hevy",
        "com.strongapp" to "Strong",
        "com.withings.wiscale2" to "Withings",
        "com.xiaomi.hm.health" to "Zepp / Mi",
        "com.huawei.health" to "Huawei Health",
        "com.apple.android.health" to "Apple Health",
    )
}
