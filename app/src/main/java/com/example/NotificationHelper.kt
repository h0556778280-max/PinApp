package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat

enum class TriggerType {
    HOME, ASSIST, CAMERA
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
        }
        return prefs.getString(key, null)
    }

    // For backwards compatibility/convenience
    fun getTargetPackage(context: Context): String? {
        return getTargetPackage(context, TriggerType.HOME)
    }

    fun getLaunchIntent(context: Context, target: String): Intent? {
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

    fun saveTargetPackage(context: Context, type: TriggerType, pkg: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (type) {
            TriggerType.HOME -> KEY_TARGET_PKG
            TriggerType.ASSIST -> KEY_TARGET_PKG_ASSIST
            TriggerType.CAMERA -> KEY_TARGET_PKG_CAMERA
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
        }
        prefs.edit().remove(key).apply()
        updateStatusNotification(context)
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
        fun getAppName(target: String?): String? {
            if (target == null) return null
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

        val parts = mutableListOf<String>()
        getAppName(homePkg)?.let { parts.add("מסך הבית: $it") }
        getAppName(assistPkg)?.let { parts.add("סייען: $it") }
        getAppName(cameraPkg)?.let { parts.add("מצלמה: $it") }

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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_revert)
            .setContentTitle("PINAPP פועל ברקע")
            .setContentText(statusText)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    // Retained legacy method for backward compatibility
    fun showCancelNotification(context: Context, pkg: String) {
        updateStatusNotification(context)
    }
}
