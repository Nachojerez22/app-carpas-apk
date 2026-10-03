package com.nachojerez.carpstrategy.ui.manual

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.manual.IssueCode
import com.nachojerez.carpstrategy.domain.manual.ManualField
import com.nachojerez.carpstrategy.domain.manual.ManualIssue
import com.nachojerez.carpstrategy.domain.manual.ManualOrigin
import com.nachojerez.carpstrategy.domain.manual.ManualRecord

@StringRes
fun ManualField.labelRes(): Int = when (this) {
    ManualField.AIR_TEMPERATURE -> R.string.field_temp_aire_c
    ManualField.PRESSURE_MSL -> R.string.field_presion_hpa
    ManualField.WIND_SPEED -> R.string.field_viento_kmh
    ManualField.WIND_DIRECTION -> R.string.field_viento_dir_grados
    ManualField.WIND_GUSTS -> R.string.field_racha_kmh
    ManualField.CLOUD_COVER -> R.string.field_nubosidad_pct
    ManualField.PRECIPITATION -> R.string.field_lluvia_mm
    ManualField.RELATIVE_HUMIDITY -> R.string.field_humedad_pct
    ManualField.WATER_TEMP_SURFACE -> R.string.field_temp_agua_superficie_c
    ManualField.WATER_TEMP_BOTTOM -> R.string.field_temp_agua_fondo_c
    ManualField.BOTTOM_DEPTH -> R.string.field_profundidad_fondo_m
    ManualField.TURBIDITY -> R.string.field_turbidez
    ManualField.DAILY_PRECIPITATION -> R.string.field_lluvia_dia_mm
    ManualField.RESERVOIR_VOLUME -> R.string.field_nivel_embalse_hm3
    ManualField.RESERVOIR_PERCENT -> R.string.field_nivel_embalse_pct
    ManualField.RESERVOIR_ELEVATION -> R.string.field_cota_embalse_m
}

@StringRes
fun DataSource.labelRes(): Int = when (this) {
    DataSource.MANUAL -> R.string.source_manual
    DataSource.AEMET -> R.string.source_aemet
    DataSource.MODELS -> R.string.source_models
}

@StringRes
fun DataSource.tagRes(): Int = when (this) {
    DataSource.MANUAL -> R.string.source_tag_manual
    DataSource.AEMET -> R.string.source_tag_aemet
    DataSource.MODELS -> R.string.source_tag_models
}

@Composable
fun originText(record: ManualRecord): String = when (val origin = record.origin) {
    ManualOrigin.Typed -> stringResource(R.string.manual_origin_typed)
    is ManualOrigin.Imported -> stringResource(R.string.manual_origin_imported, origin.fileName)
}

@Composable
fun issueText(issue: ManualIssue): String {
    val a = issue.args
    fun arg(i: Int) = a.getOrElse(i) { "?" }
    val message = when (issue.code) {
        IssueCode.NOT_JSON -> stringResource(R.string.issue_not_json)
        IssueCode.BAD_FORMAT -> stringResource(R.string.issue_bad_format, arg(0))
        IssueCode.UNSUPPORTED_VERSION -> stringResource(R.string.issue_unsupported_version, arg(0))
        IssueCode.INVALID_ZONE -> stringResource(R.string.issue_invalid_zone, arg(0))
        IssueCode.INVALID_LOCATION -> stringResource(R.string.issue_invalid_location)
        IssueCode.NO_RECORDS -> stringResource(R.string.issue_no_records)
        IssueCode.TOO_MANY_RECORDS -> stringResource(R.string.issue_too_many_records, arg(0))
        IssueCode.RECORD_NOT_OBJECT -> stringResource(R.string.issue_record_not_object)
        IssueCode.MISSING_SOURCE -> stringResource(R.string.issue_missing_source)
        IssueCode.MISSING_PERIOD -> stringResource(R.string.issue_missing_period)
        IssueCode.BOTH_TIME_AND_DATE -> stringResource(R.string.issue_both_time_and_date)
        IssueCode.INVALID_TIME -> stringResource(R.string.issue_invalid_time, arg(0))
        IssueCode.INVALID_DATE -> stringResource(R.string.issue_invalid_date, arg(0))
        IssueCode.FUTURE_PERIOD -> stringResource(R.string.issue_future_period, arg(0))
        IssueCode.NOT_A_NUMBER -> stringResource(R.string.issue_not_a_number, arg(0))
        IssueCode.OUT_OF_RANGE -> stringResource(R.string.issue_out_of_range, arg(0), arg(1), arg(2))
        IssueCode.NOT_AN_INTEGER -> stringResource(R.string.issue_not_an_integer, arg(0))
        IssueCode.FIELD_NOT_ALLOWED -> stringResource(R.string.issue_field_not_allowed, arg(0))
        IssueCode.NO_VALUES -> stringResource(R.string.issue_no_values)
        IssueCode.EMPTY_RECORD_SKIPPED -> stringResource(R.string.issue_empty_record_skipped)
        IssueCode.UNKNOWN_FIELD -> stringResource(R.string.issue_unknown_field)
        IssueCode.FAR_FROM_LOCATION -> stringResource(R.string.issue_far_from_location, arg(0))
        IssueCode.BOTTOM_WITHOUT_DEPTH -> stringResource(R.string.issue_bottom_without_depth)
        IssueCode.DUPLICATE_IN_FILE -> stringResource(R.string.issue_duplicate_in_file)
    }
    val prefix = issue.recordNumber?.let { stringResource(R.string.issue_prefix_record, it) }
        ?: stringResource(R.string.issue_prefix_file)
    return if (issue.field != null) {
        stringResource(R.string.issue_line_field, prefix, issue.field, message)
    } else {
        stringResource(R.string.issue_line, prefix, message)
    }
}
