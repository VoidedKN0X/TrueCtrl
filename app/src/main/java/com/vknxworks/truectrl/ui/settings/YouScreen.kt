package com.vknxworks.truectrl.ui.settings

import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Visibility
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.BuildConfig
import com.vknxworks.truectrl.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import com.vknxworks.truectrl.ui.components.CtrlIconButton
import com.vknxworks.truectrl.ui.components.CtrlLabel
import com.vknxworks.truectrl.ui.components.PillButton
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.CtrlType
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.ui.components.GroupSwitch
import com.vknxworks.truectrl.ui.nav.ServersRoute
import com.vknxworks.truectrl.ui.setup.AccessSummary
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.ui.components.Group
import com.vknxworks.truectrl.ui.components.GroupRow
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.nav.SettingsRoute
import com.vknxworks.truectrl.ui.theme.Gutter

private const val SPONSOR_URL = "https://github.com/sponsors/VoidedKN0X"
private const val REPO_URL = "https://github.com/VoidedKN0X/TrueCtrl"
private const val ISSUES_URL = "https://github.com/VoidedKN0X/TrueCtrl/issues"
private const val LICENCE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/**
 * Account and settings, opened from the profile picture on any tab. Shows which server you're
 * connected to (once server setup exists), settings, and the About / support section.
 */
@Composable
fun YouScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val access by c.sessions.access.collectAsStateWithLifecycle()
    val server = active?.server
    fun open(url: String) {
        try {
            com.vknxworks.truectrl.ui.lock.AppLock.expectReturn(); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Close button and a mono page label, like the Ctrl profile render.
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlIconButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = nav::back)
            CtrlLabel(stringResource(R.string.you_title), Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally))
            Spacer(Modifier.width(44.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(CtrlColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    if (server == null) {
                        Icon(Icons.Outlined.Storage, null, Modifier.size(30.dp), tint = CtrlColors.OnAccent)
                    } else {
                        Text(server.initial.uppercase(), style = MaterialTheme.typography.headlineSmall, color = CtrlColors.OnAccent)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        server?.name ?: stringResource(R.string.you_not_connected),
                        style = MaterialTheme.typography.headlineSmall,
                        color = CtrlColors.Text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (server == null) {
                        Text(stringResource(R.string.you_not_connected_body), style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted)
                    } else {
                        Text(server.host, style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val meta = listOfNotNull(
                            (access.capabilities?.version?.label ?: server.tnVersion).takeIf { it.isNotBlank() }?.let { stringResource(R.string.tn_version, it) },
                            authSummary(server),
                        ).joinToString("\n")
                        if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted)
                    }
                }
            }

            if (server != null) {
                Group {
                    GroupRow(
                        stringResource(R.string.servers_title),
                        stringResource(R.string.servers_subtitle),
                        icon = Icons.Outlined.SwapHoriz,
                        showChevron = true,
                        onClick = { nav.navigate(ServersRoute) },
                    )
                    GroupSwitch(
                        stringResource(R.string.read_only_mode),
                        stringResource(R.string.read_only_mode_subtitle),
                        checked = server.readOnly,
                        icon = Icons.Outlined.Visibility,
                    ) { c.sessions.setReadOnly(server.id, it) }
                }
                access.capabilities?.let { AccessSummary(it) }
            }

            Group {
                GroupRow(
                    stringResource(R.string.settings),
                    stringResource(R.string.settings_subtitle),
                    icon = Icons.Outlined.Settings,
                    showChevron = true,
                    onClick = { nav.navigate(SettingsRoute) },
                )
            }

            Group(stringResource(R.string.group_about)) {
                GroupRow(
                    stringResource(R.string.bugs_title),
                    stringResource(R.string.bugs_subtitle),
                    icon = Icons.Outlined.BugReport,
                    showChevron = true,
                    onClick = { open(ISSUES_URL) },
                )
                GroupRow(
                    stringResource(R.string.source_title),
                    stringResource(R.string.source_subtitle),
                    icon = Icons.Outlined.Code,
                    showChevron = true,
                    onClick = { open(REPO_URL) },
                )
                GroupRow(
                    stringResource(R.string.licence_title),
                    stringResource(R.string.licence_subtitle),
                    icon = Icons.Outlined.Gavel,
                    showChevron = true,
                    onClick = { open(LICENCE_URL) },
                )
            }

            Spacer(Modifier.height(12.dp))
            PillButton(
                stringResource(R.string.support_title),
                onClick = { open(SPONSOR_URL) },
                icon = Icons.Outlined.FavoriteBorder,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
            )
            Text(
                stringResource(R.string.support_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.you_footer_badges).uppercase(),
                style = CtrlType.Label.copy(letterSpacing = 0.12.em),
                color = CtrlColors.Muted,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.about_footer, stringResource(R.string.app_name), BuildConfig.VERSION_NAME, stringResource(R.string.vendor)),
                style = CtrlType.Mono,
                color = CtrlColors.Muted,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
