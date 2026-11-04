package com.example.metaguardrail.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.metaguardrail.BuildConfig
import com.example.metaguardrail.data.ActivityEvent
import com.example.metaguardrail.data.ReportExporter
import com.example.metaguardrail.data.SafetySettings
import com.example.metaguardrail.data.ThemeMode
import com.example.metaguardrail.monitor.LiveProtectionStatus
import com.example.metaguardrail.ui.theme.BrutalistTokens
import com.example.metaguardrail.ui.theme.GuardRailColors

// ── Neo-Brutalist local primitives ──

private val BgOffWhite = GuardRailColors.BrutalBg
private val CardWhite = GuardRailColors.BrutalSurface
private val InkBlack = GuardRailColors.BrutalBlack
private val ElectricBlue = GuardRailColors.BrutalPrimary
private val LightGrayTrack = Color(0xFFE2E8F0)
private val MonospaceBg = GuardRailColors.BrutalSurfaceVariant

@Composable
private fun BrutalShadowCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = BrutalistTokens.ShadowOffset, y = BrutalistTokens.ShadowOffset)
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(InkBlack)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(CardWhite)
                .border(BrutalistTokens.BorderWidth, InkBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(16.dp)
        ) { content() }
    }
}

@Composable
private fun GuardRailCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BrutalShadowCard(modifier = modifier.fillMaxWidth()) { content() }
}

@Composable
private fun GuardRailSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(CardWhite)
                .border(2.dp, InkBlack, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = title.uppercase(),
                color = InkBlack,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                lineHeight = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(InkBlack)
        )
    }
}

@Composable
private fun BrutalPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.height(BrutalistTokens.ButtonHeight)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = BrutalistTokens.ShadowOffset, y = BrutalistTokens.ShadowOffset)
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(InkBlack)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(ElectricBlue)
                .border(BrutalistTokens.BorderWidth, InkBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = BrutalistTokens.ButtonHeight)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.3.sp)
        }
    }
}

@Composable
private fun BrutalSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    val h = if (small) BrutalistTokens.ButtonHeightSm else BrutalistTokens.ButtonHeight
    val shadow = if (small) BrutalistTokens.ShadowOffsetSm else BrutalistTokens.ShadowOffset
    Box(modifier = modifier.height(h)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadow, y = shadow)
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(InkBlack)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(CardWhite)
                .border(BrutalistTokens.BorderWidth, InkBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .clickable(onClick = onClick)
                .defaultMinSize(minHeight = h)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = InkBlack, fontSize = if (small) 12.sp else 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BrutalToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = BrutalistTokens.ShadowOffset, y = BrutalistTokens.ShadowOffset)
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(InkBlack)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(BrutalistTokens.CornerRadius))
                .background(CardWhite)
                .border(BrutalistTokens.BorderWidth, InkBlack, RoundedCornerShape(BrutalistTokens.CornerRadius))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, color = InkBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = subtitle, color = GuardRailColors.BrutalSecondaryText, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ElectricBlue,
                    checkedBorderColor = InkBlack,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = LightGrayTrack,
                    uncheckedBorderColor = InkBlack
                )
            )
        }
    }
}

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    settings: SafetySettings,
    protectionStatus: LiveProtectionStatus,
    onManageNotificationPermission: () -> Unit,
    onManageOverlayPermission: () -> Unit,
    onManageUsagePermission: () -> Unit,
    onClearActivity: (() -> Unit)? = null,
    notifyHighRisk: Boolean = false,
    onNotifyHighRiskChange: ((Boolean) -> Unit)? = null,
    notifyWarning: Boolean = false,
    onNotifyWarningChange: ((Boolean) -> Unit)? = null,
    cloudTextAnalysisEnabled: Boolean = false,
    onCloudTextAnalysisChange: ((Boolean) -> Unit)? = null,
    cloudImageAnalysisEnabled: Boolean = false,
    onCloudImageAnalysisChange: ((Boolean) -> Unit)? = null,
    activityEvents: List<ActivityEvent> = emptyList()
) {
    val context = LocalContext.current
    val packageName = rememberPackageName()
    val appVersion = rememberAppVersion()
    val compileSdk = 37
    val notificationGranted = rememberNotificationGranted()
    val overlayGranted = rememberOverlayGranted()
    val usageGranted = rememberUsageGranted()
    var confirmShareSettings by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val json = ReportExporter.buildReportJson(activityEvents)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgOffWhite)
            .verticalScroll(rememberScrollState())
    ) {
        // Brutalist top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgOffWhite)
                .border(width = 2.dp, color = InkBlack)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElectricBlue)
                    .border(2.dp, InkBlack, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("MG", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text("Settings", color = InkBlack, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
                Text("Appearance, permissions, and info", color = GuardRailColors.BrutalSecondaryText, fontSize = 12.sp, lineHeight = 14.sp)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Appearance — Theme chips as brutalist pills
            GuardRailSectionHeader(title = "Appearance")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Theme", color = InkBlack, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Choose how the app looks. System follows your device setting.",
                        color = GuardRailColors.BrutalSecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (mode in ThemeMode.entries) {
                            val selected = themeMode == mode
                            Box(modifier = Modifier.height(40.dp)) {
                                if (selected) {
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .offset(x = 3.dp, y = 3.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(InkBlack)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) ElectricBlue else CardWhite)
                                        .border(2.dp, InkBlack, RoundedCornerShape(20.dp))
                                        .clickable { onThemeChange(mode) }
                                        .defaultMinSize(minHeight = 36.dp)
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mode.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else InkBlack
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Notifications
            GuardRailSectionHeader(title = "Notifications")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingsRowWithAction(
                        title = "Notifications",
                        subtitle = if (notificationGranted) "Granted" else "Not granted — needed for foreground service on Android 13+",
                        statusOk = notificationGranted,
                        actionLabel = "Manage",
                        onAction = onManageNotificationPermission
                    )
                    if (!notificationGranted) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MonospaceBg)
                                .border(2.dp, InkBlack, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "Incident notifications require notification permission. Enable it via Manage above.",
                                color = InkBlack,
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    BrutalToggleCard(
                        title = "High-risk alerts",
                        subtitle = "Notify when a new high-risk incident is recorded (highest priority). OFF by default.",
                        checked = notifyHighRisk,
                        onCheckedChange = { onNotifyHighRiskChange?.invoke(it) }
                    )
                    BrutalToggleCard(
                        title = "Warning alerts",
                        subtitle = "Notify when a new warning incident is recorded. OFF by default.",
                        checked = notifyWarning,
                        onCheckedChange = { onNotifyWarningChange?.invoke(it) }
                    )
                }
            }

            // Permissions
            GuardRailSectionHeader(title = "Permissions")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsRowWithAction(
                        title = "Display over other apps",
                        subtitle = if (overlayGranted) "Granted — shield overlay can appear" else "Not granted — required for shield",
                        statusOk = overlayGranted,
                        actionLabel = "Manage",
                        onAction = onManageOverlayPermission
                    )
                    SettingsRowWithAction(
                        title = "Usage access",
                        subtitle = if (usageGranted) "Granted — checks limited to Instagram & Facebook" else "Not granted — needed to scope checks",
                        statusOk = usageGranted,
                        actionLabel = "Manage",
                        onAction = onManageUsagePermission
                    )
                }
            }

            // Live protection
            GuardRailSectionHeader(title = "Live protection")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Current status", color = InkBlack, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    StatusDotRow(label = if (protectionStatus.isRunning) "Active" else "Inactive", ok = protectionStatus.isRunning)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MonospaceBg)
                            .border(2.dp, InkBlack, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(text = protectionStatus.message, color = InkBlack, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                    }
                    if (!protectionStatus.finding.isNullOrBlank()) {
                        Text(text = "Finding: ${protectionStatus.finding}", color = GuardRailColors.Warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!protectionStatus.source.isNullOrBlank()) {
                        Text(text = "Source: ${protectionStatus.source}", color = GuardRailColors.BrutalSecondaryText, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Audio
            GuardRailSectionHeader(title = "Audio")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Speech language", color = InkBlack, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MonospaceBg)
                            .border(2.dp, InkBlack, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = settings.speechLanguage.label, color = InkBlack, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Text(
                        text = "Used for live audio checks when playback capture is allowed. Never uses the room microphone; only Meta-app playback. Telugu is experimental.",
                        color = GuardRailColors.BrutalMutedText,
                        style = MaterialTheme.typography.labelSmall,
                        lineHeight = 14.sp
                    )
                }
            }

            // Privacy
            GuardRailSectionHeader(title = "Privacy")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "On-device processing", color = InkBlack, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Text, link, and image checks run on-device. Captured screen text and audio transcripts are not stored. No content is uploaded unless you configure a backend.",
                        color = GuardRailColors.BrutalSecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                    BrutalToggleCard(
                        title = "Cloud Text Analysis",
                        subtitle = "When ON, OCR-derived text from your screen is sent to the Muse Spark backend for AI safety review. When OFF (default), all checks stay on-device.",
                        checked = cloudTextAnalysisEnabled,
                        onCheckedChange = { onCloudTextAnalysisChange?.invoke(it) }
                    )
                    BrutalToggleCard(
                        title = "Cloud Image Analysis",
                        subtitle = "When ON, images may be sent for cloud multimodal review. OFF by default — no images leave device. Independent from Cloud Text Analysis.",
                        checked = cloudImageAnalysisEnabled,
                        onCheckedChange = { onCloudImageAnalysisChange?.invoke(it) }
                    )
                }
            }

            // Privacy and Data Controls
            GuardRailSectionHeader(title = "Privacy and Data Controls")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "Export & share activity", color = InkBlack, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Exports contain user-derived text and safety labels from your activity history. Only share with someone you trust.",
                        color = GuardRailColors.BrutalSecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 16.sp
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BrutalSecondaryButton(text = "Export JSON", onClick = { exportLauncher.launch(ReportExporter.suggestedFileName()) }, modifier = Modifier.weight(1f))
                        BrutalPrimaryButton(text = "Share report", onClick = { confirmShareSettings = true }, modifier = Modifier.weight(1f))
                    }
                    if (activityEvents.isNotEmpty()) {
                        Text(
                            text = "${activityEvents.size} event(s) available for export.",
                            color = InkBlack,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Data management
            GuardRailSectionHeader(title = "Data management")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "Activity history is stored locally (max 50 events).", color = GuardRailColors.BrutalSecondaryText, style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp)
                    if (onClearActivity != null) {
                        BrutalSecondaryButton(text = "Clear activity", onClick = onClearActivity, modifier = Modifier.fillMaxWidth())
                    } else {
                        Text(text = "Clear activity from the Activity tab.", color = GuardRailColors.BrutalMutedText, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Diagnostics
            GuardRailSectionHeader(title = "Diagnostics")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Live protection diagnostics", color = InkBlack, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    InfoRow(label = "Message", value = protectionStatus.message)
                    InfoRow(label = "Finding", value = protectionStatus.finding ?: "None")
                    InfoRow(label = "Source", value = protectionStatus.source ?: "—")
                    InfoRow(label = "App version", value = appVersion)
                    InfoRow(label = "Compile SDK", value = compileSdk.toString())
                }
            }

            // About
            GuardRailSectionHeader(title = "About")
            GuardRailCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Meta Guard Rail", color = InkBlack, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    InfoRow(label = "Package", value = packageName)
                    InfoRow(label = "Version", value = appVersion)
                    InfoRow(label = "Build", value = "compileSdk $compileSdk • minSdk 24 • targetSdk 37")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MonospaceBg)
                            .border(2.dp, InkBlack, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Open-source licenses and third-party notices are available in the app's legal notices (see Settings > About on your device, or check the app's open_source_licenses.html if bundled).",
                            color = GuardRailColors.BrutalMutedText,
                            style = MaterialTheme.typography.labelSmall,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
    if (confirmShareSettings) {
        AlertDialog(
            onDismissRequest = { confirmShareSettings = false },
            title = { Text("Share activity report?", fontWeight = FontWeight.ExtraBold, color = InkBlack) },
            text = { Text("This report contains user-derived text and safety labels from your activity history. Only share with someone you trust.", style = MaterialTheme.typography.bodySmall, lineHeight = 18.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmShareSettings = false
                    try {
                        val json = ReportExporter.buildReportJson(activityEvents)
                        val file = ReportExporter.prepareShareCacheFile(context, json)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share report"))
                    } catch (_: Exception) {}
                }) { Text("Share", color = ElectricBlue, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmShareSettings = false }) { Text("Cancel", color = InkBlack) } }
        )
    }
}

@Composable
private fun SettingsRowWithAction(title: String, subtitle: String, statusOk: Boolean, actionLabel: String, onAction: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = title, color = InkBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                StatusDot(ok = statusOk)
            }
            Text(text = subtitle, color = GuardRailColors.BrutalSecondaryText, fontSize = 11.sp, lineHeight = 14.sp)
        }
        BrutalSecondaryButton(text = actionLabel, onClick = onAction, modifier = Modifier.width(96.dp), small = true)
    }
}

@Composable
private fun StatusDot(ok: Boolean) {
    val color = if (ok) GuardRailColors.Safe else GuardRailColors.ProtectionInactive
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
            .border(1.dp, InkBlack, RoundedCornerShape(2.dp))
    )
}

@Composable
private fun StatusDotRow(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (ok) GuardRailColors.Safe else GuardRailColors.ProtectionInactive)
                .border(1.dp, InkBlack, RoundedCornerShape(2.dp))
        )
        Text(text = label, color = InkBlack, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MonospaceBg)
            .border(1.dp, InkBlack, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = GuardRailColors.BrutalSecondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(text = value, color = InkBlack, fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun rememberPackageName(): String { val ctx = LocalContext.current; return ctx.packageName }

@Composable
private fun rememberAppVersion(): String {
    val ctx = LocalContext.current
    return try {
        val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.PackageInfoFlags.of(0))
        else { @Suppress("DEPRECATION") ctx.packageManager.getPackageInfo(ctx.packageName, 0) }
        pi.versionName ?: BuildConfig.VERSION_NAME ?: "1.0"
    } catch (_: Exception) { BuildConfig.VERSION_NAME ?: "1.0" }
}

@Composable
private fun rememberNotificationGranted(): Boolean {
    val ctx = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
}

@Composable
private fun rememberOverlayGranted(): Boolean { val ctx = LocalContext.current; return android.provider.Settings.canDrawOverlays(ctx) }

@Composable
private fun rememberUsageGranted(): Boolean {
    val ctx = LocalContext.current
    return try {
        val appOps = ctx.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        @Suppress("DEPRECATION") appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName) == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { false }
}

@Preview(showBackground = true, name = "SettingsScreen Light", widthDp = 400, heightDp = 900)
@Composable
private fun PreviewSettingsScreen() {
    com.example.metaguardrail.ui.theme.MetaGuardRailTheme(themeMode = com.example.metaguardrail.ui.theme.AppThemeMode.LIGHT) {
        SettingsScreen(themeMode = com.example.metaguardrail.data.ThemeMode.LIGHT, onThemeChange = {}, settings = com.example.metaguardrail.data.SafetySettings(), protectionStatus = com.example.metaguardrail.monitor.LiveProtectionStatus(), onManageNotificationPermission = {}, onManageOverlayPermission = {}, onManageUsagePermission = {})
    }
}
