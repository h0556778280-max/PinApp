package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class AssistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetPkg = NotificationHelper.getTargetPackage(this, TriggerType.ASSIST)
        if (targetPkg != null) {
            val launchIntent = NotificationHelper.getLaunchIntent(this, targetPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or 
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or 
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                try {
                    startActivity(launchIntent)
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                } catch (e: Exception) {
                    // Fallback to launching the package's default launcher if sub-activity failed
                    val pkg = if (targetPkg.contains("/")) targetPkg.split("/")[0] else targetPkg
                    val fallbackIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (fallbackIntent != null) {
                        try {
                            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(fallbackIntent)
                        } catch (e2: Exception) {
                            launchSettings()
                        }
                    } else {
                        launchSettings()
                    }
                }
            } else {
                NotificationHelper.clearTargetPackage(this, TriggerType.ASSIST)
                launchSettings()
            }
        } else {
            launchSettings()
        }
        finish()
    }

    private fun launchSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }
}
