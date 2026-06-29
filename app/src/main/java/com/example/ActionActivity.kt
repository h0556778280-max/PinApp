package com.example

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.SmsManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.telecom.TelecomManager

class ActionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executeIntent(intent)
        finish()
    }

    private fun executeIntent(intentToExec: Intent) {
        val action = intentToExec.action
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
                "com.example.action.RUN_SEQUENCE" -> runShortcutSequence(intentToExec)
                "com.example.action.SEND_SMS_DIRECT" -> sendSmsDirect(intentToExec)
                "com.example.action.SWITCH_SPEAKER_ON" -> setSpeakerphoneState(true)
                "com.example.action.SWITCH_SPEAKER_OFF" -> setSpeakerphoneState(false)
                "com.example.action.MUTE_ON" -> setMuteState(true)
                "com.example.action.MUTE_OFF" -> setMuteState(false)
                "com.example.action.CALL_MUTE_ON" -> setCallMuteState(true)
                "com.example.action.CALL_MUTE_OFF" -> setCallMuteState(false)
                "com.example.action.ANSWER_CALL" -> answerIncomingCall()
                "com.example.action.DTMF_DIAL" -> playDtmfTones(intentToExec)
                "com.example.action.HOLD_CALL" -> holdCall()
                "com.example.action.RESUME_CALL" -> resumeCall()
                "com.example.action.END_CALL" -> endCall()
            }
        }
    }

    private fun isActionActivityIntent(intent: Intent): Boolean {
        val cmp = intent.component
        if (cmp != null && cmp.className == "com.example.ActionActivity") return true
        if (intent.action?.startsWith("com.example.action.") == true) return true
        return false
    }

    private fun runShortcutSequence(intentToExec: Intent) {
        val sequenceId = intentToExec.getStringExtra("sequence_id") ?: intentToExec.data?.getQueryParameter("id")
        val triggerTypeStr = intentToExec.getStringExtra("trigger_type")
        val triggerType = triggerTypeStr?.let { TriggerType.valueOf(it) }

        val allAtOnce = if (sequenceId != null) {
            NotificationHelper.isSequenceAllAtOnceForId(this, sequenceId)
        } else if (triggerType != null) {
            NotificationHelper.isSequenceAllAtOnce(this, triggerType)
        } else {
            NotificationHelper.isSequenceAllAtOnce(this, TriggerType.SHORTCUT)
        }

        val targets = if (sequenceId != null) {
            NotificationHelper.getSequenceForId(this, sequenceId)
        } else if (triggerType != null) {
            NotificationHelper.getSequence(this, triggerType)
        } else {
            NotificationHelper.getSequence(this, TriggerType.SHORTCUT)
        }

        if (targets.isEmpty()) {
            Toast.makeText(this, "סבב הפעולות ריק", Toast.LENGTH_LONG).show()
            return
        }

        if (allAtOnce) {
            var successCount = 0
            for (target in targets) {
                if (target == "action:step_separator") continue
                val launchIntent = NotificationHelper.getLaunchIntent(this, target)
                if (launchIntent != null) {
                    try {
                        if (isActionActivityIntent(launchIntent)) {
                            executeIntent(launchIntent)
                        } else {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        }
                        successCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            Toast.makeText(this, "בוצעו $successCount פעולות בבת אחת!", Toast.LENGTH_SHORT).show()
        } else {
            // Find current step index
            val prefs = getSharedPreferences("com.example.PREFS", Context.MODE_PRIVATE)
            val stepKey = if (sequenceId != null) "sequence_step_$sequenceId" else if (triggerType != null) "sequence_step_${triggerType.name}" else "sequence_step_shortcut"
            val currentStep = prefs.getInt(stepKey, 0)
            
            // Break targets into steps
            val steps = mutableListOf<List<String>>()
            var currentStepTargets = mutableListOf<String>()
            for (target in targets) {
                if (target == "action:step_separator") {
                    steps.add(currentStepTargets)
                    currentStepTargets = mutableListOf()
                } else {
                    currentStepTargets.add(target)
                }
            }
            if (currentStepTargets.isNotEmpty()) {
                steps.add(currentStepTargets)
            }
            
            if (steps.isEmpty()) return

            val validStepIndex = if (currentStep >= steps.size || currentStep < 0) 0 else currentStep
            val stepTargets = steps[validStepIndex]
            
            // Advance step
            prefs.edit().putInt(stepKey, (validStepIndex + 1) % steps.size).apply()
            
            // Refresh Quick Settings Tiles if this was triggered from a tile
            if (triggerType != null && triggerType.name.startsWith("TILE")) {
                try {
                    val componentName = android.content.ComponentName(this, "com.example.${triggerType.name.lowercase().replaceFirstChar { it.uppercase() }}Service")
                    android.service.quicksettings.TileService.requestListeningState(this, componentName)
                } catch (e: Exception) {}
            }
            
            var successCount = 0
            for (target in stepTargets) {
                val launchIntent = NotificationHelper.getLaunchIntent(this, target)
                if (launchIntent != null) {
                    try {
                        if (isActionActivityIntent(launchIntent)) {
                            executeIntent(launchIntent)
                        } else {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                        }
                        successCount++
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            if (steps.size > 1) {
                if (successCount == 1) {
                    // Try to get app name
                    val appName = NotificationHelper.getAppName(this, stepTargets.firstOrNull())
                    if (appName != null) {
                        Toast.makeText(this, "סבב (${validStepIndex + 1}/${steps.size}): הופעל $appName", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "הופעל שלב ${validStepIndex + 1} מתוך ${steps.size}", Toast.LENGTH_SHORT).show()
                    }
                } else if (successCount > 1) {
                    Toast.makeText(this, "הופעל שלב ${validStepIndex + 1} מתוך ${steps.size} ($successCount פעולות)", Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun sendSmsDirect(intentToExec: Intent) {
        val phoneNumber = intentToExec.getStringExtra("phone_number")
        val messageBody = intentToExec.getStringExtra("message_body")
        
        if (phoneNumber.isNullOrBlank() || messageBody.isNullOrBlank()) {
            Toast.makeText(this, "שגיאה בשליחת SMS: מספר טלפון או תוכן הודעה חסרים", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            var smsManager: SmsManager? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    smsManager = applicationContext.getSystemService(SmsManager::class.java)
                } catch (e: Exception) {
                    // Fallback to deprecated method
                }
            }
            if (smsManager == null) {
                @Suppress("DEPRECATION")
                smsManager = SmsManager.getDefault()
            }
            
            if (smsManager == null) {
                Toast.makeText(this, "שגיאה: לא ניתן לאתחל את מנהל ה-SMS במכשיר", Toast.LENGTH_SHORT).show()
                return
            }

            val parts = smsManager.divideMessage(messageBody)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, messageBody, null, null)
            }
            Toast.makeText(this, "הודעת ה-SMS הישירה נשלחה בהצלחה!", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "שגיאה: חסרה הרשאת שליחת SMS במכשיר", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה בשליחת SMS: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setSpeakerphoneState(on: Boolean) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (on) {
                    val devices = audioManager.availableCommunicationDevices
                    val speaker = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        audioManager.setCommunicationDevice(speaker)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = on
                    }
                } else {
                    audioManager.clearCommunicationDevice()
                    val devices = audioManager.availableCommunicationDevices
                    val earpiece = devices.firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    if (earpiece != null) {
                        audioManager.setCommunicationDevice(earpiece)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager.isSpeakerphoneOn = false
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = on
            }
            val stateText = if (on) "רמקול פעיל" else "רמקול כבוי"
            Toast.makeText(this, "ניתוב שמע שונה בהצלחה ל-$stateText!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה בשינוי ניתוב שמע: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun answerIncomingCall() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (!telecomManager.isInCall) {
                Toast.makeText(this, "אין שיחה פעילה", Toast.LENGTH_SHORT).show()
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    @Suppress("DEPRECATION")
                    telecomManager.acceptRingingCall()
                    Toast.makeText(this, "השיחה הנכנסת נענתה בהצלחה!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "שגיאה במענה לשיחה: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "שגיאה: חסרה הרשאה למענה לשיחות (ANSWER_PHONE_CALLS)", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "מענה לשיחה באופן תכנותי נתמך רק מאנדרואיד 8 ומעלה", Toast.LENGTH_LONG).show()
        }
    }

    private fun playDtmfTones(intentToExec: Intent) {
        val tones = intentToExec.getStringExtra("dtmf_tones")
        if (tones.isNullOrBlank()) {
            Toast.makeText(this, "שגיאה: לא הוזנו תווים לחיוג DTMF", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "מחייג צלילי DTMF: $tones", Toast.LENGTH_SHORT).show()
        try {
            // A comma ',' represents a pause. Semicolon ';' represents a wait.
            // We can send these tones using a tel: intent without encoding the commas so the dialer understands them.
            val uriString = "tel:,$tones"
            val dialIntent = Intent(Intent.ACTION_CALL, android.net.Uri.parse(uriString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(dialIntent)
            } else {
                // Fallback to ACTION_DIAL
                val dialerIntent = Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:,$tones")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(dialerIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה בחיוג DTMF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun holdCall() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (!telecomManager.isInCall) {
                Toast.makeText(this, "אין שיחה פעילה", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "פותח את מסך השיחה. עקב מגבלות אנדרואיד, יש ללחוץ על החזקה ידנית.", Toast.LENGTH_LONG).show()
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.showInCallScreen(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resumeCall() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (!telecomManager.isInCall) {
                Toast.makeText(this, "אין שיחה פעילה", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(this, "פותח את מסך השיחה. עקב מגבלות אנדרואיד, יש לשחרר מהחזקה ידנית.", Toast.LENGTH_LONG).show()
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.showInCallScreen(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun endCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                try {
                    val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                    @Suppress("DEPRECATION")
                    val success = telecomManager.endCall()
                    if (success) {
                        Toast.makeText(this, "השיחה נותקה בהצלחה", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "לא ניתן היה לנתק את השיחה באמצעות ה-API הרשמי", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "שגיאה בניתוק שיחה: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "חסרה הרשאה למענה/ניתוק שיחות (ANSWER_PHONE_CALLS)", Toast.LENGTH_LONG).show()
            }
        } else {
            try {
                val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
                val classTelephony = Class.forName(telephonyManager.javaClass.name)
                val methodGetITelephony = classTelephony.getDeclaredMethod("getITelephony")
                methodGetITelephony.isAccessible = true
                val telephonyInterface = methodGetITelephony.invoke(telephonyManager)
                val telephonyInterfaceClass = Class.forName(telephonyInterface.javaClass.name)
                val methodEndCall = telephonyInterfaceClass.getDeclaredMethod("endCall")
                methodEndCall.invoke(telephonyInterface)
                Toast.makeText(this, "השיחה נותקה (מצב תאימות)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "שגיאה בניתוק שיחה במכשיר ישן: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setMuteState(mute: Boolean) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (mute) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                Toast.makeText(this, "השתקה הופעלה בהצלחה", Toast.LENGTH_SHORT).show()
            } else {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                Toast.makeText(this, "השתקה בוטלה בהצלחה", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "נדרשת הרשאת 'נא לא להפריע' (Do Not Disturb) לשינוי מצב השתקה", Toast.LENGTH_LONG).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה בשינוי מצב השתקה: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setCallMuteState(mute: Boolean) {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.isMicrophoneMute = mute
            val stateText = if (mute) "השתקה מופעלת (Mute)" else "השתקה כבויה (Unmute)"
            Toast.makeText(this, "מיקרופון בשיחה: $stateText", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "שגיאה בשינוי מצב השתקת מיקרופון בשיחה: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
