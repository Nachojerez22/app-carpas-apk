package com.nachojerez.carpstrategy.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.assistant.AiKind
import com.nachojerez.carpstrategy.domain.assistant.AiLimits
import com.nachojerez.carpstrategy.domain.assistant.AiUsage
import com.nachojerez.carpstrategy.domain.assistant.LimitLevel
import com.nachojerez.carpstrategy.domain.assistant.UsageDay
import com.nachojerez.carpstrategy.ui.components.Caption
import com.nachojerez.carpstrategy.ui.components.CarpCard
import com.nachojerez.carpstrategy.ui.conditions.Formatting
import com.nachojerez.carpstrategy.ui.theme.CarpTheme
import com.nachojerez.carpstrategy.ui.theme.Spacing
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Consumo de la IA (fase 7.14): lo que ha gastado la app hoy (día de cuota de Google) en todos
 * tus móviles, frente a tus límites diarios, los últimos días y los errores de cuota. Gemini no
 * dice cuánto te queda: son las cuentas de la app.
 */
@Composable
fun AiUsageCard(days: List<UsageDay>, limits: AiLimits, now: Instant, onSaveLimits: (AiLimits) -> Unit) {
    val today = AiUsage.today(days, now)
    var editing by rememberSaveable { mutableStateOf(false) }
    CarpCard {
        Text(stringResource(R.string.ai_usage_title), style = MaterialTheme.typography.titleSmall)
        Caption(stringResource(R.string.ai_usage_reset, Formatting.clock(AiUsage.nextReset(now))))

        // Hoy, frente a tus límites.
        UsageLine(stringResource(R.string.ai_usage_requests), today.requests.toLong(), limits.requestsPerDay?.toLong())
        UsageLine(stringResource(R.string.ai_usage_searches), today.searches.toLong(), limits.searchesPerDay?.toLong())
        UsageLine(stringResource(R.string.ai_usage_tokens), today.tokens, limits.tokensPerDay)
        Caption(
            stringResource(
                R.string.ai_usage_tokens_detail,
                Formatting.number(today.inputTokens.toDouble(), 0), Formatting.number(today.outputTokens.toDouble(), 0), Formatting.number(today.thinkingTokens.toDouble(), 0),
            ),
        )
        if (today.byKind.isNotEmpty()) {
            Caption(today.byKind.entries.sortedByDescending { it.value }.joinToString(" · ") { (k, n) -> "${kindText(k)}: $n" })
        }
        if (today.quotaErrors > 0) {
            Text(
                stringResource(R.string.ai_usage_quota_errors, today.quotaErrors, today.lastQuotaAt?.let { Formatting.clock(it) } ?: "—"),
                color = MaterialTheme.colorScheme.error,
            )
            today.lastQuotaDetail?.let { Caption(stringResource(R.string.ai_error_detail, it)) }
        }

        // Últimos días.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.ai_usage_last_days), style = MaterialTheme.typography.titleSmall)
        AiUsage.lastDays(days, now).forEach { d ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(DAY.format(d.day), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(R.string.ai_usage_day_line, d.requests, Formatting.number(d.tokens / 1000.0, 1), d.searches) +
                        if (d.quotaErrors > 0) stringResource(R.string.ai_usage_day_errors, d.quotaErrors) else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // Tus límites.
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (!editing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Caption(
                    if (limits == AiLimits()) stringResource(R.string.ai_usage_limits_none) else stringResource(R.string.ai_usage_limits_set),
                    Modifier.weight(1f),
                )
                TextButton(onClick = { editing = true }) { Text(stringResource(R.string.ai_usage_limits_edit)) }
            }
        } else {
            LimitsEditor(limits, onSave = { onSaveLimits(it); editing = false }, onCancel = { editing = false })
        }
        Caption(stringResource(R.string.ai_usage_hint))
    }
}

/** «Consultas hoy: 45 de 250» con su barra; en ámbar al 80 % y en rojo al llegar al límite. */
@Composable
private fun UsageLine(label: String, used: Long, limit: Long?) {
    val level = AiUsage.level(used, limit)
    val color: Color = when (level) {
        LimitLevel.OK -> MaterialTheme.colorScheme.primary
        LimitLevel.NEAR -> CarpTheme.colors.warnStrong
        LimitLevel.OVER -> MaterialTheme.colorScheme.error
    }
    val usedText = Formatting.number(used.toDouble(), 0)
    Text(
        if (limit != null) stringResource(R.string.ai_usage_of, label, usedText, Formatting.number(limit.toDouble(), 0)) else stringResource(R.string.ai_usage_plain, label, usedText),
        color = if (level == LimitLevel.OK) MaterialTheme.colorScheme.onSurface else color,
    )
    if (limit != null && limit > 0) {
        LinearProgressIndicator(progress = { (used.toFloat() / limit).coerceIn(0f, 1f) }, color = color, modifier = Modifier.fillMaxWidth())
        when (level) {
            LimitLevel.NEAR -> Caption(stringResource(R.string.ai_usage_near), color = color)
            LimitLevel.OVER -> Caption(stringResource(R.string.ai_usage_over), color = color)
            LimitLevel.OK -> Unit
        }
    }
}

@Composable
private fun LimitsEditor(limits: AiLimits, onSave: (AiLimits) -> Unit, onCancel: () -> Unit) {
    var requests by rememberSaveable { mutableStateOf(limits.requestsPerDay?.toString().orEmpty()) }
    var searches by rememberSaveable { mutableStateOf(limits.searchesPerDay?.toString().orEmpty()) }
    var tokens by rememberSaveable { mutableStateOf(limits.tokensPerDay?.toString().orEmpty()) }
    LimitField(requests, { requests = it }, stringResource(R.string.ai_usage_limit_requests))
    LimitField(searches, { searches = it }, stringResource(R.string.ai_usage_limit_searches))
    LimitField(tokens, { tokens = it }, stringResource(R.string.ai_usage_limit_tokens))
    Row {
        TextButton(onClick = {
            onSave(
                AiLimits(
                    requestsPerDay = requests.trim().toIntOrNull()?.takeIf { it > 0 },
                    searchesPerDay = searches.trim().toIntOrNull()?.takeIf { it > 0 },
                    tokensPerDay = tokens.trim().toLongOrNull()?.takeIf { it > 0 },
                ),
            )
        }) { Text(stringResource(R.string.action_save)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
    }
}

@Composable
private fun LimitField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun kindText(kind: AiKind): String = stringResource(
    when (kind) {
        AiKind.CHECK_IN -> R.string.ai_kind_check_in
        AiKind.PLAN -> R.string.ai_kind_plan
        AiKind.REVIEW -> R.string.ai_kind_review
        AiKind.SHEET -> R.string.ai_kind_sheet
        AiKind.CHAT -> R.string.ai_kind_chat
        AiKind.BAIT -> R.string.ai_kind_bait
    },
)

/** «sáb 25/10». */
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd/MM", java.util.Locale.forLanguageTag("es-ES"))
