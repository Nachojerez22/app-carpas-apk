package com.nachojerez.carpstrategy.ui.guided

import android.content.res.Resources
import androidx.annotation.StringRes
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.advice.DayPart
import com.nachojerez.carpstrategy.domain.advice.DepthBand
import com.nachojerez.carpstrategy.domain.advice.DistanceBand
import com.nachojerez.carpstrategy.domain.advice.LessonConditions
import com.nachojerez.carpstrategy.domain.advice.LessonTopic
import com.nachojerez.carpstrategy.ui.conditions.Formatting

// Textos de «Qué hago ahora» y de «Lo aprendido» (fase 7.5).

@StringRes
fun LessonTopic.titleRes(): Int = when (this) {
    LessonTopic.BAIT -> R.string.topic_bait
    LessonTopic.WHERE -> R.string.topic_where
    LessonTopic.DISTANCE -> R.string.topic_distance
    LessonTopic.PRESENTATION -> R.string.topic_presentation
    LessonTopic.WHEN -> R.string.topic_when
    LessonTopic.GROUNDBAIT -> R.string.topic_groundbait
    LessonTopic.AVOID -> R.string.topic_avoid
    LessonTopic.OTHER -> R.string.topic_other
}

@StringRes
fun DayPart.titleRes(): Int = when (this) {
    DayPart.DAWN -> R.string.daypart_dawn
    DayPart.MORNING -> R.string.daypart_morning
    DayPart.MIDDAY -> R.string.daypart_midday
    DayPart.AFTERNOON -> R.string.daypart_afternoon
    DayPart.DUSK -> R.string.daypart_dusk
    DayPart.NIGHT -> R.string.daypart_night
}

@StringRes
fun DistanceBand.titleRes(): Int = when (this) {
    DistanceBand.CLOSE -> R.string.distance_close
    DistanceBand.MEDIUM -> R.string.distance_medium
    DistanceBand.FAR -> R.string.distance_far
}

@StringRes
fun DepthBand.titleRes(): Int = when (this) {
    DepthBand.SHALLOW -> R.string.depth_shallow
    DepthBand.MEDIUM -> R.string.depth_medium
    DepthBand.DEEP -> R.string.depth_deep
}

/** «Otoño · agua 14–18 °C · anochecer · Punta». */
fun Resources.conditionsText(c: LessonConditions): String = listOfNotNull(
    c.phase?.let { getString(it.titleRes()) },
    when {
        c.waterMinC != null && c.waterMaxC != null -> getString(R.string.lessons_water_range, Formatting.number(c.waterMinC, 0), Formatting.number(c.waterMaxC, 0))
        c.waterMinC != null -> getString(R.string.lessons_water_min, Formatting.number(c.waterMinC, 0))
        c.waterMaxC != null -> getString(R.string.lessons_water_max, Formatting.number(c.waterMaxC, 0))
        else -> null
    },
    c.dayPart?.let { getString(it.titleRes()).lowercase() },
    c.spotName,
).joinToString(" · ")
