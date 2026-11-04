package com.example.metaguardrail.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.metaguardrail.monitor.VisualContentClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Card for on-device image safety check. Lets the user pick an image from the system
 * photo picker, previews it with Coil (handles large images / downsampling / caching),
 * and runs [VisualContentClassifier.possibleAdultImage] off the main thread.
 *
 * Limitations are surfaced in the result text: the classifier is local, best-effort,
 * may produce false positives/negatives, and does not cover violence.
 */
@Composable
fun ImageSafetyCard(
    visualClassifier: VisualContentClassifier?,
    onResult: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var reportedText by remember { mutableStateOf<String?>(null) }
    var imageLoadError by remember { mutableStateOf<String?>(null) }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            pickedUri = uri
            resultText = null
            reportedText = null
            imageLoadError = null
            if (uri == null) {
                // User cancelled picker — no error needed.
            }
        }
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151D26))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Image safety check",
                color = Color(0xFFF1F4F7),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Pick an image from your device. Images are checked on-device and are not uploaded. Large images are handled automatically.",
                color = Color(0xFF9AA6B2),
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp
            )

            // Pick button
            Button(
                onClick = {
                    pickMediaLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8DD8D0))
            ) {
                Text(
                    text = if (pickedUri == null) "Pick image" else "Pick different image",
                    color = Color(0xFF0C1117),
                    fontWeight = FontWeight.Bold
                )
            }

            // Preview — Coil handles large images, downsampling and caching off main thread.
            if (pickedUri != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 380.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0C1117)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = pickedUri,
                        contentDescription = "Selected image preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 380.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit,
                        onError = {
                            imageLoadError = "Could not load image. It may be corrupt or in an unsupported format."
                        },
                        onSuccess = {
                            imageLoadError = null
                        }
                    )
                }

                if (imageLoadError != null) {
                    Text(
                        text = imageLoadError!!,
                        color = Color(0xFFFFB4B4),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp
                    )
                }

                // Analyze button — runs classifier on IO dispatcher, never on main thread.
                Button(
                    onClick = {
                        val uri = pickedUri ?: return@Button
                        if (visualClassifier == null) {
                            resultText = "Image classifier is not available on this device."
                            onResult("classifier_unavailable")
                            return@Button
                        }
                        if (imageLoadError != null) {
                            resultText = "Cannot analyze: $imageLoadError"
                            onResult("load_error")
                            return@Button
                        }
                        isAnalyzing = true
                        reportedText = null
                        scope.launch {
                            val outcome: String
                            try {
                                // Decode bitmap off main thread. Corrupt / oversized images are caught.
                                val bitmap: Bitmap? = withContext(Dispatchers.IO) {
                                    decodeBitmapForAnalysis(context, uri)
                                }
                                if (bitmap == null) {
                                    outcome = "Could not decode image. It may be corrupt or too large."
                                    resultText = outcome
                                    onResult("decode_failed")
                                    return@launch
                                }
                                try {
                                    val flagged = withContext(Dispatchers.IO) {
                                        visualClassifier.possibleAdultImage(bitmap)
                                    }
                                    outcome = if (flagged) {
                                        "Possible adult or suggestive imagery detected."
                                    } else {
                                        "No adult imagery pattern detected."
                                    }
                                    // Append limitation disclaimer to every result.
                                    resultText = outcome + "\n\nThis is a local, best-effort check. It can be wrong (false positives/negatives) and does not detect violence. Review context before acting."
                                    onResult(if (flagged) "flagged" else "not_flagged")
                                } finally {
                                    // possibleAdultImage copies pixels; we can recycle after call.
                                    // Do not recycle if classifier keeps reference (it doesn't).
                                    // Safe to recycle to free memory for large images.
                                    try { bitmap.recycle() } catch (_: Exception) { }
                                }
                            } catch (e: OutOfMemoryError) {
                                resultText = "Image is too large to analyze on this device."
                                onResult("oom")
                            } catch (e: Exception) {
                                resultText = "Analysis failed: ${e.message ?: "unknown error"}"
                                onResult("error")
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    enabled = !isAnalyzing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF8DD8D0),
                        disabledContainerColor = Color(0xFF293541)
                    )
                ) {
                    Text(
                        text = if (isAnalyzing) "Analyzing…" else "Analyze image",
                        color = if (isAnalyzing) Color(0xFF9AA6B2) else Color(0xFF0C1117),
                        fontWeight = FontWeight.Bold
                    )
                }

                if (resultText != null) {
                    // Result block
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C1117))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Result",
                            color = Color(0xFFE8EDF1),
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = resultText!!,
                            color = Color(0xFFB4C0CB),
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                resultText = null
                                reportedText = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dismiss", color = Color(0xFFE8EDF1))
                        }
                        Button(
                            onClick = {
                                // Local-only report — no network call.
                                reportedText = "Reported locally"
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF293541))
                        ) {
                            Text("Report false positive", color = Color(0xFFE8EDF1), fontSize = 12.sp)
                        }
                    }

                    if (reportedText != null) {
                        // Lightweight Snackbar-like text (no Scaffold SnackbarHost required to keep card self-contained).
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1B3A32))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = reportedText!!,
                                color = Color(0xFF8DD8D0),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "No image selected yet.",
                    color = Color(0xFF6B7A8A),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Tip: This check runs fully on-device. Nothing is uploaded.",
                color = Color(0xFF6B7A8A),
                style = MaterialTheme.typography.labelSmall
            )
            CloudViolenceSection(pickedUri = pickedUri)
        }
    }
}

@Composable
private fun CloudViolenceSection(pickedUri: android.net.Uri?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var prefs by remember { mutableStateOf<com.example.metaguardrail.data.UserPreferencesRepository?>(null) }
    var consent by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<com.example.metaguardrail.ai.VisualViolenceAssessment?>(null) }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val r = com.example.metaguardrail.data.UserPreferencesRepository(ctx.applicationContext)
        prefs = r
        r.cloudImageAnalysisEnabled.collect { consent = it }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "Cloud visual violence (LG4 via NVIDIA, temporary)", color = Color(0xFFE8EDF1), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        Text(text = "OFF by default. When ON, your picked image is sent to NVIDIA's hosted trial (may be logged by provider). Use only non-sensitive public/synthetic images. Not private or zero-retention.", color = Color(0xFFFFB4B4), style = MaterialTheme.typography.labelSmall, lineHeight = 14.sp)
        if (!consent) {
            Text(text = "Cloud image analysis is OFF — enable in Settings > Privacy to allow one image upload.", color = Color(0xFF9AA6B2), style = MaterialTheme.typography.bodySmall)
        } else {
            Button(onClick = {
                if (pickedUri == null || busy) return@Button
                busy = true; state = null
                scope.launch {
                    val res = com.example.metaguardrail.ai.CloudVisualViolenceClassifier.assess(ctx, pickedUri) { consent }
                    state = res; busy = false
                }
            }, enabled = !busy && pickedUri != null, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7A8A))) {
                Text(if (busy) "Checking cloud…" else "Check visual violence (cloud)", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            state?.let { s ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF0C1117)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Cloud result: ${s.status}", color = Color(0xFFE8EDF1), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text("Provider: ${s.provider} • ${s.modelName} • ${s.classificationMethod}", color = Color(0xFF9AA6B2), style = MaterialTheme.typography.labelSmall)
                    s.sanitizedExplanation?.let { Text(it, color = Color(0xFFB4C0CB), style = MaterialTheme.typography.bodySmall, lineHeight = 16.sp) }
                    s.errorCode?.let { Text("Error: $it", color = Color(0xFFFFB4B4), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

/**
 * Decodes a bitmap from [uri] suitable for [VisualContentClassifier].
 * Handles corrupt images (returns null) and attempts to avoid OOM by
 * downsampling if the image exceeds a safe pixel count.
 * Must be called off the main thread.
 */
private fun decodeBitmapForAnalysis(context: android.content.Context, uri: Uri): Bitmap? {
    // First, get dimensions without allocating pixels.
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOpts)
        }
    } catch (_: Exception) {
        return null
    }
    val width = boundsOpts.outWidth
    val height = boundsOpts.outHeight
    if (width <= 0 || height <= 0) return null

    // Cap decoded size to limit memory: VisualContentClassifier crops to 224x224 anyway,
    // so decoding a 4000px image at full res is wasteful. Target ~ 1024px max side.
    val maxSide = 1024
    val maxDim = maxOf(width, height)
    var sampleSize = 1
    while (maxDim / sampleSize > maxSide) sampleSize *= 2

    val decodeOpts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        // Use ARGB_8888 for classifier; classifier will crop/resize to 224.
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOpts)
        }
    } catch (_: Exception) {
        null
    }
}
