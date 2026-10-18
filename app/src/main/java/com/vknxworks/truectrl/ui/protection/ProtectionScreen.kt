package com.vknxworks.truectrl.ui.protection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.ui.components.TabPlaceholder

/** Snapshots, replication, cloud sync and scrubs. Filled in once server setup lands. */
@Composable
fun ProtectionScreen() {
    TabPlaceholder(R.string.tab_protection, R.string.protection_empty_hint, Icons.Outlined.Shield)
}
