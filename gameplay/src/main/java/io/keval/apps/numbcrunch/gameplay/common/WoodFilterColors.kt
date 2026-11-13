package io.keval.apps.numbcrunch.gameplay.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchChocolateDark
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldBorder
import io.keval.apps.numbcrunch.core.ui.theme.NumbCrunchGoldCream

/** Wood panels stay dark even when the system selects a light or dynamic theme. */
@Composable
internal fun woodFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = NumbCrunchChocolateDark,
    labelColor = NumbCrunchGoldCream,
    iconColor = NumbCrunchGoldCream,
    selectedContainerColor = NumbCrunchGoldBorder,
    selectedLabelColor = NumbCrunchChocolateDark,
    selectedLeadingIconColor = NumbCrunchChocolateDark,
    selectedTrailingIconColor = NumbCrunchChocolateDark,
)

internal fun woodFilterChipBorder(selected: Boolean) = BorderStroke(
    1.dp, if (selected) NumbCrunchGoldBorder else NumbCrunchGoldCream,
)
