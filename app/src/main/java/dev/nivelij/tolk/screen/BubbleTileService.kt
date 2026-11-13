package dev.nivelij.tolk.screen

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.nivelij.tolk.MainActivity
import dev.nivelij.tolk.data.Settings
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BubbleTileService : TileService() {
    private val scope = MainScope()
    private val settings by lazy { Settings(this) }

    override fun onStartListening() {
        scope.launch { render(settings.prefs.first().showBubble) }
    }

    override fun onClick() {
        if (!screenServiceEnabled(this)) return openSetup()
        scope.launch {
            val show = !settings.prefs.first().showBubble
            settings.setShowBubble(show)
            render(show)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(show: Boolean) {
        val tile = qsTile ?: return
        val ready = screenServiceEnabled(this)
        tile.state = if (ready && show) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            !ready -> "Tap to set up"
            show -> "Button on"
            else -> "Button off"
        }
        tile.updateTile()
    }

    private fun openSetup() {
        val intent = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
