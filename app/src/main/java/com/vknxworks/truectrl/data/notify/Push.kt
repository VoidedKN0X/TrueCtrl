package com.vknxworks.truectrl.data.notify

import android.content.Context
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.TnJson
import com.vknxworks.truectrl.data.api.TnVersion
import com.vknxworks.truectrl.data.api.arr
import com.vknxworks.truectrl.data.api.get
import com.vknxworks.truectrl.data.api.long
import com.vknxworks.truectrl.data.api.str
import com.vknxworks.truectrl.data.overview.Severity
import com.vknxworks.truectrl.data.server.Connection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/** Where TrueCtrl's UnifiedPush registration stands. */
sealed interface PushStatus {
    data object Off : PushStatus
    data object NoDistributor : PushStatus
    data object Registering : PushStatus
    data class Registered(val endpoint: String) : PushStatus
    data class Failed(val reason: String) : PushStatus
}

/**
 * UnifiedPush: a distributor app like ntfy gives TrueCtrl an endpoint URL. TrueNAS has no ntfy or
 * webhook alert service, but its Slack alert service simply posts `{"text": "..."}` to any URL, so
 * a Slack-type alert service pointed at the endpoint delivers TrueNAS's alerts to [TruePushService].
 */
object Push {
    private const val PREFS = "push"
    private const val KEY_ENDPOINT = "endpoint"

    /** The name of the alert service TrueCtrl creates on TrueNAS. */
    const val SERVICE_NAME = "TrueCtrl"

    private val _status = MutableStateFlow<PushStatus>(PushStatus.Off)
    val status: StateFlow<PushStatus> = _status.asStateFlow()

    fun load(context: Context, enabled: Boolean) {
        val endpoint = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ENDPOINT, null)
        _status.value = when {
            !enabled -> PushStatus.Off
            UnifiedPush.getDistributors(context).isEmpty() -> PushStatus.NoDistributor
            endpoint != null -> PushStatus.Registered(endpoint)
            else -> PushStatus.Registering
        }
    }

    fun distributors(context: Context): List<String> = UnifiedPush.getDistributors(context)

    /** Registers with [distributor] (a package name, e.g. io.heckel.ntfy). */
    fun register(context: Context, distributor: String) {
        UnifiedPush.saveDistributor(context, distributor)
        _status.value = PushStatus.Registering
        UnifiedPush.register(context, messageForDistributor = context.getString(R.string.app_name))
    }

    fun unregister(context: Context) {
        runCatching { UnifiedPush.unregister(context) }
        runCatching { UnifiedPush.removeDistributor(context) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ENDPOINT).apply()
        _status.value = PushStatus.Off
    }

    internal fun onEndpoint(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ENDPOINT, url).apply()
        _status.value = PushStatus.Registered(url)
    }

    internal fun onFailed(reason: String) {
        _status.value = PushStatus.Failed(reason)
    }

    internal fun onUnregistered(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_ENDPOINT).apply()
        _status.value = PushStatus.NoDistributor
    }

    /**
     * The alert service to create on TrueNAS: type Slack, pointed at [endpoint], for alerts of
     * [level] and up. TrueNAS 25.10 moved the type into `attributes`.
     */
    private fun serviceBody(endpoint: String, level: String, version: TnVersion?) = buildJsonObject {
        put("name", SERVICE_NAME)
        if (version != null && !version.atLeast(25, 10)) {
            put("type", "Slack")
            putJsonObject("attributes") { put("url", endpoint) }
        } else {
            putJsonObject("attributes") {
                put("type", "Slack")
                put("url", endpoint)
            }
        }
        put("level", level)
        put("enabled", true)
    }

    /** TrueNAS alert levels for [Severity]. */
    fun levelFor(s: Severity) = when (s) {
        Severity.CRITICAL -> "ERROR"
        Severity.WARNING -> "WARNING"
        Severity.INFO -> "INFO"
    }

    /**
     * Creates (or updates) the TrueCtrl alert service on TrueNAS so its alerts are pushed to this
     * phone. Needs the Alert Write role; throws a TnError when TrueNAS refuses.
     */
    suspend fun setUpOnServer(c: Connection, endpoint: String, level: Severity, version: TnVersion?) {
        val body = serviceBody(endpoint, levelFor(level), version)
        val filter = JsonArray(listOf(JsonArray(listOf(JsonPrimitive("name"), JsonPrimitive("="), JsonPrimitive(SERVICE_NAME)))))
        val existing = c.call("alertservice.query", listOf(filter)).arr()?.firstOrNull()?.get("id").long()
        if (existing != null) {
            c.call("alertservice.update", listOf(JsonPrimitive(existing), body))
        } else {
            c.call("alertservice.create", listOf(body))
        }
    }

    /** Asks TrueNAS to send a test alert through the TrueCtrl alert service. */
    suspend fun testOnServer(c: Connection, endpoint: String, level: Severity, version: TnVersion?) {
        c.call("alertservice.test", listOf(serviceBody(endpoint, levelFor(level), version)))
    }

    /** Whether the TrueCtrl alert service on TrueNAS already points at [endpoint]; null when unknown. */
    suspend fun isSetUp(c: Connection, endpoint: String): Boolean? = runCatching {
        val filter = JsonArray(listOf(JsonArray(listOf(JsonPrimitive("name"), JsonPrimitive("="), JsonPrimitive(SERVICE_NAME)))))
        val entry = c.call("alertservice.query", listOf(filter)).arr()?.firstOrNull() ?: return@runCatching false
        // TrueNAS may hide the URL (it's a secret); then being there and enabled is the best sign.
        val url = entry["attributes"]["url"].str()
        (url == null || url == endpoint) && (entry["enabled"].str() != "false")
    }.getOrNull()

    private fun String.oneLine(max: Int) = replace(Regex("[\\p{Cntrl}\\s]+"), " ").trim().take(max)

    /**
     * Turns a push into a notification. From TrueNAS's Slack-type alert service it's
     * `{"text": "..."}` with one or more alerts (new and cleared); anything else is shown as text.
     * Anyone who learns the push address can post to it: what's shown is kept short.
     */
    fun toAlert(context: Context, content: String): Alert {
        val o = runCatching { TnJson.parseToJsonElement(content) as? JsonObject }.getOrNull()
        val text = (o?.get("text").str() ?: content).replace("*", "").trim()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val cleared = lines.firstOrNull()?.startsWith("Gone", ignoreCase = true) == true ||
            lines.firstOrNull()?.contains("cleared", ignoreCase = true) == true
        val body = lines.filterNot { it.endsWith(":") && it.length < 40 }.joinToString("\n").ifBlank { text }
        return Alert(
            key = "push:${body.hashCode()}:${System.currentTimeMillis() / 60_000}",
            event = NotifyEvent.NEW_ALERT,
            severity = if (cleared) Severity.INFO else Severity.WARNING,
            title = context.getString(if (cleared) R.string.nt_push_cleared else R.string.nt_push_title),
            text = body.take(1000).lines().joinToString("\n") { it.oneLine(300) },
        )
    }
}

/** Receives UnifiedPush events from the distributor (ntfy). */
class TruePushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Push.onEndpoint(applicationContext, endpoint.url)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        val c = applicationContext.container
        val text = message.content.toString(Charsets.UTF_8)
        c.appScope.launch {
            val settings = c.notifySettings.current()
            if (!settings.push) return@launch
            Notifier.post(applicationContext, Push.toAlert(applicationContext, text), Notifier.CH_TRUENAS)
            // Something happened on the server: refresh the background check too.
            NotifyWorker.runNow(applicationContext)
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Push.onFailed(reason.name)
    }

    override fun onUnregistered(instance: String) {
        Push.onUnregistered(applicationContext)
    }
}
