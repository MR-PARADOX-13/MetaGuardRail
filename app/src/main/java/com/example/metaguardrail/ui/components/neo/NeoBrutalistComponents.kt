package com.example.metaguardrail.ui.components.neo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.metaguardrail.ui.theme.BrutalistTokens
import com.example.metaguardrail.ui.theme.GuardRailColors

// ── Internal hard-shadow card primitive ──
@Composable
private fun BrutalShadowBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = BrutalistTokens.CornerRadius,
    shadowOffset: Dp = BrutalistTokens.ShadowOffset,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // Shadow layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .clip(RoundedCornerShape(cornerRadius))
                .background(GuardRailColors.BrutalBlack)
        )
        content()
    }
}

// ── GuardRailCard ──
@Composable
fun GuardRailCard(
    modifier: Modifier = Modifier,
    brutalContentDescription: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BrutalShadowBox(modifier = modifier.semantics { brutalContentDescription?.let { this.contentDescription = it } }) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(MaterialTheme.colorScheme.surface)
                .border(BrutalistTokens.BorderWidth, MaterialTheme.colorScheme.outline, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(BrutalistTokens.SpacingLg)
        ) { content() }
    }
}

// ── GuardRailPrimaryButton ──
@Composable
fun GuardRailPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    BrutalShadowBox(modifier = modifier.height(BrutalistTokens.ButtonHeight)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(if (enabled) GuardRailColors.BrutalPrimary else Color(0xFF94A3B8))
                .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold))
        }
    }
}

// ── GuardRailSecondaryButton ──
@Composable
fun GuardRailSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text
) {
    BrutalShadowBox(modifier = modifier.height(BrutalistTokens.ButtonHeight)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(MaterialTheme.colorScheme.surface)
                .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .clickable(enabled = enabled, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

// ── GuardRailStatusBadge ──
@Composable
fun GuardRailStatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String = label
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(1.5.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Text(label, color = GuardRailColors.BrutalBlack, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold))
    }
}

// ── GuardRailSectionHeader ──
@Composable
fun GuardRailSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onBackground)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onActionClick != null) {
            Text(
                actionLabel,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .semantics { contentDescription = actionLabel }
                    .defaultMinSize(minWidth = 48.dp, minHeight = 32.dp),
                color = GuardRailColors.BrutalPrimary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

// ── GuardRailStatCard ──
@Composable
fun GuardRailStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    contentDescription: String = "$label $value"
) {
    BrutalShadowBox(modifier = modifier) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(MaterialTheme.colorScheme.surface)
                .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .padding(BrutalistTokens.SpacingLg)
                .semantics { this.contentDescription = contentDescription },
            horizontalAlignment = Alignment.Start
        ) {
            Text(value, style = MaterialTheme.typography.displayMedium, color = valueColor)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── GuardRailToggleRow ──
@Composable
fun GuardRailToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    contentDescription: String = title
) {
    BrutalShadowBox(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(MaterialTheme.colorScheme.surface)
                .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .padding(horizontal = BrutalistTokens.SpacingLg, vertical = 12.dp)
                .semantics { this.contentDescription = contentDescription },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { this.contentDescription = "$title toggle" },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GuardRailColors.BrutalPrimary,
                    checkedBorderColor = GuardRailColors.BrutalBlack,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE2E8F0),
                    uncheckedBorderColor = GuardRailColors.BrutalBlack
                )
            )
        }
    }
}

// ── GuardRailSeverityCard ──
@Composable
fun GuardRailSeverityCard(
    title: String,
    severity: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String = title
) {
    val badgeColor = when (severity.uppercase()) {
        "SAFE" -> GuardRailColors.BrutalMint
        "WARNING", "REVIEW" -> GuardRailColors.BrutalWarning
        "HIGH_RISK", "HIGH RISK", "BLOCKED" -> GuardRailColors.BrutalRisk
        else -> GuardRailColors.BrutalSurfaceVariant
    }
    BrutalShadowBox(modifier = modifier.semantics { this.contentDescription = contentDescription }) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(MaterialTheme.colorScheme.surface)
                .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(BrutalistTokens.SpacingLg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                GuardRailStatusBadge(label = severity, color = badgeColor)
            }
            Spacer(Modifier.height(6.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── GuardRailOutlinedTextField ──
@Composable
fun GuardRailOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    contentDescription: String = label ?: placeholder
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(6.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(MaterialTheme.colorScheme.surface)
                .border(BrutalistTokens.BorderWidth, GuardRailColors.BrutalBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .semantics { this.contentDescription = contentDescription }
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 40.dp)
                    .padding(vertical = 10.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty() && placeholder.isNotEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        inner()
                    }
                }
            )
        }
    }
}

// ── GuardRailNavigationBar ──
data class GuardRailNavItem(val label: String, val icon: ImageVector, val route: String)

@Composable
fun GuardRailNavigationBar(
    items: List<GuardRailNavItem>,
    selectedRoute: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BrutalistTokens.NavBarHeight)
            .background(MaterialTheme.colorScheme.surface)
            .border(width = BrutalistTokens.BorderWidth, color = GuardRailColors.BrutalBlack)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val selected = item.route == selectedRoute
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) GuardRailColors.BrutalPrimary else Color.Transparent)
                    .then(if (selected) Modifier.border(2.dp, GuardRailColors.BrutalBlack, RoundedCornerShape(8.dp)) else Modifier)
                    .clickable { onItemSelected(item.route) }
                    .semantics { contentDescription = item.label },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(item.icon, contentDescription = null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(item.label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium), color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
