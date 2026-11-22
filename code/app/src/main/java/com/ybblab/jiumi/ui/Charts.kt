// SPDX-FileCopyrightText: 2026 ybblab
// SPDX-License-Identifier: Apache-2.0

package com.ybblab.jiumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ybblab.jiumi.ui.theme.Ink
import com.ybblab.jiumi.R
import com.ybblab.jiumi.data.BehaviorSummary
import com.ybblab.jiumi.ui.theme.AppIconSize
import com.ybblab.jiumi.ui.theme.StatusWarning

/**
 * One kind of data an app reached: what, how often, then when it last did so, and what the user
 * would not know (refused, while not in use). [routine] work is told plainly.
 */
@Composable
fun SummaryRow(summary: BehaviorSummary, icon: ImageVector, routine: Boolean, selected: Boolean, onClick: () -> Unit) {
    val grey = MaterialTheme.colorScheme.onSurfaceVariant
    val indent = AppIconSize.Leading + 10.dp
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f) else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = grey, modifier = Modifier.size(AppIconSize.Leading))
            Spacer(Modifier.width(10.dp))
            Text(categoryName(summary.category), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(pluralStringResource(R.plurals.times_count, summary.count, formatCount(summary.count.toLong())),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface)
        }
        categoryDescription(summary.category)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = grey, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = indent, top = 4.dp))
        }
        // When first, then what stands out about it.
        val parts = buildList {
            add(stringResource(R.string.recent_at, shortTime(summary.last)) to false)
            if (!routine) {
                if (summary.denied > 0) add(stringResource(R.string.summary_denied, formatCount(summary.denied.toLong())) to true)
                if (summary.unattended > 0) add(stringResource(R.string.summary_unattended, formatCount(summary.unattended.toLong())) to true)
            }
        }
        Text(
            buildAnnotatedString {
                parts.forEachIndexed { i, (text, warn) ->
                    if (i > 0) withStyle(SpanStyle(color = grey)) { append(" · ") }
                    withStyle(SpanStyle(color = if (warn) StatusWarning else grey)) { append(text) }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = indent, top = 6.dp)
        )
    }
}

/** A row's share of the largest row as a thin bar. */
@Composable
fun ShareBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ink.fill)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)))
    }
}
