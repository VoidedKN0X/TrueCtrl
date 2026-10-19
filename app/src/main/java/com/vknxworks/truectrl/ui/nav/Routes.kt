package com.vknxworks.truectrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object OverviewRoute
@Serializable data object StorageRoute
@Serializable data object ProtectionRoute
@Serializable data object SharingRoute
@Serializable data object SystemRoute

@Serializable data object AlertsRoute
@Serializable data class PoolRoute(val id: Int)
@Serializable data class DiskRoute(val name: String)
@Serializable data class DatasetRoute(val id: String)

/** Snapshots of [dataset], or a dataset picker when null. */
@Serializable data class SnapshotsRoute(val dataset: String? = null)

// Account and settings, opened from the profile picture
@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object NotificationsRoute
@Serializable data object ServersRoute
@Serializable data object AddServerRoute
@Serializable data class EditServerRoute(val id: String)
