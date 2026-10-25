package com.vknxworks.truectrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.container
import com.vknxworks.truectrl.ui.nav.LocalNavigator
import com.vknxworks.truectrl.ui.nav.YouRoute
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.CtrlShapes
import com.vknxworks.truectrl.ui.theme.Gutter

// ---------- Top bars ----------

/** Square 44dp icon button with the Ctrl border, used for back, close and screen actions. */
@Composable
fun CtrlIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CtrlColors.Text,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CtrlShapes.Control)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) tint else CtrlColors.Inactive)
    }
}

/**
 * Top bar for pushed screens: the square back button and the title in Space Grotesk.
 * No colour fill; it sits on the page background. Actions keep their own icon buttons.
 */
@Composable
fun ScreenTopBar(
    title: String,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val nav = LocalNavigator.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(CtrlColors.Background)
            .statusBarsPadding()
            .padding(start = Gutter, end = 12.dp, top = 12.dp, bottom = 10.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CtrlIconButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = nav::back)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = CtrlColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CompositionLocalProvider(LocalContentColor provides CtrlColors.Text) { actions() }
    }
}

/** The main tabs' top bar: the TRUE/CTRL wordmark on the left, the profile picture on the right. */
@Composable
fun CtrlTopBar(
    modifier: Modifier = Modifier,
    showProfile: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        val appName = stringResource(R.string.app_name)
        CtrlWordmark("TRUE", "CTRL", Modifier.weight(1f).semantics { contentDescription = appName })
        CompositionLocalProvider(LocalContentColor provides CtrlColors.Text) { trailing() }
        if (showProfile) ProfileButton()
    }
}

/**
 * The main tabs' header: the TRUE/CTRL wordmark and the profile picture, then the tab's name as a
 * big headline below.
 */
@Composable
fun TabTitle(
    title: String,
    modifier: Modifier = Modifier,
    showProfile: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 10.dp)) {
        CtrlTopBar(showProfile = showProfile, trailing = trailing)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The profile picture: a 44dp rounded square with the server's initial and a small accent badge
 * while connected. Tapping it opens the You page with the server, Settings and About (there is no settings tab).
 */
@Composable
fun ProfileButton() {
    val nav = LocalNavigator.current
    val sessions = LocalContext.current.container.sessions
    val active by sessions.active.collectAsStateWithLifecycle()
    val access by sessions.access.collectAsStateWithLifecycle()
    val initial = active?.server?.initial
    val label = stringResource(R.string.you_title)
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShapes.Control)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Control)
                .clickable(role = Role.Button) { nav.navigate(YouRoute) }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (initial.isNullOrBlank()) {
                Icon(Icons.Outlined.Person, null, Modifier.size(20.dp), tint = CtrlColors.Text)
            } else {
                Text(initial.take(1).uppercase(), style = MaterialTheme.typography.titleSmall, color = CtrlColors.Text)
            }
        }
        if (access.connected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CtrlColors.Background)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CtrlColors.Accent),
            )
        }
    }
}

// ---------- Grouped lists (settings, account, detail pages) ----------

/** A rounded block of related rows, with an optional heading above it. */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        if (title != null) CtrlLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        Surface(
            shape = CtrlShapes.Card,
            color = CtrlColors.Surface,
            border = CtrlColors.CardStroke,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = CtrlColors.Muted,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = CtrlColors.Text,
    titleColor: Color = CtrlColors.Text,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .groupDivider()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 62.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = titleColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CtrlColors.Muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (showChevron) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = CtrlColors.Muted)
        }
    }
}

@Composable
fun GroupSwitch(title: String, subtitle: String? = null, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    // The row is the toggle (one TalkBack stop announced as a switch); the switch only draws it.
    Row(
        Modifier
            .fillMaxWidth()
            .groupDivider()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 62.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = CtrlColors.Text)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = CtrlColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = CtrlColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        CtrlSwitch(checked, onCheckedChange = null)
    }
}

/** A row showing the current value; tapping opens a sheet to pick another. */
@Composable
fun <T> GroupChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    icon: ImageVector? = null,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    GroupRow(title, current, icon, showChevron = true, onClick = { open = true })
    if (open) {
        ChoiceSheet(title, options, selected, onDismiss = { open = false }) { onPick(it); open = false }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    CtrlSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                val isSel = value == selected
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(value) }.heightIn(min = 52.dp).padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSel) CtrlColors.AccentText else CtrlColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) Icon(Icons.Outlined.Check, stringResource(R.string.selected), Modifier.size(20.dp), tint = CtrlColors.AccentText)
                }
            }
        }
    }
}

// ---------- Buttons ----------

/** The one main action on a screen: accent fill, dark text, 56dp tall. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    CtrlButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Bottom sheet: surface fill, 26dp top corners and a hairline border. */
@Composable
fun CtrlSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        shape = CtrlShapes.Sheet,
        containerColor = CtrlColors.Surface,
        contentColor = CtrlColors.Text,
        tonalElevation = 0.dp,
        scrimColor = CtrlColors.Scrim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CtrlColors.ControlBorder),
            )
        },
        modifier = Modifier.border(1.dp, CtrlColors.CardBorder, CtrlShapes.Sheet),
        content = content,
    )
}

// ---------- Empty states ----------

/** Centred icon, title and explanation for a screen with nothing to show yet. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(CtrlColors.Surface).border(1.dp, CtrlColors.CardBorder, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(30.dp), tint = CtrlColors.AccentText) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = CtrlColors.Muted,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
