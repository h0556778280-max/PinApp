package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

class AppsViewModel : ViewModel() {
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    fun setShowSystemApps(pm: PackageManager, show: Boolean) {
        _showSystemApps.value = show
        loadApps(pm, show)
    }

    fun loadApps(pm: PackageManager, showSystem: Boolean = false) {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val appList = if (showSystem) {
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                installedApps.mapNotNull { appInfo ->
                    val pkg = appInfo.packageName
                    if (pkg == "com.google.android.GoogleCameraEng") return@mapNotNull null
                    
                    AppInfo(
                        label = appInfo.loadLabel(pm).toString(),
                        packageName = pkg,
                        icon = appInfo.loadIcon(pm)
                    )
                }.sortedBy { it.label.lowercase() }
            } else {
                val mainIntent = Intent(Intent.ACTION_MAIN, null)
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                
                val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
                resolveInfos.mapNotNull { ri ->
                    val pkg = ri.activityInfo.packageName
                    if (pkg == "com.google.android.GoogleCameraEng") return@mapNotNull null
                    
                    AppInfo(
                        label = ri.loadLabel(pm).toString(),
                        packageName = pkg,
                        icon = ri.loadIcon(pm)
                    )
                }.sortedBy { it.label.lowercase() }
            }
            
            withContext(Dispatchers.Main) {
                _apps.value = appList
                _isLoading.value = false
            }
        }
    }
}

enum class MenuSection {
    HOME_SHORTCUTS,
    HARDWARE_GESTURES,
    TILES_NOTIFICATION,
    SYSTEM_SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AppsViewModel = viewModel()
) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val showSystemApps by viewModel.showSystemApps.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    var isNotificationEnabled by remember {
        mutableStateOf(NotificationHelper.isNotificationEnabled(context))
    }
    var isDevModeEnabled by remember {
        mutableStateOf(NotificationHelper.isDevModeEnabled(context))
    }
    var notificationTitle by remember {
        mutableStateOf(NotificationHelper.getNotificationTitle(context))
    }
    var notificationIconType by remember {
        mutableStateOf(NotificationHelper.getNotificationIconType(context))
    }
    var notificationPresetIcon by remember {
        mutableStateOf(NotificationHelper.getNotificationPresetIcon(context))
    }
    var customIconPath by remember {
        mutableStateOf(NotificationHelper.getNotificationCustomIconPath(context))
    }
    var notificationBitmap by remember(customIconPath, notificationIconType) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }
    LaunchedEffect(customIconPath, notificationIconType) {
        if (notificationIconType == "custom" && customIconPath != null) {
            val loadedBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val file = java.io.File(customIconPath!!)
                    if (file.exists()) {
                        android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            notificationBitmap = loadedBitmap
        } else {
            notificationBitmap = null
        }
    }
    var selectedImageUriForConversion by remember { mutableStateOf<android.net.Uri?>(null) }
    var activeTileIdForImageConversion by remember { mutableStateOf<Int?>(null) }
    
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedImageUriForConversion = uri
        }
    }

    // Reactive states for currently selected target packages
    var homeTargetPkg by remember {
        mutableStateOf(NotificationHelper.getTargetPackage(context, TriggerType.HOME))
    }
    var assistTargetPkg by remember {
        mutableStateOf(NotificationHelper.getTargetPackage(context, TriggerType.ASSIST))
    }
    var cameraTargetPkg by remember {
        mutableStateOf(NotificationHelper.getTargetPackage(context, TriggerType.CAMERA))
    }
    val tileTargetPkgs = remember {
        androidx.compose.runtime.mutableStateMapOf<Int, String?>().apply {
            for (i in 1..15) {
                val type = NotificationHelper.getTileTriggerType(i)!!
                put(i, NotificationHelper.getTargetPackage(context, type))
            }
        }
    }
    val tileAddedStates = remember {
        androidx.compose.runtime.mutableStateMapOf<Int, Boolean>().apply {
            for (i in 1..15) {
                put(i, NotificationHelper.isTileAdded(context, i))
            }
        }
    }
    var showAddTileDialog by remember { mutableStateOf(false) }
    var shortcutTargetPkg by remember {
        mutableStateOf(NotificationHelper.getTargetPackage(context, TriggerType.SHORTCUT))
    }

    var refreshCounter by remember { mutableStateOf(0) }
    var isSelectingForSequence by remember { mutableStateOf(false) }

    // Track active selection
    var selectingForTrigger by remember { mutableStateOf<TriggerType?>(null) }
    var isSelectingForShortcut by remember { mutableStateOf(false) }
    var addingActionToSequenceId by remember { mutableStateOf<String?>(null) }
    var showShortcutTypeSelector by remember { mutableStateOf(false) }
    var activeSequenceIdForDesign by remember { mutableStateOf<String?>(null) }
    var selectedMenuSection by remember { mutableStateOf<MenuSection?>(null) }

    // State for launching the shortcut designer
    var designerTargetApp by remember { mutableStateOf<AppInfo?>(null) }
    var designerTargetActivity by remember { mutableStateOf<android.content.pm.ActivityInfo?>(null) }

    val manageRuntimePermissions = remember {
        listOf(
            Pair(Manifest.permission.POST_NOTIFICATIONS, "התראות (Notifications)"),
            Pair(Manifest.permission.CALL_PHONE, "ביצוע שיחות טלפון (Call Phone)"),
            Pair(Manifest.permission.SEND_SMS, "שליחת הודעות SMS (Send SMS)"),
            Pair(Manifest.permission.ANSWER_PHONE_CALLS, "מענה לשיחות (Answer Calls)"),
            Pair(Manifest.permission.READ_PHONE_STATE, "קריאת מצב טלפון (Read Phone State)"),
            Pair(Manifest.permission.RECEIVE_SMS, "קבלת הודעות SMS (Receive SMS)"),
            Pair(Manifest.permission.READ_SMS, "קריאת הודעות SMS (Read SMS)"),
            Pair(Manifest.permission.ACCESS_FINE_LOCATION, "מיקום מדויק (Fine Location)"),
            Pair(Manifest.permission.ACCESS_COARSE_LOCATION, "מיקום מקורב (Coarse Location)"),
            Pair(Manifest.permission.CAMERA, "גישה למצלמה (Camera)"),
            Pair(Manifest.permission.RECORD_AUDIO, "הקלטת שמע (Record Audio)")
        )
    }

    var appPermissionsStatusMap by remember {
        mutableStateOf(
            manageRuntimePermissions.associate { perm ->
                val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && perm.first == Manifest.permission.ANSWER_PHONE_CALLS) {
                    context.checkSelfPermission(perm.first) == PackageManager.PERMISSION_GRANTED
                } else if (perm.first == Manifest.permission.ANSWER_PHONE_CALLS) {
                    true
                } else {
                    context.checkSelfPermission(perm.first) == PackageManager.PERMISSION_GRANTED
                }
                perm.first to granted
            }
        )
    }

    val requestAllPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        appPermissionsStatusMap = manageRuntimePermissions.associate { perm ->
            val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && perm.first == Manifest.permission.ANSWER_PHONE_CALLS) {
                context.checkSelfPermission(perm.first) == PackageManager.PERMISSION_GRANTED
            } else if (perm.first == Manifest.permission.ANSWER_PHONE_CALLS) {
                true
            } else {
                context.checkSelfPermission(perm.first) == PackageManager.PERMISSION_GRANTED
            }
            perm.first to granted
        }
        val grantedCount = results.values.count { it }
        android.widget.Toast.makeText(context, "עודכן! $grantedCount הרשאות מאושרות.", android.widget.Toast.LENGTH_SHORT).show()
    }

    // States for custom Intents / system requests
    var isCreatingIntent by remember { mutableStateOf(false) }
    var intentToEdit by remember { mutableStateOf<Intent?>(null) }
    var editingIntentUri by remember { mutableStateOf<String?>(null) }
    var isManagingIntents by remember { mutableStateOf(false) }
    var designerTargetIntentUri by remember { mutableStateOf<String?>(null) }
    var designerTargetIntentLabel by remember { mutableStateOf<String?>(null) }
    var savedIntentsList by remember { mutableStateOf(NotificationHelper.getSavedIntents(context)) }

    var hasWriteSettingsPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(context)
            } else {
                true
            }
        )
    }
    val tileIntents = remember {
        androidx.compose.runtime.mutableStateMapOf<Int, Intent?>().apply {
            for (i in 1..15) {
                put(i, NotificationHelper.getTileIntent(context, i))
            }
        }
    }
    var isSelectingForTileId by remember { mutableStateOf<Int?>(null) }
    var devEditTriggerTargetUri by remember { mutableStateOf<String?>(null) }
    var devEditTriggerTargetType by remember { mutableStateOf<TriggerType?>(null) }
    var devEditTriggerIsSequence by remember { mutableStateOf(false) }
    var devEditSequenceTargetId by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, refreshCounter) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasWriteSettingsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.System.canWrite(context)
                } else {
                    true
                }
                for (i in 1..15) {
                    val type = NotificationHelper.getTileTriggerType(i)!!
                    tileIntents[i] = NotificationHelper.getTileIntent(context, i)
                    tileTargetPkgs[i] = NotificationHelper.getTargetPackage(context, type)
                    tileAddedStates[i] = NotificationHelper.isTileAdded(context, i)
                }
                homeTargetPkg = NotificationHelper.getTargetPackage(context, TriggerType.HOME)
                assistTargetPkg = NotificationHelper.getTargetPackage(context, TriggerType.ASSIST)
                cameraTargetPkg = NotificationHelper.getTargetPackage(context, TriggerType.CAMERA)
                shortcutTargetPkg = NotificationHelper.getTargetPackage(context, TriggerType.SHORTCUT)
                savedIntentsList = NotificationHelper.getSavedIntents(context)
                appPermissionsStatusMap = manageRuntimePermissions.associate { perm ->
                    val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && perm.first == Manifest.permission.ANSWER_PHONE_CALLS) {
                        context.checkSelfPermission(perm.first) == PackageManager.PERMISSION_GRANTED
                    } else if (perm.first == Manifest.permission.ANSWER_PHONE_CALLS) {
                        true
                    } else {
                        context.checkSelfPermission(perm.first) == PackageManager.PERMISSION_GRANTED
                    }
                    perm.first to granted
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(refreshCounter) {
        for (i in 1..15) {
            val type = NotificationHelper.getTileTriggerType(i)!!
            tileIntents[i] = NotificationHelper.getTileIntent(context, i)
            tileTargetPkgs[i] = NotificationHelper.getTargetPackage(context, type)
            tileAddedStates[i] = NotificationHelper.isTileAdded(context, i)
        }
        homeTargetPkg = NotificationHelper.getTargetPackage(context, TriggerType.HOME)
        assistTargetPkg = NotificationHelper.getTargetPackage(context, TriggerType.ASSIST)
        cameraTargetPkg = NotificationHelper.getTargetPackage(context, TriggerType.CAMERA)
        shortcutTargetPkg = NotificationHelper.getTargetPackage(context, TriggerType.SHORTCUT)
    }

    BackHandler(enabled = selectedMenuSection != null) {
        selectedMenuSection = null
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val saveTriggerTarget: (String, TriggerType, Boolean) -> Unit = { target, targetTrigger, isSeq ->
        if (isSeq) {
            NotificationHelper.addSequenceTarget(context, targetTrigger, target)
            refreshCounter++
        } else {
            NotificationHelper.saveTargetPackage(context, targetTrigger, target)
            when (targetTrigger) {
                TriggerType.HOME -> homeTargetPkg = target
                TriggerType.ASSIST -> assistTargetPkg = target
                TriggerType.CAMERA -> cameraTargetPkg = target
                TriggerType.SHORTCUT -> shortcutTargetPkg = target
                else -> {
                    val tileId = targetTrigger.name.removePrefix("TILE").toIntOrNull()
                    if (tileId != null) {
                        tileTargetPkgs[tileId] = target
                    }
                }
            }
        }
        android.widget.Toast.makeText(context, "ההגדרה נשמרה בהצלחה!", android.widget.Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        viewModel.loadApps(context.packageManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (selectingForTrigger != null || isSelectingForShortcut || addingActionToSequenceId != null) {
        val trigger = selectingForTrigger
        val triggerTitle = when {
            addingActionToSequenceId != null -> "בחירת פעולה לסבב פעולות"
            isSelectingForShortcut -> "בחירת אפליקציה לייצור אייקון"
            trigger == TriggerType.HOME -> "בחירת אפליקציה למסך הבית"
            trigger == TriggerType.ASSIST -> "בחירת אפליקציה לסייען הקולי"
            trigger == TriggerType.CAMERA -> "בחירת אפליקציה למצלמה"
            trigger == TriggerType.TILE1 -> "בחירת אפליקציה לאריח מהיר 1"
            trigger == TriggerType.TILE2 -> "בחירת אפליקציה לאריח מהיר 2"
            trigger == TriggerType.TILE3 -> "בחירת אפליקציה לאריח מהיר 3"
            trigger == TriggerType.SHORTCUT -> "בחירת אפליקציה לסבב פעולות"
            else -> ""
        }

        var selectedAppForActivities by remember { mutableStateOf<AppInfo?>(null) }
        var activitiesList by remember { mutableStateOf<List<android.content.pm.ActivityInfo>>(emptyList()) }
        var isActivitiesLoading by remember { mutableStateOf(false) }
        var activitiesSearchQuery by remember { mutableStateOf("") }
        
        BackHandler {
            if (selectedAppForActivities != null) {
                selectedAppForActivities = null
                activitiesSearchQuery = ""
            } else {
                selectingForTrigger = null
                isSelectingForShortcut = false
                addingActionToSequenceId = null
                searchQuery = ""
            }
        }

        val filteredActivities = remember(activitiesList, activitiesSearchQuery) {
            if (activitiesSearchQuery.isBlank()) {
                activitiesList
            } else {
                activitiesList.filter { activity ->
                    val activityLabel = try {
                        val label = activity.loadLabel(context.packageManager).toString()
                        if (label.isNotEmpty()) label else ""
                    } catch (e: Exception) {
                        ""
                    }
                    activity.name.contains(activitiesSearchQuery, ignoreCase = true) ||
                            activityLabel.contains(activitiesSearchQuery, ignoreCase = true)
                }
            }
        }

        LaunchedEffect(selectedAppForActivities) {
            val app = selectedAppForActivities
            if (app != null) {
                isActivitiesLoading = true
                withContext(Dispatchers.IO) {
                    val pm = context.packageManager
                    val list = try {
                        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()))
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES)
                        }
                        packageInfo.activities?.toList() ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                    withContext(Dispatchers.Main) {
                        activitiesList = list.sortedBy { it.name.substringAfterLast('.') }
                        isActivitiesLoading = false
                    }
                }
            } else {
                activitiesList = emptyList()
            }
        }

        if (selectedAppForActivities != null) {
            val app = selectedAppForActivities!!
            val appActivitiesTitle = "בחירת תת-אקטיביטי מ-${app.label}"

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(appActivitiesTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { 
                                selectedAppForActivities = null
                                activitiesSearchQuery = ""
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "חזרה לבחירת אפליקציות"
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    OutlinedTextField(
                        value = activitiesSearchQuery,
                        onValueChange = { activitiesSearchQuery = it },
                        label = { Text("חיפוש תת-אקטיביטי (שם או מחלקה)") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "חיפוש") },
                        trailingIcon = {
                            if (activitiesSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { activitiesSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "נקה")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                        item {
                            // Header card with default action
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = app.icon,
                                            contentDescription = app.label,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "הפעלת האפליקציה כרגיל",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "ברירת מחדל",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Design custom shortcut icon button
                                            IconButton(
                                                onClick = {
                                                    designerTargetApp = app
                                                    designerTargetActivity = null
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "ייצר אייקון",
                                                    tint = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // Add default app launch to sequence (loop)
                                            IconButton(
                                                onClick = {
                                                    saveTriggerTarget(app.packageName, TriggerType.SHORTCUT, true)
                                                    android.widget.Toast.makeText(context, "האפליקציה נוספה לסבב פעולות!", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "הוסף לסבב פעולות",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            if (addingActionToSequenceId != null) {
                                                Button(
                                                    onClick = {
                                                        if (isDevModeEnabled) {
                                                            val defaultIntent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent(Intent.ACTION_MAIN).apply {
                                                                setPackage(app.packageName)
                                                            }
                                                            defaultIntent.putExtra("PINAPP_INTENT_LABEL", app.label)
                                                            devEditTriggerTargetUri = defaultIntent.toUri(Intent.URI_INTENT_SCHEME)
                                                            devEditSequenceTargetId = addingActionToSequenceId
                                                        } else {
                                                            NotificationHelper.addSequenceTargetForId(context, addingActionToSequenceId!!, app.packageName)
                                                            refreshCounter++
                                                        }
                                                        addingActionToSequenceId = null
                                                        selectedAppForActivities = null
                                                        isSelectingForSequence = false
                                                        searchQuery = ""
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("בחר", style = MaterialTheme.typography.labelMedium)
                                                }
                                            } else if (!isSelectingForShortcut && trigger != null) {
                                                Button(
                                                    onClick = {
                                                        val defaultIntent = context.packageManager.getLaunchIntentForPackage(app.packageName) ?: Intent(Intent.ACTION_MAIN).apply {
                                                            setPackage(app.packageName)
                                                        }
                                                        defaultIntent.putExtra("PINAPP_INTENT_LABEL", app.label)
                                                        val defaultUri = defaultIntent.toUri(Intent.URI_INTENT_SCHEME)
                                                        if (isDevModeEnabled) {
                                                            devEditTriggerTargetUri = defaultUri
                                                            devEditTriggerTargetType = trigger
                                                            devEditTriggerIsSequence = isSelectingForSequence
                                                        } else {
                                                            saveTriggerTarget(app.packageName, trigger, isSelectingForSequence)
                                                        }
                                                        selectedAppForActivities = null
                                                        selectingForTrigger = null
                                                        isSelectingForSequence = false
                                                        searchQuery = ""
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("בחר", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "תת-אקטיביטיז פומביים ופנימיים זמינים:",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        if (isActivitiesLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        } else if (activitiesList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "לא נמצאו תת-אקטיביטיז במניפסט של אפליקציה זו. תוכל לבחור רק בהפעלת האפליקציה כרגיל.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else if (filteredActivities.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "לא נמצאו תת-אקטיביטיז המתאימים לחיפוש שלך.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(filteredActivities, key = { it.name }) { activity ->
                                val activityLabel = remember(activity) {
                                    try {
                                        val label = activity.loadLabel(context.packageManager).toString()
                                        if (label.isNotEmpty() && label != app.label) label else null
                                    } catch (e: Exception) {
                                        null
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (addingActionToSequenceId != null) {
                                                if (isDevModeEnabled) {
                                                    val customIntent = Intent(Intent.ACTION_MAIN).apply {
                                                        setClassName(app.packageName, activity.name)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    val actLabel = try {
                                                        val l = activity.loadLabel(context.packageManager).toString()
                                                        if (l.isNotEmpty()) l else null
                                                     } catch (e: Exception) { null }
                                                    val displayLabel = if (actLabel != null && actLabel != app.label) "${app.label} - $actLabel" else app.label
                                                    customIntent.putExtra("PINAPP_INTENT_LABEL", displayLabel)
                                                    devEditTriggerTargetUri = customIntent.toUri(Intent.URI_INTENT_SCHEME)
                                                    devEditSequenceTargetId = addingActionToSequenceId
                                                } else {
                                                    val fullTarget = "${app.packageName}/${activity.name}"
                                                    NotificationHelper.addSequenceTargetForId(context, addingActionToSequenceId!!, fullTarget)
                                                    refreshCounter++
                                                }
                                                addingActionToSequenceId = null
                                                selectedAppForActivities = null
                                                isSelectingForSequence = false
                                                searchQuery = ""
                                                activitiesSearchQuery = ""
                                            } else if (isSelectingForShortcut) {
                                                designerTargetApp = app
                                                designerTargetActivity = activity
                                            } else if (trigger != null) {
                                                val fullTarget = "${app.packageName}/${activity.name}"
                                                if (isDevModeEnabled) {
                                                    val customIntent = Intent(Intent.ACTION_MAIN).apply {
                                                        setClassName(app.packageName, activity.name)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    val actLabel = try {
                                                        val l = activity.loadLabel(context.packageManager).toString()
                                                        if (l.isNotEmpty()) l else null
                                                     } catch (e: Exception) { null }
                                                    val displayLabel = if (actLabel != null && actLabel != app.label) "${app.label} - $actLabel" else app.label
                                                    customIntent.putExtra("PINAPP_INTENT_LABEL", displayLabel)
                                                    devEditTriggerTargetUri = customIntent.toUri(Intent.URI_INTENT_SCHEME)
                                                    devEditTriggerTargetType = trigger
                                                    devEditTriggerIsSequence = isSelectingForSequence
                                                } else {
                                                    saveTriggerTarget(fullTarget, trigger, isSelectingForSequence)
                                                }
                                                selectedAppForActivities = null
                                                selectingForTrigger = null
                                                isSelectingForSequence = false
                                                searchQuery = ""
                                                activitiesSearchQuery = ""
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = activityLabel ?: activity.name.substringAfterLast('.'),
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = activity.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        
                                        // Badge for exported status
                                        val badgeText = if (activity.exported) "ציבורי (בטוח)" else "פנימי (עלול לא לפעול)"
                                        val badgeColor = if (activity.exported) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.errorContainer
                                        }
                                        val badgeTextColor = if (activity.exported) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        }
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = badgeColor,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = badgeText,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = badgeTextColor
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Custom Shortcut Icon Maker button
                                        IconButton(
                                            onClick = {
                                                designerTargetApp = app
                                                designerTargetActivity = activity
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = "ייצר קיצור דרך מעוצב",
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                        }

                                        // Add to sequence button (loop icon)
                                        IconButton(
                                            onClick = {
                                                val fullTarget = "${app.packageName}/${activity.name}"
                                                saveTriggerTarget(fullTarget, TriggerType.SHORTCUT, true)
                                                android.widget.Toast.makeText(context, "תת-אקטיביטי נוסף לסבב פעולות!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "הוסף לסבב פעולות (לולאה)",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        } else {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(triggerTitle) },
                        navigationIcon = {
                            IconButton(onClick = { 
                                selectingForTrigger = null 
                                isSelectingForShortcut = false
                                addingActionToSequenceId = null
                                searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "חזרה"
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                var selectedTab by remember { mutableStateOf(0) }
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("אפליקציות") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("בקשות מערכת (Intents)") }
                        )
                    }

                    if (selectedTab == 0) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("חיפוש אפליקציות...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "חיפוש") },
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "הצג אפליקציות מערכת וחבילות מוסתרות",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Switch(
                                checked = showSystemApps,
                                onCheckedChange = { isChecked ->
                                    viewModel.setShowSystemApps(context.packageManager, isChecked)
                                }
                            )
                        }

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            val filteredApps = remember(searchQuery, apps) {
                                apps.filter { 
                                    it.label.contains(searchQuery, ignoreCase = true) || 
                                    it.packageName.contains(searchQuery, ignoreCase = true)
                                }
                            }

                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredApps, key = { it.packageName }) { app ->
                                    AppListItem(app = app, onClick = {
                                        selectedAppForActivities = app
                                    })
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { isManagingIntents = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ניהול בקשות")
                                }
                                Button(
                                    onClick = { isCreatingIntent = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("בקשה חדשה")
                                }
                            }

                            if (savedIntentsList.isEmpty()) {
                                Box(
                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "אין בקשות מערכת שמורות. צור אחת חדשה כדי לבחור אותה.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(savedIntentsList) { intent ->
                                        val intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
                                        val label = intent.getStringExtra("PINAPP_INTENT_LABEL") ?: "בקשת מערכת"
                                        val action = intent.action ?: "ללא Action"
                                        val data = intent.dataString ?: "ללא Data"

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Send,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Action: $action", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (data != "ללא Data") {
                                                    Text("Data: $data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.End
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            try {
                                                                val testIntent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME).apply {
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                                context.startActivity(testIntent)
                                                            } catch (e: Exception) {
                                                                android.widget.Toast.makeText(context, "שגיאה בהפעלה: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "בדיקה", tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            NotificationHelper.deleteIntent(context, intentUri)
                                                            savedIntentsList = NotificationHelper.getSavedIntents(context)
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "מחיקה", tint = MaterialTheme.colorScheme.error)
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Button(
                                                        onClick = {
                                                            if (addingActionToSequenceId != null) {
                                                                if (isDevModeEnabled) {
                                                                    devEditTriggerTargetUri = intentUri
                                                                    devEditSequenceTargetId = addingActionToSequenceId
                                                                } else {
                                                                    NotificationHelper.addSequenceTargetForId(context, addingActionToSequenceId!!, intentUri)
                                                                    refreshCounter++
                                                                }
                                                                addingActionToSequenceId = null
                                                            } else if (isSelectingForShortcut) {
                                                                designerTargetIntentUri = intentUri
                                                                designerTargetIntentLabel = label
                                                            } else if (trigger != null) {
                                                                if (isDevModeEnabled) {
                                                                    devEditTriggerTargetUri = intentUri
                                                                    devEditTriggerTargetType = trigger
                                                                    devEditTriggerIsSequence = isSelectingForSequence
                                                                } else {
                                                                    saveTriggerTarget(intentUri, trigger, isSelectingForSequence)
                                                                }
                                                            }
                                                            selectingForTrigger = null
                                                            isSelectingForShortcut = false
                                                            isSelectingForSequence = false
                                                        }
                                                    ) {
                                                        Text("בחר")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        if (selectedMenuSection == null) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "PINAPP - תפריט ראשי",
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Right
                            )
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "ברוכים הבאים ל-PINAPP 🚀",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "התאם אישית מחוות פיזיות, אריחי הגדרות מהירות וקיצורי דרך למסך הבית להפעלת כל אפליקציה או פקודה במהירות שיא.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "בחר קטגוריה להגדרה:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    MenuCard(
                        title = "אייקונים וקיצורי דרך למסך הבית",
                        description = "יצירת קיצורי דרך מותאמים אישית (כולל אימוג'י וצבעים) וסבבי פעולות מהירים ישירות ממסך הבית.",
                        icon = Icons.Default.Home,
                        badgeText = if (shortcutTargetPkg != null) "סבב פעולות: פעיל" else "סבב פעולות: לא מוגדר",
                        badgeColor = if (shortcutTargetPkg != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        onClick = { selectedMenuSection = MenuSection.HOME_SHORTCUTS }
                    )

                    val definedGesturesCount = listOfNotNull(homeTargetPkg, assistTargetPkg, cameraTargetPkg).size
                    MenuCard(
                        title = "מחוות ולחיצות על מקשי המכשיר",
                        description = "התאמת פקודות ואפליקציות ללחיצה על כפתור הבית, הסייען הקולי או מקש המצלמה הפיזי.",
                        icon = Icons.Default.Settings,
                        badgeText = "מוגדרים: $definedGesturesCount / 3",
                        badgeColor = if (definedGesturesCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        onClick = { selectedMenuSection = MenuSection.HARDWARE_GESTURES }
                    )

                    val activeTilesCount = tileAddedStates.values.count { it }
                    MenuCard(
                        title = "אריחים ולוח ההתראות העליון",
                        description = "ניהול התראה קבועה בלוח העליון והגדרת עד 15 אריחי גישה מהירה מותאמים אישית.",
                        icon = Icons.Default.Notifications,
                        badgeText = "התראה: ${if (isNotificationEnabled) "פעילה" else "כבויה"} | אריחים: $activeTilesCount פעילים",
                        badgeColor = if (isNotificationEnabled || activeTilesCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        onClick = { selectedMenuSection = MenuSection.TILES_NOTIFICATION }
                    )

                    val permissionStatusText = if (hasWriteSettingsPermission) "הרשאה מאושרת" else "נדרשת הרשאת כתיבה"
                    MenuCard(
                        title = "הגדרות והרשאות מערכת",
                        description = "ניהול פקודות מערכת מותאמות (Intents), הרשאות מערכת והגדרות ברירת מחדל.",
                        icon = Icons.Default.Lock,
                        badgeText = "$permissionStatusText | פקודות: ${savedIntentsList.size}",
                        badgeColor = if (hasWriteSettingsPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        onClick = { selectedMenuSection = MenuSection.SYSTEM_SETTINGS }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        } else {
            when (selectedMenuSection) {
                MenuSection.HOME_SHORTCUTS -> {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("אייקונים וקיצורי דרך למסך הבית") },
                                navigationIcon = {
                                    IconButton(onClick = { selectedMenuSection = null }) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "הגדר אילו אפליקציות ייפתחו אוטומטית בכל פעם שתפעיל את קיצורי הדרך השונים של המכשיר שלך.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            CategoryHeader(title = "אייקונים וקיצורי דרך למסך הבית", icon = Icons.Default.Home)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "יצירת אייקונים וקיצורי דרך למסך הבית",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "צור אייקונים מעוצבים משלך (כולל אימוג'י וצבעים מותאמים אישית) והוסף אותם למסך הבית להפעלת אפליקציות או תת-אקטיביטיז פנימיים במהירות.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                                    ) {
                                        Button(
                                            onClick = { showShortcutTypeSelector = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.Star, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("צור קיצור דרך מעוצב")
                                        }
                                    }
                                }
                            }

                            TriggerConfigCard(
                                title = "הגדרת סבב פעולות לאייקונים (קיצורי דרך)",
                                description = "הגדר את רשימת הפעולות שיופעלו במחזוריות (אחת אחרי השנייה) בכל פעם שתלחץ על הקיצור המעוצב שייצרת באמצעות כפתור 'אייקון לסבב פעולות' שלמעלה.",
                                type = TriggerType.SHORTCUT,
                                icon = Icons.Default.Refresh,
                                targetPkg = shortcutTargetPkg,
                                onSelectClick = { 
                                    isSelectingForSequence = NotificationHelper.isSequenceEnabled(context, TriggerType.SHORTCUT)
                                    selectingForTrigger = TriggerType.SHORTCUT 
                                },
                                onClearClick = {
                                    NotificationHelper.clearTargetPackage(context, TriggerType.SHORTCUT)
                                    shortcutTargetPkg = null
                                },
                                refreshCounter = refreshCounter,
                                onRefreshRequest = { refreshCounter++ }
                            )

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
                MenuSection.HARDWARE_GESTURES -> {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("מחוות ולחיצות על מקשי המכשיר") },
                                navigationIcon = {
                                    IconButton(onClick = { selectedMenuSection = null }) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "הגדר אילו אפליקציות ייפתחו אוטומטית בכל פעם שתפעיל את קיצורי הדרך השונים של המכשיר שלך.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            CategoryHeader(title = "מחוות ולחיצות על מקשי המכשיר", icon = Icons.Default.Settings)

                            TriggerConfigCard(
                                title = "לחיצה על כפתור הבית",
                                description = "הגדר איזו אפליקציה תיפתח בכל פעם שתלחץ על כפתור הבית במכשיר.",
                                type = TriggerType.HOME,
                                icon = Icons.Default.Home,
                                targetPkg = homeTargetPkg,
                                onSelectClick = { 
                                    isSelectingForSequence = NotificationHelper.isSequenceEnabled(context, TriggerType.HOME)
                                    selectingForTrigger = TriggerType.HOME 
                                },
                                onClearClick = {
                                    NotificationHelper.clearTargetPackage(context, TriggerType.HOME)
                                    homeTargetPkg = null
                                },
                                refreshCounter = refreshCounter,
                                onRefreshRequest = { refreshCounter++ }
                            )

                            TriggerConfigCard(
                                title = "הפעלת הסייען הקולי (Assist)",
                                description = "הגדר איזו אפליקציה תיפתח בעת הפעלת הסייען (לחיצה ארוכה על כפתור הבית, מחווה, או כפתור ייעודי).",
                                type = TriggerType.ASSIST,
                                icon = Icons.Default.Star,
                                targetPkg = assistTargetPkg,
                                onSelectClick = { 
                                    isSelectingForSequence = NotificationHelper.isSequenceEnabled(context, TriggerType.ASSIST)
                                    selectingForTrigger = TriggerType.ASSIST 
                                },
                                onClearClick = {
                                    NotificationHelper.clearTargetPackage(context, TriggerType.ASSIST)
                                    assistTargetPkg = null
                                },
                                refreshCounter = refreshCounter,
                                onRefreshRequest = { refreshCounter++ }
                            )

                            TriggerConfigCard(
                                title = "קיצור דרך למצלמה (לחיצה כפולה)",
                                description = "הגדר איזו אפליקציה תיפתח בעת הפעלת קיצור הדרך למצלמה של המכשיר (למשל לחיצה כפולה על מקש ההפעלה).",
                                type = TriggerType.CAMERA,
                                icon = Icons.Default.Settings,
                                targetPkg = cameraTargetPkg,
                                onSelectClick = { 
                                    isSelectingForSequence = NotificationHelper.isSequenceEnabled(context, TriggerType.CAMERA)
                                    selectingForTrigger = TriggerType.CAMERA 
                                },
                                onClearClick = {
                                    NotificationHelper.clearTargetPackage(context, TriggerType.CAMERA)
                                    cameraTargetPkg = null
                                },
                                refreshCounter = refreshCounter,
                                onRefreshRequest = { refreshCounter++ }
                            )

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
                MenuSection.TILES_NOTIFICATION -> {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("אריחים ולוח ההתראות העליון") },
                                navigationIcon = {
                                    IconButton(onClick = { selectedMenuSection = null }) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "הגדר אילו אפליקציות ייפתחו אוטומטית בכל פעם שתפעיל את קיצורי הדרך השונים של המכשיר שלך.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            CategoryHeader(title = "אריחים ולוח ההתראות העליון", icon = Icons.Default.Notifications)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "הצגת התראה קבועה",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "מאפשרת לגשת להגדרות אלו במהירות מלוח ההתראות בכל עת",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = isNotificationEnabled,
                                            onCheckedChange = { checked ->
                                                NotificationHelper.setNotificationEnabled(context, checked)
                                                isNotificationEnabled = checked
                                            }
                                        )
                                    }

                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = isNotificationEnabled
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(1.dp)
                                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            )

                                            Text(
                                                text = "עריכת מראה ההתראה",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            OutlinedTextField(
                                                value = notificationTitle,
                                                onValueChange = { notificationTitle = it },
                                                label = { Text("כותרת ההתראה (שם)") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                trailingIcon = {
                                                    IconButton(
                                                        onClick = {
                                                            NotificationHelper.setNotificationTitle(context, notificationTitle)
                                                            Toast.makeText(context, "שם ההתראה עודכן!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = "שמור כותרת",
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            )

                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "בחירת סמל (אייקון)",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "סמלים מובנים מראש:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            val presets = listOf(
                                                Pair(android.R.drawable.ic_menu_revert, "חץ"),
                                                Pair(android.R.drawable.ic_menu_compass, "מצפן"),
                                                Pair(android.R.drawable.ic_menu_mylocation, "מיקום"),
                                                Pair(android.R.drawable.ic_menu_directions, "ניווט"),
                                                Pair(android.R.drawable.ic_menu_camera, "מצלמה"),
                                                Pair(android.R.drawable.ic_menu_manage, "הגדרות"),
                                                Pair(android.R.drawable.ic_menu_view, "עין"),
                                                Pair(android.R.drawable.ic_dialog_info, "מידע"),
                                                Pair(android.R.drawable.star_on, "כוכב"),
                                                Pair(android.R.drawable.ic_menu_send, "שליחה")
                                            )

                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                items(presets) { (resId, label) ->
                                                    val isSelected = notificationIconType == "preset" && notificationPresetIcon == resId
                                                    val iconDrawable = try {
                                                        androidx.core.content.ContextCompat.getDrawable(context, resId)
                                                    } catch (e: Exception) {
                                                        null
                                                    }

                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(
                                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                                else Color.Transparent
                                                            )
                                                            .border(
                                                                width = 1.dp,
                                                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                                                else MaterialTheme.colorScheme.outlineVariant,
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                            .clickable {
                                                                NotificationHelper.setNotificationPresetIcon(context, resId)
                                                                notificationPresetIcon = resId
                                                                notificationIconType = "preset"
                                                                Toast.makeText(context, "סמל ההתראה עודכן!", Toast.LENGTH_SHORT).show()
                                                            }
                                                            .padding(8.dp)
                                                            .width(54.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.size(24.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            if (iconDrawable != null) {
                                                                val bitmap = Bitmap.createBitmap(
                                                                    iconDrawable.intrinsicWidth.coerceAtLeast(48),
                                                                    iconDrawable.intrinsicHeight.coerceAtLeast(48),
                                                                    Bitmap.Config.ARGB_8888
                                                                )
                                                                val canvas = Canvas(bitmap)
                                                                iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                                                                iconDrawable.draw(canvas)
                                                                androidx.compose.foundation.Image(
                                                                    bitmap = bitmap.asImageBitmap(),
                                                                    contentDescription = label,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            } else {
                                                                Icon(
                                                                    Icons.Default.Star,
                                                                    contentDescription = label,
                                                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                                    else MaterialTheme.colorScheme.onSurface
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "סמל מותאם מהגלריה:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Button(
                                                        onClick = { galleryLauncher.launch("image/*") },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = MaterialTheme.colorScheme.secondary
                                                        )
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Edit,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("בחר תמונה מהגלריה")
                                                    }

                                                    if (notificationIconType == "custom" && notificationBitmap != null) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                                        ) {
                                                            androidx.compose.foundation.Image(
                                                                bitmap = notificationBitmap!!.asImageBitmap(),
                                                                contentDescription = "סמל מותאם נוכחי",
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .background(Color.DarkGray, shape = CircleShape)
                                                                    .padding(2.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = "פעיל",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            DynamicQuickSettingsTileManagementCard(
                                context = context,
                                tileTargetPkgs = tileTargetPkgs,
                                tileAddedStates = tileAddedStates,
                                onTileRemove = { tileNum ->
                                    NotificationHelper.setTileAdded(context, tileNum, false)
                                    tileTargetPkgs[tileNum] = null
                                    tileAddedStates[tileNum] = false
                                    Toast.makeText(context, "אריח $tileNum הוסר בהצלחה", Toast.LENGTH_SHORT).show()
                                },
                                onSelectTile = { trigger ->
                                    isSelectingForSequence = NotificationHelper.isSequenceEnabled(context, trigger)
                                    selectingForTrigger = trigger
                                },
                                onAddTileClick = {
                                    showAddTileDialog = true
                                },
                                refreshCounter = refreshCounter,
                                onRefreshRequest = { refreshCounter++ },
                                onCustomIconRequest = { tileId ->
                                    activeTileIdForImageConversion = tileId
                                    galleryLauncher.launch("image/*")
                                }
                            )

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
                MenuSection.SYSTEM_SETTINGS -> {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("הגדרות והרשאות מערכת") },
                                navigationIcon = {
                                    IconButton(onClick = { selectedMenuSection = null }) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "חזרה")
                                    }
                                }
                            )
                        }
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .padding(padding)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "הגדר אילו אפליקציות ייפתחו אוטומטית בכל פעם שתפעיל את קיצורי הדרך השונים של המכשיר שלך.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            CategoryHeader(title = "הגדרות והרשאות מערכת", icon = Icons.Default.Lock)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "ניהול בקשות מערכת (Intents)",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "הגדר פקודות מערכת לביצוע מהיר (כמו חיוג, SMS, ניווט, שליחת בקשות) ושייך אותן למחוות או צור להן קיצורי דרך מעוצבים למסך הבית.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { isManagingIntents = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("ניהול בקשות")
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { isCreatingIntent = true },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondary
                                            )
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("צור בקשת מערכת חדשה")
                                        }
                                    }
                                    
                                    if (savedIntentsList.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("בקשות שמורות (${savedIntentsList.size}):", style = MaterialTheme.typography.titleSmall)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        savedIntentsList.take(3).forEach { intent ->
                                            val intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
                                            val label = intent.getStringExtra("PINAPP_INTENT_LABEL") ?: "בקשת מערכת"
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Row {
                                                    IconButton(
                                                        onClick = {
                                                            try {
                                                                val testIntent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME).apply {
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                                context.startActivity(testIntent)
                                                            } catch (e: Exception) {
                                                                android.widget.Toast.makeText(context, "שגיאה בהפעלה: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                                            }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.PlayArrow, contentDescription = "בדיקה", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    IconButton(
                                                        onClick = {
                                                            NotificationHelper.deleteIntent(context, intentUri)
                                                            savedIntentsList = NotificationHelper.getSavedIntents(context)
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "מחיקה", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = "הגדרת ברירת מחדל במערכת",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "כדי שהקיצורים הללו יפעלו, עליך להגדיר את PINAPP כאפליקציית ברירת המחדל המתאימה במערכת.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                openSettingsSafely(context, Settings.ACTION_HOME_SETTINGS)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        ) {
                                            Text("הגדרת מסך בית", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }

                                        Button(
                                            onClick = {
                                                openSettingsSafely(context, Settings.ACTION_VOICE_INPUT_SETTINGS)
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.secondaryContainer
                                            )
                                        ) {
                                            Text("הגדרת סייען", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            openSettingsSafely(context, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Text("פתיחת הגדרות ברירת מחדל מלאות")
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hasWriteSettingsPermission) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = if (hasWriteSettingsPermission) Icons.Default.Check else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (hasWriteSettingsPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "שינוי הגדרות מערכת",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (hasWriteSettingsPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "נדרש כדי לשנות את בהירות המסך, זמן כיבוי מסך, או מצב סיבוב מסך אוטומטי מתוך קיצורי הדרך.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (hasWriteSettingsPermission) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    if (!hasWriteSettingsPermission) {
                                        Button(
                                            onClick = {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                                        data = android.net.Uri.parse("package:${context.packageName}")
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            },
                                            modifier = Modifier.align(Alignment.End),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("הענק הרשאת שינוי הגדרות מערכת")
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("ההרשאה מאושרת במכשיר", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }

                            CategoryHeader(title = "כל הרשאות המערכת והאפליקציה", icon = Icons.Default.Lock)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "אישור גורף של כל הרשאות האפליקציה",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "האפליקציה זקוקה למספר הרשאות runtime כדי לבצע מגוון פעולות כמו שיחות טלפון ישירות, שליחת SMS, פתיחת מצלמה, הדלקת פלאש, הקלטת שמע, מיקום ועוד. תוכל לאשר את כולן כעת בלחיצה אחת.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Display each permission status
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        manageRuntimePermissions.forEach { (permission, desc) ->
                                            val isGranted = appPermissionsStatusMap[permission] == true
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                    contentDescription = null,
                                                    tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = desc,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (isGranted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                                Spacer(modifier = Modifier.weight(1f))
                                                Text(
                                                    text = if (isGranted) "מאושר" else "לא מאושר",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            requestAllPermissionsLauncher.launch(
                                                manageRuntimePermissions.map { it.first }.toTypedArray()
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("הענק את כל ההרשאות כעת במכה אחת", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            CategoryHeader(title = "אפשרויות מתקדמות", icon = Icons.Default.Edit)

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "אפשרויות מפתחים (עריכת פקודות)",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = isDevModeEnabled,
                                            onCheckedChange = { checked ->
                                                isDevModeEnabled = checked
                                                NotificationHelper.setDevModeEnabled(context, checked)
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "כאשר אפשרות זו פעילה, לפני כל יצירת קיצור דרך, אייקון או סבב פעולות תופיע תיבת דו-שיח המציגה את פקודת ה-Intent המלאה. תוכל לערוך ולבדוק אותה כרצונך לפני השמירה וליהנות מגמישות מרבית.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
                else -> {}
            }
        }
    }

    if (designerTargetApp != null) {
        ShortcutDesignerDialog(
            app = designerTargetApp!!,
            activity = designerTargetActivity,
            onDismiss = {
                designerTargetApp = null
                designerTargetActivity = null
            },
            onShortcutCreated = {
                designerTargetApp = null
                designerTargetActivity = null
                isSelectingForShortcut = false
                selectingForTrigger = null
                searchQuery = ""
            }
        )
    }

    if (designerTargetIntentUri != null) {
        ShortcutDesignerDialogForIntent(
            intentUri = designerTargetIntentUri!!,
            intentLabel = designerTargetIntentLabel ?: "בקשת מערכת",
            onDismiss = {
                designerTargetIntentUri = null
                designerTargetIntentLabel = null
            },
            onShortcutCreated = {
                designerTargetIntentUri = null
                designerTargetIntentLabel = null
                isSelectingForShortcut = false
                selectingForTrigger = null
                searchQuery = ""
            }
        )
    }

    if (devEditTriggerTargetUri != null && devEditTriggerTargetType != null) {
        DeveloperIntentEditDialog(
            initialIntentUri = devEditTriggerTargetUri!!,
            onDismiss = {
                devEditTriggerTargetUri = null
                devEditTriggerTargetType = null
                devEditTriggerIsSequence = false
            },
            onConfirm = { editedUri ->
                saveTriggerTarget(editedUri, devEditTriggerTargetType!!, devEditTriggerIsSequence)
                devEditTriggerTargetUri = null
                devEditTriggerTargetType = null
                devEditTriggerIsSequence = false
            }
        )
    }

    if (devEditTriggerTargetUri != null && devEditSequenceTargetId != null) {
        DeveloperIntentEditDialog(
            initialIntentUri = devEditTriggerTargetUri!!,
            onDismiss = {
                devEditTriggerTargetUri = null
                devEditSequenceTargetId = null
            },
            onConfirm = { editedUri ->
                NotificationHelper.addSequenceTargetForId(context, devEditSequenceTargetId!!, editedUri)
                devEditTriggerTargetUri = null
                devEditSequenceTargetId = null
                refreshCounter++
            }
        )
    }

    if (showShortcutTypeSelector) {
        AlertDialog(
            onDismissRequest = { showShortcutTypeSelector = false },
            title = {
                Text(
                    text = "בחירת סוג קיצור דרך",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "בחר איזה סוג של קיצור דרך ברצונך לייצר ולהוסיף למסך הבית:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Simple shortcut to App/Setting
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showShortcutTypeSelector = false
                                isSelectingForShortcut = true
                            }
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "קיצור דרך פשוט (שיוך רגיל)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "הפעלת אפליקציה מסוימת, תת-הגדרה פנימית או פקודה בודדת.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Custom Action Sequence Shortcut
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showShortcutTypeSelector = false
                                activeSequenceIdForDesign = "seq_${System.currentTimeMillis()}"
                            }
                            .border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "קיצור לסבב פעולות (מחזורי)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "כל לחיצה על האייקון תריץ את הפעולה הבאה ברשימה מותאמת אישית שתגדיר.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showShortcutTypeSelector = false }) {
                    Text("ביטול")
                }
            }
        )
    }

    if (activeSequenceIdForDesign != null && addingActionToSequenceId == null) {
        ShortcutDesignerDialogForSequence(
            sequenceId = activeSequenceIdForDesign!!,
            apps = apps,
            savedIntentsList = savedIntentsList,
            refreshKey = refreshCounter,
            onAddActionRequested = { addingActionToSequenceId = activeSequenceIdForDesign },
            onDismiss = { activeSequenceIdForDesign = null },
            onShortcutCreated = {
                activeSequenceIdForDesign = null
                isSelectingForShortcut = false
                selectingForTrigger = null
                searchQuery = ""
            }
        )
    }

    if (isCreatingIntent || intentToEdit != null) {
        IntentCreatorDialog(
            intentToEdit = intentToEdit,
            editingUri = editingIntentUri,
            onDismiss = {
                isCreatingIntent = false
                intentToEdit = null
                editingIntentUri = null
            },
            onSaved = {
                savedIntentsList = NotificationHelper.getSavedIntents(context)
                isCreatingIntent = false
                intentToEdit = null
                editingIntentUri = null
            }
        )
    }

    if (isManagingIntents) {
        IntentsManagementDialog(
            savedIntents = savedIntentsList,
            onDismiss = { isManagingIntents = false },
            onEditIntent = { intent, uri ->
                intentToEdit = intent
                editingIntentUri = uri
            },
            onDeleteIntent = { uri ->
                NotificationHelper.deleteIntent(context, uri)
                savedIntentsList = NotificationHelper.getSavedIntents(context)
            },
            onTestIntent = { uri ->
                try {
                    val intent = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                    if (intent.action?.startsWith("com.example.action.") == true) {
                        intent.setClass(context, com.example.ActionActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    } else if (intent.action == Intent.ACTION_CALL) {
                        // Check call permission or fallback to dial
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                            intent.action = Intent.ACTION_DIAL
                        }
                    }
                    if (intent.resolveActivity(context.packageManager) != null || intent.component != null || intent.action?.startsWith("com.example.action.") == true) {
                        context.startActivity(intent)
                    } else {
                        android.widget.Toast.makeText(context, "לא נמצאה אפליקציה מתאימה לביצוע הפעולה", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "שגיאה בהפעלת ה-Intent: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            onCreateNew = {
                intentToEdit = null
                editingIntentUri = null
                isCreatingIntent = true
            }
        )
    }

    if (selectedImageUriForConversion != null) {
        val tileId = activeTileIdForImageConversion
        if (tileId != null) {
            TileIconConverterDialog(
                selectedUri = selectedImageUriForConversion!!,
                tileId = tileId,
                onDismiss = {
                    selectedImageUriForConversion = null
                    activeTileIdForImageConversion = null
                },
                onIconProcessed = { savedPath ->
                    NotificationHelper.setTileCustomIcon(context, tileId, savedPath)
                    selectedImageUriForConversion = null
                    activeTileIdForImageConversion = null
                    refreshCounter++
                    Toast.makeText(context, "סמל אריח $tileId עודכן לסמל מותאם בהצלחה!", Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            StatusBarIconConverterDialog(
                selectedUri = selectedImageUriForConversion!!,
                onDismiss = { selectedImageUriForConversion = null },
                onIconProcessed = { savedPath ->
                    NotificationHelper.setNotificationCustomIcon(context, savedPath)
                    customIconPath = savedPath
                    notificationIconType = "custom"
                    selectedImageUriForConversion = null
                    Toast.makeText(context, "סמל ההתראה עודכן לסמל מותאם בהצלחה!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    if (showAddTileDialog) {
        val dormantTiles = (1..15).filter { tileAddedStates[it] != true }
        AlertDialog(
            onDismissRequest = { showAddTileDialog = false },
            title = {
                Text(
                    "הוספת אריח מהיר חדש",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "בחר איזה אריח ברצונך להוסיף מווילון ההתראות המהירות:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (dormantTiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "הגעת למגבלה! כל 15 האריחים כבר הופעלו. לא ניתן להוסיף אריחים חדשים.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(dormantTiles) { tileNum ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            NotificationHelper.setTileAdded(context, tileNum, true)
                                            tileAddedStates[tileNum] = true
                                            showAddTileDialog = false
                                            Toast.makeText(context, "אריח $tileNum הופעל בהצלחה!", Toast.LENGTH_SHORT).show()
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text("אריח מהיר $tileNum", fontWeight = FontWeight.Bold)
                                            Text("הפעלת אריח מס' $tileNum מווילון ההתראות המהירות", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddTileDialog = false }) {
                    Text("ביטול")
                }
            }
        )
    }
}

fun resolveTargetLabel(context: Context, target: String?): String {
    if (target == null) return "לא הוגדרה פעולה"
    if (target == "action:step_separator") return "--- מפריד שלבים ---"
    val isIntent = target.startsWith("intent:")
    if (isIntent) {
        return try {
            val intent = Intent.parseUri(target, Intent.URI_INTENT_SCHEME)
            intent.getStringExtra("PINAPP_INTENT_LABEL") ?: "בקשת מערכת מותאמת"
        } catch (e: Exception) {
            "בקשת מערכת מותאמת"
        }
    } else {
        val pm = context.packageManager
        val pkg = if (target.contains("/")) target.split("/")[0] else target
        val cls = if (target.contains("/")) target.split("/")[1] else null
        return try {
            val appAi = pm.getApplicationInfo(pkg, 0)
            val appLabelStr = pm.getApplicationLabel(appAi).toString()
            if (cls != null) {
                try {
                    val compName = android.content.ComponentName(pkg, cls)
                    val activityInfo = pm.getActivityInfo(compName, 0)
                    val actLabel = activityInfo.loadLabel(pm).toString()
                    if (actLabel != appLabelStr && actLabel.isNotEmpty()) {
                        "$appLabelStr - $actLabel"
                    } else {
                        "$appLabelStr (${cls.substringAfterLast('.')})"
                    }
                } catch (e: Exception) {
                    "$appLabelStr (${cls.substringAfterLast('.')})"
                }
            } else {
                appLabelStr
            }
        } catch (e: Exception) {
            target
        }
    }
}

fun resolveTargetIcon(context: Context, target: String?): Any? {
    if (target == null) return null
    val isIntent = target.startsWith("intent:")
    if (isIntent) return null
    val pkg = if (target.contains("/")) target.split("/")[0] else target
    return try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun TriggerConfigCard(
    title: String,
    description: String,
    type: TriggerType,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    targetPkg: String?,
    onSelectClick: () -> Unit,
    onClearClick: () -> Unit,
    refreshCounter: Int,
    onRefreshRequest: () -> Unit,
    modifier: Modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
) {
    val context = LocalContext.current
    
    var isSequenceEnabled by remember(type) { mutableStateOf(NotificationHelper.isSequenceEnabled(context, type)) }
    var sequenceList by remember(type) { mutableStateOf(NotificationHelper.getSequence(context, type)) }
    var nextIndex by remember(type) { mutableStateOf(NotificationHelper.getSequenceIndex(context, type)) }

    LaunchedEffect(type, refreshCounter) {
        isSequenceEnabled = NotificationHelper.isSequenceEnabled(context, type)
        sequenceList = NotificationHelper.getSequence(context, type)
        nextIndex = NotificationHelper.getSequenceIndex(context, type)
    }

    var editingTileConfigIndex by remember(refreshCounter) { mutableStateOf<Int?>(null) }
    
    if (editingTileConfigIndex != null) {
        TileStepConfigDialog(
            context = context,
            type = type,
            index = editingTileConfigIndex!!,
            onDismiss = {
                editingTileConfigIndex = null
                onRefreshRequest()
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSequenceEnabled || targetPkg != null) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // Sequence Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "מצב סט פעולות מחזורי (סבב)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "בלחיצה הבאה, המערכת תעבור לפעולה הבאה ברשימה",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isSequenceEnabled,
                    onCheckedChange = { checked ->
                        NotificationHelper.setSequenceEnabled(context, type, checked)
                        onRefreshRequest()
                        val msg = if (checked) "מצב סבב פעולות הופעל!" else "מצב סבב פעולות כובה"
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                )
            }

            if (isSequenceEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                
                var isAllAtOnce by remember(type) { mutableStateOf(NotificationHelper.isSequenceAllAtOnce(context, type)) }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAllAtOnce,
                        onCheckedChange = { checked ->
                            NotificationHelper.setSequenceAllAtOnce(context, type, checked)
                            isAllAtOnce = checked
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.clickable {
                        val newChecked = !isAllAtOnce
                        NotificationHelper.setSequenceAllAtOnce(context, type, newChecked)
                        isAllAtOnce = newChecked
                    }) {
                        Text(
                            text = "הפעל את כל הפעולות בבת אחת (במקום לפי סדר)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isSequenceEnabled) {
                // SEQUENCE VIEW MODE
                Text(
                    text = "רשימת הפעולות בסבב (לפי סדר ההרצה):",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (sequenceList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "אין פעולות בסבב הנוכחי.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onSelectClick
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("הוסף פעולה ראשונה לסבב")
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sequenceList.forEachIndexed { index, target ->
                            val label = resolveTargetLabel(context, target)
                            val iconDrawable = resolveTargetIcon(context, target)
                            val isCurrentActive = index == nextIndex

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isCurrentActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        else MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = if (isCurrentActive) 1.5.dp else 1.dp,
                                        color = if (isCurrentActive) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Step Indicator & Icon
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            if (isCurrentActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.secondaryContainer,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (index + 1).toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentActive) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))

                                if (iconDrawable != null) {
                                    AsyncImage(
                                        model = iconDrawable,
                                        contentDescription = label,
                                        modifier = Modifier.size(28.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrentActive) FontWeight.Bold else FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isCurrentActive) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "הבא",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 9.sp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = target,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                // Reordering and Delete Buttons
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (type.name.startsWith("TILE")) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .clickable { editingTileConfigIndex = index }
                                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "הגדר אריח לשלב זה",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = index > 0) {
                                                NotificationHelper.moveSequenceTarget(context, type, index, true)
                                                onRefreshRequest()
                                            }
                                            .background(
                                                if (index > 0) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "הזז למעלה",
                                            tint = if (index > 0) MaterialTheme.colorScheme.onSecondaryContainer 
                                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = index < sequenceList.size - 1) {
                                                NotificationHelper.moveSequenceTarget(context, type, index, false)
                                                onRefreshRequest()
                                            }
                                            .background(
                                                if (index < sequenceList.size - 1) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "הזז למטה",
                                            tint = if (index < sequenceList.size - 1) MaterialTheme.colorScheme.onSecondaryContainer 
                                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                NotificationHelper.removeSequenceTargetAt(context, type, index)
                                                onRefreshRequest()
                                                Toast.makeText(context, "הפעולה הוסרה מהסבב", Toast.LENGTH_SHORT).show()
                                            }
                                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "מחק",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sequence Control Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                NotificationHelper.setSequenceIndex(context, type, 0)
                                onRefreshRequest()
                                Toast.makeText(context, "איפוס המדד בוצע בהצלחה", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("אפס סבב לראשון", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }

                        Button(
                            onClick = onSelectClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("הוסף פעולה לסבב", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            NotificationHelper.addSequenceTarget(context, type, "action:step_separator")
                            onRefreshRequest()
                            Toast.makeText(context, "מפריד שלבים נוסף לסבב", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("הוסף מפריד שלבים (לקבוצת פעולות)", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                // SINGLE ACTION VIEW MODE
                val isIntent = remember(targetPkg) {
                    targetPkg?.startsWith("intent:") == true
                }
                val parsedPkg = remember(targetPkg, isIntent) {
                    if (targetPkg == null || isIntent) null else {
                        if (targetPkg.contains("/")) targetPkg.split("/")[0] else targetPkg
                    }
                }
                val appLabel = remember(targetPkg, isIntent) {
                    resolveTargetLabel(context, targetPkg)
                }
                val appIcon = remember(parsedPkg) {
                    resolveTargetIcon(context, targetPkg)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (targetPkg != null) {
                        if (isIntent) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else if (appIcon != null) {
                            AsyncImage(
                                model = appIcon,
                                contentDescription = appLabel,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = targetPkg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "לא מוגדר (הגדרת ברירת המחדל של המכשיר)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons for Single Target
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (targetPkg != null) {
                        TextButton(
                            onClick = onClearClick,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("הסר")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(
                        onClick = onSelectClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(if (targetPkg != null) "שנה אפליקציה" else "בחר אפליקציה")
                    }
                }
            }
        }
    }
}

fun openSettingsSafely(context: Context, action: String) {
    try {
        val intent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e2: Exception) {
            android.widget.Toast.makeText(
                context, 
                "לא ניתן לפתוח את ההגדרות באופן אוטומטי", 
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
fun AppListItem(app: AppInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = app.icon,
            contentDescription = app.label,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutDesignerDialog(
    app: AppInfo,
    activity: android.content.pm.ActivityInfo?,
    onDismiss: () -> Unit,
    onShortcutCreated: () -> Unit
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(activity?.loadLabel(context.packageManager)?.toString()?.takeIf { it.isNotEmpty() } ?: app.label) }
    var iconSource by remember { mutableStateOf("original") } // "original", "original_bg", "emoji", "custom_image"
    val isDevMode = remember { NotificationHelper.isDevModeEnabled(context) }
    var showDevEditDialog by remember { mutableStateOf(false) }
    var devEditUri by remember { mutableStateOf("") }
    var pendingBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var selectedEmoji by remember { mutableStateOf("📱") }
    var selectedColor by remember { mutableStateOf(0xFF2196F3.toInt()) }
    var selectedShape by remember { mutableStateOf("circle") } // "circle", "squircle", "square"

    var customImageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customImageUri = uri
            iconSource = "custom_image"
        }
    }

    val colors = listOf(
        0xFF2196F3.toInt(), // Blue
        0xFF009688.toInt(), // Teal
        0xFF4CAF50.toInt(), // Green
        0xFFFF9800.toInt(), // Orange
        0xFFF44336.toInt(), // Red
        0xFFE91E63.toInt(), // Pink
        0xFF9C27B0.toInt(), // Purple
        0xFF3F51B5.toInt(), // Indigo
        0xFF00BCD4.toInt(), // Cyan
        0xFF8BC34A.toInt(), // Lime
        0xFFFFC107.toInt(), // Amber
        0xFF795548.toInt(), // Brown
        0xFF607D8B.toInt(), // Blue Gray
        0xFF000000.toInt()  // Black
    )

    val emojis = listOf(
        "📱", "🏠", "📷", "🎵", "💬", "🎮", "⚙️", "🔔", "✉️", "🗺️", 
        "🛒", "🎬", "🎨", "❤️", "🔥", "💡", "🚀", "⭐", "🔍", "🔒",
        "📂", "📅", "⏰", "☀️", "🌙", "☁️", "🌍", "🐱", "🍕", "⚽"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עיצוב אייקון וקיצור דרך", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Warning for internal activities
                if (activity != null && !activity.exported) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "אזהרה: אקטיביטי פנימי",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "תת-אקטיביטי זה מסומן כפנימי באפליקציית המקור. מערכת ההפעלה עלולה למנוע את הפעלתו ישירות ממסך הבית.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Live Preview
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("תצוגה מקדימה של האייקון:", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    val shape = when (selectedShape) {
                        "circle" -> CircleShape
                        "squircle" -> RoundedCornerShape(24.dp)
                        else -> RoundedCornerShape(8.dp)
                    }

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .let {
                                if (iconSource != "original") {
                                    it.clip(shape)
                                } else {
                                    it
                                }
                            }
                            .background(
                                if (iconSource == "original") Color.Transparent else Color(selectedColor),
                                shape
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = shape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (iconSource) {
                            "original" -> {
                                AsyncImage(
                                    model = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                            "original_bg" -> {
                                AsyncImage(
                                    model = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                            "emoji" -> {
                                Text(text = selectedEmoji, fontSize = 40.sp)
                            }
                            "custom_image" -> {
                                if (customImageUri != null) {
                                    AsyncImage(
                                        model = customImageUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "בחר תמונה",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Label Input
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("שם קיצור הדרך") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Icon Source selector
                Text("מקור האייקון:", style = MaterialTheme.typography.titleSmall)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(listOf(
                        "original" to "מקורי",
                        "original_bg" to "רקע מותאם",
                        "emoji" to "אימוג'י",
                        "custom_image" to "גלריה (תמונה)"
                    )) { (src, name) ->
                        FilterChip(
                            selected = iconSource == src,
                            onClick = {
                                if (src == "custom_image" && customImageUri == null) {
                                    imagePickerLauncher.launch("image/*")
                                } else {
                                    iconSource = src
                                }
                            },
                            label = { Text(name) }
                        )
                    }
                }

                if (iconSource == "custom_image") {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("בחר תמונה אחרת מהגלריה")
                    }
                }

                if (iconSource != "original") {
                    // Shape selector
                    Text("צורת האייקון:", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "circle" to "עיגול",
                            "squircle" to "ריבוע מעוגל",
                            "square" to "ריבוע"
                        ).forEach { (shp, name) ->
                            FilterChip(
                                selected = selectedShape == shp,
                                onClick = { selectedShape = shp },
                                label = { Text(name) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Background Color Selector
                    Text("צבע רקע:", style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(colors) { colorValue ->
                            val isSelected = selectedColor == colorValue
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color(colorValue), CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorValue }
                            )
                        }
                    }
                }

                if (iconSource == "emoji") {
                    // Emoji Selector
                    Text("בחר אימוג'י:", style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(emojis) { emoji ->
                            val isSelected = selectedEmoji == emoji
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer 
                                        else Color.Transparent, 
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isBlank()) {
                        android.widget.Toast.makeText(context, "נא להזין שם לקיצור הדרך", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val bitmap = when (iconSource) {
                        "original_bg" -> {
                            ShortcutHelper.generateAppIconWithBg(
                                app.icon,
                                selectedColor,
                                selectedShape
                            )
                        }
                        "emoji" -> {
                            ShortcutHelper.generateEmojiIcon(
                                context,
                                selectedEmoji,
                                selectedColor,
                                selectedShape
                            )
                        }
                        "custom_image" -> {
                            if (customImageUri != null) {
                                ShortcutHelper.generateCustomImageIcon(
                                    context,
                                    customImageUri!!,
                                    selectedColor,
                                    selectedShape
                                )
                            } else {
                                android.widget.Toast.makeText(context, "נא לבחור תמונה מהגלריה", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }
                        else -> {
                            null
                        }
                    }

                    val originalIntent = Intent(Intent.ACTION_MAIN).apply {
                        if (app.packageName != null) {
                            if (activity?.name != null) {
                                setClassName(app.packageName, activity.name)
                            } else {
                                val pm = context.packageManager
                                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                                if (launchIntent != null) {
                                    component = launchIntent.component
                                } else {
                                    setClassName(app.packageName, "")
                                }
                            }
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    val originalUri = originalIntent.toUri(Intent.URI_INTENT_SCHEME)

                    if (isDevMode) {
                        pendingBitmap = bitmap
                        devEditUri = originalUri
                        showDevEditDialog = true
                    } else {
                        val success = ShortcutHelper.createShortcut(
                            context = context,
                            label = label,
                            packageName = app.packageName,
                            className = activity?.name,
                            customIcon = bitmap
                        )

                        if (success) {
                            android.widget.Toast.makeText(context, "קיצור הדרך נוצר בהצלחה!", android.widget.Toast.LENGTH_SHORT).show()
                            onShortcutCreated()
                        } else {
                            android.widget.Toast.makeText(context, "שגיאה ביצירת קיצור הדרך", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Text("ייצר קיצור דרך")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )

    if (showDevEditDialog) {
        DeveloperIntentEditDialog(
            initialIntentUri = devEditUri,
            onDismiss = { showDevEditDialog = false },
            onConfirm = { editedUri ->
                showDevEditDialog = false
                val isChanged = editedUri != devEditUri
                val finalLabel = if (isChanged) {
                    if (!label.endsWith(" ערוך")) "$label ערוך" else label
                } else {
                    label
                }
                val success = ShortcutHelper.createShortcut(
                    context = context,
                    label = finalLabel,
                    packageName = app.packageName,
                    className = activity?.name,
                    customIcon = pendingBitmap,
                    intentUri = editedUri
                )
                if (success) {
                    android.widget.Toast.makeText(context, "קיצור הדרך נוצר בהצלחה!", android.widget.Toast.LENGTH_SHORT).show()
                    onShortcutCreated()
                } else {
                    android.widget.Toast.makeText(context, "שגיאה ביצירת קיצור הדרך", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

object ShortcutHelper {
    fun createShortcut(
        context: Context,
        label: String,
        packageName: String?,
        className: String?,
        customIcon: Bitmap? = null,
        intentUri: String? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            ?: return false

        if (!shortcutManager.isRequestPinShortcutSupported) {
            return false
        }

        val launchIntent = if (intentUri != null) {
            try {
                Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        } else {
            Intent(Intent.ACTION_MAIN).apply {
                if (packageName != null) {
                    if (className != null) {
                        setClassName(packageName, className)
                    } else {
                        val pm = context.packageManager
                        val intent = pm.getLaunchIntentForPackage(packageName)
                        if (intent != null) {
                            component = intent.component
                        } else {
                            setClassName(packageName, "")
                        }
                    }
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        val id = "shortcut_${packageName ?: "intent"}_${className ?: "main"}_${System.currentTimeMillis()}"

        val icon = if (customIcon != null) {
            android.graphics.drawable.Icon.createWithBitmap(customIcon)
        } else {
            try {
                if (packageName != null) {
                    val appIconDrawable = context.packageManager.getApplicationIcon(packageName)
                    val bitmap = drawableToBitmap(appIconDrawable)
                    android.graphics.drawable.Icon.createWithBitmap(bitmap)
                } else {
                    val ownIcon = context.packageManager.getApplicationIcon(context.packageName)
                    android.graphics.drawable.Icon.createWithBitmap(drawableToBitmap(ownIcon))
                }
            } catch (e: Exception) {
                try {
                    val ownIcon = context.packageManager.getApplicationIcon(context.packageName)
                    android.graphics.drawable.Icon.createWithBitmap(drawableToBitmap(ownIcon))
                } catch (e2: Exception) {
                    val defIcon = context.packageManager.defaultActivityIcon
                    android.graphics.drawable.Icon.createWithBitmap(drawableToBitmap(defIcon))
                }
            }
        }

        val shortcutInfo = ShortcutInfo.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(icon)
            .setIntent(launchIntent)
            .build()

        return try {
            shortcutManager.requestPinShortcut(shortcutInfo, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 192
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 192
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun generateEmojiIcon(
        context: Context,
        emoji: String,
        backgroundColor: Int,
        shape: String
    ): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }

        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        when (shape) {
            "circle" -> {
                canvas.drawOval(rect, paint)
            }
            "squircle" -> {
                val radius = size * 0.35f
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            else -> { // square
                val radius = size * 0.15f
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.55f
            textAlign = Paint.Align.CENTER
        }
        
        val x = size / 2f
        val y = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(emoji, x, y, textPaint)

        return bitmap
    }

    fun generateAppIconWithBg(
        originalIcon: Drawable,
        backgroundColor: Int,
        shape: String
    ): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        when (shape) {
            "circle" -> canvas.drawOval(rect, paint)
            "squircle" -> {
                val radius = size * 0.35f
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            else -> {
                val radius = size * 0.15f
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }

        val margin = (size * 0.15f).toInt()
        originalIcon.setBounds(margin, margin, size - margin, size - margin)
        originalIcon.draw(canvas)

        return bitmap
    }

    fun generateCustomImageIcon(
        context: Context,
        uri: Uri,
        backgroundColor: Int,
        shape: String
    ): Bitmap? {
        val srcBitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } ?: return null

        val size = 192
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        // 1. Draw Background shape
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
        }
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        when (shape) {
            "circle" -> canvas.drawOval(rect, paint)
            "squircle" -> {
                val radius = size * 0.35f
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
            else -> {
                val radius = size * 0.15f
                canvas.drawRoundRect(rect, radius, radius, paint)
            }
        }

        // 2. Draw scaled and cropped source bitmap onto the canvas
        val maskBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val maskCanvas = Canvas(maskBitmap)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        when (shape) {
            "circle" -> maskCanvas.drawOval(rect, maskPaint)
            "squircle" -> {
                val radius = size * 0.35f
                maskCanvas.drawRoundRect(rect, radius, radius, maskPaint)
            }
            else -> {
                val radius = size * 0.15f
                maskCanvas.drawRoundRect(rect, radius, radius, maskPaint)
            }
        }

        val srcWidth = srcBitmap.width
        val srcHeight = srcBitmap.height
        val srcAspect = srcWidth.toFloat() / srcHeight.toFloat()
        
        val srcRect = if (srcAspect > 1.0f) {
            val newWidth = srcHeight
            val offset = (srcWidth - newWidth) / 2
            Rect(offset, 0, offset + newWidth, srcHeight)
        } else {
            val newHeight = srcWidth
            val offset = (srcHeight - newHeight) / 2
            Rect(0, offset, srcWidth, offset + newHeight)
        }

        val maskedBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val maskedCanvas = Canvas(maskedBitmap)
        val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        
        maskedCanvas.drawBitmap(srcBitmap, srcRect, Rect(0, 0, size, size), drawPaint)
        
        drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        maskedCanvas.drawBitmap(maskBitmap, 0f, 0f, drawPaint)
        
        canvas.drawBitmap(maskedBitmap, 0f, 0f, null)
        
        try {
            maskBitmap.recycle()
            maskedBitmap.recycle()
            srcBitmap.recycle()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return output
    }
}

@Composable
fun ShortcutDesignerDialogForIntent(
    intentUri: String,
    intentLabel: String,
    onDismiss: () -> Unit,
    onShortcutCreated: () -> Unit
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf(intentLabel) }
    val isDevMode = remember { NotificationHelper.isDevModeEnabled(context) }
    var showDevEditDialog by remember { mutableStateOf(false) }
    var devEditUri by remember { mutableStateOf("") }
    var pendingBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var selectedColor by remember { mutableStateOf(0xFF1E88E5.toInt()) }
    var selectedShape by remember { mutableStateOf("circle") } // circle, squircle, square
    var selectedEmoji by remember { mutableStateOf("⚡") }
    var iconSource by remember { mutableStateOf("emoji") } // "emoji", "custom_image"
    
    var customImageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customImageUri = uri
            iconSource = "custom_image"
        }
    }
    
    val colors = listOf(
        0xFF1E88E5.toInt(), // Blue
        0xFF43A047.toInt(), // Green
        0xFFE53935.toInt(), // Red
        0xFFFDD835.toInt(), // Yellow
        0xFF8E24AA.toInt(), // Purple
        0xFFFB8C00.toInt(), // Orange
        0xFF00ACC1.toInt(), // Cyan
        0xFF5D4037.toInt()  // Brown
    )

    val emojis = listOf("⚡", "📞", "✉️", "🌐", "⏰", "🗺️", "⚙️", "📱", "🏠", "🌟", "🔥", "🚀", "❤️", "🔔", "🎮", "🎵")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עיצוב אייקון לקיצור הדרך") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Label TextField
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("שם קיצור הדרך") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Visual Preview Card
                Text("תצוגה מקדימה:", style = MaterialTheme.typography.titleSmall)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val shape = when (selectedShape) {
                        "circle" -> CircleShape
                        "squircle" -> RoundedCornerShape(24.dp)
                        else -> RoundedCornerShape(12.dp)
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(shape)
                            .background(
                                color = Color(selectedColor),
                                shape = shape
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = shape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (iconSource) {
                            "emoji" -> {
                                Text(text = selectedEmoji, fontSize = 36.sp)
                            }
                            "custom_image" -> {
                                if (customImageUri != null) {
                                    AsyncImage(
                                        model = customImageUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "בחר תמונה",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Background Shape
                Text("בחר צורה:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        "circle" to "עיגול",
                        "squircle" to "ריבוע מעוגל",
                        "square" to "ריבוע"
                    ).forEach { (shape, name) ->
                        val isSelected = selectedShape == shape
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedShape = shape },
                            label = { Text(name) }
                        )
                    }
                }

                // Background Color Selector
                Text("בחר צבע רקע:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colors.forEach { colorValue ->
                        val isSelected = selectedColor == colorValue
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(colorValue), CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorValue }
                        )
                    }
                }

                // Icon Source selector
                Text("מקור האייקון:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "emoji" to "אימוג'י",
                        "custom_image" to "גלריה (תמונה)"
                    ).forEach { (src, name) ->
                        FilterChip(
                            selected = iconSource == src,
                            onClick = {
                                if (src == "custom_image" && customImageUri == null) {
                                    imagePickerLauncher.launch("image/*")
                                } else {
                                    iconSource = src
                                }
                            },
                            label = { Text(name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (iconSource == "custom_image") {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("בחר תמונה אחרת מהגלריה")
                    }
                }

                if (iconSource == "emoji") {
                    // Emoji Selector
                    Text("בחר אימוג'י:", style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(emojis) { emoji ->
                            val isSelected = selectedEmoji == emoji
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer 
                                        else Color.Transparent, 
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isBlank()) {
                        android.widget.Toast.makeText(context, "נא להזין שם לקיצור הדרך", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val bitmap = when (iconSource) {
                        "emoji" -> {
                            ShortcutHelper.generateEmojiIcon(
                                context,
                                selectedEmoji,
                                selectedColor,
                                selectedShape
                            )
                        }
                        "custom_image" -> {
                            if (customImageUri != null) {
                                ShortcutHelper.generateCustomImageIcon(
                                    context,
                                    customImageUri!!,
                                    selectedColor,
                                    selectedShape
                                )
                            } else {
                                android.widget.Toast.makeText(context, "נא לבחור תמונה מהגלריה", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }
                        else -> null
                    }

                    if (isDevMode) {
                        pendingBitmap = bitmap
                        devEditUri = intentUri
                        showDevEditDialog = true
                    } else {
                        val success = ShortcutHelper.createShortcut(
                            context = context,
                            label = label,
                            packageName = null,
                            className = null,
                            customIcon = bitmap,
                            intentUri = intentUri
                        )

                        if (success) {
                            android.widget.Toast.makeText(context, "קיצור הדרך נוצר בהצלחה!", android.widget.Toast.LENGTH_SHORT).show()
                            onShortcutCreated()
                        } else {
                            android.widget.Toast.makeText(context, "שגיאה ביצירת קיצור הדרך", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Text("ייצר קיצור דרך")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )

    if (showDevEditDialog) {
        DeveloperIntentEditDialog(
            initialIntentUri = devEditUri,
            onDismiss = { showDevEditDialog = false },
            onConfirm = { editedUri ->
                showDevEditDialog = false
                val isChanged = editedUri != devEditUri
                val finalLabel = if (isChanged) {
                    if (!label.endsWith(" ערוך")) "$label ערוך" else label
                } else {
                    label
                }
                val success = ShortcutHelper.createShortcut(
                    context = context,
                    label = finalLabel,
                    packageName = null,
                    className = null,
                    customIcon = pendingBitmap,
                    intentUri = editedUri
                )
                if (success) {
                    android.widget.Toast.makeText(context, "קיצור הדרך נוצר בהצלחה!", android.widget.Toast.LENGTH_SHORT).show()
                    onShortcutCreated()
                } else {
                    android.widget.Toast.makeText(context, "שגיאה ביצירת קיצור הדרך", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun SequenceActionPickerDialog(
    apps: List<AppInfo>,
    savedIntentsList: List<Intent>,
    onDismiss: () -> Unit,
    onActionSelected: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Apps, 1: Custom Intents
    
    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps else {
            apps.filter { it.label.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
        }
    }
    
    val filteredIntents = remember(searchQuery, savedIntentsList) {
        if (searchQuery.isBlank()) savedIntentsList else {
            savedIntentsList.filter { 
                val label = it.getStringExtra("PINAPP_INTENT_LABEL") ?: "בקשת מערכת"
                label.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "בחירת פעולה להוספה לסבב",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Right
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("חיפוש אפליקציה..." ) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )
                
                // Tabs
                TabRow(selectedTabIndex = selectedTab, modifier = Modifier.padding(bottom = 8.dp)) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("אפליקציות", modifier = Modifier.padding(vertical = 10.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("פקודות מערכת", modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
                
                // Content
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onActionSelected(app.packageName)
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = app.icon,
                                        contentDescription = app.label,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    } else {
                        if (filteredIntents.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("אין פקודות מערכת שמורות", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredIntents) { savedIntent ->
                                    val intentUri = savedIntent.toUri(Intent.URI_INTENT_SCHEME)
                                    val label = savedIntent.getStringExtra("PINAPP_INTENT_LABEL") ?: "בקשת מערכת"
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onActionSelected(intentUri)
                                            }
                                            .padding(vertical = 12.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Send,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text(intentUri, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
fun ShortcutDesignerDialogForSequence(
    sequenceId: String,
    apps: List<AppInfo>,
    savedIntentsList: List<Intent>,
    refreshKey: Int,
    onAddActionRequested: () -> Unit,
    onDismiss: () -> Unit,
    onShortcutCreated: () -> Unit
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("סבב פעולות") }
    val isDevMode = remember { NotificationHelper.isDevModeEnabled(context) }
    var showDevEditDialog by remember { mutableStateOf(false) }
    var devEditUri by remember { mutableStateOf("") }
    var pendingBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var selectedColor by remember { mutableStateOf(0xFFE91E63.toInt()) } // Pink default for sequences
    var selectedShape by remember { mutableStateOf("circle") } // circle, squircle, square
    var selectedEmoji by remember { mutableStateOf("🔄") }
    var iconSource by remember { mutableStateOf("emoji") } // "emoji", "custom_image"
    
    var customImageUri by remember { mutableStateOf<Uri?>(null) }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customImageUri = uri
            iconSource = "custom_image"
        }
    }
    
    var sequenceList by remember(refreshKey) { mutableStateOf(NotificationHelper.getSequenceForId(context, sequenceId)) }
    var showActionPicker by remember { mutableStateOf(false) }
    
    val colors = listOf(
        0xFFE91E63.toInt(), // Pink
        0xFF9C27B0.toInt(), // Purple
        0xFF673AB7.toInt(), // Deep Purple
        0xFF3F51B5.toInt(), // Indigo
        0xFF2196F3.toInt(), // Blue
        0xFF00BCD4.toInt(), // Cyan
        0xFF009688.toInt(), // Teal
        0xFF4CAF50.toInt(), // Green
        0xFFFF9800.toInt(), // Orange
        0xFFF44336.toInt()  // Red
    )

    val emojis = listOf("🔄", "🔁", "⚡", "📱", "🚀", "🔥", "🌟", "⚙️", "🔔", "✉️", "🏠", "🎮", "🎵", "❤️")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עיצוב אייקון לסבב פעולות") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Label TextField
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("שם קיצור הדרך במסך הבית") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Visual Preview Card
                Text("תצוגה מקדימה של האייקון:", style = MaterialTheme.typography.titleSmall)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val shape = when (selectedShape) {
                        "circle" -> CircleShape
                        "squircle" -> RoundedCornerShape(24.dp)
                        else -> RoundedCornerShape(12.dp)
                    }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(shape)
                            .background(
                                color = Color(selectedColor),
                                shape = shape
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                shape = shape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (iconSource) {
                            "emoji" -> {
                                Text(text = selectedEmoji, fontSize = 36.sp)
                            }
                            "custom_image" -> {
                                if (customImageUri != null) {
                                    AsyncImage(
                                        model = customImageUri,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "בחר תמונה",
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Background Shape
                Text("בחר צורה:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf(
                        "circle" to "עיגול",
                        "squircle" to "ריבוע מעוגל",
                        "square" to "ריבוע"
                    ).forEach { (shape, name) ->
                        val isSelected = selectedShape == shape
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedShape = shape },
                            label = { Text(name) }
                        )
                    }
                }

                // Background Color Selector
                Text("בחר צבע רקע:", style = MaterialTheme.typography.titleSmall)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(colors) { colorValue ->
                        val isSelected = selectedColor == colorValue
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(colorValue), CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = colorValue }
                        )
                    }
                }

                // Icon Source selector
                Text("מקור האייקון:", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "emoji" to "אימוג'י",
                        "custom_image" to "גלריה"
                    ).forEach { (src, name) ->
                        FilterChip(
                            selected = iconSource == src,
                            onClick = {
                                if (src == "custom_image" && customImageUri == null) {
                                    imagePickerLauncher.launch("image/*")
                                } else {
                                    iconSource = src
                                }
                            },
                            label = { Text(name) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (iconSource == "custom_image") {
                    Button(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("בחר תמונה אחרת מהגלריה")
                    }
                }

                if (iconSource == "emoji") {
                    // Emoji Selector
                    Text("בחר אימוג'י:", style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(emojis) { emoji ->
                            val isSelected = selectedEmoji == emoji
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer 
                                        else Color.Transparent, 
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // --- ACTIONS LIST SECTION ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "פעולות בסבב זה:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Button(
                        onClick = onAddActionRequested,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("הוסף פעולה", style = MaterialTheme.typography.labelMedium)
                    }
                }
                
                Button(
                    onClick = {
                        NotificationHelper.addSequenceTargetForId(context, sequenceId, "action:step_separator")
                        sequenceList = NotificationHelper.getSequenceForId(context, sequenceId)
                        Toast.makeText(context, "מפריד שלבים נוסף לסבב", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("הוסף מפריד שלבים (לקבוצת פעולות)")
                }

                var isAllAtOnceForId by remember(refreshKey) { mutableStateOf(NotificationHelper.isSequenceAllAtOnceForId(context, sequenceId)) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAllAtOnceForId,
                        onCheckedChange = { checked ->
                            NotificationHelper.setSequenceAllAtOnceForId(context, sequenceId, checked)
                            isAllAtOnceForId = checked
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.clickable {
                        val newChecked = !isAllAtOnceForId
                        NotificationHelper.setSequenceAllAtOnceForId(context, sequenceId, newChecked)
                        isAllAtOnceForId = newChecked
                    }) {
                        Text(
                            text = "הפעל את כל הפעולות בבת אחת",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (sequenceList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "הסבב ריק. הוסף אפליקציות או פקודות מערכת.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sequenceList.forEachIndexed { index, target ->
                            val actionLabel = resolveTargetLabel(context, target)
                            val actionIcon = resolveTargetIcon(context, target)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                    .padding(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Step Indicator
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (index + 1).toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))

                                if (actionIcon != null) {
                                    AsyncImage(
                                        model = actionIcon,
                                        contentDescription = actionLabel,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = actionLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Reordering and Delete Buttons
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            NotificationHelper.moveSequenceTargetForId(context, sequenceId, index, true)
                                            sequenceList = NotificationHelper.getSequenceForId(context, sequenceId)
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "למעלה",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            NotificationHelper.moveSequenceTargetForId(context, sequenceId, index, false)
                                            sequenceList = NotificationHelper.getSequenceForId(context, sequenceId)
                                        },
                                        enabled = index < sequenceList.size - 1,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "למטה",
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            NotificationHelper.removeSequenceTargetAtForId(context, sequenceId, index)
                                            sequenceList = NotificationHelper.getSequenceForId(context, sequenceId)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "מחק",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isBlank()) {
                        android.widget.Toast.makeText(context, "נא להזין שם לקיצור הדרך", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (sequenceList.isEmpty()) {
                        android.widget.Toast.makeText(context, "נא להוסיף לפחות פעולה אחת לסבב", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val bitmap = when (iconSource) {
                        "emoji" -> {
                            ShortcutHelper.generateEmojiIcon(
                                context,
                                selectedEmoji,
                                selectedColor,
                                selectedShape
                            )
                        }
                        "custom_image" -> {
                            if (customImageUri != null) {
                                ShortcutHelper.generateCustomImageIcon(
                                    context,
                                    customImageUri!!,
                                    selectedColor,
                                    selectedShape
                                )
                            } else {
                                android.widget.Toast.makeText(context, "נא לבחור תמונה מהגלריה", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                        }
                        else -> null
                    }

                    val intent = Intent().apply {
                        setClassName(context.packageName, "com.example.ActionActivity")
                        action = "com.example.action.RUN_SEQUENCE"
                        data = android.net.Uri.parse("sequence://run?id=$sequenceId")
                        putExtra("sequence_id", sequenceId)
                    }
                    val intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)

                    if (isDevMode) {
                        pendingBitmap = bitmap
                        devEditUri = intentUri
                        showDevEditDialog = true
                    } else {
                        val success = ShortcutHelper.createShortcut(
                            context = context,
                            label = label,
                            packageName = null,
                            className = null,
                            customIcon = bitmap,
                            intentUri = intentUri
                        )

                        if (success) {
                            android.widget.Toast.makeText(context, "קיצור הדרך לסבב הפעולות נוצר בהצלחה!", android.widget.Toast.LENGTH_SHORT).show()
                            onShortcutCreated()
                        } else {
                            android.widget.Toast.makeText(context, "שגיאה ביצירת קיצור הדרך", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            ) {
                Text("ייצר קיצור דרך")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )

    if (showDevEditDialog) {
        DeveloperIntentEditDialog(
            initialIntentUri = devEditUri,
            onDismiss = { showDevEditDialog = false },
            onConfirm = { editedUri ->
                showDevEditDialog = false
                val isChanged = editedUri != devEditUri
                val finalLabel = if (isChanged) {
                    if (!label.endsWith(" ערוך")) "$label ערוך" else label
                } else {
                    label
                }
                val success = ShortcutHelper.createShortcut(
                    context = context,
                    label = finalLabel,
                    packageName = context.packageName,
                    className = null,
                    customIcon = pendingBitmap,
                    intentUri = editedUri
                )
                if (success) {
                    android.widget.Toast.makeText(context, "קיצור הדרך לסבב הפעולות נוצר בהצלחה!", android.widget.Toast.LENGTH_SHORT).show()
                    onShortcutCreated()
                } else {
                    android.widget.Toast.makeText(context, "שגיאה ביצירת קיצור הדרך", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun IntentCreatorDialog(
    intentToEdit: Intent? = null,
    editingUri: String? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    
    val initialLabel = remember(intentToEdit) { intentToEdit?.getStringExtra("PINAPP_INTENT_LABEL") ?: "" }
    val initialPreset = remember(intentToEdit) {
        if (intentToEdit != null) {
            val action = intentToEdit.action ?: ""
            val dataStr = intentToEdit.dataString ?: ""
            when {
                action == Intent.ACTION_CALL -> "call"
                action == Intent.ACTION_DIAL -> "dial"
                action == Intent.ACTION_SENDTO && dataStr.startsWith("smsto:") -> "sms"
                action == "com.example.action.SEND_SMS_DIRECT" -> "sms_direct"
                action == "com.example.action.SWITCH_SPEAKER_ON" -> "speaker_on"
                action == "com.example.action.SWITCH_SPEAKER_OFF" -> "speaker_off"
                action == "com.example.action.MUTE_ON" -> "mute_on"
                action == "com.example.action.MUTE_OFF" -> "mute_off"
                action == "com.example.action.CALL_MUTE_ON" -> "call_mute_on"
                action == "com.example.action.CALL_MUTE_OFF" -> "call_mute_off"
                action == "com.example.action.ANSWER_CALL" -> "answer_call"
                action == "com.example.action.DTMF_DIAL" -> "dtmf_dial"
                action == "com.example.action.HOLD_CALL" -> "hold_call"
                action == "com.example.action.RESUME_CALL" -> "resume_call"
                action == "com.example.action.END_CALL" -> "end_call"
                action == android.provider.AlarmClock.ACTION_SET_ALARM -> "alarm"
                action == Intent.ACTION_VIEW && dataStr.startsWith("google.navigation:q=") -> "map"
                action == android.provider.Settings.ACTION_SETTINGS -> "settings"
                action == "com.example.action.TOGGLE_WIFI" -> "wifi_toggle"
                action == "com.example.action.WIFI_ON" -> "wifi_on"
                action == "com.example.action.WIFI_OFF" -> "wifi_off"
                action == "com.example.action.TOGGLE_BLUETOOTH" -> "bt_toggle"
                action == "com.example.action.BLUETOOTH_ON" -> "bt_on"
                action == "com.example.action.BLUETOOTH_OFF" -> "bt_off"
                action == "com.example.action.SET_BRIGHTNESS_MAX" -> "bright_max"
                action == "com.example.action.SET_BRIGHTNESS_MIN" -> "bright_min"
                action == "com.example.action.SET_BRIGHTNESS_HALF" -> "bright_half"
                action == "com.example.action.SET_TIMEOUT_30S" -> "timeout_30s"
                action == "com.example.action.SET_TIMEOUT_5M" -> "timeout_5m"
                action == "com.example.action.TOGGLE_ROTATION" -> "rotate_toggle"
                else -> "custom"
            }
        } else {
            "call"
        }
    }

    val initialPhone = remember(intentToEdit) {
        if (intentToEdit != null) {
            val action = intentToEdit.action ?: ""
            val dataStr = intentToEdit.dataString ?: ""
            when {
                action == Intent.ACTION_CALL || action == Intent.ACTION_DIAL -> {
                    intentToEdit.data?.schemeSpecificPart ?: ""
                }
                action == Intent.ACTION_SENDTO && dataStr.startsWith("smsto:") -> {
                    intentToEdit.data?.schemeSpecificPart ?: ""
                }
                action == "com.example.action.SEND_SMS_DIRECT" -> {
                    intentToEdit.getStringExtra("phone_number") ?: ""
                }
                else -> ""
            }
        } else {
            ""
        }
    }

    val initialSmsBody = remember(intentToEdit) {
        intentToEdit?.getStringExtra("sms_body") ?: intentToEdit?.getStringExtra("message_body") ?: ""
    }
    
    val initialWebUrl = remember(intentToEdit) {
        if (intentToEdit != null && (intentToEdit.action == Intent.ACTION_VIEW) && intentToEdit.dataString?.startsWith("http") == true) {
            intentToEdit.dataString ?: ""
        } else ""
    }
    
    val initialDtmf = remember(intentToEdit) {
        intentToEdit?.getStringExtra("dtmf_tones") ?: ""
    }
    
    val initialAlarmHour = remember(intentToEdit) {
        intentToEdit?.getIntExtra(android.provider.AlarmClock.EXTRA_HOUR, 8)?.toString() ?: "8"
    }
    
    val initialAlarmMinute = remember(intentToEdit) {
        intentToEdit?.getIntExtra(android.provider.AlarmClock.EXTRA_MINUTES, 0)?.toString() ?: "0"
    }
    
    val initialMap = remember(intentToEdit) {
        if (intentToEdit != null && intentToEdit.dataString?.startsWith("google.navigation:q=") == true) {
            android.net.Uri.decode(intentToEdit.dataString!!.substringAfter("google.navigation:q="))
        } else ""
    }
    
    val initialCustomAction = remember(intentToEdit) {
        intentToEdit?.action ?: "android.intent.action.VIEW"
    }
    
    val initialCustomData = remember(intentToEdit) {
        intentToEdit?.dataString ?: ""
    }
    
    val initialCustomPackage = remember(intentToEdit) {
        intentToEdit?.`package` ?: ""
    }
    
    val initialCustomClass = remember(intentToEdit) {
        intentToEdit?.component?.className ?: ""
    }
    
    val initialRawUri = remember(editingUri) {
        editingUri ?: ""
    }

    var label by remember { mutableStateOf(initialLabel) }
    var presetType by remember { mutableStateOf(initialPreset) }
    
    var phoneNumber by remember { mutableStateOf(initialPhone) }
    var smsBody by remember { mutableStateOf(initialSmsBody) }
    var webUrl by remember { mutableStateOf(initialWebUrl) }
    var dtmfTones by remember { mutableStateOf(initialDtmf) }
    var alarmHour by remember { mutableStateOf(initialAlarmHour) }
    var alarmMinute by remember { mutableStateOf(initialAlarmMinute) }
    var mapQuery by remember { mutableStateOf(initialMap) }
    
    var customAction by remember { mutableStateOf(initialCustomAction) }
    var customData by remember { mutableStateOf(initialCustomData) }
    var customPackage by remember { mutableStateOf(initialCustomPackage) }
    var customClass by remember { mutableStateOf(initialCustomClass) }
    
    var rawUriString by remember { mutableStateOf(initialRawUri) }
    var literalActionString by remember { mutableStateOf(intentToEdit?.action ?: "") }
    
    var isDeveloperModeEdit by remember { mutableStateOf(editingUri != null) }
    
    var callPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        callPermissionGranted = isGranted
        if (isGranted) {
            android.widget.Toast.makeText(context, "הרשאת שיחות טלפון אושרה!", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "הרשאת שיחות טלפון נדחתה. לא ניתן יהיה לבצע שיחות ישירות.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    var smsPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        smsPermissionGranted = isGranted
        if (isGranted) {
            android.widget.Toast.makeText(context, "הרשאת שליחת SMS אושרה!", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "הרשאת שליחת SMS נדחתה. לא ניתן יהיה לשלוח SMS ישירות.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    var answerCallsPermissionGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val answerCallsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        answerCallsPermissionGranted = isGranted
        if (isGranted) {
            android.widget.Toast.makeText(context, "הרשאת מענה לשיחות אושרה!", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "הרשאת מענה לשיחות נדחתה. לא ניתן יהיה לענות לשיחות ישירות.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    val presets = listOf(
        Triple("call", "שיחה ישירה", Icons.Default.Phone),
        Triple("dial", "חיוג למספר", Icons.Default.Phone),
        Triple("sms", "שליחת SMS", Icons.Default.Send),
        Triple("sms_direct", "שליחת SMS ישיר (רקע)", Icons.Default.Send),
        Triple("web", "אתר אינטרנט", Icons.Default.Search),
        Triple("alarm", "שעון מעורר", Icons.Default.Star),
        Triple("map", "ניווט ומפות", Icons.Default.Home),
        Triple("settings", "הגדרות מכשיר", Icons.Default.Settings),
        Triple("wifi_toggle", "שנה מצב Wi-Fi", Icons.Default.Settings),
        Triple("wifi_on", "הדלק Wi-Fi", Icons.Default.Settings),
        Triple("wifi_off", "כבה Wi-Fi", Icons.Default.Settings),
        Triple("bt_toggle", "שנה מצב Bluetooth", Icons.Default.Settings),
        Triple("bt_on", "הדלק Bluetooth", Icons.Default.Settings),
        Triple("bt_off", "כבה Bluetooth", Icons.Default.Settings),
        Triple("bright_max", "בהירות למקסימום (100%)", Icons.Default.Settings),
        Triple("bright_min", "בהירות למינימום (10%)", Icons.Default.Settings),
        Triple("bright_half", "בהירות לבינוני (50%)", Icons.Default.Settings),
        Triple("timeout_30s", "זמן כיבוי מסך ל-30 שניות", Icons.Default.Settings),
        Triple("timeout_5m", "זמן כיבוי מסך ל-5 דקות", Icons.Default.Settings),
        Triple("rotate_toggle", "שנה מצב סיבוב מסך אוטומטי", Icons.Default.Settings),
        Triple("speaker_on", "הפעל רמקול בשיחה פעילה", Icons.Default.PlayArrow),
        Triple("speaker_off", "כבה רמקול בשיחה פעילה", Icons.Default.PlayArrow),
        Triple("mute_on", "הפעל השתקה (מצב שקט)", Icons.Default.PlayArrow),
        Triple("mute_off", "כבה השתקה (מצב רגיל)", Icons.Default.PlayArrow),
        Triple("call_mute_on", "השתקת מיקרופון בשיחה (Mute)", Icons.Default.PlayArrow),
        Triple("call_mute_off", "ביטול השתקת מיקרופון בשיחה (Unmute)", Icons.Default.PlayArrow),
        Triple("answer_call", "מענה לשיחה נכנסת", Icons.Default.Phone),
        Triple("dtmf_dial", "חיוג צלילי DTMF", Icons.Default.Phone),
        Triple("hold_call", "העבר למצב ממתינה (Hold)", Icons.Default.Phone),
        Triple("resume_call", "שחרר ממצב ממתינה (Resume)", Icons.Default.Phone),
        Triple("end_call", "ניתוק שיחה פעילה", Icons.Default.Phone),
        Triple("custom", "מותאם אישית", Icons.Default.Settings),
        Triple("raw", "כתיבה חופשית", Icons.Default.Edit),
        Triple("literal_action", "פקודה ישירה (עצמאית)", Icons.Default.PlayArrow)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingUri != null) "עריכת בקשת מערכת (Intent)" else "צור בקשת מערכת (Intent) חדשה") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Label TextField
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("שם הבקשה (למשל: התקשר לאמא)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("עריכה מלאה (מצב מפתחים)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Switch(
                        checked = isDeveloperModeEdit,
                        onCheckedChange = { checked ->
                            if (checked) {
                                // Dynamically generate current preset's intent and convert it to URI scheme for raw editing!
                                try {
                                    val tempIntent = Intent(customAction)
                                    when (presetType) {
                                        "call" -> {
                                            tempIntent.action = Intent.ACTION_CALL
                                            tempIntent.data = android.net.Uri.parse("tel:${phoneNumber.trim()}")
                                        }
                                        "dial" -> {
                                            tempIntent.action = Intent.ACTION_DIAL
                                            tempIntent.data = android.net.Uri.parse("tel:${phoneNumber.trim()}")
                                        }
                                        "sms" -> {
                                            tempIntent.action = Intent.ACTION_SENDTO
                                            tempIntent.data = android.net.Uri.parse("smsto:${phoneNumber.trim()}")
                                            if (smsBody.isNotEmpty()) {
                                                tempIntent.putExtra("sms_body", smsBody)
                                            }
                                        }
                                        "sms_direct" -> {
                                            tempIntent.action = "com.example.action.SEND_SMS_DIRECT"
                                            tempIntent.putExtra("phone_number", phoneNumber.trim())
                                            tempIntent.putExtra("sms_body", smsBody)
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "web" -> {
                                            tempIntent.action = Intent.ACTION_VIEW
                                            tempIntent.data = android.net.Uri.parse(if (webUrl.startsWith("http")) webUrl else "https://$webUrl")
                                        }
                                        "alarm" -> {
                                            tempIntent.action = android.provider.AlarmClock.ACTION_SET_ALARM
                                            tempIntent.putExtra(android.provider.AlarmClock.EXTRA_HOUR, alarmHour.toIntOrNull() ?: 8)
                                            tempIntent.putExtra(android.provider.AlarmClock.EXTRA_MINUTES, alarmMinute.toIntOrNull() ?: 0)
                                            tempIntent.putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                                        }
                                        "map" -> {
                                            tempIntent.action = Intent.ACTION_VIEW
                                            tempIntent.data = android.net.Uri.parse("google.navigation:q=${android.net.Uri.encode(mapQuery)}")
                                        }
                                        "settings" -> {
                                            tempIntent.action = android.provider.Settings.ACTION_SETTINGS
                                        }
                                        "wifi_toggle" -> {
                                            tempIntent.action = "com.example.action.TOGGLE_WIFI"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "wifi_on" -> {
                                            tempIntent.action = "com.example.action.WIFI_ON"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "wifi_off" -> {
                                            tempIntent.action = "com.example.action.WIFI_OFF"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "bt_toggle" -> {
                                            tempIntent.action = "com.example.action.TOGGLE_BLUETOOTH"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "bt_on" -> {
                                            tempIntent.action = "com.example.action.BLUETOOTH_ON"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "bt_off" -> {
                                            tempIntent.action = "com.example.action.BLUETOOTH_OFF"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "bright_max" -> {
                                            tempIntent.action = "com.example.action.SET_BRIGHTNESS_MAX"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "bright_min" -> {
                                            tempIntent.action = "com.example.action.SET_BRIGHTNESS_MIN"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "bright_half" -> {
                                            tempIntent.action = "com.example.action.SET_BRIGHTNESS_HALF"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "timeout_30s" -> {
                                            tempIntent.action = "com.example.action.SET_TIMEOUT_30S"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "timeout_5m" -> {
                                            tempIntent.action = "com.example.action.SET_TIMEOUT_5M"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "rotate_toggle" -> {
                                            tempIntent.action = "com.example.action.TOGGLE_ROTATION"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "speaker_on" -> {
                                            tempIntent.action = "com.example.action.SWITCH_SPEAKER_ON"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "speaker_off" -> {
                                            tempIntent.action = "com.example.action.SWITCH_SPEAKER_OFF"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "mute_on" -> {
                                            tempIntent.action = "com.example.action.MUTE_ON"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "mute_off" -> {
                                            tempIntent.action = "com.example.action.MUTE_OFF"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "call_mute_on" -> {
                                            tempIntent.action = "com.example.action.CALL_MUTE_ON"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "call_mute_off" -> {
                                            tempIntent.action = "com.example.action.CALL_MUTE_OFF"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "answer_call" -> {
                                            tempIntent.action = "com.example.action.ANSWER_CALL"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "dtmf_dial" -> {
                                            tempIntent.action = "com.example.action.DTMF_DIAL"
                                            tempIntent.putExtra("dtmf_tones", dtmfTones.trim())
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "hold_call" -> {
                                            tempIntent.action = "com.example.action.HOLD_CALL"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "resume_call" -> {
                                            tempIntent.action = "com.example.action.RESUME_CALL"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "end_call" -> {
                                            tempIntent.action = "com.example.action.END_CALL"
                                            tempIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                        }
                                        "custom" -> {
                                            tempIntent.action = customAction
                                            if (customData.isNotEmpty()) {
                                                tempIntent.data = android.net.Uri.parse(customData)
                                            }
                                            if (customPackage.isNotEmpty()) {
                                                if (customClass.isNotEmpty()) {
                                                    tempIntent.setClassName(customPackage, customClass)
                                                } else {
                                                    tempIntent.setPackage(customPackage)
                                                }
                                            }
                                        }
                                        "literal_action" -> {
                                            tempIntent.action = literalActionString.trim()
                                        }
                                        "raw" -> {
                                            tempIntent.action = Intent.ACTION_VIEW
                                        }
                                    }
                                    tempIntent.putExtra("PINAPP_INTENT_LABEL", label)
                                    rawUriString = tempIntent.toUri(Intent.URI_INTENT_SCHEME)
                                    presetType = "raw"
                                } catch (e: Exception) {
                                    rawUriString = ""
                                }
                            }
                            isDeveloperModeEdit = checked
                        }
                    )
                }

                Text("סוג הפעולה:", style = MaterialTheme.typography.titleSmall)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { preset ->
                        val isSelected = presetType == preset.first
                        FilterChip(
                            selected = isSelected,
                            onClick = { 
                                presetType = preset.first 
                                if (label.isBlank()) {
                                    label = preset.second
                                }
                            },
                            label = { Text(preset.second) },
                            leadingIcon = { Icon(preset.third, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
  
                when (presetType) {
                    "call", "dial" -> {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("מספר טלפון") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        if (presetType == "call") {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!callPermissionGranted) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("שימוש בשיחה ישירה דורש הרשאה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CALL_PHONE) }) {
                                            Text("הענק הרשאה", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("הרשאת שיחות טלפון מאושרת!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                    "sms" -> {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("מספר נמען (אופציונלי)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = smsBody,
                            onValueChange = { smsBody = it },
                            label = { Text("תוכן ההודעה") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    "sms_direct" -> {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text("מספר נמען") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = smsBody,
                            onValueChange = { smsBody = it },
                            label = { Text("תוכן ההודעה") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!smsPermissionGranted) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("שליחת SMS ישיר דורשת הרשאה מתאימה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                    TextButton(onClick = { smsPermissionLauncher.launch(Manifest.permission.SEND_SMS) }) {
                                        Text("הענק הרשאה ל-SMS", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("הרשאת שליחת SMS מאושרת!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    "speaker_on", "speaker_off", "mute_on", "mute_off", "call_mute_on", "call_mute_off" -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (presetType) {
                                    "speaker_on" -> "פקודה זו תשנה את ניתוב השמע לרמקול במהלך שיחה פעילה."
                                    "speaker_off" -> "פקודה זו תכבה את הרמקול ותחזיר את השמע לאפרכסת במהלך שיחה פעילה."
                                    "mute_on" -> "פקודה זו תפעיל את מצב ההשתקה (מצב שקט) במכשיר. שים לב כי ייתכן ויהיה צורך לאשר הרשאת 'נא לא להפריע' בהפעלה הראשונה."
                                    "mute_off" -> "פקודה זו תבטל את מצב ההשתקה ותחזיר את המכשיר למצב קול רגיל."
                                    "call_mute_on" -> "פקודה זו תשתיק את המיקרופון במהלך שיחה פעילה (הצד השני לא ישמע אותך)."
                                    else -> "פקודה זו תבטל את השתקת המיקרופון ותחזיר אותו למצב פעיל במהלך שיחה פעילה."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    "answer_call" -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "פקודה זו תענה באופן אוטומטי לשיחה נכנסת במכשיר.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!answerCallsPermissionGranted) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("מענה לשיחה דורש הרשאה מתאימה.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                        TextButton(onClick = { answerCallsPermissionLauncher.launch(Manifest.permission.ANSWER_PHONE_CALLS) }) {
                                            Text("הענק הרשאה למענה לשיחות", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("הרשאת מענה לשיחות מאושרת!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                    }
                    "web" -> {
                        OutlinedTextField(
                            value = webUrl,
                            onValueChange = { webUrl = it },
                            label = { Text("כתובת האתר (URL)") },
                            placeholder = { Text("https://example.com") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    "dtmf_dial" -> {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "פקודה זו תחייג צלילי DTMF (למשל לצורך ניווט בתפריטים קוליים במענה אוטומטי). פסיק (,) מייצג השהיה של 2 שניות.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = dtmfTones,
                                onValueChange = { dtmfTones = it },
                                label = { Text("צלילי ה-DTMF לחיוג (למשל: 123# או ,,2)") },
                                placeholder = { Text("למשל: 123#") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    "hold_call" -> {
                        Text(
                            text = "פקודה זו תנסה להעביר את השיחה הפעילה במכשיר למצב ממתינה (החזקה - Hold).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "resume_call" -> {
                        Text(
                            text = "פקודה זו תנסה לשחרר את השיחה הפעילה במכשיר ממצב ממתינה (שחרור החזקה - Resume).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "end_call" -> {
                        Text(
                            text = "פקודה זו תנתק את השיחה הפעילה במכשיר (ניתוק שיחה - Hang up).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "alarm" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = alarmHour,
                                onValueChange = { alarmHour = it },
                                label = { Text("שעה (0-23)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = alarmMinute,
                                onValueChange = { alarmMinute = it },
                                label = { Text("דקה (0-59)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                    "map" -> {
                        OutlinedTextField(
                            value = mapQuery,
                            onValueChange = { mapQuery = it },
                            label = { Text("כתובת או יעד לניווט") },
                            placeholder = { Text("למשל: תל אביב") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    "wifi_toggle", "wifi_on", "wifi_off" -> {
                        Text(
                            text = "פעולה זו תשלוט בקישוריות ה-Wi-Fi במכשיר שלך.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "bt_toggle", "bt_on", "bt_off" -> {
                        Text(
                            text = "פעולה זו תשלוט בקישוריות ה-Bluetooth במכשיר שלך.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "bright_max", "bright_min", "bright_half" -> {
                        Text(
                            text = "פעולה זו תשנה את בהירות המסך במכשיר שלך (דורש הרשאת שינוי הגדרות מערכת).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "timeout_30s", "timeout_5m" -> {
                        Text(
                            text = "פעולה זו תשנה את זמן כיבוי המסך האוטומטי (דורש הרשאת שינוי הגדרות מערכת).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "rotate_toggle" -> {
                        Text(
                            text = "פעולה זו תשנה את מצב סיבוב המסך האוטומטי (דורש הרשאת שינוי הגדרות מערכת).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "custom" -> {
                        OutlinedTextField(
                            value = customAction,
                            onValueChange = { customAction = it },
                            label = { Text("Intent Action") },
                            placeholder = { Text("android.intent.action.VIEW") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = customData,
                            onValueChange = { customData = it },
                            label = { Text("Data URI (אופציונלי)") },
                            placeholder = { Text("tel:12345") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = customPackage,
                            onValueChange = { customPackage = it },
                            label = { Text("Package Name (אופציונלי)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = customClass,
                            onValueChange = { customClass = it },
                            label = { Text("Class Name (אופציונלי)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    "raw" -> {
                        OutlinedTextField(
                            value = rawUriString,
                            onValueChange = { rawUriString = it },
                            label = { Text("הזן Intent URI או קישור (URL / URI)") },
                            placeholder = { Text("intent:#Intent;action=android.settings.SETTINGS;end") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "תוכל להזין פורמט Android Intent URI מלא, או קישור URI חופשי. דוגמאות:\n" +
                                   "• לפתיחת הגדרות: intent:#Intent;action=android.settings.SETTINGS;end\n" +
                                   "• לפתיחת אתר: https://google.com\n" +
                                   "• חיוג מהיר: tel:0501234567",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    "literal_action" -> {
                        OutlinedTextField(
                            value = literalActionString,
                            onValueChange = { literalActionString = it },
                            label = { Text("הזן פקודה מלאה (Action)") },
                            placeholder = { Text("למשל: android.intent.action.VIEW או ACTION_SET_ALARM") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "הזן את הפקודה/הפעולה (Action) המלאה בדיוק כפי שהיא צריכה להישלח למערכת. האפליקציה תשתמש בה ישירות ללא שינויים או תוספות, כולל מילים כמו action, active או כל פקודה מותאמת אישית אחרת שתבחר.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (label.isBlank()) {
                        android.widget.Toast.makeText(context, "נא להזין שם לבקשת המערכת", android.widget.Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    val finalIntent = Intent()
                    finalIntent.putExtra("PINAPP_INTENT_LABEL", label)
                    
                    try {
                        when (presetType) {
                            "call" -> {
                                if (!callPermissionGranted) {
                                    android.widget.Toast.makeText(context, "נא להעניק הרשאת שיחות טלפון תחילה", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                finalIntent.action = Intent.ACTION_CALL
                                finalIntent.data = android.net.Uri.parse("tel:$phoneNumber")
                            }
                            "dial" -> {
                                finalIntent.action = Intent.ACTION_DIAL
                                finalIntent.data = android.net.Uri.parse("tel:$phoneNumber")
                            }
                            "sms" -> {
                                finalIntent.action = Intent.ACTION_SENDTO
                                finalIntent.data = android.net.Uri.parse("smsto:$phoneNumber")
                                if (smsBody.isNotEmpty()) {
                                    finalIntent.putExtra("sms_body", smsBody)
                                }
                            }
                            "sms_direct" -> {
                                if (phoneNumber.isBlank() || smsBody.isBlank()) {
                                    android.widget.Toast.makeText(context, "נא להזין מספר טלפון ותוכן הודעה", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (!smsPermissionGranted) {
                                    android.widget.Toast.makeText(context, "נא להעניק הרשאת שליחת SMS תחילה", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                finalIntent.action = "com.example.action.SEND_SMS_DIRECT"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                finalIntent.putExtra("phone_number", phoneNumber)
                                finalIntent.putExtra("message_body", smsBody)
                            }
                            "speaker_on" -> {
                                finalIntent.action = "com.example.action.SWITCH_SPEAKER_ON"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "speaker_off" -> {
                                finalIntent.action = "com.example.action.SWITCH_SPEAKER_OFF"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "mute_on" -> {
                                finalIntent.action = "com.example.action.MUTE_ON"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "mute_off" -> {
                                finalIntent.action = "com.example.action.MUTE_OFF"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "call_mute_on" -> {
                                finalIntent.action = "com.example.action.CALL_MUTE_ON"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "call_mute_off" -> {
                                finalIntent.action = "com.example.action.CALL_MUTE_OFF"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "answer_call" -> {
                                if (!answerCallsPermissionGranted) {
                                    android.widget.Toast.makeText(context, "נא להעניק הרשאת מענה לשיחות תחילה", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                finalIntent.action = "com.example.action.ANSWER_CALL"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "web" -> {
                                var url = webUrl
                                if (url.isNotBlank()) {
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        url = "https://$url"
                                    }
                                    finalIntent.action = Intent.ACTION_VIEW
                                    finalIntent.data = android.net.Uri.parse(url)
                                } else {
                                    android.widget.Toast.makeText(context, "נא להזין כתובת אתר", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            }
                            "dtmf_dial" -> {
                                if (dtmfTones.isBlank()) {
                                    android.widget.Toast.makeText(context, "נא להזין צלילי DTMF לחיוג", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                finalIntent.action = "com.example.action.DTMF_DIAL"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                                finalIntent.putExtra("dtmf_tones", dtmfTones)
                            }
                            "hold_call" -> {
                                finalIntent.action = "com.example.action.HOLD_CALL"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "resume_call" -> {
                                finalIntent.action = "com.example.action.RESUME_CALL"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "end_call" -> {
                                finalIntent.action = "com.example.action.END_CALL"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "alarm" -> {
                                finalIntent.action = android.provider.AlarmClock.ACTION_SET_ALARM
                                val h = alarmHour.toIntOrNull() ?: 8
                                val m = alarmMinute.toIntOrNull() ?: 0
                                finalIntent.putExtra(android.provider.AlarmClock.EXTRA_HOUR, h)
                                finalIntent.putExtra(android.provider.AlarmClock.EXTRA_MINUTES, m)
                                finalIntent.putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                            }
                            "map" -> {
                                if (mapQuery.isNotBlank()) {
                                    finalIntent.action = Intent.ACTION_VIEW
                                    finalIntent.data = android.net.Uri.parse("google.navigation:q=" + android.net.Uri.encode(mapQuery))
                                } else {
                                    android.widget.Toast.makeText(context, "נא להזין יעד לניווט", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                            }
                             "settings" -> {
                                finalIntent.action = android.provider.Settings.ACTION_SETTINGS
                            }
                            "wifi_toggle" -> {
                                finalIntent.action = "com.example.action.TOGGLE_WIFI"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "wifi_on" -> {
                                finalIntent.action = "com.example.action.WIFI_ON"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "wifi_off" -> {
                                finalIntent.action = "com.example.action.WIFI_OFF"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "bt_toggle" -> {
                                finalIntent.action = "com.example.action.TOGGLE_BLUETOOTH"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "bt_on" -> {
                                finalIntent.action = "com.example.action.BLUETOOTH_ON"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "bt_off" -> {
                                finalIntent.action = "com.example.action.BLUETOOTH_OFF"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "bright_max" -> {
                                finalIntent.action = "com.example.action.SET_BRIGHTNESS_MAX"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "bright_min" -> {
                                finalIntent.action = "com.example.action.SET_BRIGHTNESS_MIN"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "bright_half" -> {
                                finalIntent.action = "com.example.action.SET_BRIGHTNESS_HALF"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "timeout_30s" -> {
                                finalIntent.action = "com.example.action.SET_TIMEOUT_30S"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "timeout_5m" -> {
                                finalIntent.action = "com.example.action.SET_TIMEOUT_5M"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "rotate_toggle" -> {
                                finalIntent.action = "com.example.action.TOGGLE_ROTATION"
                                finalIntent.setClassName(context.packageName, "com.example.ActionActivity")
                            }
                            "custom" -> {
                                if (customAction.isBlank()) {
                                    android.widget.Toast.makeText(context, "Intent Action לא יכול להיות ריק", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                finalIntent.action = customAction
                                if (customData.isNotEmpty()) {
                                    finalIntent.data = android.net.Uri.parse(customData)
                                }
                                if (customPackage.isNotEmpty()) {
                                    if (customClass.isNotEmpty()) {
                                        finalIntent.setClassName(customPackage, customClass)
                                    } else {
                                        finalIntent.setPackage(customPackage)
                                    }
                                }
                            }
                            "literal_action" -> {
                                if (literalActionString.isBlank()) {
                                    android.widget.Toast.makeText(context, "נא להזין פקודה (Action)", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                finalIntent.action = literalActionString.trim()
                            }
                            "raw" -> {
                                if (rawUriString.isBlank()) {
                                    android.widget.Toast.makeText(context, "נא להזין Intent URI", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val input = rawUriString.trim()
                                val parsed = try {
                                    if (input.startsWith("intent:")) {
                                        Intent.parseUri(input, Intent.URI_INTENT_SCHEME)
                                    } else if (input.startsWith("http://") || input.startsWith("https://")) {
                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(input))
                                    } else if (input.startsWith("tel:")) {
                                        Intent(Intent.ACTION_DIAL, android.net.Uri.parse(input))
                                    } else if (input.startsWith("geo:") || input.startsWith("mailto:") || input.startsWith("sms:")) {
                                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(input))
                                    } else if (input.contains("/") && !input.contains(":") && !input.contains(" ")) {
                                        val parts = input.split("/")
                                        if (parts.size == 2) {
                                            Intent(Intent.ACTION_MAIN).apply {
                                                setClassName(parts[0], parts[1])
                                            }
                                        } else {
                                            null
                                        }
                                    } else if (input.contains(".") && !input.contains(" ") && !input.contains(":") && !input.contains("/")) {
                                        if (input.startsWith("android.intent.action.") || input.startsWith("android.settings.") || input.contains(".action.")) {
                                            Intent(input)
                                        } else {
                                            val launchIntent = context.packageManager.getLaunchIntentForPackage(input)
                                            launchIntent ?: Intent(input)
                                        }
                                    } else {
                                        if (!input.contains(" ") && !input.contains(":") && !input.contains("/")) {
                                            Intent(input)
                                        } else {
                                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(input))
                                        }
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                                if (parsed == null) {
                                    android.widget.Toast.makeText(context, "פורמט ה-URI אינו תקין", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                parsed.putExtra("PINAPP_INTENT_LABEL", label)
                                if (editingUri != null) {
                                    NotificationHelper.deleteIntent(context, editingUri)
                                }
                                NotificationHelper.saveIntent(context, parsed)
                                onSaved()
                                return@Button
                            }
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "שגיאה ביצירת ה-Intent: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    
                    if (editingUri != null) {
                        NotificationHelper.deleteIntent(context, editingUri)
                    }
                    NotificationHelper.saveIntent(context, finalIntent)
                    onSaved()
                }
            ) {
                Text("שמור")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
fun IntentsManagementDialog(
    savedIntents: List<Intent>,
    onDismiss: () -> Unit,
    onEditIntent: (Intent, String) -> Unit,
    onDeleteIntent: (String) -> Unit,
    onTestIntent: (String) -> Unit,
    onCreateNew: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val filteredIntents = remember(savedIntents, searchQuery) {
        if (searchQuery.isBlank()) {
            savedIntents
        } else {
            savedIntents.filter {
                val label = it.getStringExtra("PINAPP_INTENT_LABEL") ?: ""
                val uri = it.toUri(Intent.URI_INTENT_SCHEME)
                label.contains(searchQuery, ignoreCase = true) || uri.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ניהול בקשות מערכת (Intents)")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("חיפוש בקשה...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (filteredIntents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("לא נמצאו בקשות תואמות", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredIntents) { intent ->
                            val intentUri = intent.toUri(Intent.URI_INTENT_SCHEME)
                            val label = intent.getStringExtra("PINAPP_INTENT_LABEL") ?: "בקשת מערכת"
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = intentUri,
                                            style = androidx.compose.ui.text.TextStyle(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Run/Test Button
                                        IconButton(
                                            onClick = { onTestIntent(intentUri) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "הרץ", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                        
                                        // Edit Button
                                        IconButton(
                                            onClick = { onEditIntent(intent, intentUri) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "ערוך", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                        }
                                        
                                        // Delete Button
                                        IconButton(
                                            onClick = { onDeleteIntent(intentUri) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "מחק", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onCreateNew) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("חדש")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("סגור")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBarIconConverterDialog(
    selectedUri: Uri,
    onDismiss: () -> Unit,
    onIconProcessed: (String) -> Unit
) {
    val context = LocalContext.current
    var conversionMode by remember { mutableStateOf("PRESERVE_ALPHA") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(true) }

    LaunchedEffect(selectedUri, conversionMode) {
        isProcessing = true
        val processed = withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(resolver, selectedUri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val b = MediaStore.Images.Media.getBitmap(resolver, selectedUri)
                    b.copy(Bitmap.Config.ARGB_8888, true)
                }

                val size = 96
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
                val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(outputBitmap)

                when (conversionMode) {
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
                outputBitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        previewBitmap = processed
        isProcessing = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "המרת תמונה לסמל סטטוס בר",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Right
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "בחר את שיטת ההמרה המתאימה ביותר כדי שהסמל ייראה מעולה בסטטוס בר העליון (רקע שקוף וסמל לבן):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                // Options selector
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modes = listOf(
                        Triple("PRESERVE_ALPHA", "שימור שקיפות (PNG)", "מתאים לתמונות עם רקע שקוף מראש"),
                        Triple("LIGHT_TO_ALPHA", "הסרת רקע בהיר (לסמלים כהים)", "הופך צבעים בהירים לשקופים וכהים ללבן"),
                        Triple("DARK_TO_ALPHA", "הסרת רקע כהה (לסמלים בהירים)", "הופך צבעים כהים לשקופים ובהירים ללבן"),
                        Triple("ORIGINAL", "צבעוני מקורי", "ללא המרה - מציג את התמונה בצבעיה המקוריים")
                    )

                    modes.forEach { (mode, title, desc) ->
                        val isSelected = conversionMode == mode
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { conversionMode = mode },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { conversionMode = mode }
                                    )
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 36.dp)
                                )
                            }
                        }
                    }
                }

                // High-fidelity Preview Section
                Text(
                    text = "תצוגה מקדימה בסטטוס בר:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (previewBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = previewBitmap!!.asImageBitmap(),
                                        contentDescription = "תצוגה מקדימה",
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(Color.White.copy(alpha = 0.2f), shape = CircleShape)
                                    )
                                }
                                Text(
                                    text = "PINAPP פעיל",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "18:40",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val savedPath = NotificationHelper.processAndSaveCustomIcon(context, selectedUri, conversionMode)
                    if (savedPath != null) {
                        onIconProcessed(savedPath)
                    } else {
                        Toast.makeText(context, "שגיאה בעיבוד והמרת התמונה", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isProcessing && previewBitmap != null
            ) {
                Text("אשר ושמור סמל")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
fun TileIconConverterDialog(
    selectedUri: Uri,
    tileId: Int,
    onDismiss: () -> Unit,
    onIconProcessed: (String) -> Unit
) {
    val context = LocalContext.current
    var conversionMode by remember { mutableStateOf("PRESERVE_ALPHA") }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(true) }

    LaunchedEffect(selectedUri, conversionMode) {
        isProcessing = true
        val processed = withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(resolver, selectedUri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val b = MediaStore.Images.Media.getBitmap(resolver, selectedUri)
                    b.copy(Bitmap.Config.ARGB_8888, true)
                }

                val size = 96
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, size, size, true)
                val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(outputBitmap)

                when (conversionMode) {
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
                outputBitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        previewBitmap = processed
        isProcessing = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "המרת תמונה לסמל אריח $tileId",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Right
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "בחר את שיטת ההמרה המתאימה ביותר כדי שסמל האריח ייראה מעולה בלוח ההתראות המהירות:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                // Options selector
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val modes = listOf(
                        Triple("PRESERVE_ALPHA", "שימור שקיפות (PNG)", "מתאים לתמונות עם רקע שקוף מראש"),
                        Triple("LIGHT_TO_ALPHA", "הסרת רקע בהיר (לסמלים כהים)", "הופך צבעים בהירים לשקופים וכהים ללבן"),
                        Triple("DARK_TO_ALPHA", "הסרת רקע כהה (לסמלים בהירים)", "הופך צבעים כהים לשקופים ובהירים ללבן"),
                        Triple("ORIGINAL", "צבעוני מקורי", "ללא המרה - מציג את התמונה בצבעיה המקוריים")
                    )

                    modes.forEach { (mode, title, desc) ->
                        val isSelected = conversionMode == mode
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { conversionMode = mode },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { conversionMode = mode }
                                    )
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 36.dp)
                                )
                            }
                        }
                    }
                }

                // High-fidelity Preview Section
                Text(
                    text = "תצוגה מקדימה של האריח המהיר:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Right
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1C1E))
                        .border(1.dp, Color(0xFF2C2C2E), RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (previewBitmap != null) {
                                    androidx.compose.foundation.Image(
                                        bitmap = previewBitmap!!.asImageBitmap(),
                                        contentDescription = "תצוגה מקדימה",
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(Color.White.copy(alpha = 0.2f), shape = CircleShape)
                                    )
                                }
                                Text(
                                    text = NotificationHelper.getTileLabel(context, tileId),
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val savedPath = NotificationHelper.processAndSaveTileIcon(context, selectedUri, conversionMode, tileId)
                    if (savedPath != null) {
                        onIconProcessed(savedPath)
                    } else {
                        Toast.makeText(context, "שגיאה בעיבוד והמרת התמונה", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isProcessing && previewBitmap != null
            ) {
                Text("אשר ושמור סמל")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
fun CategoryHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun DynamicQuickSettingsTileManagementCard(
    context: android.content.Context,
    tileTargetPkgs: Map<Int, String?>,
    tileAddedStates: Map<Int, Boolean>,
    onTileRemove: (Int) -> Unit,
    onSelectTile: (TriggerType) -> Unit,
    onAddTileClick: () -> Unit,
    refreshCounter: Int,
    onRefreshRequest: () -> Unit,
    onCustomIconRequest: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ניהול אריחי סטטוס בר (Quick Tiles)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "הוסף אריחים מותאמים אישית מווילון ההתראות המהירות של המכשיר שלך להפעלת קיצורי הדרך שלך במהירות.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(16.dp))

            val totalAdded = tileAddedStates.values.count { it }

            if (totalAdded == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "טרם הוספת אריחים מהירים.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "לחץ על כפתור ההוספה למטה כדי להתחיל לעצב ולהגדיר אריח מהיר ראשון!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (tileNum in 1..15) {
                        if (tileAddedStates[tileNum] == true) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                    .padding(4.dp)
                            ) {
                                val type = NotificationHelper.getTileTriggerType(tileNum)!!
                                val currentLabel = NotificationHelper.getTileLabel(context, tileNum)
                                TriggerConfigCard(
                                    title = "אריח מהיר $tileNum: $currentLabel",
                                    description = "הגדר אילו אפליקציות או פעולות מערכת ירוצו בעת לחיצה על אריח $tileNum.",
                                    type = type,
                                    icon = Icons.Default.PlayArrow,
                                    targetPkg = tileTargetPkgs[tileNum],
                                    onSelectClick = { onSelectTile(type) },
                                    onClearClick = {
                                        NotificationHelper.clearTargetPackage(context, type)
                                        onRefreshRequest()
                                    },
                                    refreshCounter = refreshCounter,
                                    onRefreshRequest = onRefreshRequest,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "עריכת מראה האריח (שם וסמל)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    var tileLabelText by remember(tileNum, refreshCounter) {
                                        mutableStateOf(NotificationHelper.getTileLabel(context, tileNum))
                                    }

                                    OutlinedTextField(
                                        value = tileLabelText,
                                        onValueChange = { tileLabelText = it },
                                        label = { Text("שם האריח") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    NotificationHelper.setTileLabel(context, tileNum, tileLabelText)
                                                    onRefreshRequest()
                                                    Toast.makeText(context, "שם האריח $tileNum עודכן!", Toast.LENGTH_SHORT).show()
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "שמור שם",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    )

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "בחירת סמל (אייקון):",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "סמלים מובנים מראש:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    val tileIconType = NotificationHelper.getTileIconType(context, tileNum)
                                    val tilePresetIcon = NotificationHelper.getTilePresetIcon(context, tileNum)

                                    val presets = listOf(
                                        Pair(android.R.drawable.ic_menu_revert, "חץ"),
                                        Pair(android.R.drawable.ic_menu_compass, "מצפן"),
                                        Pair(android.R.drawable.ic_menu_mylocation, "מיקום"),
                                        Pair(android.R.drawable.ic_menu_directions, "ניווט"),
                                        Pair(android.R.drawable.ic_menu_camera, "מצלמה"),
                                        Pair(android.R.drawable.ic_menu_manage, "הגדרות"),
                                        Pair(android.R.drawable.ic_menu_view, "עין"),
                                        Pair(android.R.drawable.ic_dialog_info, "מידע"),
                                        Pair(android.R.drawable.star_on, "כוכב"),
                                        Pair(android.R.drawable.ic_menu_send, "שליחה")
                                    )

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(presets) { (resId, label) ->
                                            val isSelected = tileIconType == "preset" && tilePresetIcon == resId
                                            val iconDrawable = try {
                                                androidx.core.content.ContextCompat.getDrawable(context, resId)
                                            } catch (e: Exception) {
                                                null
                                            }

                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                        else Color.Transparent
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.outlineVariant,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        NotificationHelper.setTilePresetIcon(context, tileNum, resId)
                                                        onRefreshRequest()
                                                        Toast.makeText(context, "סמל אריח $tileNum עודכן!", Toast.LENGTH_SHORT).show()
                                                    }
                                                    .padding(8.dp)
                                                    .width(54.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(24.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (iconDrawable != null) {
                                                        val bitmap = Bitmap.createBitmap(
                                                            iconDrawable.intrinsicWidth.coerceAtLeast(48),
                                                            iconDrawable.intrinsicHeight.coerceAtLeast(48),
                                                            Bitmap.Config.ARGB_8888
                                                        )
                                                        val canvas = Canvas(bitmap)
                                                        iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                                                        iconDrawable.draw(canvas)
                                                        androidx.compose.foundation.Image(
                                                            bitmap = bitmap.asImageBitmap(),
                                                            contentDescription = label,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Default.Star,
                                                            contentDescription = label,
                                                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                            else MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "סמל מותאם מהגלריה:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Button(
                                                onClick = { onCustomIconRequest(tileNum) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.secondary
                                                )
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("בחר תמונה מהגלריה")
                                            }

                                            TileCustomIconLoader(
                                                context = context,
                                                tileNum = tileNum,
                                                tileIconType = tileIconType,
                                                refreshCounter = refreshCounter
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                androidx.compose.material3.HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = { onTileRemove(tileNum) },
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(end = 12.dp, bottom = 12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("הסר אריח מהיר $tileNum")
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (totalAdded < 15) {
                Button(
                    onClick = onAddTileClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("הוסף אריח מהיר חדש (${15 - totalAdded} נותרו)")
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "הגעת להגבלת האריחים הנתמכת במערכת (מקסימום 15 אריחים)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun MenuCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeText: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun TileCustomIconLoader(
    context: Context,
    tileNum: Int,
    tileIconType: String,
    refreshCounter: Int
) {
    var bitmap by remember(tileNum, tileIconType, refreshCounter) {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(tileNum, tileIconType, refreshCounter) {
        if (tileIconType == "custom") {
            val loadedBitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val customIconPath = NotificationHelper.getTileCustomIconPath(context, tileNum)
                    if (customIconPath != null) {
                        val file = java.io.File(customIconPath)
                        if (file.exists()) {
                            android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        } else null
                    } else null
                } catch (e: Exception) {
                    null
                }
            }
            bitmap = loadedBitmap
        } else {
            bitmap = null
        }
    }

    if (tileIconType == "custom" && bitmap != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "סמל מותאם נוכחי",
                modifier = Modifier
                    .size(24.dp)
                    .background(Color.DarkGray, shape = CircleShape)
                    .padding(2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "פעיל",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DeveloperIntentEditDialog(
    initialIntentUri: String,
    onDismiss: () -> Unit,
    onConfirm: (editedUri: String) -> Unit
) {
    var editedUri by remember { mutableStateOf(initialIntentUri) }
    var testError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("אפשרויות מפתחים - עריכת פקודה", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "לפני יצירת קיצור הדרך, באפשרותך לצפות ולערוך את פקודת ה-Intent (במבנה URI) שיופעל במערכת:",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = editedUri,
                    onValueChange = { 
                        editedUri = it 
                        testError = null
                    },
                    label = { Text("פקודת Intent URI") },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6
                )

                if (testError != null) {
                    Text(
                        text = testError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val testIntent = Intent.parseUri(editedUri, Intent.URI_INTENT_SCHEME).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(testIntent)
                                android.widget.Toast.makeText(context, "נשלח בהצלחה למערכת לצורך בדיקה", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                testError = "שגיאה בהפעלה: ${e.message}"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("בדוק פקודה", maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        Intent.parseUri(editedUri, Intent.URI_INTENT_SCHEME)
                        onConfirm(editedUri)
                    } catch (e: Exception) {
                        testError = "פקודה לא תקינה: ${e.message}"
                    }
                }
            ) {
                Text("אישור ושמירה")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

@Composable
fun TileStepConfigDialog(
    context: Context,
    type: TriggerType,
    index: Int,
    onDismiss: () -> Unit
) {
    val configs = remember { NotificationHelper.getSequenceStepConfigs(context, type).toMutableList() }
    val currentConfigStr = if (index in configs.indices) configs[index] else ""
    val parts = currentConfigStr.split("|||").let { if (it.size == 1) it[0].split("|") else it }
    
    var label by remember { mutableStateOf(if (parts.size >= 1 && parts[0].isNotEmpty()) parts[0] else "שלב ${index + 1}") }
    var iconRes by remember { mutableStateOf(if (parts.size >= 2 && parts[1].isNotEmpty()) parts[1].toIntOrNull() ?: android.R.drawable.ic_menu_revert else android.R.drawable.ic_menu_revert) }
    var isActive by remember { mutableStateOf(if (parts.size >= 3 && parts[2].isNotEmpty()) parts[2].toBoolean() else true) }

    val presets = listOf(
        Pair(android.R.drawable.ic_menu_revert, "חץ"),
        Pair(android.R.drawable.ic_menu_compass, "מצפן"),
        Pair(android.R.drawable.ic_menu_mylocation, "מיקום"),
        Pair(android.R.drawable.ic_menu_directions, "ניווט"),
        Pair(android.R.drawable.ic_menu_camera, "מצלמה"),
        Pair(android.R.drawable.ic_menu_manage, "הגדרות"),
        Pair(android.R.drawable.ic_menu_view, "עין"),
        Pair(android.R.drawable.ic_dialog_info, "מידע"),
        Pair(android.R.drawable.star_on, "כוכב"),
        Pair(android.R.drawable.ic_menu_send, "שליחה")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הגדרות תצוגה לשלב ${index + 1}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("שם האריח בשלב זה") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text("צבע האריח בשלב זה (פעיל/כבוי):", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isActive = true },
                            color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "פעיל (דולק)",
                                modifier = Modifier.padding(12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { isActive = false },
                            color = if (!isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (!isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "כבוי (אפור)",
                                modifier = Modifier.padding(12.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = if (!isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Column {
                    Text("סמל האריח בשלב זה:", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { (resId, _) ->
                            val isSelected = iconRes == resId
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { iconRes = resId }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = resId,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newConfigStr = "$label|$iconRes|$isActive"
                    if (index in configs.indices) {
                        configs[index] = newConfigStr
                    } else {
                        while (configs.size <= index) configs.add("")
                        configs[index] = newConfigStr
                    }
                    NotificationHelper.saveSequenceStepConfigs(context, type, configs)
                    onDismiss()
                }
            ) {
                Text("שמור לשלב זה")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ביטול")
            }
        }
    )
}

