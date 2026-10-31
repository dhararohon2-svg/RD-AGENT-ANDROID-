package com.example.gemini.ui.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class ApiProfile(val id: String, val name: String, val baseUrl: String, val model: String, val key: String = "")

internal object ApiProfileVault {
    private const val PREFS = "rda_api_profiles"
    private const val LIST = "profiles"
    private const val ACTIVE_PROFILE = "active_profile_id"
    private const val KEY_ALIAS = "rda_coding_api_v1"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        return gen.generateKey()
    }
    private fun encrypt(value: String): String {
        if (value.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        return android.util.Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), android.util.Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String = try {
        if (value.isBlank()) "" else {
            val bytes = android.util.Base64.decode(value, android.util.Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }
    } catch (_: Exception) { "" }

    fun load(c: Context): List<ApiProfile> = try {
        val arr = JSONArray(prefs(c).getString(LIST, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ApiProfile(o.optString("id"), o.optString("name"), o.optString("url"), o.optString("model"), decrypt(o.optString("secret")))
        }
    } catch (_: Exception) { emptyList() }
    fun activeProfile(c: Context): ApiProfile? {
        val id = prefs(c).getString(ACTIVE_PROFILE, null) ?: return null
        return load(c).firstOrNull { it.id == id }
    }
    fun setActiveProfile(c: Context, id: String?) {
        prefs(c).edit().apply { if (id.isNullOrBlank()) remove(ACTIVE_PROFILE) else putString(ACTIVE_PROFILE, id) }.apply()
    }
    fun save(c: Context, profiles: List<ApiProfile>) {
        val arr = JSONArray()
        profiles.forEach { p -> arr.put(JSONObject().put("id", p.id).put("name", p.name).put("url", p.baseUrl).put("model", p.model).put("secret", encrypt(p.key))) }
        prefs(c).edit().putString(LIST, arr.toString()).apply()
    }
}

private data class ProbeResult(val ok: Boolean, val message: String, val models: List<String> = emptyList())

/** Converts common provider homepages into API base URLs. Unknown hosts are treated as OpenAI-compatible only. */
private fun normalizeApiBaseUrl(raw: String): String {
    val value = raw.trim().trimEnd('/')
    if (value.isBlank()) return value
    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return value
    val host = uri.host?.lowercase().orEmpty()
    val path = uri.path.orEmpty().trimEnd('/')
    if (path.endsWith("/v1") || path.endsWith("/api/v1") || path.endsWith("/openai/v1")) return value
    val mapped = when {
        host == "openrouter.ai" || host == "www.openrouter.ai" -> "https://openrouter.ai/api/v1"
        host == "openai.com" || host == "www.openai.com" -> "https://api.openai.com/v1"
        host == "api.openai.com" -> "https://api.openai.com/v1"
        host == "groq.com" || host == "www.groq.com" -> "https://api.groq.com/openai/v1"
        host == "api.groq.com" -> "https://api.groq.com/openai/v1"
        host == "deepseek.com" || host == "www.deepseek.com" -> "https://api.deepseek.com/v1"
        host == "api.deepseek.com" -> "https://api.deepseek.com/v1"
        host == "together.ai" || host == "www.together.ai" -> "https://api.together.xyz/v1"
        host == "api.together.xyz" -> "https://api.together.xyz/v1"
        host == "mistral.ai" || host == "www.mistral.ai" -> "https://api.mistral.ai/v1"
        host == "api.mistral.ai" -> "https://api.mistral.ai/v1"
        host == "fireworks.ai" || host == "www.fireworks.ai" -> "https://api.fireworks.ai/inference/v1"
        host == "api.fireworks.ai" -> "https://api.fireworks.ai/inference/v1"
        host == "api.cerebras.ai" || host == "cerebras.ai" || host == "www.cerebras.ai" -> "https://api.cerebras.ai/v1"
        else -> null
    }
    if (mapped != null) return mapped
    if (path.isBlank() || path == "/") return value + "/v1"
    return value
}

internal fun runApiChat(profile: ApiProfile, prompt: String): String {
    if (profile.model.isBlank()) return "Model ID দিন।"
    if (prompt.isBlank()) return "Prompt লিখুন।"
    val base = profile.baseUrl.trim().trimEnd('/')
    val endpoint = if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"; connectTimeout = 20000; readTimeout = 90000; doOutput = true
        setRequestProperty("Content-Type", "application/json")
        if (profile.key.isNotBlank()) setRequestProperty("Authorization", "Bearer ${profile.key}")
    }
    val payload = JSONObject().put("model", profile.model).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt))).put("stream", false)
    conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
    val code = conn.responseCode
    val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
    conn.disconnect()
    if (code !in 200..299) return "HTTP $code: ${body.take(1200)}"
    return try { JSONObject(body).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").ifBlank { body.take(3000) } }
    catch (_: Exception) { "Response parse করা যায়নি: ${body.take(1800)}" }
}

private fun probeApi(profile: ApiProfile): ProbeResult {
    if (profile.baseUrl.isBlank()) return ProbeResult(false, "API Base URL দিন।")
    val base = profile.baseUrl.trim().trimEnd('/')
    if (!base.startsWith("https://") && !base.startsWith("http://")) return ProbeResult(false, "URL অবশ্যই http:// বা https:// দিয়ে শুরু হতে হবে।")
    return try {
        val endpoint = if (base.endsWith("/v1")) "$base/models" else "$base/v1/models"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 12000; readTimeout = 12000
            if (profile.key.isNotBlank()) setRequestProperty("Authorization", "Bearer ${profile.key}")
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()
        if (code !in 200..299) ProbeResult(false, "HTTP $code: ${body.take(240)}") else {
            val data = JSONObject(body).optJSONArray("data")
            val models = if (data == null) emptyList() else (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
            ProbeResult(true, "সংযোগ সফল • ${models.size}টি model পাওয়া গেছে", models)
        }
    } catch (e: Exception) { ProbeResult(false, "সংযোগ ব্যর্থ: ${e.message ?: e.javaClass.simpleName}") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiProviderHubScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(ApiProfileVault.load(context)) }
    var editingId by remember { mutableStateOf<String?>(profiles.firstOrNull()?.id) }
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var models by remember { mutableStateOf(emptyList<String>()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var testPrompt by remember { mutableStateOf("Explain what this AI model can do.") }
    var testReply by remember { mutableStateOf("") }
    var activeProfileId by remember { mutableStateOf(ApiProfileVault.activeProfile(context)?.id) }
    fun loadProfile(p: ApiProfile?) { editingId = p?.id; name = p?.name.orEmpty(); baseUrl = p?.baseUrl.orEmpty(); apiKey = p?.key.orEmpty(); model = p?.model.orEmpty(); models = emptyList(); status = "" }
    LaunchedEffect(Unit) { profiles.firstOrNull()?.let { loadProfile(it) } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Universal API Provider Hub", style = MaterialTheme.typography.titleLarge)
        Text("OpenAI-compatible API Base URL + API Key + Model. Provider-এর homepage URL দিলে পরিচিত provider-গুলোর API URL auto-convert হবে; custom provider-এর ক্ষেত্রে API-compatible endpoint লাগতে পারে।", style = MaterialTheme.typography.bodySmall)
        Text(if (activeProfileId != null) "Main chat provider: ${profiles.firstOrNull { it.id == activeProfileId }?.name ?: "Unavailable"}" else "Main chat provider: built-in agent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (profiles.isNotEmpty()) {
            Text("Saved providers", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.heightIn(max = 130.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(profiles, key = { it.id }) { p ->
                    OutlinedButton(onClick = { loadProfile(p) }, modifier = Modifier.fillMaxWidth()) {
                        Text("${if (p.id == editingId) "✓ " else ""}${p.name}  •  ${p.model.ifBlank { "Model not set" }}", maxLines = 1)
                    }
                }
            }
        }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Provider name") }, placeholder = { Text("OpenRouter / Local / Custom") }, singleLine = true)
        OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text("API Base URL") }, placeholder = { Text("https://openrouter.ai/api/v1") }, singleLine = true)
        OutlinedTextField(apiKey, { apiKey = it }, Modifier.fillMaxWidth(), label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Model ID") }, placeholder = { Text("provider/model-name") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = editingId != null && profiles.any { it.id == editingId }, onClick = {
                val id = editingId ?: return@Button
                val saved = ApiProfile(id, name.ifBlank { "Custom Provider" }, normalizeApiBaseUrl(baseUrl), model.trim(), apiKey)
                baseUrl = saved.baseUrl
                profiles = (profiles.filterNot { it.id == id } + saved)
                ApiProfileVault.save(context, profiles)
                ApiProfileVault.setActiveProfile(context, id)
                activeProfileId = id
                status = "${saved.name} এখন Main Chat-এ নির্বাচিত। এই provider OpenAI-compatible chat API ব্যবহার করবে; টার্মিনাল/ফাইল টুল আলাদা agent integration-এর ওপর নির্ভর করে।"
            }) { Text("Use in Main Chat") }
            OutlinedButton(onClick = {
                ApiProfileVault.setActiveProfile(context, null)
                activeProfileId = null
                status = "Main Chat built-in agent-এ ফিরেছে।"
            }) { Text("Use built-in agent") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                busy = true; status = "Checking /models…"
                val normalized = normalizeApiBaseUrl(baseUrl)
                baseUrl = normalized
                val draft = ApiProfile(editingId ?: java.util.UUID.randomUUID().toString(), name.ifBlank { "Custom Provider" }, normalized, model, apiKey)
                scope.launch {
                    val result = withContext(Dispatchers.IO) { probeApi(draft) }
                    models = result.models; status = result.message; busy = false
                }
            }) { Icon(Icons.Outlined.WifiTethering, null); Spacer(Modifier.width(6.dp)); Text("Test / Load models") }
            OutlinedButton(onClick = {
                val id = editingId ?: java.util.UUID.randomUUID().toString()
                val normalized = normalizeApiBaseUrl(baseUrl)
                baseUrl = normalized
                val p = ApiProfile(id, name.ifBlank { "Custom Provider" }, normalized, model.trim(), apiKey)
                profiles = (profiles.filterNot { it.id == id } + p); ApiProfileVault.save(context, profiles); editingId = id; status = "Provider encrypted locally and saved."
            }) { Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(4.dp)); Text("Save") }
        }
        if (status.isNotBlank()) Text(status, color = if (status.contains("সফল") || status.contains("saved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (editingId != null || name.isNotBlank() || baseUrl.isNotBlank()) {
            HorizontalDivider()
            Text("Try this model", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(testPrompt, { testPrompt = it }, Modifier.fillMaxWidth(), label = { Text("Prompt") }, minLines = 2)
            Button(enabled = !busy, onClick = {
                busy = true; testReply = "Request চলছে…"
                val draft = ApiProfile(editingId ?: java.util.UUID.randomUUID().toString(), name, baseUrl, model, apiKey)
                scope.launch {
                    val reply = withContext(Dispatchers.IO) { try { runApiChat(draft, testPrompt) } catch (e: Exception) { "Request failed: ${e.message ?: e.javaClass.simpleName}" } }
                    testReply = reply; busy = false
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Send test prompt to selected model") }
            if (testReply.isNotBlank()) Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium) { Text(testReply, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
        }
        if (models.isNotEmpty()) {
            Text("Tap a model to select it", style = MaterialTheme.typography.labelLarge)
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 220.dp)) {
                items(models, key = { it }) { id -> TextButton(onClick = { model = id }) { Text(id) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { loadProfile(null) }) { Icon(Icons.Outlined.Api, null); Spacer(Modifier.width(4.dp)); Text("New provider") }
            OutlinedButton(enabled = editingId != null, onClick = {
                profiles = profiles.filterNot { it.id == editingId }; ApiProfileVault.save(context, profiles); if (activeProfileId == editingId) { ApiProfileVault.setActiveProfile(context, null); activeProfileId = null }; loadProfile(profiles.firstOrNull())
            }) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(4.dp)); Text("Delete") }
        }
        Text("সমর্থন: OpenAI-compatible API (/models এবং /chat/completions)। OpenRouter/OpenAI/Groq/DeepSeek/Together/Mistral/Fireworks/Cerebras-এর পরিচিত homepage URL auto-convert করা হয়। অন্য সাইটের homepage শুধু পেস্ট করলেই সব সময় কাজ করবে না—তার API protocol ও authentication সমর্থিত হতে হবে। Native Gemini/Anthropic এবং CLI coding agents-এর জন্য পৃথক adapter প্রয়োজন।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
