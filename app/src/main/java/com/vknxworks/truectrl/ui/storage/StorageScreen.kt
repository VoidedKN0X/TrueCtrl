package com.vknxworks.truectrl.ui.storage

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.ui.components.TabPlaceholder

/** Pools, datasets and disks. Filled in once server setup lands. */
@Composable
fun StorageScreen() {
    TabPlaceholder(R.string.tab_storage, R.string.storage_empty_hint, Icons.Outlined.Storage)
}
