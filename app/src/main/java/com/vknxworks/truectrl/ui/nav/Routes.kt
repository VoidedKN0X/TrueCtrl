package com.vknxworks.truectrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object OverviewRoute
@Serializable data object StorageRoute
@Serializable data object ProtectionRoute
@Serializable data object SharingRoute
@Serializable data object SystemRoute

// Account and settings, opened from the profile picture
@Serializable data object YouRoute
@Serializable data object SettingsRoute
