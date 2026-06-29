package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat


enum class TriggerType {
    HOME, ASSIST, CAMERA,
    TILE1, TILE2, TILE3, TILE4, TILE5, TILE6, TILE7, TILE8, TILE9, TILE10, TILE11, TILE12, TILE13, TILE14, TILE15,
    SHORTCUT
}

object NotificationHelper {
    const val PREFS_NAME = "AnyHomePrefs"
    const val KEY_TARGET_PKG = "target_package" // Legacy/Home target package
    const val KEY_TARGET_PKG_ASSIST = "target_package_assist"
    const val KEY_TARGET_PKG_CAMERA = "target_package_camera"
    const val KEY_NOTIF_ENABLED = "notification_enabled"
    const val NOTIFICATION_ID = 1001
    const val CHANNEL_ID = "anyhome_status"

    fun isNotificationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_NOTIF_ENABLED, true)
    }

    fun setNotificationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIF_ENABLED, enabled)
            .apply()

        if (!enabled) {
            cancelNotification(context)
        } else {
            updateStatusNotification(context)
        }
    }

    fun getTargetPackage(context: Context, type: TriggerType): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (type) {
            TriggerType.HOME -> KEY_TARGET_PKG
            TriggerType.ASSIST -> KEY_TARGET_PKG_ASSIST
            TriggerType.CAMERA -> KEY_TARGET_PKG_CAMERA
            TriggerType.TILE1 -> "target_package_tile1"
            TriggerType.TILE2 -> "target_package_tile2"
            TriggerType.TILE3 -> "target_package_tile3"
            TriggerType.TILE4 -> "target_package_tile4"
            TriggerType.TILE5 -> "target_package_tile5"
            TriggerType.TILE6 -> "target_package_tile6"
            TriggerType.TILE7 -> "target_package_tile7"
            TriggerType.TILE8 -> "target_package_tile8"
            TriggerType.TILE9 -> "target_package_tile9"
            TriggerType.TILE10 -> "target_package_tile10"
            TriggerType.TILE11 -> "target_package_tile11"
            TriggerType.TILE12 -> "target_package_tile12"
            TriggerType.TILE13 -> "target_package_tile13"
            TriggerType.TILE14 -> "target_package_tile14"
            TriggerType.TILE15 -> "target_package_tile15"
            TriggerType.SHORTCUT -> "target_package_shortcut"
        }
        return prefs.getString(key, null)
    }

    // For backwards compatibility/convenience
    fun getTargetPackage(context: Context): String? {
        return getTargetPackage(context, TriggerType.HOME)
    }

    fun getLaunchIntent(context: Context, target: String): Intent? {
        if (target.startsWith("intent:")) {
            return try {
                Intent.parseUri(target, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        val pm = context.packageManager
        if (target.contains("/")) {
            val parts = target.split("/")
            if (parts.size == 2) {
                val packageName = parts[0]
                val className = parts[1]
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    setClassName(packageName, className)
                }
                // Verify if the intent can be resolved
                if (intent.resolveActivity(pm) != null) {
                    return intent
                }
            }
        }
        // Fallback to getLaunchIntentForPackage
        val pkg = if (target.contains("/")) target.split("/")[0] else target
        return pm.getLaunchIntentForPackage(pkg)
    }

    fun getSavedIntents(context: Context): List<Intent> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // If the preferences are empty (e.g. first launch), prepopulate some highly useful system intents!
        if (!prefs.contains("saved_intents_initialized")) {
            val defaults = listOf(
                // 1. Wifi Toggle
                Intent("com.example.action.TOGGLE_WIFI").apply {
                    setClassName(context.packageName, "com.example.ActionActivity")
                    putExtra("PINAPP_INTENT_LABEL", "שנה מצב Wi-Fi")
                },
                // 2. Bluetooth Toggle
                Intent("com.example.action.TOGGLE_BLUETOOTH").apply {
                    setClassName(context.packageName, "com.example.ActionActivity")
                    putExtra("PINAPP_INTENT_LABEL", "שנה מצב Bluetooth")
                },
                // 3. Brightness Max
                Intent("com.example.action.SET_BRIGHTNESS_MAX").apply {
                    setClassName(context.packageName, "com.example.ActionActivity")
                    putExtra("PINAPP_INTENT_LABEL", "בהירות למקסימום (100%)")
                },
                // 4. Brightness Min
                Intent("com.example.action.SET_BRIGHTNESS_MIN").apply {
                    setClassName(context.packageName, "com.example.ActionActivity")
                    putExtra("PINAPP_INTENT_LABEL", "בהירות למינימום (10%)")
                },
                // 5. Brightness Medium
                Intent("com.example.action.SET_BRIGHTNESS_HALF").apply {
                    setClassName(context.packageName, "com.example.ActionActivity")
                    putExtra("PINAPP_INTENT_LABEL", "בהירות לבינוני (50%)")
                },
                // 6. Timeout 30 seconds
                Intent("com.example.action.SET_TIMEOUT_30S").apply {
                    setClassName(context.packageName, "com.example.ActionActivity")
                    putExtra("PINAPP_INTENT_LABEL", "זמן כיבוי מסך ל-30 שניות")
                },
                // 7. Timeout 5 minutes
                Intent("com.example.action.SET_TIMEOUT_5M").apply {
                    setClassName(context.packageName, "com.example.ActionActivity")
                    putExtra("PINAPP_INTENT_LABEL", "זמן כיבוי מסך ל-5 דקות")
                },
                // 8. Auto Rotation Toggle
                Intent("com.example.action.TOGGLE_ROTATION").apply {
                    setClassName(context.packageName, "com.example.ActionActivity")
                    putExtra("PINAPP_INTENT_LABEL", "שנה מצב סיבוב מסך")
                }
            )
            val set = defaults.map { it.toUri(Intent.URI_INTENT_SCHEME) }.toSet()
            prefs.edit()
                .putStringSet("saved_intents", set)
                .putBoolean("saved_intents_initialized", true)
                .apply()
        }

        val set = prefs.getStringSet("saved_intents", null) ?: emptySet()
        return set.mapNotNull {
            try {
                Intent.parseUri(it, Intent.URI_INTENT_SCHEME)
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.getStringExtra("PINAPP_INTENT_LABEL") ?: "" }
    }

    fun saveIntent(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet("saved_intents", null) ?: emptySet()
        val newSet = set.toMutableSet()
        newSet.add(intent.toUri(Intent.URI_INTENT_SCHEME))
        prefs.edit().putStringSet("saved_intents", newSet).apply()
    }

    fun deleteIntent(context: Context, intentUri: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = prefs.getStringSet("saved_intents", null) ?: emptySet()
        val newSet = set.toMutableSet()
        newSet.remove(intentUri)
        prefs.edit().putStringSet("saved_intents", newSet).apply()
    }

    const val KEY_TILE_INTENT = "tile_intent_uri"

    fun getTileTriggerType(tileId: Int): TriggerType? {
        return when (tileId) {
            1 -> TriggerType.TILE1
            2 -> TriggerType.TILE2
            3 -> TriggerType.TILE3
            4 -> TriggerType.TILE4
            5 -> TriggerType.TILE5
            6 -> TriggerType.TILE6
            7 -> TriggerType.TILE7
            8 -> TriggerType.TILE8
            9 -> TriggerType.TILE9
            10 -> TriggerType.TILE10
            11 -> TriggerType.TILE11
            12 -> TriggerType.TILE12
            13 -> TriggerType.TILE13
            14 -> TriggerType.TILE14
            15 -> TriggerType.TILE15
            else -> null
        }
    }

    fun getTileIntent(context: Context, tileId: Int): Intent? {
        val type = getTileTriggerType(tileId) ?: return null
        return getTriggerIntent(context, type)
    }

    fun saveTileIntent(context: Context, tileId: Int, intent: Intent) {
        val type = getTileTriggerType(tileId) ?: return
        saveTargetPackage(context, type, intent.toUri(Intent.URI_INTENT_SCHEME))
    }

    fun clearTileIntent(context: Context, tileId: Int) {
        val type = getTileTriggerType(tileId) ?: return
        clearTargetPackage(context, type)
    }

    fun saveTargetPackage(context: Context, type: TriggerType, pkg: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (type) {
            TriggerType.HOME -> KEY_TARGET_PKG
            TriggerType.ASSIST -> KEY_TARGET_PKG_ASSIST
            TriggerType.CAMERA -> KEY_TARGET_PKG_CAMERA
            TriggerType.TILE1 -> "target_package_tile1"
            TriggerType.TILE2 -> "target_package_tile2"
            TriggerType.TILE3 -> "target_package_tile3"
            TriggerType.TILE4 -> "target_package_tile4"
            TriggerType.TILE5 -> "target_package_tile5"
            TriggerType.TILE6 -> "target_package_tile6"
            TriggerType.TILE7 -> "target_package_tile7"
            TriggerType.TILE8 -> "target_package_tile8"
            TriggerType.TILE9 -> "target_package_tile9"
            TriggerType.TILE10 -> "target_package_tile10"
            TriggerType.TILE11 -> "target_package_tile11"
            TriggerType.TILE12 -> "target_package_tile12"
            TriggerType.TILE13 -> "target_package_tile13"
            TriggerType.TILE14 -> "target_package_tile14"
            TriggerType.TILE15 -> "target_package_tile15"
            TriggerType.SHORTCUT -> "target_package_shortcut"
        }
        prefs.edit().putString(key, pkg).apply()
        updateStatusNotification(context)
    }

    fun clearTargetPackage(context: Context, type: TriggerType) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (type) {
            TriggerType.HOME -> KEY_TARGET_PKG
            TriggerType.ASSIST -> KEY_TARGET_PKG_ASSIST
            TriggerType.CAMERA -> KEY_TARGET_PKG_CAMERA
            TriggerType.TILE1 -> "target_package_tile1"
            TriggerType.TILE2 -> "target_package_tile2"
            TriggerType.TILE3 -> "target_package_tile3"
            TriggerType.TILE4 -> "target_package_tile4"
            TriggerType.TILE5 -> "target_package_tile5"
            TriggerType.TILE6 -> "target_package_tile6"
            TriggerType.TILE7 -> "target_package_tile7"
            TriggerType.TILE8 -> "target_package_tile8"
            TriggerType.TILE9 -> "target_package_tile9"
            TriggerType.TILE10 -> "target_package_tile10"
            TriggerType.TILE11 -> "target_package_tile11"
            TriggerType.TILE12 -> "target_package_tile12"
            TriggerType.TILE13 -> "target_package_tile13"
            TriggerType.TILE14 -> "target_package_tile14"
            TriggerType.TILE15 -> "target_package_tile15"
            TriggerType.SHORTCUT -> "target_package_shortcut"
        }
        prefs.edit().remove(key).apply()
        updateStatusNotification(context)
    }

    fun isSequenceEnabled(context: Context, type: TriggerType): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("sequence_enabled_${type.name}", false)
    }

    fun setSequenceEnabled(context: Context, type: TriggerType, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sequence_enabled_${type.name}", enabled).apply()
    }

    fun getSequence(context: Context, type: TriggerType): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val data = prefs.getString("sequence_data_${type.name}", "") ?: ""
        if (data.isEmpty()) return emptyList()
        return data.split("|||")
    }

    fun saveSequence(context: Context, type: TriggerType, sequence: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val data = sequence.joinToString("|||")
        prefs.edit().putString("sequence_data_${type.name}", data).apply()
    }

    fun getSequenceIndex(context: Context, type: TriggerType): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("sequence_index_${type.name}", 0)
    }

    fun setSequenceIndex(context: Context, type: TriggerType, index: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("sequence_index_${type.name}", index).apply()
    }

    // ID-based custom sequence methods
    fun getSequenceForId(context: Context, id: String): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val data = prefs.getString("sequence_data_$id", "") ?: ""
        if (data.isEmpty()) return emptyList()
        return data.split("|||")
    }

    fun saveSequenceForId(context: Context, id: String, sequence: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val data = sequence.joinToString("|||")
        prefs.edit().putString("sequence_data_$id", data).apply()
    }

    fun getSequenceIndexForId(context: Context, id: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("sequence_index_$id", 0)
    }

    fun setSequenceIndexForId(context: Context, id: String, index: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt("sequence_index_$id", index).apply()
    }

    fun getSequenceNextIntentForId(context: Context, id: String): Intent? {
        val sequence = getSequenceForId(context, id)
        if (sequence.isEmpty()) return null
        var index = getSequenceIndexForId(context, id)
        if (index >= sequence.size || index < 0) {
            index = 0
        }
        val target = sequence[index]
        val nextIndex = (index + 1) % sequence.size
        setSequenceIndexForId(context, id, nextIndex)
        return getLaunchIntent(context, target)
    }

    fun getSequenceStepConfigsForId(context: Context, id: String): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val configStr = prefs.getString("sequence_configs_$id", "") ?: ""
        val sequenceSize = getSequenceForId(context, id).size
        val list = (if (configStr.isEmpty()) emptyList() else configStr.split("|||")).toMutableList()
        while (list.size < sequenceSize) {
            list.add("")
        }
        return list
    }

    fun saveSequenceStepConfigsForId(context: Context, id: String, configs: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("sequence_configs_$id", configs.joinToString("|||")).apply()
    }

    fun addSequenceTargetForId(context: Context, id: String, target: String) {
        val list = getSequenceForId(context, id).toMutableList()
        list.add(target)
        saveSequenceForId(context, id, list)
        
        val configs = getSequenceStepConfigsForId(context, id).toMutableList()
        configs.add("")
        saveSequenceStepConfigsForId(context, id, configs)
    }

    fun removeSequenceTargetAtForId(context: Context, id: String, index: Int) {
        val list = getSequenceForId(context, id).toMutableList()
        val configs = getSequenceStepConfigsForId(context, id).toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            saveSequenceForId(context, id, list)
            if (index in configs.indices) {
                configs.removeAt(index)
                saveSequenceStepConfigsForId(context, id, configs)
            }
            val currentIndex = getSequenceIndexForId(context, id)
            if (currentIndex >= list.size) {
                setSequenceIndexForId(context, id, maxOf(0, list.size - 1))
            }
        }
    }

    fun moveSequenceTargetForId(context: Context, id: String, index: Int, up: Boolean) {
        val list = getSequenceForId(context, id).toMutableList()
        val configs = getSequenceStepConfigsForId(context, id).toMutableList()
        val targetIndex = if (up) index - 1 else index + 1
        if (index in list.indices && targetIndex in list.indices) {
            val temp = list[index]
            list[index] = list[targetIndex]
            list[targetIndex] = temp
            saveSequenceForId(context, id, list)
            
            if (index in configs.indices && targetIndex in configs.indices) {
                val tempConfig = configs[index]
                configs[index] = configs[targetIndex]
                configs[targetIndex] = tempConfig
                saveSequenceStepConfigsForId(context, id, configs)
            }
        }
    }

    fun getSequenceNextIntent(context: Context, type: TriggerType): Intent? {
        val sequence = getSequence(context, type)
        if (sequence.isEmpty()) return null
        var index = getSequenceIndex(context, type)
        if (index >= sequence.size || index < 0) {
            index = 0
        }
        val target = sequence[index]
        
        // Save next index (wrapping around)
        val nextIndex = (index + 1) % sequence.size
        setSequenceIndex(context, type, nextIndex)
        
        // Parse and return the intent
        return getLaunchIntent(context, target)
    }

    fun getTriggerIntent(context: Context, type: TriggerType): Intent? {
        if (isSequenceEnabled(context, type)) {
            return Intent(context, ActionActivity::class.java).apply {
                action = "com.example.action.RUN_SEQUENCE"
                putExtra("trigger_type", type.name)
            }
        }
        val target = getTargetPackage(context, type) ?: return null
        return getLaunchIntent(context, target)
    }

    fun getSequenceStepConfigs(context: Context, type: TriggerType): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val configStr = prefs.getString("sequence_configs_${type.name}", "") ?: ""
        val sequenceSize = getSequence(context, type).size
        val list = (if (configStr.isEmpty()) emptyList() else configStr.split("|||")).toMutableList()
        while (list.size < sequenceSize) {
            list.add("")
        }
        return list
    }

    fun saveSequenceStepConfigs(context: Context, type: TriggerType, configs: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("sequence_configs_${type.name}", configs.joinToString("|||")).apply()
    }

    fun addSequenceTarget(context: Context, type: TriggerType, target: String) {
        val list = getSequence(context, type).toMutableList()
        list.add(target)
        saveSequence(context, type, list)
        
        val configs = getSequenceStepConfigs(context, type).toMutableList()
        configs.add("")
        saveSequenceStepConfigs(context, type, configs)
    }

    fun removeSequenceTargetAt(context: Context, type: TriggerType, index: Int) {
        val list = getSequence(context, type).toMutableList()
        val configs = getSequenceStepConfigs(context, type).toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            saveSequence(context, type, list)
            if (index in configs.indices) {
                configs.removeAt(index)
                saveSequenceStepConfigs(context, type, configs)
            }
            // Adjust index if out of bounds
            val currentIndex = getSequenceIndex(context, type)
            if (currentIndex >= list.size) {
                setSequenceIndex(context, type, maxOf(0, list.size - 1))
            }
        }
    }

    fun moveSequenceTarget(context: Context, type: TriggerType, index: Int, up: Boolean) {
        val list = getSequence(context, type).toMutableList()
        val configs = getSequenceStepConfigs(context, type).toMutableList()
        val targetIndex = if (up) index - 1 else index + 1
        if (index in list.indices && targetIndex in list.indices) {
            val temp = list[index]
            list[index] = list[targetIndex]
            list[targetIndex] = temp
            saveSequence(context, type, list)
            
            if (index in configs.indices && targetIndex in configs.indices) {
                val tempConfig = configs[index]
                configs[index] = configs[targetIndex]
                configs[targetIndex] = tempConfig
                saveSequenceStepConfigs(context, type, configs)
            }
        }
    }

    fun clearAllTargetPackages(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_TARGET_PKG)
            .remove(KEY_TARGET_PKG_ASSIST)
            .remove(KEY_TARGET_PKG_CAMERA)
            .apply()
        cancelNotification(context)
    }

    fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    fun getAppName(context: Context, target: String?): String? {
        if (target == null) return null
        if (target.startsWith("intent:")) {
            return try {
                val intent = Intent.parseUri(target, Intent.URI_INTENT_SCHEME)
                intent.getStringExtra("PINAPP_INTENT_LABEL") ?: "בקשת מערכת מותאמת"
            } catch (e: Exception) {
                "בקשת מערכת"
            }
        }
        val pm = context.packageManager
        val pkg = if (target.contains("/")) target.split("/")[0] else target
        val cls = if (target.contains("/")) target.split("/")[1] else null
        return try {
            if (cls != null) {
                val compName = android.content.ComponentName(pkg, cls)
                val activityInfo = pm.getActivityInfo(compName, 0)
                val actLabel = activityInfo.loadLabel(pm).toString()
                val appAi = pm.getApplicationInfo(pkg, 0)
                val appLabelStr = pm.getApplicationLabel(appAi).toString()
                if (actLabel != appLabelStr) {
                    "$appLabelStr - $actLabel"
                } else {
                    "$appLabelStr (${cls.substringAfterLast('.')})"
                }
            } else {
                val ai = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(ai).toString()
            }
        } catch (e: Exception) {
            if (cls != null) {
                "${pkg.substringAfterLast('.')}/${cls.substringAfterLast('.')}"
            } else {
                pkg
            }
        }
    }

    fun updateStatusNotification(context: Context) {
        if (!isNotificationEnabled(context)) return

        val homePkg = getTargetPackage(context, TriggerType.HOME)
        val assistPkg = getTargetPackage(context, TriggerType.ASSIST)
        val cameraPkg = getTargetPackage(context, TriggerType.CAMERA)

        if (homePkg == null && assistPkg == null && cameraPkg == null) {
            cancelNotification(context)
            return
        }

        val pm = context.packageManager

        val parts = mutableListOf<String>()
        getAppName(context, homePkg)?.let { parts.add("מסך הבית: $it") }
        getAppName(context, assistPkg)?.let { parts.add("סייען: $it") }
        getAppName(context, cameraPkg)?.let { parts.add("מצלמה: $it") }

        val statusText = parts.joinToString(" | ")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, 
                "Launcher Status", 
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val cancelIntent = Intent(context, MainActivity::class.java).apply {
            action = "CANCEL_FORWARD"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = getNotificationTitle(context)
        val iconType = getNotificationIconType(context)
        
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (iconType == "custom") {
            val customPath = getNotificationCustomIconPath(context)
            var iconSet = false
            if (customPath != null) {
                try {
                    val file = java.io.File(customPath)
                    if (file.exists()) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            val iconCompat = IconCompat.createWithBitmap(bitmap)
                            notificationBuilder.setSmallIcon(iconCompat)
                            iconSet = true
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            if (!iconSet) {
                notificationBuilder.setSmallIcon(android.R.drawable.ic_menu_revert)
            }
        } else {
            val presetResId = getNotificationPresetIcon(context)
            notificationBuilder.setSmallIcon(presetResId)
        }

        nm.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    fun getNotificationTitle(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("notification_title", "PINAPP פועל ברקע") ?: "PINAPP פועל ברקע"
    }

    fun setNotificationTitle(context: Context, title: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("notification_title", title).apply()
        updateStatusNotification(context)
    }

    fun getNotificationIconType(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("notification_icon_type", "preset") ?: "preset"
    }

    fun getNotificationPresetIcon(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("notification_preset_icon", android.R.drawable.ic_menu_revert)
    }

    fun setNotificationPresetIcon(context: Context, resId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("notification_icon_type", "preset")
            .putInt("notification_preset_icon", resId)
            .apply()
        updateStatusNotification(context)
    }

    fun getNotificationCustomIconPath(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("notification_custom_icon_path", null)
    }

    fun setNotificationCustomIcon(context: Context, filePath: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("notification_icon_type", "custom")
            .putString("notification_custom_icon_path", filePath)
            .apply()
        updateStatusNotification(context)
    }

    fun processAndSaveCustomIcon(
        context: Context,
        uri: Uri,
        mode: String // "PRESERVE_ALPHA", "DARK_TO_ALPHA", "LIGHT_TO_ALPHA", "ORIGINAL"
    ): String? {
        return try {
            val resolver = context.contentResolver
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                val b = MediaStore.Images.Media.getBitmap(resolver, uri)
                b.copy(Bitmap.Config.ARGB_8888, true)
            }

            // Resize bitmap to 96x96 pixels for status bar
            val size = 96
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
            val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)

            when (mode) {
                "PRESERVE_ALPHA" -> {
                    // Keep existing transparency, make opaque pixels solid white
                    for (x in 0 until size) {
                        for (y in 0 until size) {
                            val pixel = scaledBitmap.getPixel(x, y)
                            val alpha = android.graphics.Color.alpha(pixel)
                            if (alpha > 10) {
                                outputBitmap.setPixel(x, y, android.graphics.Color.argb(alpha, 255, 255, 255))
                            } else {
                                outputBitmap.setPixel(x, y, android.graphics.Color.TRANSPARENT)
                            }
                        }
                    }
                }
                "DARK_TO_ALPHA" -> {
                    // Dark background becomes transparent, light foreground becomes white
                    for (x in 0 until size) {
                        for (y in 0 until size) {
                            val pixel = scaledBitmap.getPixel(x, y)
                            val r = android.graphics.Color.red(pixel)
                            val g = android.graphics.Color.green(pixel)
                            val b = android.graphics.Color.blue(pixel)
                            val originalAlpha = android.graphics.Color.alpha(pixel)
                            
                            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                            val finalAlpha = (luminance * (originalAlpha / 255f)).toInt()
                            outputBitmap.setPixel(x, y, android.graphics.Color.argb(finalAlpha, 255, 255, 255))
                        }
                    }
                }
                "LIGHT_TO_ALPHA" -> {
                    // Light background becomes transparent, dark foreground becomes white
                    for (x in 0 until size) {
                        for (y in 0 until size) {
                            val pixel = scaledBitmap.getPixel(x, y)
                            val r = android.graphics.Color.red(pixel)
                            val g = android.graphics.Color.green(pixel)
                            val b = android.graphics.Color.blue(pixel)
                            val originalAlpha = android.graphics.Color.alpha(pixel)
                            
                            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                            val invertedLuminance = 255 - luminance
                            val finalAlpha = (invertedLuminance * (originalAlpha / 255f)).toInt()
                            outputBitmap.setPixel(x, y, android.graphics.Color.argb(finalAlpha, 255, 255, 255))
                        }
                    }
                }
                else -> {
                    // Keep original colors
                    canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                }
            }

            // Save outputBitmap to internal storage
            // Save outputBitmap to internal storage
            val file = java.io.File(context.filesDir, "custom_status_bar_icon.png")
            java.io.FileOutputStream(file).use { out ->
                outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getTileLabel(context: Context, tileId: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("tile_${tileId}_label", "אריח $tileId") ?: "אריח $tileId"
    }

    fun setTileLabel(context: Context, tileId: Int, label: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("tile_${tileId}_label", label).apply()
        updateQuickSettingsTile(context, tileId)
    }

    fun getTileIconType(context: Context, tileId: Int): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("tile_${tileId}_icon_type", "preset") ?: "preset"
    }

    fun getTilePresetIcon(context: Context, tileId: Int): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("tile_${tileId}_preset_icon", android.R.drawable.ic_dialog_info)
    }

    fun setTilePresetIcon(context: Context, tileId: Int, resId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("tile_${tileId}_icon_type", "preset")
            .putInt("tile_${tileId}_preset_icon", resId)
            .apply()
        updateQuickSettingsTile(context, tileId)
    }

    fun getTileCustomIconPath(context: Context, tileId: Int): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("tile_${tileId}_custom_icon_path", null)
    }

    fun setTileCustomIcon(context: Context, tileId: Int, filePath: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("tile_${tileId}_icon_type", "custom")
            .putString("tile_${tileId}_custom_icon_path", filePath)
            .apply()
        updateQuickSettingsTile(context, tileId)
    }

    fun processAndSaveTileIcon(
        context: Context,
        uri: Uri,
        mode: String, // "PRESERVE_ALPHA", "DARK_TO_ALPHA", "LIGHT_TO_ALPHA", "ORIGINAL"
        tileId: Int
    ): String? {
        return try {
            val resolver = context.contentResolver
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                val b = MediaStore.Images.Media.getBitmap(resolver, uri)
                b.copy(Bitmap.Config.ARGB_8888, true)
            }

            // Resize bitmap to 96x96 pixels for Quick Settings tile
            val size = 96
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
            val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(outputBitmap)

            when (mode) {
                "PRESERVE_ALPHA" -> {
                    for (x in 0 until size) {
                        for (y in 0 until size) {
                            val pixel = scaledBitmap.getPixel(x, y)
                            val alpha = android.graphics.Color.alpha(pixel)
                            if (alpha > 10) {
                                outputBitmap.setPixel(x, y, android.graphics.Color.argb(alpha, 255, 255, 255))
                            } else {
                                outputBitmap.setPixel(x, y, android.graphics.Color.TRANSPARENT)
                            }
                        }
                    }
                }
                "DARK_TO_ALPHA" -> {
                    for (x in 0 until size) {
                        for (y in 0 until size) {
                            val pixel = scaledBitmap.getPixel(x, y)
                            val r = android.graphics.Color.red(pixel)
                            val g = android.graphics.Color.green(pixel)
                            val b = android.graphics.Color.blue(pixel)
                            val originalAlpha = android.graphics.Color.alpha(pixel)
                            
                            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                            val finalAlpha = (luminance * (originalAlpha / 255f)).toInt()
                            outputBitmap.setPixel(x, y, android.graphics.Color.argb(finalAlpha, 255, 255, 255))
                        }
                    }
                }
                "LIGHT_TO_ALPHA" -> {
                    for (x in 0 until size) {
                        for (y in 0 until size) {
                            val pixel = scaledBitmap.getPixel(x, y)
                            val r = android.graphics.Color.red(pixel)
                            val g = android.graphics.Color.green(pixel)
                            val b = android.graphics.Color.blue(pixel)
                            val originalAlpha = android.graphics.Color.alpha(pixel)
                            
                            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                            val invertedLuminance = 255 - luminance
                            val finalAlpha = (invertedLuminance * (originalAlpha / 255f)).toInt()
                            outputBitmap.setPixel(x, y, android.graphics.Color.argb(finalAlpha, 255, 255, 255))
                        }
                    }
                }
                else -> {
                    canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                }
            }

            // Save outputBitmap to internal storage
            val file = java.io.File(context.filesDir, "custom_tile_icon_${tileId}.png")
            java.io.FileOutputStream(file).use { out ->
                outputBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun updateQuickSettingsTile(context: Context, tileId: Int) {
        try {
            val clsName = "com.example.Tile${tileId}Service"
            val component = android.content.ComponentName(context, clsName)
            android.service.quicksettings.TileService.requestListeningState(context, component)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Retained legacy method for backward compatibility
    fun showCancelNotification(context: Context, pkg: String) {
        updateStatusNotification(context)
    }

    fun isTileAdded(context: Context, tileNum: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "tile${tileNum}_added"
        if (!prefs.contains(key)) {
            val type = getTileTriggerType(tileNum) ?: return false
            val hasTarget = getTargetPackage(context, type) != null || isSequenceEnabled(context, type)
            if (hasTarget) {
                prefs.edit().putBoolean(key, true).apply()
                updateTileServiceState(context, tileNum, true)
                return true
            }
            return false
        }
        return prefs.getBoolean(key, false)
    }

    fun setTileAdded(context: Context, tileNum: Int, added: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("tile${tileNum}_added", added).apply()
        updateTileServiceState(context, tileNum, added)
        if (!added) {
            val type = getTileTriggerType(tileNum) ?: return
            clearTargetPackage(context, type)
            setSequenceEnabled(context, type, false)
        }
    }

    fun updateTileServiceState(context: Context, tileNum: Int, enabled: Boolean) {
        try {
            val pm = context.packageManager
            val className = "com.example.Tile${tileNum}Service"
            val compName = android.content.ComponentName(context, className)
            val state = if (enabled) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(compName, state, android.content.pm.PackageManager.DONT_KILL_APP)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isSequenceAllAtOnce(context: Context, type: TriggerType): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("sequence_all_at_once_${type.name}", false)
    }

    fun setSequenceAllAtOnce(context: Context, type: TriggerType, allAtOnce: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sequence_all_at_once_${type.name}", allAtOnce).apply()
    }

    fun isSequenceAllAtOnceForId(context: Context, id: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("sequence_all_at_once_$id", false)
    }

    fun setSequenceAllAtOnceForId(context: Context, id: String, allAtOnce: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sequence_all_at_once_$id", allAtOnce).apply()
    }

    fun isDevModeEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("dev_mode_enabled", false)
    }

    fun setDevModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("dev_mode_enabled", enabled)
            .apply()
    }
}
