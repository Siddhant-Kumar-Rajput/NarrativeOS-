package com.narrativeos

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private val Ink = Color(0xFF172522)
private val Leaf = Color(0xFF245B4A)
private val Canvas = Color(0xFFF4F3EC)
private val Muted = Color(0xFF64716B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NarrativeHome(onStartBubble = ::startBubble)
            }
        }
    }

    private fun startBubble() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, OverlayBubbleService::class.java),
        )
    }
}

@Composable
private fun NarrativeHome(onStartBubble: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("narrativeos", Context.MODE_PRIVATE)
    }
    val coroutineScope = rememberCoroutineScope()
    var inputType by remember { mutableStateOf("URL") }
    var content by remember { mutableStateOf("") }
    var backendUrl by remember {
        mutableStateOf(preferences.getString("backend_url", BuildConfig.BACKEND_URL) ?: BuildConfig.BACKEND_URL)
    }
    var showBackendSettings by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<AnalysisReport?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas)
                .padding(horizontal = 24.dp, vertical = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "NARRATIVEOS",
                    color = Leaf,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showBackendSettings = !showBackendSettings }) {
                        Text("Server", color = Leaf)
                    }
                    TextButton(onClick = onStartBubble) {
                        Text("Bubble", color = Leaf, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "Pause the scroll.\nCheck the claim.",
                color = Ink,
                fontSize = 34.sp,
                lineHeight = 39.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Bring a post or claim. Get evidence, context, and a clear account of what is still unknown.",
                color = Muted,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Analyze content", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = inputType == "URL",
                        onClick = {
                            inputType = "URL"
                            report = null
                            message = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text("URL") },
                    )
                    SegmentedButton(
                        selected = inputType == "TEXT",
                        onClick = {
                            inputType = "TEXT"
                            report = null
                            message = null
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text("Paste text") },
                    )
                }
                OutlinedTextField(
                    value = content,
                    onValueChange = {
                        content = it
                        message = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (inputType == "URL") "Public post URL" else "Text containing a claim") },
                    placeholder = {
                        Text(if (inputType == "URL") "instagram.com/... or x.com/..." else "Paste the post text to analyze")
                    },
                    singleLine = inputType == "URL",
                    minLines = if (inputType == "URL") 1 else 4,
                    maxLines = if (inputType == "URL") 1 else 8,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (inputType == "URL") KeyboardType.Uri else KeyboardType.Text,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )
                Button(
                    onClick = {
                        if (content.isBlank()) {
                            message = "Enter a public post URL or text to analyze."
                            return@Button
                        }
                        isAnalyzing = true
                        report = null
                        message = null
                        coroutineScope.launch {
                            try {
                                report = AnalysisApiClient.analyze(backendUrl, inputType, content.trim())
                            } catch (error: Exception) {
                                message = error.message ?: "Could not reach the analysis service."
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isAnalyzing,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Analyze", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                message?.let { text ->
                    Text(text = text, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }

            report?.let { analysis -> AnalysisResult(report = analysis) }

            Text(
                text = "NarrativeOS shows what sources support, what they challenge, and where evidence runs out.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }
    }
}

@Composable
private fun AnalysisResult(report: AnalysisReport) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = when (report.retrievalStatus) {
                "FULL" -> "Evidence report"
                "PARTIAL" -> "Partial report"
                "UNSUPPORTED" -> "Input not supported"
                else -> "Content unavailable"
            },
            color = Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(report.overview, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
        report.claims.forEach { claim ->
            Text(claim.originalClaim, color = Ink, fontWeight = FontWeight.SemiBold)
            Text("${claim.verdict.replace('_', ' ')} · ${claim.category.replace('_', ' ')}", color = Leaf)
            Text(claim.reasoning, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            Text(claim.confidenceExplanation, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            claim.citations.forEach { citation ->
                Text("${citation.title}: ${citation.url}", color = Leaf, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        report.limitations.forEach { limitation ->
            Text(limitation, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
private fun validatePostUrl(value: String): String {
    val uri = runCatching { Uri.parse(value.trim()) }.getOrNull()
        ?: return "Enter a valid Instagram or X post URL."
    val host = uri.host?.lowercase()?.removePrefix("www.")
    val supportedHost = host in setOf("instagram.com", "x.com", "twitter.com")
    if (uri.scheme !in setOf("https", "http") || !supportedHost) {
        return "Use a public Instagram or X URL. Other platforms are not supported yet."
    }
    if (uri.path.isNullOrBlank() || uri.path == "/") {
        return "That looks like a platform homepage, not a post."
    }
    return "URL accepted. Evidence analysis will be enabled when the backend is connected."
}