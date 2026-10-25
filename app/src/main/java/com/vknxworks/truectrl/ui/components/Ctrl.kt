package com.vknxworks.truectrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.vknxworks.truectrl.ui.theme.CtrlColors
import com.vknxworks.truectrl.ui.theme.CtrlShapes
import com.vknxworks.truectrl.ui.theme.CtrlType
import com.vknxworks.truectrl.ui.theme.Gutter

// The Ctrl / Signal building blocks shared by every screen.

/** The "TRUE/CTRL" wordmark: JetBrains Mono, wide tracking, the slash in the accent. */
@Composable
fun CtrlWordmark(name: String, suffix: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(name.uppercase())
            withStyle(SpanStyle(color = CtrlColors.AccentText)) { append("/") }
            append(suffix.uppercase())
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        modifier = modifier,
    )
}

/** A section label: JetBrains Mono 11sp, upper case, wide tracking, muted. */
@Composable
fun CtrlLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** A section heading row: title on the left, an optional mono readout on the right. */
@Composable
fun CtrlSectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = CtrlColors.Text, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing.uppercase(), style = CtrlType.Mono.copy(fontSize = CtrlType.Label.fontSize), color = CtrlColors.Muted)
    }
}

/** Status dot: an 8dp square with 2dp corners. */
@Composable
fun StatusSquare(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CtrlShapes.Dot).background(color))
}

/** Secondary card: surface fill, 1dp border, 22dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = CtrlShapes.Card,
        color = CtrlColors.Surface,
        border = CtrlColors.CardStroke,
        modifier = modifier
            .clip(CtrlShapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** The hero card: accent fill with dark content, for the one most important number on a screen. */
@Composable
fun CtrlHeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = CtrlShapes.Card,
        color = CtrlColors.Accent,
        contentColor = CtrlColors.OnAccent,
        modifier = modifier
            .clip(CtrlShapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** A metric card: mono label, a value with a muted unit, and an optional bar. */
@Composable
fun CtrlMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    fraction: Double? = null,
    barColor: Color = CtrlColors.Text,
    onClick: (() -> Unit)? = null,
) {
    CtrlCard(modifier, onClick = onClick) {
        CtrlLabel(label)
        Spacer(Modifier.height(10.dp))
        Text(
            buildAnnotatedString {
                append(value)
                if (!unit.isNullOrBlank()) {
                    withStyle(SpanStyle(fontSize = MaterialTheme.typography.bodyMedium.fontSize, color = CtrlColors.Muted, letterSpacing = MaterialTheme.typography.bodyMedium.letterSpacing)) {
                        append(" ")
                        append(unit)
                    }
                }
            },
            style = CtrlType.Metric,
            color = CtrlColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (fraction != null) {
            Spacer(Modifier.height(10.dp))
            UsageBar(fraction, barColor, height = 6.dp)
        }
    }
}

/**
 * A plain list row: 52dp minimum, a 1dp top divider (except the first), leading mono meta,
 * a 16sp title, and a trailing mono status with a square dot.
 */
@Composable
fun CtrlListRow(
    title: String,
    modifier: Modifier = Modifier,
    leading: String? = null,
    subtitle: String? = null,
    status: String? = null,
    statusColor: Color = CtrlColors.AccentText,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.topDivider() else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                Text(leading, style = CtrlType.Mono, color = CtrlColors.Muted, modifier = Modifier.width(44.dp), maxLines = 1)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = CtrlColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = CtrlType.Mono, color = CtrlColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (status != null) {
                Spacer(Modifier.width(12.dp))
                Text(status.uppercase(), style = CtrlType.Status, color = statusColor, maxLines = 1)
                Spacer(Modifier.width(10.dp))
                StatusSquare(statusColor)
            }
        }
        if (below != null) {
            Spacer(Modifier.height(8.dp))
            below()
        }
    }
}

/** A 1dp list divider along the top edge. */
@Composable
fun Modifier.topDivider(): Modifier = topDivider(CtrlColors.Divider)

/** A 1dp divider of [color] along the top edge. */
fun Modifier.topDivider(color: Color): Modifier = drawWithContent {
    drawContent()
    drawLine(color, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx())
}

/**
 * A 1dp divider along the top edge of a row in a grouped card, left out for the first row of the
 * card (the one placed at the top of its parent).
 */
@Composable
fun Modifier.groupDivider(color: Color = CtrlColors.CardBorder): Modifier {
    var first by remember { mutableStateOf(true) }
    return this
        .onPlaced { first = it.positionInParent().y < 1f }
        .drawWithContent {
            drawContent()
            if (!first) drawLine(color, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx())
        }
}

/**
 * The Ctrl switch: a 52x32dp rounded rectangle with a 24dp knob. On: accent track, dark knob.
 * Off: dark track, grey knob. Announced as a switch to TalkBack.
 */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchOff, label = "switch-track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.SwitchKnobOff, label = "switch-knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch-x")
    val base = modifier
        .size(width = 52.dp, height = 32.dp)
        .clip(RoundedCornerShape10)
        .background(if (enabled) track else track.copy(alpha = 0.4f))
    val interactive = if (onCheckedChange != null) {
        base.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        base
    }
    Box(interactive.padding(4.dp)) {
        Box(
            Modifier
                .offset(x = x)
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                .background(if (enabled) knob else knob.copy(alpha = 0.5f)),
        )
    }
}

private val RoundedCornerShape10 = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

/** Primary button: accent fill, dark text, 18dp corners, 56dp tall. */
@Composable
fun CtrlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = CtrlShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = CtrlColors.Accent,
            contentColor = CtrlColors.OnAccent,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Inactive,
        ),
        contentPadding = contentPadding,
        content = content,
    )
}

/** Secondary button: surface fill with a 1dp border, 16dp corners. */
@Composable
fun CtrlOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = CtrlShapes.Field,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CtrlColors.Surface,
            contentColor = CtrlColors.Text,
            disabledContainerColor = CtrlColors.Surface,
            disabledContentColor = CtrlColors.Inactive,
        ),
        border = BorderStroke(1.dp, CtrlColors.ControlBorder),
        contentPadding = contentPadding,
        content = content,
    )
}

/** Text field: surface fill, 1dp border, 16dp corners; accent border and cursor when focused. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = CtrlShapes.Field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CtrlColors.Surface,
            unfocusedContainerColor = CtrlColors.Surface,
            disabledContainerColor = CtrlColors.Surface,
            errorContainerColor = CtrlColors.Surface,
            focusedBorderColor = CtrlColors.AccentText,
            unfocusedBorderColor = CtrlColors.ControlBorder,
            disabledBorderColor = CtrlColors.CardBorder,
            cursorColor = CtrlColors.AccentText,
            focusedLabelColor = CtrlColors.AccentText,
            unfocusedLabelColor = CtrlColors.Muted,
        ),
    )
}

/** Dialog: surface fill, 26dp corners and a 1dp border. Same slots as Material's AlertDialog. */
@Composable
fun CtrlDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, CtrlColors.CardBorder, CtrlShapes.Dialog),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = CtrlShapes.Dialog,
        containerColor = CtrlColors.Surface,
        iconContentColor = CtrlColors.AccentText,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
        properties = properties,
    )
}

/** Progress bar: 6dp tall, 3dp corners, light fill on a dark track. Null [progress] is indeterminate. */
@Composable
fun CtrlProgressBar(progress: (() -> Float)?, modifier: Modifier = Modifier, color: Color = CtrlColors.Text) {
    val m = modifier.fillMaxWidth().height(6.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
    if (progress != null) {
        LinearProgressIndicator(
            progress = progress,
            modifier = m,
            color = color,
            trackColor = CtrlColors.Track,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    } else {
        LinearProgressIndicator(
            modifier = m,
            color = color,
            trackColor = CtrlColors.Track,
            strokeCap = StrokeCap.Butt,
            gapSize = 0.dp,
        )
    }
}

/** Segmented control shape: 14dp corners on the outer ends. */
@Composable
fun ctrlSegmentShape(index: Int, count: Int) =
    SegmentedButtonDefaults.itemShape(index = index, count = count, baseShape = CtrlShapes.Control)

/** Segmented control colours: accent for the selected part, surface for the rest. */
@Composable
fun ctrlSegmentColors() = SegmentedButtonDefaults.colors(
    activeContainerColor = CtrlColors.Accent,
    activeContentColor = CtrlColors.OnAccent,
    activeBorderColor = CtrlColors.ControlBorder,
    inactiveContainerColor = CtrlColors.Surface,
    inactiveContentColor = CtrlColors.Muted,
    inactiveBorderColor = CtrlColors.ControlBorder,
)

/** Page padding for cards placed directly in a list. */
val CardMargin = PaddingValues(horizontal = Gutter, vertical = 6.dp)
