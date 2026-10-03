package com.nachojerez.carpstrategy.ui.diary

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.journal.FishingZone
import com.nachojerez.carpstrategy.domain.journal.Fulfilled
import com.nachojerez.carpstrategy.domain.journal.SessionIssue
import com.nachojerez.carpstrategy.domain.journal.SessionIssueCode

/** Zona en 4 rumbos; el detalle lo escribe el usuario (no se inventan nombres de Brovales). */
@StringRes
fun FishingZone.zoneLabelRes(): Int = when (this) {
    FishingZone.NORTH -> R.string.zone_north
    FishingZone.EAST -> R.string.zone_east
    FishingZone.SOUTH -> R.string.zone_south
    FishingZone.WEST -> R.string.zone_west
}

@StringRes
fun Fulfilled.fulfilledLabelRes(): Int = when (this) {
    Fulfilled.YES -> R.string.fulfilled_yes
    Fulfilled.PARTLY -> R.string.fulfilled_partly
    Fulfilled.NO -> R.string.fulfilled_no
}

@Composable
fun sessionIssueText(issue: SessionIssue): String {
    val arg = issue.args.firstOrNull().orEmpty()
    return when (issue.code) {
        SessionIssueCode.END_BEFORE_START -> stringResource(R.string.session_issue_end_before_start)
        SessionIssueCode.TOO_LONG -> stringResource(R.string.session_issue_too_long)
        SessionIssueCode.START_IN_FUTURE -> stringResource(R.string.session_issue_start_in_future)
        SessionIssueCode.INVALID_RODS -> stringResource(R.string.session_issue_invalid_rods, arg)
        SessionIssueCode.NEGATIVE_COUNT -> stringResource(R.string.session_issue_negative)
        SessionIssueCode.BLANK_NOT_ANSWERED -> stringResource(R.string.session_issue_blank_not_answered)
        SessionIssueCode.BLANK_WITH_CATCHES -> stringResource(R.string.session_issue_blank_with_catches)
        SessionIssueCode.INVALID_WEIGHT -> stringResource(R.string.session_issue_invalid_weight)
        SessionIssueCode.INVALID_RANGE -> stringResource(R.string.session_issue_invalid_range)
        SessionIssueCode.RODS_OVER_LIMIT -> stringResource(R.string.session_issue_rods_over_limit, arg)
        SessionIssueCode.OUTSIDE_LEGAL_HOURS -> stringResource(R.string.session_issue_outside_legal)
        SessionIssueCode.CATCH_OUTSIDE_SESSION -> stringResource(R.string.session_issue_catch_outside)
    }
}
