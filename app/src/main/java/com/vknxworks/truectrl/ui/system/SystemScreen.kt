package com.vknxworks.truectrl.ui.system

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.runtime.Composable
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.ui.components.TabPlaceholder

/** Apps, alerts, jobs, updates, network and power. Filled in once server setup lands. */
@Composable
fun SystemScreen() {
    TabPlaceholder(R.string.tab_system, R.string.system_empty_hint, Icons.Outlined.Dns)
}
