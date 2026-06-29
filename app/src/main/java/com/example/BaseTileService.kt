package com.example

import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.app.PendingIntent
import android.widget.Toast
import android.graphics.drawable.Icon
import android.graphics.BitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class BaseTileService : TileService() {
    abstract val tileId: Int
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    fun updateTileState() {
        val tile = qsTile ?: return
        val type = NotificationHelper.getTileTriggerType(tileId)
        
        if (type != null && NotificationHelper.isSequenceEnabled(this, type)) {
            val index = NotificationHelper.getSequenceIndex(this, type)
            val configs = NotificationHelper.getSequenceStepConfigs(this, type)
            val configStr = if (index in configs.indices) configs[index] else ""
            val parts = configStr.split("|||").let { if (it.size == 1) it[0].split("|") else it }
            
            val sequenceTargets = NotificationHelper.getSequence(this, type)
            val displayLabel = if (parts.size >= 1 && parts[0].isNotEmpty()) parts[0] else "שלב ${index + 1}"
            val iconResId = if (parts.size >= 2 && parts[1].isNotEmpty()) parts[1].toIntOrNull() ?: android.R.drawable.ic_menu_revert else android.R.drawable.ic_menu_revert
            val isActive = if (parts.size >= 3 && parts[2].isNotEmpty()) parts[2].toBoolean() else true
            
            if (sequenceTargets.isNotEmpty()) {
                tile.label = displayLabel
                tile.icon = Icon.createWithResource(this, iconResId)
                tile.state = if (isActive) android.service.quicksettings.Tile.STATE_ACTIVE else android.service.quicksettings.Tile.STATE_INACTIVE
                tile.updateTile()
                return
            }
        }
        
        // Load custom label
        val customLabel = NotificationHelper.getTileLabel(this, tileId)
        tile.label = customLabel
        
        // Load custom icon
        val iconType = NotificationHelper.getTileIconType(this, tileId)
        if (iconType == "custom") {
            val customIconPath = NotificationHelper.getTileCustomIconPath(this, tileId)
            if (customIconPath != null) {
                serviceScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        try {
                            val file = java.io.File(customIconPath)
                            if (file.exists()) {
                                BitmapFactory.decodeFile(file.absolutePath)
                            } else null
                        } catch (e: Exception) {
                            e.printStackTrace()
                            null
                        }
                    }
                    val currentTile = qsTile
                    if (currentTile != null && bitmap != null) {
                        currentTile.icon = Icon.createWithBitmap(bitmap)
                        currentTile.state = android.service.quicksettings.Tile.STATE_INACTIVE
                        currentTile.updateTile()
                    }
                }
            }
        } else {
            val presetResId = NotificationHelper.getTilePresetIcon(this, tileId)
            tile.icon = Icon.createWithResource(this, presetResId)
            tile.state = android.service.quicksettings.Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        
        val targetIntent = NotificationHelper.getTileIntent(this, tileId)
        
        if (targetIntent == null) {
            val configIntent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    configIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(configIntent)
            }
            Toast.makeText(this, "נא להגדיר פעולה לאריח $tileId בתוך האפליקציה", Toast.LENGTH_LONG).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    targetIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(targetIntent)
            }
        }
    }
}
