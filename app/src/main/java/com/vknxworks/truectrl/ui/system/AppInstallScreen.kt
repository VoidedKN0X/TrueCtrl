package com.vknxworks.truectrl.ui.system

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.system.AppForm
import com.vknxworks.truectrl.data.system.AppGroup
import com.vknxworks.truectrl.data.system.AppQuestion
import com.vknxworks.truectrl.data.system.CatalogDetail
import com.vknxworks.truectrl.data.system.FieldKind
import com.vknxworks.truectrl.ui.components.ChoiceSheet
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.CtrlLabel
import com.vknxworks.truectrl.ui.components.CtrlOutlinedButton
import com.vknxworks.truectrl.ui.components.CtrlSwitch
import com.vknxworks.truectrl.ui.components.CtrlTextField
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.PillButton
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.SecureWindow
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.nav.AppInstallRoute
import com.vknxworks.truectrl.ui.nav.JobsRoute
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.sharing.DatasetPickerSheet
import com.vknxworks.truectrl.ui.sharing.sharePath
import com.vknxworks.truectrl.ui.sharing.shareableDatasets
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.CtrlShapes
import com.vknxworks.truectrl.ui.theme.Gutter
import com.vknxworks.truectrl.ui.theme.Health
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Deepest nesting the form draws; anything deeper is edited as JSON. */
private const val MAX_DEPTH = 6

/**
 * Installs a catalog app: the app name, the version, and a form built from the version's
 * questions, starting from TrueNAS's defaults. Installing starts an app.create job and opens Jobs.
 */
@Composable
fun AppInstallScreen(name: String, train: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val datasetsState by c.datasets.state.collectAsStateWithLifecycle()
    val server = active?.server
    val snackbar = remember { SnackbarHostState() }

    var detail by remember { mutableStateOf<CatalogDetail?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var versionIndex by remember { mutableStateOf(0) }
    // Never saved to instance state: values can hold passwords.
    var values by remember { mutableStateOf<JsonObject?>(null) }
    var appName by remember { mutableStateOf(name.take(40)) }
    var taken by remember { mutableStateOf<Set<String>>(emptySet()) }
    var usedPorts by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var versionSheet by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<((String) -> Unit)?>(null) }
    val rawErrors = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(server?.id, access.generation) {
        if (!access.connected || detail != null) return@LaunchedEffect
        try {
            val d = c.catalog.details(name, train)
            detail = d
            val idx = d?.versions?.indexOfFirst { it.healthy }?.takeIf { it >= 0 } ?: 0
            versionIndex = idx
            val v = d?.versions?.getOrNull(idx)
            values = AppForm.initialValues(v?.schema, v?.values)
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e
        }
        taken = runCatching { c.system.apps().map { it.name }.toSet() }.getOrDefault(emptySet())
        if (appName in taken) appName = generateSequence(2) { it + 1 }.map { "${name.take(36)}-$it" }.first { it !in taken }
        usedPorts = c.catalog.usedPorts()
        if (datasetsState.datasets.isEmpty()) c.datasets.refresh()
    }

    val d = detail
    val version = d?.versions?.getOrNull(versionIndex)
    val groups = remember(version) { AppForm.groups(version?.schema) }
    val questions = remember(version) { AppQuestion.parseList(version?.schema?.get("questions")) }
    val secret = remember(version) { AppForm.anyPrivate(questions) }
    if (secret) SecureWindow()

    val nameOk = AppForm.appNameValid(appName) && appName !in taken
    val v = values
    val problem = if (v != null) AppForm.firstProblem(questions, v) else null
    val clashes = if (v != null) AppForm.ports(questions, v).filter { it in usedPorts }.distinct() else emptyList()
    val canInstall = nameOk && v != null && problem == null && rawErrors.values.none { it } && !busy && access.connected

    fun install() {
        val ver = version ?: return
        val vals = values ?: return
        busy = true
        scope.launch {
            try {
                c.catalog.install(name, appName, train, ver.version, AppForm.prune(questions, vals))
                c.catalog.forget()
                Toast.makeText(context, context.getString(R.string.install_started, appName), Toast.LENGTH_LONG).show()
                nav.nav.navigate(JobsRoute) { popUpTo<AppInstallRoute> { inclusive = true } }
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.install_title, d?.app?.title ?: name))
        if (!access.connected && access.error != null) ConnectionBanner()
        if (d == null || v == null || version == null) {
            EmptyState(
                Icons.Outlined.Apps,
                stringResource(
                    when {
                        error != null -> R.string.catalog_app_failed
                        d != null && d.versions.isEmpty() -> R.string.install_no_versions
                        else -> R.string.overview_loading
                    },
                ),
                error?.let { errorMessage(context, it, server?.host.orEmpty()) }.orEmpty(),
                Modifier.weight(1f),
            )
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                Group(stringResource(R.string.install_group_app)) {
                    FieldPad {
                        CtrlTextField(
                            value = appName,
                            onValueChange = { appName = it.lowercase().trim() },
                            label = { Text(stringResource(R.string.install_name)) },
                            singleLine = true,
                            isError = !nameOk,
                            supportingText = {
                                Text(stringResource(if (appName in taken) R.string.install_name_taken else R.string.install_name_hint))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    GroupRow(
                        stringResource(R.string.install_version),
                        listOfNotNull(version.humanVersion, version.version.takeIf { it != version.humanVersion }, if (!version.healthy) stringResource(R.string.install_unhealthy) else null).joinToString("  ·  "),
                        showChevron = d.versions.size > 1,
                        onClick = if (d.versions.size > 1) ({ versionSheet = true }) else null,
                    )
                    GroupRow(stringResource(R.string.catalog_train), train)
                }

                if (groups.isEmpty()) {
                    Text(
                        stringResource(R.string.install_no_questions),
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                    )
                }
                groups.forEach { g ->
                    FormGroup(g, v, onPickPath = { set -> pickFor = set }, rawErrors = rawErrors) { values = it }
                }

                if (clashes.isNotEmpty()) {
                    Text(
                        stringResource(R.string.install_port_clash, clashes.joinToString(", ")),
                        style = MaterialTheme.typography.bodySmall,
                        color = Health.warn,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp),
                    )
                }
                problem?.let { (where, p) ->
                    Text(
                        stringResource(R.string.install_problem, where, problemText(p)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp),
                    )
                }
                PillButton(
                    text = stringResource(if (busy) R.string.install_starting else R.string.catalog_install),
                    icon = Icons.Outlined.Download,
                    onClick = ::install,
                    enabled = canInstall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                )
                Text(
                    stringResource(R.string.install_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = Gutter),
                )
            }
        }
        SnackbarHost(snackbar)
    }

    if (versionSheet && d != null) {
        ChoiceSheet(
            title = stringResource(R.string.install_version),
            options = d.versions.mapIndexed { i, ver ->
                i to listOfNotNull(ver.version, ver.humanVersion.takeIf { it != ver.version }, if (!ver.healthy) stringResource(R.string.install_unhealthy) else null).joinToString("  ·  ")
            },
            selected = versionIndex,
            onDismiss = { versionSheet = false },
        ) { i ->
            versionSheet = false
            if (i != versionIndex) {
                versionIndex = i
                val ver = d.versions[i]
                values = AppForm.initialValues(ver.schema, ver.values)
                rawErrors.clear()
            }
        }
    }

    pickFor?.let { set ->
        DatasetPickerSheet(shareableDatasets(datasetsState.datasets), onDismiss = { pickFor = null }) { ds ->
            pickFor = null
            set(ds.sharePath())
        }
    }
}

@Composable
private fun problemText(p: AppForm.Problem): String = stringResource(
    when (p) {
        AppForm.Problem.REQUIRED -> R.string.form_required
        AppForm.Problem.NUMBER -> R.string.form_number
        AppForm.Problem.RANGE -> R.string.form_range
        AppForm.Problem.LENGTH -> R.string.form_length
        AppForm.Problem.CHARS -> R.string.form_chars
        AppForm.Problem.JSON -> R.string.form_json
    },
)

@Composable
private fun FieldPad(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) { content() }
}

@Composable
private fun FormGroup(
    g: AppGroup,
    values: JsonObject,
    onPickPath: (((String) -> Unit)) -> Unit,
    rawErrors: MutableMap<String, Boolean>,
    onChange: (JsonObject) -> Unit,
) {
    val shown = g.questions.filter { !it.hidden && AppForm.visible(it, values) }
    if (shown.isEmpty()) return
    Group(g.name.ifBlank { null }, footer = g.description) {
        shown.forEach { q ->
            QuestionField(q, values[q.variable] ?: q.initialValue(), q.variable, 0, onPickPath, rawErrors) { nv ->
                onChange(JsonObject(values + (q.variable to nv)))
            }
        }
    }
}

/** One question, drawn by its type. Unknown types (and anything too deep) fall back to raw text or JSON. */
@Composable
private fun QuestionField(
    q: AppQuestion,
    value: JsonElement,
    path: String,
    depth: Int,
    onPickPath: (((String) -> Unit)) -> Unit,
    rawErrors: MutableMap<String, Boolean>,
    onChange: (JsonElement) -> Unit,
) {
    val enabled = q.editable
    val problem = AppForm.problem(q, value)
    val help = listOfNotNull(q.description, if (q.required) stringResource(R.string.form_required_mark) else null).joinToString("  ")
    val kind = if (depth >= MAX_DEPTH && (q.kind == FieldKind.DICT || q.kind == FieldKind.LIST)) FieldKind.RAW else q.kind
    when (kind) {
        FieldKind.BOOLEAN -> {
            val on = (value as? JsonPrimitive)?.content == "true"
            GroupRow(
                title = q.label,
                subtitle = q.description,
                trailing = { CtrlSwitch(on, onCheckedChange = if (enabled) ({ onChange(JsonPrimitive(it)) }) else null, enabled = enabled) },
            )
        }
        FieldKind.ENUM -> {
            var open by remember { mutableStateOf(false) }
            val options = q.choices.orEmpty()
            val current = options.firstOrNull { it.first == value }?.second ?: AppForm.rawText(value).ifBlank { "—" }
            GroupRow(q.label, listOfNotNull(current, q.description).joinToString("\n"), showChevron = enabled, onClick = if (enabled) ({ open = true }) else null)
            if (open) {
                // Labels are the sheet's keys: make them unique.
                val labelled = options.mapIndexed { i, (_, label) -> i to label }
                    .groupBy { it.second }
                    .flatMap { (label, list) -> if (list.size == 1) list else list.map { (i, _) -> i to "$label (${AppForm.rawText(options[i].first)})" } }
                    .distinctBy { it.second }
                    .sortedBy { it.first }
                ChoiceSheet(q.label, labelled, options.indexOfFirst { it.first == value }.takeIf { it >= 0 }, onDismiss = { open = false }) { i ->
                    open = false
                    onChange(options[i].first)
                }
            }
        }
        FieldKind.INT -> FieldPad {
            CtrlTextField(
                value = AppForm.rawText(value),
                onValueChange = { t ->
                    val trimmed = t.trim()
                    onChange(
                        when {
                            trimmed.isEmpty() -> JsonNull
                            trimmed.toLongOrNull() != null -> JsonPrimitive(trimmed.toLong())
                            else -> JsonPrimitive(trimmed)
                        },
                    )
                },
                label = { Text(q.label) },
                singleLine = true,
                enabled = enabled,
                isError = problem != null,
                supportingText = fieldHelp(help, problem, q),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FieldKind.TEXT, FieldKind.PATH, FieldKind.HOSTPATH -> FieldPad {
            var reveal by remember { mutableStateOf(false) }
            CtrlTextField(
                value = AppForm.rawText(value),
                onValueChange = { t -> onChange(if (t.isEmpty() && q.nullable) JsonNull else JsonPrimitive(t)) },
                label = { Text(q.label) },
                singleLine = q.type != "text",
                minLines = if (q.type == "text") 3 else 1,
                enabled = enabled,
                isError = problem != null,
                supportingText = fieldHelp(if (kind == FieldKind.HOSTPATH) listOf(help, stringResource(R.string.form_hostpath_hint)).filter { it.isNotBlank() }.joinToString("  ") else help, problem, q),
                visualTransformation = if (q.secret && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (q.secret) KeyboardType.Password else if (kind == FieldKind.TEXT) KeyboardType.Text else KeyboardType.Uri,
                    autoCorrectEnabled = false,
                ),
                trailingIcon = when {
                    q.secret -> ({
                        IconButton(onClick = { reveal = !reveal }) {
                            Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(if (reveal) R.string.form_hide else R.string.form_show))
                        }
                    })
                    kind == FieldKind.HOSTPATH && enabled -> ({
                        IconButton(onClick = { onPickPath { p -> onChange(JsonPrimitive(p)) } }) {
                            Icon(Icons.Outlined.Folder, stringResource(R.string.share_pick_dataset))
                        }
                    })
                    else -> null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        FieldKind.DICT -> {
            val obj = value as? JsonObject ?: (q.initialValue() as? JsonObject) ?: JsonObject(emptyMap())
            val children = q.attrs.filter { !it.hidden && AppForm.visible(it, obj) }
            if (children.isEmpty()) return
            Column(Modifier.fillMaxWidth().padding(start = if (depth > 0) 12.dp else 0.dp)) {
                CtrlLabel(q.label, Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp))
                if (q.description != null) {
                    Text(q.description, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 16.dp))
                }
                children.forEach { child ->
                    QuestionField(child, obj[child.variable] ?: child.initialValue(), "$path.${child.variable}", depth + 1, onPickPath, rawErrors) { nv ->
                        onChange(JsonObject(obj + (child.variable to nv)))
                    }
                }
            }
        }
        FieldKind.LIST -> {
            val list = (value as? JsonArray)?.toList().orEmpty()
            val item = q.item!!
            Column(Modifier.fillMaxWidth().padding(start = if (depth > 0) 12.dp else 0.dp)) {
                CtrlLabel(q.label, Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp))
                if (q.description != null) {
                    Text(q.description, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, modifier = Modifier.padding(horizontal = 16.dp))
                }
                list.forEachIndexed { i, e ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).border(1.dp, CtrlColors.CardBorder, CtrlShapes.Control),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(stringResource(R.string.form_item, item.label, i + 1), style = MaterialTheme.typography.titleSmall)
                            if (enabled) {
                                IconButton(onClick = { onChange(JsonArray(list.filterIndexed { j, _ -> j != i })) }) {
                                    Icon(Icons.Outlined.Delete, stringResource(R.string.form_remove), tint = Health.critical)
                                }
                            }
                        }
                        QuestionField(item, e, "$path[$i]", depth + 1, onPickPath, rawErrors) { nv ->
                            onChange(JsonArray(list.mapIndexed { j, old -> if (j == i) nv else old }))
                        }
                    }
                }
                if (enabled && (q.max == null || list.size < q.max!!)) {
                    CtrlOutlinedButton(
                        onClick = { onChange(JsonArray(list + item.initialValue())) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Text(stringResource(R.string.form_add, item.label), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        FieldKind.RAW -> FieldPad {
            var text by remember(path) { mutableStateOf(AppForm.rawText(value)) }
            var bad by remember(path) { mutableStateOf(false) }
            CtrlTextField(
                value = text,
                onValueChange = { t ->
                    text = t
                    val parsed = AppForm.parseRaw(t, q.type)
                    bad = parsed == null
                    rawErrors[path] = bad
                    if (parsed != null) onChange(parsed)
                },
                label = { Text(q.label) },
                minLines = 2,
                enabled = enabled,
                isError = bad,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                supportingText = {
                    Text(
                        listOf(
                            if (bad) stringResource(R.string.form_json) else stringResource(R.string.form_raw_hint, q.type),
                            help,
                        ).filter { it.isNotBlank() }.joinToString("  "),
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun fieldHelp(help: String, problem: AppForm.Problem?, q: AppQuestion): (@Composable () -> Unit)? {
    if (help.isBlank() && problem == null) return null
    return {
        val range = when {
            problem == AppForm.Problem.RANGE && q.kind == FieldKind.INT -> listOfNotNull(q.min?.toString() ?: "…", q.max?.toString() ?: "…").joinToString("–")
            problem == AppForm.Problem.LENGTH -> listOfNotNull(q.minLength?.toString() ?: "0", q.maxLength?.toString() ?: "…").joinToString("–")
            else -> null
        }
        val p = problem?.let { problemText(it) + (range?.let { r -> " ($r)" } ?: "") }
        Text(listOfNotNull(p, help.ifBlank { null }).joinToString("  "))
    }
}
