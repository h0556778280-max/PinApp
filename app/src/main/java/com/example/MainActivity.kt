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
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
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

    fun loadApps(pm: PackageManager) {
        viewModelScope.launch(Dispatchers.IO) {
            val mainIntent = Intent(Intent.ACTION_MAIN, null)
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
            
            val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            val appList = resolveInfos.mapNotNull { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg == "com.google.android.GoogleCameraEng") return@mapNotNull null
                
                AppInfo(
                    label = ri.loadLabel(pm).toString(),
                    packageName = pkg,
                    icon = ri.loadIcon(pm)
                )
            }.sortedBy { it.label.lowercase() }
            
            withContext(Dispatchers.Main) {
                _apps.value = appList
                _isLoading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AppsViewModel = viewModel()
) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var isNotificationEnabled by remember {
        mutableStateOf(NotificationHelper.isNotificationEnabled(context))
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

    // Track active selection
    var selectingForTrigger by remember { mutableStateOf<TriggerType?>(null) }
    var isSelectingForShortcut by remember { mutableStateOf(false) }

    // State for launching the shortcut designer
    var designerTargetApp by remember { mutableStateOf<AppInfo?>(null) }
    var designerTargetActivity by remember { mutableStateOf<android.content.pm.ActivityInfo?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        viewModel.loadApps(context.packageManager)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (selectingForTrigger != null || isSelectingForShortcut) {
        val trigger = selectingForTrigger
        val triggerTitle = when {
            isSelectingForShortcut -> "בחירת אפליקציה לייצור אייקון"
            trigger == TriggerType.HOME -> "בחירת אפליקציה למסך הבית"
            trigger == TriggerType.ASSIST -> "בחירת אפליקציה לסייען הקולי"
            trigger == TriggerType.CAMERA -> "בחירת אפליקציה למצלמה"
            else -> ""
        }

        var selectedAppForActivities by remember { mutableStateOf<AppInfo?>(null) }
        var activitiesList by remember { mutableStateOf<List<android.content.pm.ActivityInfo>>(emptyList()) }
        var isActivitiesLoading by remember { mutableStateOf(false) }

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
                            IconButton(onClick = { selectedAppForActivities = null }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
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
                    // Header card with default action
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = app.icon,
                                contentDescription = app.label,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "הפעלת האפליקציה כרגיל",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "ייפתח האקטיביטי הראשי של האפליקציה כברירת מחדל.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Design custom shortcut icon button
                                IconButton(
                                    onClick = {
                                        designerTargetApp = app
                                        designerTargetActivity = null
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "ייצר אייקון לקיצור דרך",
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }

                                if (!isSelectingForShortcut && trigger != null) {
                                    Button(
                                        onClick = {
                                            NotificationHelper.saveTargetPackage(context, trigger, app.packageName)
                                            when (trigger) {
                                                TriggerType.HOME -> homeTargetPkg = app.packageName
                                                TriggerType.ASSIST -> assistTargetPkg = app.packageName
                                                TriggerType.CAMERA -> cameraTargetPkg = app.packageName
                                            }
                                            selectedAppForActivities = null
                                            selectingForTrigger = null
                                            searchQuery = ""
                                            android.widget.Toast.makeText(
                                                context, 
                                                "ההגדרה נשמרה בהצלחה!", 
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    ) {
                                        Text("בחר")
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

                    if (isActivitiesLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (activitiesList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "לא נמצאו תת-אקטיביטיז במניפסט של אפליקציה זו. תוכל לבחור רק בהפעלת האפליקציה כרגיל.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(activitiesList, key = { it.name }) { activity ->
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
                                            if (isSelectingForShortcut) {
                                                designerTargetApp = app
                                                designerTargetActivity = activity
                                            } else if (trigger != null) {
                                                val fullTarget = "${app.packageName}/${activity.name}"
                                                NotificationHelper.saveTargetPackage(context, trigger, fullTarget)
                                                when (trigger) {
                                                    TriggerType.HOME -> homeTargetPkg = fullTarget
                                                    TriggerType.ASSIST -> assistTargetPkg = fullTarget
                                                    TriggerType.CAMERA -> cameraTargetPkg = fullTarget
                                                }
                                                selectedAppForActivities = null
                                                selectingForTrigger = null
                                                searchQuery = ""
                                                android.widget.Toast.makeText(
                                                    context, 
                                                    "תת-האקטיביטי נשמר בהצלחה!", 
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
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
                                searchQuery = ""
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "סגור"
                                )
                            }
                        }
                    )
                }
            ) { padding ->
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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

                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val filteredApps = apps.filter { 
                            it.label.contains(searchQuery, ignoreCase = true) || 
                            it.packageName.contains(searchQuery, ignoreCase = true)
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredApps, key = { it.packageName }) { app ->
                                AppListItem(app = app, onClick = {
                                    selectedAppForActivities = app
                                })
                            }
                        }
                    }
                }
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("PINAPP - הגדרות קיצורי דרך") }
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

                // 1. HOME SCREEN CARD
                TriggerConfigCard(
                    title = "לחיצה על כפתור הבית",
                    description = "הגדר איזו אפליקציה תיפתח בכל פעם שתלחץ על כפתור הבית במכשיר.",
                    targetPkg = homeTargetPkg,
                    icon = Icons.Default.Home,
                    onSelectClick = { selectingForTrigger = TriggerType.HOME },
                    onClearClick = {
                        NotificationHelper.clearTargetPackage(context, TriggerType.HOME)
                        homeTargetPkg = null
                    }
                )

                // 2. ASSISTANT CARD
                TriggerConfigCard(
                    title = "הפעלת הסייען הקולי (Assist)",
                    description = "הגדר איזו אפליקציה תיפתח בעת הפעלת הסייען (לחיצה ארוכה על כפתור הבית, מחווה, או כפתור ייעודי).",
                    targetPkg = assistTargetPkg,
                    icon = Icons.Default.Star,
                    onSelectClick = { selectingForTrigger = TriggerType.ASSIST },
                    onClearClick = {
                        NotificationHelper.clearTargetPackage(context, TriggerType.ASSIST)
                        assistTargetPkg = null
                    }
                )

                // 3. CAMERA CARD
                TriggerConfigCard(
                    title = "קיצור דרך למצלמה (לחיצה כפולה)",
                    description = "הגדר איזו אפליקציה תיפתח בעת הפעלת קיצור הדרך למצלמה של המכשיר (למשל לחיצה כפולה על מקש ההפעלה).",
                    targetPkg = cameraTargetPkg,
                    icon = Icons.Default.Settings,
                    onSelectClick = { selectingForTrigger = TriggerType.CAMERA },
                    onClearClick = {
                        NotificationHelper.clearTargetPackage(context, TriggerType.CAMERA)
                        cameraTargetPkg = null
                    }
                )

                // 4. CUSTOM SHORTCUTS CARD
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
                        Button(
                            onClick = { isSelectingForShortcut = true },
                            modifier = Modifier.align(Alignment.End),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Text("צור קיצור דרך מעוצב")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SYSTEM DEFAULT SETTINGS GUIDE
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

                // Notification toggle
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
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
                }

                Spacer(modifier = Modifier.height(32.dp))
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
}

@Composable
fun TriggerConfigCard(
    title: String,
    description: String,
    targetPkg: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onSelectClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    val parsedPkg = remember(targetPkg) {
        if (targetPkg == null) null else {
            if (targetPkg.contains("/")) targetPkg.split("/")[0] else targetPkg
        }
    }

    val appLabel = remember(targetPkg) {
        if (targetPkg == null) null else {
            val pkg = if (targetPkg.contains("/")) targetPkg.split("/")[0] else targetPkg
            val cls = if (targetPkg.contains("/")) targetPkg.split("/")[1] else null
            try {
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
                targetPkg
            }
        }
    }
    val appIcon = remember(parsedPkg) {
        if (parsedPkg == null) null else {
            try {
                pm.getApplicationIcon(parsedPkg)
            } catch (e: Exception) {
                null
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (targetPkg != null) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // Current target app info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (targetPkg != null) {
                    if (appIcon != null) {
                        AsyncImage(
                            model = appIcon,
                            contentDescription = appLabel,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = appLabel ?: "",
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

            // Action Buttons
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
    var iconSource by remember { mutableStateOf("original") } // "original", "original_bg", "emoji"
    var selectedEmoji by remember { mutableStateOf("📱") }
    var selectedColor by remember { mutableStateOf(0xFF2196F3.toInt()) }
    var selectedShape by remember { mutableStateOf("circle") } // "circle", "squircle", "square"

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "original" to "מקורי",
                        "original_bg" to "רקע מותאם",
                        "emoji" to "אימוג'י"
                    ).forEach { (src, name) ->
                        FilterChip(
                            selected = iconSource == src,
                            onClick = { iconSource = src },
                            label = { Text(name) },
                            modifier = Modifier.weight(1f)
                        )
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
                        else -> {
                            null
                        }
                    }

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
}

object ShortcutHelper {
    fun createShortcut(
        context: Context,
        label: String,
        packageName: String,
        className: String?,
        customIcon: Bitmap? = null
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }

        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            ?: return false

        if (!shortcutManager.isRequestPinShortcutSupported) {
            return false
        }

        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
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
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val id = "shortcut_${packageName}_${className ?: "main"}_${System.currentTimeMillis()}"

        val icon = if (customIcon != null) {
            Icon.createWithBitmap(customIcon)
        } else {
            try {
                val appIconDrawable = context.packageManager.getApplicationIcon(packageName)
                val bitmap = drawableToBitmap(appIconDrawable)
                Icon.createWithBitmap(bitmap)
            } catch (e: Exception) {
                try {
                    val ownIcon = context.packageManager.getApplicationIcon(context.packageName)
                    Icon.createWithBitmap(drawableToBitmap(ownIcon))
                } catch (e2: Exception) {
                    val defIcon = context.packageManager.defaultActivityIcon
                    Icon.createWithBitmap(drawableToBitmap(defIcon))
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
}
