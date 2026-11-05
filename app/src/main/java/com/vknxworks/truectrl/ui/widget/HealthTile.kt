package com.vknxworks.truectrl.ui.widget

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vknxworks.truectrl.R

/** Quick Settings tile with the selected server's overall health; tapping it opens TrueCtrl. */
class HealthTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val locked = appLocked(this)
        val s = if (locked) null else currentSummary(this)
        if (locked) {
            tile.label = getString(R.string.app_name)
            tile.subtitle = getString(R.string.widget_locked_short)
            tile.state = Tile.STATE_INACTIVE
        } else if (s == null) {
            tile.label = getString(R.string.app_name)
            tile.subtitle = getString(R.string.widget_no_data_short)
            tile.state = Tile.STATE_INACTIVE
        } else {
            tile.label = s.serverName
            tile.subtitle = when {
                s.unreachable -> getString(R.string.tile_unreachable)
                s.critical + s.warning == 0 -> getString(R.string.widget_all_good)
                else -> resources.getQuantityString(R.plurals.widget_problems, s.critical + s.warning, s.critical + s.warning)
            }
            // Active (highlighted) when something needs a look.
            tile.state = if (!s.unreachable && s.critical + s.warning == 0) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        startActivityAndCollapse(openApp(this))
    }
}
