package com.vknxworks.truectrl.ui.widget

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vknxworks.truectrl.MainActivity
import com.vknxworks.truectrl.R

/** Quick Settings tile with the selected server's overall health; tapping it opens TrueCtrl. */
class HealthTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val s = currentSummary(this)
        if (s == null) {
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

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(openApp(this))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
