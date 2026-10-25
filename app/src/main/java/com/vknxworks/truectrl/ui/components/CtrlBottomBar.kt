package com.vknxworks.truectrl.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.truectrl.ui.theme.CtrlColors

/**
 * Space taken at the bottom of the screen by the floating bar. Scrolling screens add it to their
 * bottom content padding, so their last items can scroll clear of the bar.
 */
val LocalBottomClearance = compositionLocalOf { 0.dp }

data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val BarShape = RoundedCornerShape(22.dp)
private val ItemShape = RoundedCornerShape(16.dp)

/**
 * The Ctrl floating bottom bar: 64dp tall, 22dp corners, a translucent surface with a hairline
 * border. The selected tab is an accent block with its icon and name; the others show their icon only.
 */
@Composable
fun CtrlBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(BarShape)
            .background(CtrlColors.Bar.copy(alpha = 0.92f))
            .border(1.dp, CtrlColors.ControlBorder, BarShape)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            Box(
                // The selected tab takes what's left and shortens its name if it has to.
                (if (selected) Modifier.weight(1f, fill = false) else Modifier)
                    .height(48.dp)
                    .widthIn(min = 48.dp)
                    .clip(ItemShape)
                    .background(if (selected) CtrlColors.Accent else Color.Transparent)
                    .selectable(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                    )
                    // The selected tab shows its name; the others are named for TalkBack.
                    .then(if (selected) Modifier else Modifier.semantics { contentDescription = item.label })
                    .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(item.selectedIcon, null, Modifier.size(20.dp), tint = CtrlColors.OnAccent)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = CtrlColors.OnAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Icon(item.icon, null, Modifier.size(22.dp), tint = CtrlColors.Muted)
                }
            }
        }
    }
}
