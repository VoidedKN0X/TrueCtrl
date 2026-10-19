package com.vknxworks.truectrl.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.Capabilities
import com.vknxworks.truectrl.data.api.LoginResult
import com.vknxworks.truectrl.data.api.RpcSocket
import com.vknxworks.truectrl.data.overview.SystemInfo
import com.vknxworks.truectrl.data.api.TnAuth
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.api.TnVersion
import com.vknxworks.truectrl.data.api.httpFor
import com.vknxworks.truectrl.data.server.AuthMethod
import com.vknxworks.truectrl.data.server.Server
import com.vknxworks.truectrl.data.server.ServerAddress
import com.vknxworks.truectrl.data.server.ServerSecrets
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupSwitch
import com.vknxworks.truectrl.ui.components.PillButton
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.SecureWindow
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** Everything learned from a successful connection, shown before the server is saved. */
private class Connected(
    val server: Server,
    val secrets: ServerSecrets,
    val capabilities: Capabilities,
    val info: SystemInfo?,
)

private class CertPrompt(val cert: CertInfo, val changed: Boolean)

/**
 * Adds a TrueNAS server, or changes the address or sign-in of an existing one ([editId]).
 *
 * 1. The address is checked with GET /api/versions, which needs no login: that shows whether it
 *    is a TrueNAS with the JSON-RPC API, which version, and whether its certificate is trusted. An
 *    untrusted certificate is shown by its SHA-256 fingerprint to confirm and pin (nothing has been
 *    sent to the server at that point).
 * 2. Then it opens the WebSocket and signs in with auth.login_ex: an API key, or a username and
 *    password (plus a one-time password when the account uses two-factor authentication).
 * 3. The version and the user's roles are read and shown, and the server is saved on Continue.
 */
@Composable
fun SetupScreen(editId: String? = null, onDone: () -> Unit, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val existing = remember(editId) { editId?.let { id -> c.sessions.servers.value.firstOrNull { it.id == id } } }
    // Only whether a key is saved; the key itself is decrypted when it's used, not kept in the screen's state.
    val keptKey = remember(editId) { existing?.auth == AuthMethod.API_KEY && editId != null && c.serverStore.hasApiKey(editId) }

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var address by remember { mutableStateOf(existing?.url?.removePrefix("https://").orEmpty()) }
    var method by remember { mutableStateOf(existing?.auth ?: AuthMethod.API_KEY) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var certPrompt by remember { mutableStateOf<CertPrompt?>(null) }
    var otpSocket by remember { mutableStateOf<RpcSocket?>(null) }
    var otpVersion by remember { mutableStateOf<TnVersion?>(null) }
    var otpError by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf<Connected?>(null) }
    var readOnly by remember { mutableStateOf(existing?.readOnly ?: false) }

    // A certificate the user trusted in this session, or the one already pinned for this server.
    var pinnedHost by remember { mutableStateOf(existing?.host) }
    var pin by remember { mutableStateOf(existing?.certPin.orEmpty()) }

    // A socket left open for the one-time password step is closed when leaving the screen.
    DisposableEffect(Unit) { onDispose { otpSocket?.close() } }
    // API keys, passwords and one-time passwords are typed here: no screenshots, recordings or casting.
    SecureWindow()

    val resolved: HttpUrl? = ServerAddress.normalize(address)
    val addressError = when {
        address.isBlank() -> null
        ServerAddress.isPlainHttp(address) -> stringResource(R.string.address_http)
        resolved == null -> stringResource(R.string.address_invalid)
        else -> null
    }
    val canConnect = !busy && resolved != null && username.isNotBlank() && when (method) {
        AuthMethod.API_KEY -> apiKey.isNotBlank() || keptKey
        AuthMethod.PASSWORD -> password.isNotEmpty()
    }

    fun pinFor(url: HttpUrl): String = if (pinnedHost.equals(url.host, ignoreCase = true)) pin else ""

    /** The API key to sign in with: the one typed, or the saved one when the field was left empty. */
    fun keyToUse(): String = apiKey.trim().ifBlank { if (keptKey && editId != null) c.serverStore.secrets(editId).apiKey else "" }

    /** Reads roles, version and system info on the signed-in [socket], then shows the result. */
    suspend fun finish(socket: RpcSocket, url: HttpUrl, newest: TnVersion, usesOtp: Boolean) {
        try {
            val caps = TnAuth.capabilities(socket, newest)
            val info = TnAuth.systemInfo(socket)
            val draft = Server(
                id = existing?.id.orEmpty(),
                name = name.trim().ifBlank { info?.hostname?.takeIf { it.isNotBlank() } ?: url.host },
                url = ServerAddress.base(url),
                auth = method,
                username = username.trim(),
                certPin = pinFor(url),
                usesOtp = usesOtp,
                tnVersion = caps.version?.label.orEmpty(),
            )
            // The password is only kept when it can sign in again on its own (no one-time password).
            val secrets = when (method) {
                AuthMethod.API_KEY -> ServerSecrets(apiKey = keyToUse())
                AuthMethod.PASSWORD -> ServerSecrets(password = if (usesOtp) "" else password)
            }
            // Keep the user's read-only choice when editing; a user that can't change anything starts read-only.
            readOnly = existing?.readOnly ?: caps.isReadOnly
            connected = Connected(draft, secrets, caps, info)
        } finally {
            socket.close()
        }
    }

    fun connect() {
        val url = resolved ?: return
        val base = ServerAddress.base(url)
        busy = true
        error = null
        scope.launch {
            val http = httpFor(c.http, url.host, pinFor(url))
            // 1. Reachable, a supported TrueNAS, and a trusted certificate? Nothing secret is sent yet.
            val newest = try {
                TnAuth.requireSupported(TnAuth.probe(http, base))
            } catch (e: TnError.UntrustedCertificate) {
                askToTrust(url, changed = false, onCert = { certPrompt = it }, onFail = { error = it })
                busy = false
                return@launch
            } catch (e: TnError.PinMismatch) {
                askToTrust(url, changed = true, onCert = { certPrompt = it }, onFail = { error = it })
                busy = false
                return@launch
            } catch (e: Exception) {
                error = errorMessage(context, e, url.host)
                busy = false
                return@launch
            }
            // 2. Open the WebSocket and sign in.
            val socket = RpcSocket(http, TnAuth.socketUrl(base))
            try {
                socket.open()
                val result = when (method) {
                    AuthMethod.API_KEY -> TnAuth.loginApiKey(socket, username.trim(), keyToUse())
                    AuthMethod.PASSWORD -> TnAuth.loginPassword(socket, username.trim(), password)
                }
                if (result == LoginResult.OtpRequired) {
                    otpError = null
                    otpVersion = newest
                    otpSocket = socket
                    busy = false
                    return@launch
                }
                // 3. What may this user do?
                finish(socket, url, newest, usesOtp = false)
            } catch (e: Exception) {
                socket.close()
                error = errorMessage(context, e, url.host)
            }
            busy = false
        }
    }

    fun answerOtp(code: String) {
        val url = resolved ?: return
        val socket = otpSocket ?: return
        val newest = otpVersion ?: return
        busy = true
        scope.launch {
            try {
                when (TnAuth.continueOtp(socket, code)) {
                    LoginResult.Success -> {
                        otpSocket = null
                        finish(socket, url, newest, usesOtp = true)
                    }
                    LoginResult.OtpRequired -> otpError = context.getString(R.string.error_bad_otp)
                }
            } catch (e: TnError.BadOtp) {
                otpError = context.getString(R.string.error_bad_otp)
            } catch (e: Exception) {
                otpSocket = null
                socket.close()
                error = errorMessage(context, e, url.host)
            }
            busy = false
        }
    }

    fun cancelOtp() {
        otpSocket?.close()
        otpSocket = null
        busy = false
    }

    fun save() {
        val result = connected ?: return
        c.sessions.add(result.server.copy(readOnly = readOnly), result.secrets)
        // Usage stats (if opted in): only how the server was set up, never its address, name or user.
        c.telemetry.track(
            "server_saved",
            mapOf(
                "kind" to if (editId != null) "edited" else "added",
                "auth" to if (result.server.auth == AuthMethod.API_KEY) "api_key" else "password",
                "two_factor" to result.server.usesOtp.toString(),
                "certificate" to if (result.server.certPin.isNotBlank()) "pinned" else "trusted",
                "read_only" to readOnly.toString(),
            ),
        )
        onDone()
    }

    val result = connected
    Column(Modifier.fillMaxSize()) {
        if (onCancel != null) {
            ScreenTopBar(stringResource(if (editId != null) R.string.edit_server_title else R.string.add_server))
        } else {
            Spacer(Modifier.statusBarsPadding())
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            if (result == null) {
                if (onCancel == null) {
                    Column(Modifier.padding(horizontal = Gutter, vertical = 16.dp)) {
                        Icon(Icons.Filled.Storage, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.setup_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it.trim() },
                        label = { Text(stringResource(R.string.field_address)) },
                        placeholder = { Text(stringResource(R.string.field_address_hint)) },
                        singleLine = true,
                        isError = addressError != null,
                        supportingText = {
                            when {
                                addressError != null -> Text(addressError)
                                resolved != null -> Text(stringResource(R.string.address_resolves, ServerAddress.base(resolved)))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.field_name)) },
                        placeholder = { Text(stringResource(R.string.field_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.auth_method), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(AuthMethod.API_KEY to R.string.auth_api_key, AuthMethod.PASSWORD to R.string.auth_password).forEachIndexed { i, (m, label) ->
                            SegmentedButton(
                                selected = method == m,
                                onClick = { method = m; error = null },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(stringResource(label)) }
                        }
                    }
                    Text(
                        stringResource(if (method == AuthMethod.API_KEY) R.string.auth_api_key_note else R.string.auth_password_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    val secretToggle: @Composable () -> Unit = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                stringResource(if (showSecret) R.string.hide_secret else R.string.show_secret),
                            )
                        }
                    }
                    val secretTransform = if (showSecret) VisualTransformation.None else PasswordVisualTransformation()

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it.trim() },
                        label = { Text(stringResource(R.string.field_username)) },
                        placeholder = { Text(stringResource(R.string.field_username_hint)) },
                        supportingText = if (method == AuthMethod.API_KEY) {
                            { Text(stringResource(R.string.field_username_key_note)) }
                        } else {
                            null
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when (method) {
                        AuthMethod.API_KEY -> OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(stringResource(R.string.field_api_key)) },
                            placeholder = if (keptKey) {
                                { Text("••••••••") }
                            } else {
                                null
                            },
                            singleLine = true,
                            visualTransformation = secretTransform,
                            trailingIcon = secretToggle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AuthMethod.PASSWORD -> OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(stringResource(R.string.field_password)) },
                            singleLine = true,
                            visualTransformation = secretTransform,
                            trailingIcon = secretToggle,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    PillButton(
                        text = stringResource(if (busy) R.string.connecting else R.string.connect),
                        onClick = ::connect,
                        enabled = canConnect,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (busy) CircularProgressIndicator(Modifier.padding(top = 8.dp).size(24.dp), strokeWidth = 2.dp)
                }
            } else {
                ConnectedSummary(result, readOnly, onReadOnly = { readOnly = it })
                PillButton(
                    text = stringResource(if (editId != null) R.string.save_changes else R.string.continue_label),
                    onClick = ::save,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                )
            }
        }
    }

    certPrompt?.let { prompt ->
        CertTrustDialog(
            cert = prompt.cert,
            changed = prompt.changed,
            onTrust = {
                certPrompt = null
                pinnedHost = prompt.cert.host
                pin = prompt.cert.fingerprint
                connect()
            },
            onCancel = { certPrompt = null },
        )
    }
    if (otpSocket != null) {
        OtpDialog(
            error = otpError,
            busy = busy,
            onSubmit = ::answerOtp,
            onCancel = ::cancelOtp,
        )
    }
}

/** Reads the certificate the server presents and asks the user to confirm it. */
private suspend fun askToTrust(url: HttpUrl, changed: Boolean, onCert: (CertPrompt) -> Unit, onFail: (String) -> Unit) {
    try {
        onCert(CertPrompt(fetchCertInfo(url), changed))
    } catch (e: Exception) {
        onFail(e.message ?: url.host)
    }
}

@Composable
private fun ConnectedSummary(result: Connected, readOnly: Boolean, onReadOnly: (Boolean) -> Unit) {
    val server = result.server
    Column(Modifier.padding(horizontal = Gutter, vertical = 16.dp)) {
        Icon(Icons.Filled.Storage, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.connected_to, server.name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            listOf(
                stringResource(R.string.tn_version, server.tnVersion.ifBlank { "?" }),
                stringResource(R.string.signed_in_as, server.username),
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Group(footer = if (result.capabilities.isReadOnly && readOnly) stringResource(R.string.read_only_suggest) else null) {
        GroupSwitch(
            stringResource(R.string.read_only_mode),
            stringResource(R.string.read_only_mode_subtitle),
            checked = readOnly,
            onChange = onReadOnly,
        )
    }
    AccessSummary(result.capabilities)
}
