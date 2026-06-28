package com.example

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity

class ActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val action = intent.action
        if (action != null) {
            when (action) {
                "com.example.action.TOGGLE_WIFI" -> toggleWifi()
                "com.example.action.WIFI_ON" -> setWifiState(true)
                "com.example.action.WIFI_OFF" -> setWifiState(false)
                "com.example.action.TOGGLE_BLUETOOTH" -> toggleBluetooth()
                "com.example.action.BLUETOOTH_ON" -> setBluetoothState(true)
                "com.example.action.BLUETOOTH_OFF" -> setBluetoothState(false)
                "com.example.action.SET_BRIGHTNESS_MAX" -> setBrightness(255, "מקסימום (100%)")
                "com.example.action.SET_BRIGHTNESS_MIN" -> setBrightness(10, "מינימום (שמירת סוללה)")
                "com.example.action.SET_BRIGHTNESS_HALF" -> setBrightness(128, "בינוני (50%)")
                "com.example.action.SET_TIMEOUT_30S" -> setTimeout(30000, "30 שניות")
                "com.example.action.SET_TIMEOUT_5M" -> setTimeout(300000, "5 דקות")
                "com.example.action.TOGGLE_ROTATION" -> toggleRotation()
                "com.example.action.RUN_SEQUENCE" -> runShortcutSequence()
            }
        }
        finish()
    }

    private fun runShortcutSequence() {
        val sequenceId = intent.getStringExtra("sequence_id") ?: intent.data?.getQueryParameter("id")
        val launchIntent = if (sequenceId != null) {
            NotificationHelper.getSequenceNextIntentForId(this, sequenceId)
        } else {
            NotificationHelper.getTriggerIntent(this, TriggerType.SHORTCUT)
        }
        
        if (launchIntent != null) {
            try {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } catch (e: Exception) {
                Toast.makeText(this, "שגיאה בהפעלת הפעולה הבאה בסבב: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "סבב הפעולות ריק או אינו מסומן כפעיל", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAndExecuteSystemSetting(block: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.System.canWrite(this)) {
                try {
                    block()
                } catch (e: Exception) {
                    Toast.makeText(this, "שגיאה בשינוי ההגדרה: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "אנא אשר את הרשאת 'שינוי הגדרות מערכת' בתוך הגדרות המכשיר", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } else {
            try {
                block()
            } catch (e: Exception) {
                Toast.makeText(this, "שגיאה: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setBrightness(value: Int, description: String) {
        checkAndExecuteSystemSetting {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            Toast.makeText(this, "הבהירות שונתה ל-$description", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setTimeout(ms: Int, description: String) {
        checkAndExecuteSystemSetting {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms)
            Toast.makeText(this, "זמן כיבוי המסך שונה ל-$description", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleRotation() {
        checkAndExecuteSystemSetting {
            val current = Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            val next = if (current == 1) 0 else 1
            Settings.System.putInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, next)
            Toast.makeText(this, if (next == 1) "סיבוב מסך אוטומטי מופעל" else "סיבוב מסך אוטומטי כבוי", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleWifi() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val isEnabled = wifiManager.isWifiEnabled
            setWifiState(!isEnabled)
        } catch (e: Exception) {
            e.printStackTrace()
            // Direct fallback
            setWifiState(true)
        }
    }

    private fun setWifiState(enable: Boolean) {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                // On Q/10+, direct toggling is disallowed. We open Settings panel.
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(panelIntent)
                Toast.makeText(this, "פתיחת פאנל Wi-Fi...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                try {
                    val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(settingsIntent)
                    Toast.makeText(this, "פתיחת הגדרות Wi-Fi...", Toast.LENGTH_SHORT).show()
                } catch (ex: Exception) {
                    Toast.makeText(this, "שגיאה בפתיחת הגדרות Wi-Fi", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                val success = wifiManager.setWifiEnabled(enable)
                if (success) {
                    Toast.makeText(this, if (enable) "Wi-Fi מופעל" else "Wi-Fi כבוי", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "נכשל בשינוי מצב Wi-Fi במכשיר", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback setting panel
                try {
                    val settingsIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(settingsIntent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "שגיאה בשינוי הגדרות Wi-Fi", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleBluetooth() {
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val bluetoothAdapter = bluetoothManager.adapter
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "מכשיר זה אינו תומך ב-Bluetooth", Toast.LENGTH_SHORT).show()
                return
            }
            val isEnabled = bluetoothAdapter.isEnabled
            setBluetoothState(!isEnabled)
        } catch (e: Exception) {
            e.printStackTrace()
            setBluetoothState(true)
        }
    }

    private fun setBluetoothState(enable: Boolean) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "מכשיר זה אינו תומך ב-Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                Toast.makeText(this, "פתיחת הגדרות Bluetooth...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "שגיאה בפתיחת הגדרות Bluetooth", Toast.LENGTH_SHORT).show()
            }
        } else {
            try {
                val success = if (enable) {
                    @Suppress("DEPRECATION")
                    bluetoothAdapter.enable()
                } else {
                    @Suppress("DEPRECATION")
                    bluetoothAdapter.disable()
                }
                if (success) {
                    Toast.makeText(this, if (enable) "Bluetooth מופעל" else "Bluetooth כבוי", Toast.LENGTH_SHORT).show()
                } else {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
            } catch (e: SecurityException) {
                try {
                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "שגיאת הרשאות Bluetooth", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
