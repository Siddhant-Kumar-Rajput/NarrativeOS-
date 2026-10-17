package com.narrativeos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class FirebaseSession(
    val idToken: String,
    val refreshToken: String,
    val email: String,
    val userId: String,
    val expiresAtEpochSeconds: Long,
)

object FirebaseAuthClient {
    suspend fun authenticate(
        webApiKey: String,
        email: String,
        password: String,
        createAccount: Boolean,
    ): FirebaseSession = withContext(Dispatchers.IO) {
        require(webApiKey.isNotBlank()) { "Set the Firebase Web API key in Server settings first." }
        val endpoint = if (createAccount) "signUp" else "signInWithPassword"
        val url = "https://identitytoolkit.googleapis.com/v1/accounts:$endpoint?key=${encode(webApiKey)}"
        val response = requestJson(
            url = url,
            body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .put("returnSecureToken", true),
        )
        FirebaseSession(
            idToken = response.getString("idToken"),
            refreshToken = response.getString("refreshToken"),
            email = response.optString("email", email.trim()),
            userId = response.getString("localId"),
            expiresAtEpochSeconds = currentEpochSeconds() + response.optString("expiresIn", "3600").toLong(),
        )
    }

    suspend fun refreshIfNeeded(webApiKey: String, session: FirebaseSession): FirebaseSession {
        if (session.expiresAtEpochSeconds - currentEpochSeconds() > 60) return session
        return withContext(Dispatchers.IO) {
            val url = "https://securetoken.googleapis.com/v1/token?key=${encode(webApiKey)}"
            val body = "grant_type=refresh_token&refresh_token=${encode(session.refreshToken)}"
            val response = requestForm(url, body)
            session.copy(
                idToken = response.getString("id_token"),
                refreshToken = response.optString("refresh_token", session.refreshToken),
                expiresAtEpochSeconds = currentEpochSeconds() + response.optString("expires_in", "3600").toLong(),
            )
        }
    }

    private fun requestJson(url: String, body: JSONObject): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return execute(connection, body.toString().toByteArray(Charsets.UTF_8))
    }

    private fun requestForm(url: String, body: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("Accept", "application/json")
        }
        return execute(connection, body.toByteArray(Charsets.UTF_8))
    }

    private fun execute(connection: HttpURLConnection, body: ByteArray): JSONObject {
        try {
            connection.outputStream.use { it.write(body) }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = runCatching { JSONObject(responseBody) }.getOrElse { JSONObject() }
            if (statusCode !in 200..299) {
                val code = response.optJSONObject("error")?.optString("message").orEmpty()
                throw IOException(authErrorMessage(code, statusCode))
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun authErrorMessage(code: String, statusCode: Int): String = when (code.substringBefore(':')) {
        "EMAIL_EXISTS" -> "An account already exists for this email. Sign in instead."
        "EMAIL_NOT_FOUND", "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" -> "Email or password is incorrect."
        "WEAK_PASSWORD" -> "Choose a password with at least 6 characters."
        "OPERATION_NOT_ALLOWED" -> "Enable Email/Password sign-in in Firebase Authentication settings."
        "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Too many attempts. Try again later."
        else -> "Firebase sign-in failed (HTTP $statusCode). Check the Firebase project settings."
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000
}