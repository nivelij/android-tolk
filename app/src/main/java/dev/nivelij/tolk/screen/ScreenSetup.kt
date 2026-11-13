package dev.nivelij.tolk.screen

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityManager
import dev.nivelij.tolk.R

fun screenServiceEnabled(context: Context): Boolean =
    context.getSystemService(AccessibilityManager::class.java)
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { it.resolveInfo.serviceInfo.packageName == context.packageName }

fun openScreenServiceSettings(context: Context) {
    val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
        .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, ScreenTranslatorService::class.java).flattenToString())
    runCatching { context.startActivity(details) }
        .onFailure { context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
}

fun refreshBubbleTile(context: Context) =
    TileService.requestListeningState(context, ComponentName(context, BubbleTileService::class.java))

fun requestAddBubbleTile(context: Context, onResult: (Boolean) -> Unit) {
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, BubbleTileService::class.java),
        context.getString(R.string.tile_label),
        Icon.createWithResource(context, R.drawable.ic_tile),
        context.mainExecutor,
    ) { result ->
        onResult(result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
    }
}
