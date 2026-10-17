package com.narrativeos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AnalysisCitation(
    val title: String,
    val url: String,
    val evidenceSummary: String,
)

data class ClaimFinding(
    val originalClaim: String,
    val category: String,
    val verdict: String,
    val reasoning: String,
    val confidenceExplanation: String,
    val citations: List<AnalysisCitation>,
)

data class AnalysisReport(
    val retrievalStatus: String,
    val overview: String,
    val claims: List<ClaimFinding>,
    val limitations: List<String>,
    val analysisId: String? = null,
    val createdAt: String? = null,
)

object AnalysisApiClient {
    suspend fun analyze(
        backendUrl: String,
        inputType: String,
        content: String,
        clientToken: String,
        firebaseIdToken: String,
    ): AnalysisReport = withContext(Dispatchers.IO) {
        val body = JSONObject().put("input_type", inputType).put("content", content)
        parseReport(
            JSONObject(
                execute(
                    backendUrl = backendUrl,
                    path = "/api/v1/analyze",
                    method = "POST",
                    clientToken = clientToken,
                    firebaseIdToken = firebaseIdToken,
                    body = body.toString(),
                ),
            ),
        )
    }

    suspend fun loadHistory(
        backendUrl: String,
        clientToken: String,
        firebaseIdToken: String,
    ): List<AnalysisReport> = withContext(Dispatchers.IO) {
        val response = JSONArray(
            execute(
                backendUrl = backendUrl,
                path = "/api/v1/history",
                method = "GET",
                clientToken = clientToken,
                firebaseIdToken = firebaseIdToken,
            ),
        )
        buildList {
            for (index in 0 until response.length()) {
                response.optJSONObject(index)?.let { add(parseReport(it)) }
            }
        }
    }

    suspend fun deleteHistoryItem(
        backendUrl: String,
        analysisId: String,
        clientToken: String,
        firebaseIdToken: String,
    ) = withContext(Dispatchers.IO) {
        val encodedId = java.net.URLEncoder.encode(analysisId, Charsets.UTF_8.name())
        execute(
            backendUrl = backendUrl,
            path = "/api/v1/history/$encodedId",
            method = "DELETE",
            clientToken = clientToken,
            firebaseIdToken = firebaseIdToken,
        )
    }

    private fun execute(
        backendUrl: String,
        path: String,
        method: String,
        clientToken: String,
        firebaseIdToken: String,
        body: String? = null,
    ): String {
        val connection = (URL(backendUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 90_000
            setRequestProperty("Accept", "application/json")
            if (clientToken.isNotBlank()) setRequestProperty("X-NarrativeOS-Token", clientToken)
            if (firebaseIdToken.isNotBlank()) setRequestProperty("Authorization", "Bearer $firebaseIdToken")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            body?.let { requestBody ->
                connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            }
            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                val detail = runCatching { JSONObject(responseText).optString("detail") }.getOrNull().orEmpty()
                throw IOException(detail.ifBlank { "Analysis service returned HTTP $statusCode." })
            }
            return responseText
        } finally {
            connection.disconnect()
        }
    }

    private fun parseReport(json: JSONObject): AnalysisReport {
        val claimsJson = json.optJSONArray("claims") ?: JSONArray()
        val claims = buildList {
            for (claimIndex in 0 until claimsJson.length()) {
                val claimJson = claimsJson.optJSONObject(claimIndex) ?: continue
                val citationsJson = claimJson.optJSONArray("citations") ?: JSONArray()
                val citations = buildList {
                    for (citationIndex in 0 until citationsJson.length()) {
                        val citationJson = citationsJson.optJSONObject(citationIndex) ?: continue
                        add(
                            AnalysisCitation(
                                title = citationJson.optString("title"),
                                url = citationJson.optString("url"),
                                evidenceSummary = citationJson.optString("evidence_summary"),
                            ),
                        )
                    }
                }
                add(
                    ClaimFinding(
                        originalClaim = claimJson.optString("original_claim"),
                        category = claimJson.optString("category"),
                        verdict = claimJson.optString("verdict"),
                        reasoning = claimJson.optString("reasoning"),
                        confidenceExplanation = claimJson.optString("confidence_explanation"),
                        citations = citations,
                    ),
                )
            }
        }
        val limitationsJson = json.optJSONArray("limitations") ?: JSONArray()
        val limitations = buildList {
            for (index in 0 until limitationsJson.length()) {
                limitationsJson.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
        return AnalysisReport(
            retrievalStatus = json.optString("retrieval_status"),
            overview = json.optString("overview"),
            claims = claims,
            limitations = limitations,
            analysisId = json.optString("analysis_id").takeIf(String::isNotBlank),
            createdAt = json.optString("created_at").takeIf(String::isNotBlank),
        )
    }
}