package com.kishan.preptracker.domain

object TodayLine {
    /** One sentence: mode, target, and the one thing that matters most. */
    fun compose(review: Review, target: MacroTarget): String {
        val mode = if (review.mode == "C") "Compressed" else "Standard"
        val src = if (target.source == "fixed") "fixed" else "adaptive"
        val focus = review.recs.firstOrNull()?.title ?: "No change."
        return "$mode · ${target.kcal} kcal ($src) · $focus"
    }
}
