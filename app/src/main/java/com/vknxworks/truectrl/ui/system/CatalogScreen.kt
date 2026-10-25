package com.vknxworks.truectrl.ui.system

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.data.api.TnError
import com.vknxworks.truectrl.data.system.CatalogApp
import com.vknxworks.truectrl.data.system.CatalogDetail
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.CtrlOutlinedButton
import com.vknxworks.truectrl.ui.components.CtrlTextField
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.components.PillButton
import com.vknxworks.truectrl.ui.components.RemoteImage
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import com.vknxworks.truectrl.ui.nav.AppInstallRoute
import com.vknxworks.truectrl.ui.nav.AppsRoute
import com.vknxworks.truectrl.ui.nav.CatalogAppRoute
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.overview.alertText
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.CtrlShapes
import com.vknxworks.truectrl.ui.theme.Gutter
import kotlinx.coroutines.launch

/** The TrueNAS app catalog: search, categories, and the way in to an app's page. */
@Composable
fun CatalogScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    var apps by remember { mutableStateOf(c.catalog.cached()) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            apps = c.catalog.available()
            categories = c.catalog.categories().ifEmpty { apps.orEmpty().flatMap { it.categories }.distinct().sorted() }
            error = null
        } catch (e: TnError) {
            error = e
        }
    }

    LaunchedEffect(server?.id, access.generation) { if (access.connected) load() }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.catalog_title))
        if (!access.connected && access.error != null) ConnectionBanner()
        CtrlTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.catalog_search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.catalog_search_clear)) } }) else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
        )
        if (categories.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Gutter, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") {
                    FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.catalog_all)) })
                }
                items(categories, key = { it }) { cat ->
                    FilterChip(selected = category == cat, onClick = { category = if (category == cat) null else cat }, label = { Text(categoryLabel(cat)) })
                }
            }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    load()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            val list = apps
            val q = query.trim().lowercase()
            val cat = category
            val shown = list.orEmpty().filter { a ->
                (cat == null || cat in a.categories) &&
                    (q.isEmpty() || a.title.lowercase().contains(q) || a.name.contains(q) || a.description.lowercase().contains(q) || a.tags.any { it.lowercase().contains(q) })
            }
            when {
                list == null && error != null -> EmptyState(Icons.Outlined.Apps, stringResource(R.string.catalog_failed), errorMessage(context, error!!, server?.host.orEmpty()))
                list == null -> EmptyState(Icons.Outlined.Apps, stringResource(R.string.overview_loading), stringResource(R.string.catalog_loading_hint))
                shown.isEmpty() -> EmptyState(Icons.Outlined.Search, stringResource(R.string.catalog_none), stringResource(R.string.catalog_none_body))
                else -> LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "list") {
                        Group(footer = stringResource(R.string.catalog_footer, shown.size)) {
                            shown.forEach { a ->
                                GroupRow(
                                    title = a.title,
                                    subtitle = listOfNotNull(
                                        a.description.takeIf { it.isNotBlank() },
                                        listOfNotNull(
                                            a.appVersion,
                                            a.train.takeIf { it != "stable" },
                                            if (a.installed) stringResource(R.string.catalog_installed) else null,
                                        ).joinToString("  ·  ").ifBlank { null },
                                    ).joinToString("\n"),
                                    titleColor = CtrlColors.Text,
                                    showChevron = true,
                                    onClick = { nav.navigate(CatalogAppRoute(a.name, a.train)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Catalog categories are lowercase ids like "media" or "home-automation". */
internal fun categoryLabel(cat: String): String =
    cat.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }

/** One catalog app: what it is, its version and train, links, optional images, and Install. */
@Composable
fun CatalogAppScreen(name: String, train: String) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    val caps = access.capabilities
    var detail by remember { mutableStateOf<CatalogDetail?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    // Icons and screenshots come from the catalog's own servers, not from TrueNAS: only loaded when asked.
    var showImages by rememberSaveable { mutableStateOf(false) }
    val summary = c.catalog.cached()?.firstOrNull { it.name == name && it.train == train }
    val canInstall = caps?.has("app.create") == true && c.sessions.canWrite("APPS_WRITE")

    LaunchedEffect(server?.id, access.generation) {
        if (!access.connected) return@LaunchedEffect
        try {
            detail = c.catalog.details(name, train)
            error = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e
        }
    }

    val app: CatalogApp? = detail?.app ?: summary
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(app?.title ?: name)
        if (!access.connected && access.error != null) ConnectionBanner()
        if (app == null) {
            EmptyState(
                Icons.Outlined.Apps,
                stringResource(if (error != null) R.string.catalog_app_failed else R.string.overview_loading),
                error?.let { errorMessage(context, it, server?.host.orEmpty()) }.orEmpty(),
                Modifier.weight(1f),
            )
            return@Column
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(CtrlShapes.Control).background(CtrlColors.Surface).border(1.dp, CtrlColors.CardBorder, CtrlShapes.Control),
                    contentAlignment = Alignment.Center,
                ) {
                    val icon = app.iconUrl
                    if (showImages && icon != null) {
                        RemoteImage(c.http, icon, null, Modifier.size(52.dp)) { Icon(Icons.Outlined.Apps, null, tint = CtrlColors.AccentText) }
                    } else {
                        Icon(Icons.Outlined.Apps, null, Modifier.size(28.dp), tint = CtrlColors.AccentText)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(app.appVersion, app.train, if (app.installed) stringResource(R.string.catalog_installed) else null).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                    )
                }
            }
            if (app.description.isNotBlank()) {
                Text(app.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp))
            }
            val d = detail
            if (canInstall) {
                PillButton(
                    text = stringResource(R.string.catalog_install),
                    icon = Icons.Outlined.Download,
                    onClick = { nav.navigate(AppInstallRoute(name, train)) },
                    enabled = d != null && d.versions.isNotEmpty() && access.connected,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                )
                if (app.installed) {
                    Text(
                        stringResource(R.string.catalog_installed_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = CtrlColors.Muted,
                        modifier = Modifier.padding(horizontal = Gutter),
                    )
                }
            } else {
                Text(
                    stringResource(if (server?.readOnly == true) R.string.read_only_hint else R.string.catalog_role_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 12.dp),
                )
            }
            if (app.installed) {
                Group {
                    GroupRow(stringResource(R.string.catalog_open_installed), null, Icons.Outlined.Apps, showChevron = true, onClick = { nav.navigate(AppsRoute) })
                }
            }
            Group(stringResource(R.string.catalog_about)) {
                GroupRow(stringResource(R.string.catalog_version), listOfNotNull(app.appVersion, app.latestVersion?.let { stringResource(R.string.catalog_chart_version, it) }).joinToString("  ·  "))
                GroupRow(stringResource(R.string.catalog_train), app.train)
                if (app.categories.isNotEmpty()) GroupRow(stringResource(R.string.catalog_categories), app.categories.joinToString(", ") { categoryLabel(it) })
                if (d != null && d.maintainers.isNotEmpty()) GroupRow(stringResource(R.string.catalog_maintainers), d.maintainers.joinToString(", "))
                if (d != null && d.versions.isNotEmpty()) {
                    GroupRow(stringResource(R.string.catalog_versions), d.versions.take(5).joinToString(", ") { it.version })
                }
            }
            if (d != null) {
                val links = listOfNotNull(d.home) + d.sources
                val webLinks = links.distinct().mapNotNull { url -> webUri(url)?.let { url to it } }
                if (webLinks.isNotEmpty()) {
                    Group(stringResource(R.string.catalog_links)) {
                        webLinks.take(6).forEach { (url, uri) ->
                            GroupRow(url.removePrefix("https://").removePrefix("http://"), null, Icons.Outlined.Language, onClick = {
                                runCatching { com.vknxworks.truectrl.ui.lock.AppLock.expectReturn(); context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            })
                        }
                    }
                }
                if (d.screenshots.isNotEmpty() || app.iconUrl != null) {
                    Group(stringResource(R.string.catalog_screenshots), footer = if (!showImages) stringResource(R.string.catalog_images_hint) else null) {
                        if (!showImages) {
                            GroupRow(stringResource(R.string.catalog_show_images), null, Icons.Outlined.Image, onClick = { showImages = true })
                        }
                    }
                    if (showImages && d.screenshots.isNotEmpty()) {
                        LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(d.screenshots.filter { it.startsWith("https://") }.take(8)) { url ->
                                RemoteImage(
                                    c.http,
                                    url,
                                    stringResource(R.string.catalog_screenshot),
                                    Modifier.height(200.dp).width(320.dp).clip(CtrlShapes.Control).border(1.dp, CtrlColors.CardBorder, CtrlShapes.Control),
                                    contentScale = ContentScale.Crop,
                                ) { Icon(Icons.Outlined.Image, null, tint = CtrlColors.Muted) }
                            }
                        }
                    }
                }
                d.readme?.let { html ->
                    Group(stringResource(R.string.catalog_readme)) {
                        Text(
                            alertText(html),
                            style = MaterialTheme.typography.bodySmall,
                            color = CtrlColors.Muted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            } else if (error != null) {
                Text(
                    errorMessage(context, error!!, server?.host.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                )
                CtrlOutlinedButton(onClick = { nav.back() }, modifier = Modifier.padding(horizontal = Gutter)) { Text(stringResource(R.string.back)) }
            }
        }
    }
}
