package com.bookintok.app.ui.books

/**
 * Distances offered by the search slider. Steps grow with the distance, because nobody needs to
 * choose between 512 and 550 km: 1 km steps up to 10, then 2 km up to 50, 10 km up to 100 and
 * 50 km up to 1000.
 */
object DistanceSteps {
    const val DEFAULT_KM = 5

    val values: List<Int> = buildList {
        addAll(1..10)
        addAll(12..50 step 2)
        addAll(60..100 step 10)
        addAll(150..1000 step 50)
    }

    /** Index of the closest offered distance, for positioning the slider. */
    fun indexOf(km: Int): Int = values.indices.minBy { kotlin.math.abs(values[it] - km) }
}
