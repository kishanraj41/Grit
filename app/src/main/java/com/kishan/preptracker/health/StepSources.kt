package com.kishan.preptracker.health

/**
 * Health Connect stores a StepsRecord from every app that writes steps.
 * Summing them double-counts the same walking (Fitbit + Samsung Health + phone).
 * Pick one writer per day. Fitbit is the intended tracker for this app.
 */
object StepSources {
    const val FITBIT = "com.fitbit.FitbitMobile"
    const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"
    const val GOOGLE_FIT = "com.google.android.apps.fitness"

    private val preferred = listOf(FITBIT, SAMSUNG_HEALTH, GOOGLE_FIT)

    /** [byOrigin] is already de-duplicated inside each package (HC aggregate or merged intervals). */
    fun pick(byOrigin: Map<String, Long>): Pair<String, Long>? {
        val usable = byOrigin.filter { it.value > 0 }
        if (usable.isEmpty()) return null
        for (pkg in preferred) {
            usable[pkg]?.let { return pkg to it }
        }
        return usable.maxBy { it.value }.toPair()
    }

    fun label(pkg: String?): String = when (pkg) {
        null, "", "healthconnect" -> "Health Connect"
        FITBIT -> "Fitbit"
        SAMSUNG_HEALTH -> "Samsung Health"
        GOOGLE_FIT -> "Google Fit"
        else -> pkg.substringAfterLast('.')
    }

    fun srcTag(pkg: String?): String = if (pkg.isNullOrBlank()) "hc" else "hc:$pkg"

    fun packageOf(src: String?): String? =
        src?.takeIf { it.startsWith("hc:") }?.removePrefix("hc:")?.takeIf { it.isNotBlank() }
}
