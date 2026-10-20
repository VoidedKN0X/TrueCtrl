package com.vknxworks.truectrl.data.notify

import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import java.util.UUID

/** Where TrueCtrl's push registration with ntfy stands. */
sealed interface PushStatus {
    data object Off : PushStatus
    data object NtfyMissing : PushStatus
    data object Registering : PushStatus

    /** [tempUnavailable]: ntfy can't reach the ntfy server for now; the address stays valid. */
    data class Registered(val endpoint: String, val tempUnavailable: Boolean = false) : PushStatus
    data class Failed(val reason: String) : PushStatus
}

/**
 * Push through ntfy only: the ntfy app gives TrueCtrl an endpoint URL on the user's ntfy server.
 * TrueNAS has no ntfy or webhook alert service, but its Slack alert service simply posts
 * `{"text": "..."}` to any URL, so a Slack-type alert service pointed at the endpoint delivers
 * TrueNAS's alerts to [PushReceiver]. This is the app side of the UnifiedPush Android protocol
 * (https://unifiedpush.org/developers/spec/android/), written out here instead of using a library,
 * and it only ever talks to the ntfy app.
 */
object Push {
    /** The ntfy app, the only push app TrueCtrl registers with. */
    const val NTFY = "io.heckel.ntfy"

    /** Where to get ntfy. */
    const val NTFY_DOWNLOAD = "https://f-droid.org/packages/io.heckel.ntfy/"

    private const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    private const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"
    private const val ACTION_MESSAGE_ACK = "org.unifiedpush.android.distributor.MESSAGE_ACK"
    const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"
    const val ACTION_UNREGISTERED = "org.unifiedpush.android.connector.UNREGISTERED"
    const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
    const val ACTION_TEMP_UNAVAILABLE = "org.unifiedpush.android.connector.TEMP_UNAVAILABLE"
    private const val FEATURE_BYTES_MESSAGE = "org.unifiedpush.android.distributor.feature.BYTES_MESSAGE"

    const val EXTRA_TOKEN = "token"
    const val EXTRA_ENDPOINT = "endpoint"
    const val EXTRA_BYTES_MESSAGE = "bytesMessage"
    const val EXTRA_MESSAGE = "message"
    const val EXTRA_MESSAGE_ID = "id"
    const val EXTRA_REASON = "reason"

    private const val PREFS = "push"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_TOKEN = "token"
    private const val KEY_DISTRIBUTOR = "distributor"
    private const val KEY_ERROR = "error"
    private const val KEY_TEMP_UNAVAILABLE = "temp_unavailable"

    // Where the push library TrueCtrl used up to 0.9.2 kept its registration (read once, then deleted).
    private const val LEGACY_PREFS = "unifiedpush.connector"
    private const val LEGACY_DISTRIBUTOR = "unifiedpush.distributor"
    private const val LEGACY_TOKEN = "default/unifiedpush.connector"

    /** The name of the alert service TrueCtrl creates on TrueNAS. */
    const val SERVICE_NAME = "TrueCtrl"

    private val _status = MutableStateFlow<PushStatus>(PushStatus.Off)
    val status: StateFlow<PushStatus> = _status.asStateFlow()

    @Volatile
    private var enabled = false

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Reads the registration. Push is on ([enabled]) but not registered yet, e.g. because ntfy was
     * just installed: TrueCtrl registers with ntfy now. Without ntfy, push stays off until it's installed.
     */
    fun load(context: Context, enabled: Boolean) {
        this.enabled = enabled
        val migrated = migrateLegacy(context)
        val p = prefs(context)
        val distributor = p.getString(KEY_DISTRIBUTOR, null)
        if (distributor != null && distributor != NTFY) {
            p.getString(KEY_TOKEN, null)?.let { sendUnregister(context, distributor, it) }
            clearRegistration(context)
        }
        if (enabled && ntfyInstalled(context) && (migrated || p.getString(KEY_DISTRIBUTOR, null) == null)) {
            register(context)
            return
        }
        publish(context)
    }

    /**
     * Moves a registration made by the push library TrueCtrl used before. With ntfy, TrueCtrl keeps the
     * token, so ntfy keeps the same address and TrueNAS's alert service still reaches the phone. With
     * any other push app, the registration is cancelled there. True when there was one to move.
     */
    private fun migrateLegacy(context: Context): Boolean {
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return false
        val distributor = legacy.getString(LEGACY_DISTRIBUTOR, null)
        val token = legacy.getString(LEGACY_TOKEN, null)
        if (distributor == NTFY && token != null) {
            prefs(context).edit().putString(KEY_TOKEN, token).putString(KEY_DISTRIBUTOR, NTFY).apply()
        } else {
            if (distributor != null && token != null) sendUnregister(context, distributor, token)
            clearRegistration(context)
        }
        legacy.edit().clear().apply()
        runCatching { context.deleteSharedPreferences(LEGACY_PREFS) }
        return distributor != null && token != null
    }

    private fun clearRegistration(context: Context) {
        prefs(context).edit()
            .remove(KEY_TOKEN).remove(KEY_DISTRIBUTOR).remove(KEY_ENDPOINT).remove(KEY_ERROR).remove(KEY_TEMP_UNAVAILABLE)
            .apply()
    }

    private fun publish(context: Context) {
        val p = prefs(context)
        val endpoint = p.getString(KEY_ENDPOINT, null)
        val error = p.getString(KEY_ERROR, null)
        _status.value = when {
            !enabled -> PushStatus.Off
            !ntfyInstalled(context) -> PushStatus.NtfyMissing
            error != null -> PushStatus.Failed(error)
            endpoint != null -> PushStatus.Registered(endpoint, p.getBoolean(KEY_TEMP_UNAVAILABLE, false))
            else -> PushStatus.Registering
        }
    }

    /** Whether the ntfy app is installed and can hand out push addresses. */
    fun ntfyInstalled(context: Context): Boolean =
        context.packageManager.queryBroadcastReceivers(Intent(ACTION_REGISTER).setPackage(NTFY), PackageManager.ResolveInfoFlags.of(0))
            .isNotEmpty()

    /** The random secret that proves a broadcast really comes from ntfy for this registration. */
    fun token(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    /** Asks ntfy for an endpoint. The answer arrives in [PushReceiver]. */
    fun register(context: Context) {
        enabled = true
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null) ?: UUID.randomUUID().toString()
        p.edit().putString(KEY_TOKEN, token).putString(KEY_DISTRIBUTOR, NTFY).remove(KEY_ERROR).remove(KEY_TEMP_UNAVAILABLE).apply()
        publish(context)
        if (!ntfyInstalled(context)) return
        val intent = Intent(ACTION_REGISTER)
            .putExtra(EXTRA_TOKEN, token)
            .putExtra("features", arrayOf(FEATURE_BYTES_MESSAGE))
            .putExtra("application", context.packageName)
            .putExtra(EXTRA_MESSAGE, context.getString(R.string.app_name))
        runCatching { sendToDistributor(context, NTFY, intent) }
    }

    fun unregister(context: Context) {
        enabled = false
        val p = prefs(context)
        val token = p.getString(KEY_TOKEN, null)
        val distributor = p.getString(KEY_DISTRIBUTOR, null)
        if (token != null && distributor != null) sendUnregister(context, distributor, token)
        // A fresh token next time, so an old address can't reach the app any more.
        clearRegistration(context)
        publish(context)
    }

    private fun sendUnregister(context: Context, distributor: String, token: String) {
        runCatching { sendToDistributor(context, distributor, Intent(ACTION_UNREGISTER).putExtra(EXTRA_TOKEN, token)) }
    }

    /**
     * Sends [intent] to [distributor] with TrueCtrl's identity: shared with the broadcast from
     * Android 14, through a pending intent before that.
     */
    private fun sendToDistributor(context: Context, distributor: String, intent: Intent) {
        intent.setPackage(distributor)
        if (Build.VERSION.SDK_INT >= 34) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true)
            context.sendBroadcast(intent, null, options.toBundle())
        } else {
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent("org.unifiedpush.dummy_app").setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE,
            )
            context.sendBroadcast(intent.putExtra("pi", pi))
        }
    }

    internal fun onEndpoint(context: Context, url: String) {
        prefs(context).edit().putString(KEY_ENDPOINT, url).remove(KEY_ERROR).remove(KEY_TEMP_UNAVAILABLE).apply()
        publish(context)
    }

    internal fun onFailed(context: Context, reason: String) {
        val p = prefs(context)
        // Ignored once ntfy has given an address for this token.
        if (p.getString(KEY_ENDPOINT, null) != null) return
        // A new token for the next try.
        p.edit().remove(KEY_TOKEN).putString(KEY_ERROR, reason).apply()
        publish(context)
    }

    internal fun onUnregistered(context: Context) {
        prefs(context).edit()
            .remove(KEY_TOKEN).remove(KEY_ENDPOINT).remove(KEY_TEMP_UNAVAILABLE)
            .putString(KEY_ERROR, "UNREGISTERED")
            .apply()
        publish(context)
    }

    internal fun onTempUnavailable(context: Context) {
        prefs(context).edit().putBoolean(KEY_TEMP_UNAVAILABLE, true).apply()
        publish(context)
    }

    internal fun ack(context: Context, id: String?) {
        if (id == null) return
        val token = token(context) ?: return
        runCatching {
            context.sendBroadcast(
                Intent(ACTION_MESSAGE_ACK).setPackage(NTFY).putExtra(EXTRA_TOKEN, token).putExtra(EXTRA_MESSAGE_ID, id),
            )
        }
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

/**
 * Receives ntfy's answers and TrueNAS's alerts. Exported (ntfy is another app), so every broadcast must
 * carry TrueCtrl's secret token, and from Android 14 come from the ntfy app, or it's ignored.
 */
class PushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 34) {
            val from = sentFromPackage
            if (from != null && from != Push.NTFY) return
        }
        val token = intent.getStringExtra(Push.EXTRA_TOKEN) ?: return
        if (token != Push.token(context)) return
        val app = context.applicationContext
        when (intent.action) {
            Push.ACTION_NEW_ENDPOINT -> {
                val endpoint = intent.getStringExtra(Push.EXTRA_ENDPOINT) ?: return
                Push.onEndpoint(app, endpoint)
                Push.ack(app, intent.getStringExtra(Push.EXTRA_MESSAGE_ID))
            }
            Push.ACTION_REGISTRATION_FAILED -> Push.onFailed(app, intent.getStringExtra(Push.EXTRA_REASON) ?: "UNKNOWN")
            Push.ACTION_UNREGISTERED -> Push.onUnregistered(app)
            Push.ACTION_TEMP_UNAVAILABLE -> Push.onTempUnavailable(app)
            Push.ACTION_MESSAGE -> {
                val bytes = intent.getByteArrayExtra(Push.EXTRA_BYTES_MESSAGE)
                    ?: intent.getStringExtra(Push.EXTRA_MESSAGE)?.toByteArray()
                    ?: return
                Push.ack(app, intent.getStringExtra(Push.EXTRA_MESSAGE_ID))
                val text = bytes.toString(Charsets.UTF_8)
                val c = app.container
                // Keeps the process alive until the notification is posted.
                val pending = goAsync()
                c.appScope.launch {
                    try {
                        val settings = c.notifySettings.current()
                        if (!settings.push) return@launch
                        Notifier.post(app, Push.toAlert(app, text), Notifier.CH_TRUENAS)
                        // Something happened on the server: refresh the background check too.
                        NotifyWorker.runNow(app)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
