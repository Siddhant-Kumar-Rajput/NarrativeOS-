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
)

object AnalysisApiClient {
    suspend fun analyze(
        backendUrl: String,
        inputType: String,
        content: String,
    ): AnalysisReport = withContext(Dispatchers.IO) {
        val endpoint = backendUrl.trimEnd('/') + "/api/v1/analyze"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val request = JSONObject()
                .put("input_type", inputType)
                .put("content", content)
            connection.outputStream.use { stream ->
                stream.write(request.toString().toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (statusCode !in 200..299) {
                val detail = runCatching {
                    JSONObject(responseText).optString("detail")
                }.getOrNull().orEmpty()
                throw IOException(detail.ifBlank { "Analysis service returned HTTP $statusCode." })
            }
            parseReport(JSONObject(responseText))
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
        )
    }
}