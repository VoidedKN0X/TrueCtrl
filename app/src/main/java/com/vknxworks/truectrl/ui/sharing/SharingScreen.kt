package com.vknxworks.truectrl.ui.sharing

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderShared
import androidx.compose.runtime.Composable
import com.vknxworks.truectrl.R
import com.vknxworks.truectrl.ui.components.TabPlaceholder

/** SMB and NFS shares and services. Filled in once server setup lands. */
@Composable
fun SharingScreen() {
    TabPlaceholder(R.string.tab_sharing, R.string.sharing_empty_hint, Icons.Outlined.FolderShared)
}
