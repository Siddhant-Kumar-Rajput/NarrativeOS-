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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
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
    var clientToken by remember {
        mutableStateOf(preferences.getString("client_token", "") ?: "")
    }
    var firebaseWebApiKey by remember {
        mutableStateOf(
            preferences.getString("firebase_web_api_key", context.getString(R.string.google_api_key))
                ?: context.getString(R.string.google_api_key),
        )
    }
    var googleWebClientId by remember {
        mutableStateOf(
            preferences.getString("google_web_client_id", context.getString(R.string.default_web_client_id))
                ?: context.getString(R.string.default_web_client_id),
        )
    }
    var firebaseSession by remember { mutableStateOf<FirebaseSession?>(null) }
    var authEmail by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<AnalysisReport>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }
    var isLoadingHistory by remember { mutableStateOf(false) }
    var historyMessage by remember { mutableStateOf<String?>(null) }
    var showBackendSettings by remember { mutableStateOf(false) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<AnalysisReport?>(null) }
    val credentialManager = remember(context) { CredentialManager.create(context) }

    fun authenticate(createAccount: Boolean) {
        if (firebaseWebApiKey.isBlank() || authEmail.isBlank() || authPassword.isBlank()) {
            authMessage = "Enter the Firebase Web API key, email, and password."
            return
        }
        isAuthenticating = true
        authMessage = null
        coroutineScope.launch {
            try {
                firebaseSession = FirebaseAuthClient.authenticate(
                    firebaseWebApiKey,
                    authEmail,
                    authPassword,
                    createAccount,
                )
                authPassword = ""
                authMessage = "Signed in. Reports will be saved to your Firestore history."
            } catch (error: Exception) {
                authMessage = error.message ?: "Firebase sign-in failed."
            } finally {
                isAuthenticating = false
            }
        }
    }

    fun authenticateWithGoogle() {
        if (firebaseWebApiKey.isBlank() || googleWebClientId.isBlank()) {
            authMessage = "Set the Firebase Web API key and Web OAuth client ID in Server settings."
            return
        }
        isAuthenticating = true
        authMessage = null
        coroutineScope.launch {
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(googleWebClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .setAutoSelectEnabled(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential !is CustomCredential ||
                    credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    throw IllegalStateException("Google did not return an ID token credential.")
                }
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                firebaseSession = FirebaseAuthClient.authenticateWithGoogle(firebaseWebApiKey, googleIdToken)
                authMessage = "Signed in with Google. Reports will be saved to your Firestore history."
            } catch (error: Exception) {
                authMessage = error.message ?: "Google sign-in failed. Check Firebase Android app setup."
            } finally {
                isAuthenticating = false
            }
        }
    }

    fun loadHistory() {
        val currentSession = firebaseSession ?: return
        if (isLoadingHistory) return
        isLoadingHistory = true
        historyMessage = null
        coroutineScope.launch {
            try {
                val activeSession = FirebaseAuthClient.refreshIfNeeded(firebaseWebApiKey, currentSession)
                firebaseSession = activeSession
                history = AnalysisApiClient.loadHistory(backendUrl, clientToken, activeSession.idToken)
                showHistory = true
            } catch (error: Exception) {
                historyMessage = error.message ?: "Could not load Firestore history."
            } finally {
                isLoadingHistory = false
            }
        }
    }

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

            if (showBackendSettings) {
                OutlinedTextField(
                    value = backendUrl,
                    onValueChange = {
                        backendUrl = it
                        preferences.edit().putString("backend_url", it).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Backend URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = firebaseWebApiKey,
                    onValueChange = {
                        firebaseWebApiKey = it
                        preferences.edit().putString("firebase_web_api_key", it).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Firebase Web API key") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = googleWebClientId,
                    onValueChange = {
                        googleWebClientId = it
                        preferences.edit().putString("google_web_client_id", it).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Google Web OAuth client ID") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = clientToken,
                    onValueChange = {
                        clientToken = it
                        preferences.edit().putString("client_token", it).apply()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API access token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(8.dp),
                )
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val currentSession = firebaseSession
                if (currentSession == null) {
                    Text("Firebase account", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = authEmail,
                        onValueChange = { authEmail = it; authMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(8.dp),
                    )
                    OutlinedTextField(
                        value = authPassword,
                        onValueChange = { authPassword = it; authMessage = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { authenticate(createAccount = false) },
                            enabled = !isAuthenticating,
                            colors = ButtonDefaults.buttonColors(containerColor = Leaf),
                        ) {
                            Text(if (isAuthenticating) "Please wait" else "Sign in")
                        }
                        OutlinedButton(
                            onClick = { authenticate(createAccount = true) },
                            enabled = !isAuthenticating,
                        ) {
                            Text("Create account")
                        }
                    }
                    OutlinedButton(
                        onClick = { authenticateWithGoogle() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAuthenticating,
                    ) {
                        Text("Sign in with Google")
                    }
                } else {
                    Text("Signed in as ${currentSession.email}", color = Ink, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { loadHistory() }) {
                            Text(if (isLoadingHistory) "Loading" else "History", color = Leaf)
                        }
                        TextButton(onClick = {
                            firebaseSession = null
                            history = emptyList()
                            showHistory = false
                            authMessage = null
                        }) {
                            Text("Sign out", color = Leaf)
                        }
                    }
                }
                authMessage?.let { Text(it, color = Muted, fontSize = 13.sp, lineHeight = 18.sp) }
            }

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
                        val currentSession = firebaseSession
                        if (currentSession == null) {
                            message = "Sign in with Firebase before analyzing content."
                            return@Button
                        }
                        isAnalyzing = true
                        report = null
                        message = null
                        coroutineScope.launch {
                            try {
                                val activeSession = FirebaseAuthClient.refreshIfNeeded(
                                    firebaseWebApiKey,
                                    currentSession,
                                )
                                firebaseSession = activeSession
                                report = AnalysisApiClient.analyze(
                                    backendUrl,
                                    inputType,
                                    content.trim(),
                                    clientToken,
                                    activeSession.idToken,
                                )
                                history = AnalysisApiClient.loadHistory(
                                    backendUrl,
                                    clientToken,
                                    activeSession.idToken,
                                )
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
                    enabled = !isAnalyzing && firebaseSession != null,
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

            if (showHistory) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Saved reports", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    if (history.isEmpty()) Text("No saved reports yet.", color = Muted, fontSize = 14.sp)
                    history.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.createdAt ?: "Saved report", color = Muted, fontSize = 12.sp)
                            Text(item.overview, color = Ink, fontSize = 14.sp, lineHeight = 20.sp)
                            TextButton(onClick = {
                                val activeSession = firebaseSession ?: return@TextButton
                                val analysisId = item.analysisId ?: return@TextButton
                                coroutineScope.launch {
                                    try {
                                        val refreshedSession = FirebaseAuthClient.refreshIfNeeded(
                                            firebaseWebApiKey,
                                            activeSession,
                                        )
                                        firebaseSession = refreshedSession
                                        AnalysisApiClient.deleteHistoryItem(
                                            backendUrl,
                                            analysisId,
                                            clientToken,
                                            refreshedSession.idToken,
                                        )
                                        history = history.filterNot { it.analysisId == analysisId }
                                        historyMessage = null
                                    } catch (error: Exception) {
                                        historyMessage = error.message ?: "Could not delete this report."
                                    }
                                }
                            }) {
                                Text("Delete report", color = Leaf)
                            }
                        }
                    }
                    historyMessage?.let { Text(it, color = Muted, fontSize = 13.sp) }
                }
            }

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
        if (report.analysisId != null) {
            Text("Saved to your Firestore history", color = Leaf, fontSize = 12.sp)
        }
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