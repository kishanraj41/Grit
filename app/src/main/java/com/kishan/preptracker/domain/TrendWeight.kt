package com.kishan.preptracker.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
        val elapsed = ChronoUnit.DAYS.between(start.first, end.first)
        if (elapsed < 7) return null
        return (start.second - end.second) / elapsed * 7.0
    }
}
