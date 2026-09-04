/*
 * Zalith Launcher 2 — Zeryth Fork
 * Crash Analyzer: Reusable expandable card for analyzer sections
 */

package com.movtery.zalithlauncher.ui.screens.main.crash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.movtery.zalithlauncher.R

@Composable
fun ExpandableAnalyzerCard(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    headerContent: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    headerContent?.invoke()
                }
                Icon(
                    painter = painterResource(
                        if (isExpanded) R.drawable.ic_arrow_drop_up_rounded
                        else R.drawable.ic_arrow_drop_down_rounded
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Animated expand/collapse
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = content
                )
            }
        }
    }
}
