package com.nachojerez.carpstrategy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nachojerez.carpstrategy.R
import com.nachojerez.carpstrategy.domain.manual.DataSource
import com.nachojerez.carpstrategy.domain.rules.Evidence
import com.nachojerez.carpstrategy.ui.theme.EvidenceKind
import com.nachojerez.carpstrategy.ui.theme.SourceKind

/** Evidencia del dominio → insignia del diseño. La normativa no es evidencia: usa [RegulationBadge]. */
fun Evidence.toKind(): EvidenceKind? = when (this) {
    Evidence.GREEN -> EvidenceKind.Strong
    Evidence.YELLOW -> EvidenceKind.Moderate
    Evidence.RED -> EvidenceKind.NoEvidence
    Evidence.PURPLE -> EvidenceKind.LocalHypothesis
    Evidence.BLUE -> EvidenceKind.IndividualVariability
    Evidence.REGULATION -> null
}

fun DataSource.toKind(): SourceKind = when (this) {
    DataSource.MANUAL -> SourceKind.Manual
    DataSource.AEMET -> SourceKind.Aemet
    DataSource.MODELS -> SourceKind.Forecast
}

/** Insignia para reglas de normativa (filtro legal), con el mismo formato que la de evidencia. */
@Composable
fun RegulationBadge(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val label = stringResource(R.string.badge_regulation)
    Row(
        modifier
            .height(24.dp)
            .background(cs.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(start = 7.dp, end = 9.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⚖ $label", color = cs.onSurfaceVariant, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Insignia de evidencia o de normativa según corresponda. */
@Composable
fun AnyEvidenceBadge(evidence: Evidence, modifier: Modifier = Modifier, short: Boolean = true) {
    val kind = evidence.toKind()
    if (kind == null) RegulationBadge(modifier) else EvidenceBadge(kind, modifier, short)
}
