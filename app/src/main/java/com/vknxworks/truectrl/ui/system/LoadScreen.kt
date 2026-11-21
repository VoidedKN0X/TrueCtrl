package com.vknxworks.truectrl.ui.system

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.ui.components.ConnectionBanner
import com.vknxworks.truectrl.ui.components.EmptyState
import com.vknxworks.truectrl.ui.components.PollWhileVisible
import com.vknxworks.truectrl.ui.components.ScreenTopBar
import com.vknxworks.truectrl.ui.components.errorMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What a [LoadScreen]'s content can do: reload, and run a change with a message afterwards. */
class LoadScope(
    private val context: Context,
    private val scope: CoroutineScope,
    private val snackbar: SnackbarHostState,
    private val host: () -> String,
    private val reload: suspend () -> Boolean,
) {
    /** A change is being sent: buttons should wait. */
    var busy by mutableStateOf(false)
        private set

    /** Runs [action], reads the screen again, then says [done] or why it didn't work. */
    fun change(done: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
                reload()
                snackbar.showSnackbar(done)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar(errorMessage(context, e, host()))
            } finally {
                busy = false
            }
        }
    }

    fun refresh() {
        scope.launch { reload() }
    }
}

/**
 * A pushed System screen that reads one thing from TrueNAS when it opens (and every [pollMs] while
 * it's on screen, when given): top bar, connection banner, pull to refresh, the loading and
 * failed states, and a snackbar for the result of changes. [content] draws the loaded value and
 * scrolls itself. [key] reloads from scratch when it changes (a filter, for example).
 */
@Composable
fun <T : Any> LoadScreen(
    title: String,
    icon: ImageVector,
    failedTitle: String,
    load: suspend () -> T,
    key: Any? = null,
    pollMs: Long = 60_000L,
    actions: @Composable RowScope.() -> Unit = {},
    /** Stays on screen above the content while loading and after a failure (filters, for example). */
    header: @Composable () -> Unit = {},
    content: @Composable LoadScope.(T) -> Unit,
) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val server = active?.server
    var value by remember(server?.id, key) { mutableStateOf<T?>(null) }
    var error by remember(server?.id, key) { mutableStateOf<Throwable?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun reload(): Boolean = try {
        value = load()
        error = null
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        error = e
        false
    }

    PollWhileVisible(listOf(server?.id, access.generation, key), pollMs) {
        if (access.connected) reload() else false
    }
    // The scope outlives recompositions; it always calls the newest reload (a changed filter makes a new one).
    val currentReload by rememberUpdatedState<suspend () -> Boolean>({ reload() })
    val loadScope = remember(server?.id) { LoadScope(context, scope, snackbar, { c.sessions.active.value?.server?.host.orEmpty() }, { currentReload() }) }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title, actions = actions)
        if (!access.connected && access.error != null) ConnectionBanner()
        header()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    reload()
                    refreshing = false
                }
            },
            modifier = Modifier.weight(1f),
        ) {
            val v = value
            val e = error
            when {
                v != null -> loadScope.content(v)
                e != null -> EmptyState(icon, failedTitle, errorMessage(context, e, server?.host.orEmpty()))
                else -> EmptyState(icon, stringResource(R.string.overview_loading), "")
            }
        }
        SnackbarHost(snackbar)
    }
}

/** One action in a bottom sheet: what it does and, when given, a line on what that means. */
@Composable
fun SheetAction(title: String, subtitle: String? = null, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The title of a bottom sheet. */
@Composable
fun SheetTitle(title: String, subtitle: String? = null) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    if (subtitle != null) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
    }
}

/** One labelled value in a detail sheet: a small label with the value under it. */
@Composable
fun SheetFact(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
