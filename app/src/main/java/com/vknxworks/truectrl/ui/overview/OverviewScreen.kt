package com.vknxworks.truectrl.ui.overview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.runtime.Composable
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.ui.components.TabPlaceholder

/** System health and the attention list. Filled in once server setup lands. */
@Composable
fun OverviewScreen() {
    TabPlaceholder(R.string.tab_overview, R.string.overview_empty_hint, Icons.Outlined.Dashboard)
}
