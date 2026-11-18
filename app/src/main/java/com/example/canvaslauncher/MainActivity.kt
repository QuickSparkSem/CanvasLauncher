package com.example.canvaslauncher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.canvaslauncher.data.AppColorClassifier
import com.example.canvaslauncher.data.AppInfo
import com.example.canvaslauncher.data.StatusBarMode
import com.example.canvaslauncher.data.StorageManager
import com.example.canvaslauncher.model.CanvasItem
import com.example.canvaslauncher.model.ItemType
import com.example.canvaslauncher.ui.CanvasLauncherApp
import java.util.UUID

class MainActivity : ComponentActivity() {

    companion object {
        private const val APPWIDGET_HOST_ID = 2048
        private const val REQUEST_PICK_APPWIDGET = 101
        private const val REQUEST_CONFIGURE_APPWIDGET = 102
        private const val REQUEST_PICK_SHORTCUT_APP = 103
        private const val REQUEST_CREATE_SHORTCUT = 104
    }

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private val canvasItems = mutableStateListOf<CanvasItem>()
    private val appList = mutableStateListOf<AppInfo>()
    private var incomingText by mutableStateOf<String?>(null)
    private var incomingImageUri by mutableStateOf<Uri?>(null)
    private var incomingAudioUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        applyStatusBarMode(StorageManager.getStatusBarMode(this))

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

        canvasItems.clear()
        canvasItems.addAll(StorageManager.loadItems(this))
        loadInstalledAndArchivedApps()
        extractShareIntent(intent)

        setContent {
            CanvasLauncherApp(
                items = canvasItems,
                appList = appList,
                appWidgetHost = appWidgetHost,
                appWidgetManager = appWidgetManager,
                onRequestPickSystemWidget = { startPickSystemWidget() },
                onRequestPickSystemShortcut = { startPickSystemShortcut() },
                onStatusBarModeChanged = { mode -> applyStatusBarMode(mode) },
                onShowSelfInDrawerChanged = { loadInstalledAndArchivedApps() },
                incomingText = incomingText,
                incomingImageUri = incomingImageUri,
                incomingAudioUri = incomingAudioUri,
                onIncomingConsumed = {
                    incomingText = null
                    incomingImageUri = null
                    incomingAudioUri = null
                }
            )
        }
    }

    private fun applyStatusBarMode(mode: StatusBarMode) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (mode == StatusBarMode.HIDDEN) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            appWidgetHost.startListening()
        } catch (_: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            appWidgetHost.stopListening()
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun startPickSystemWidget() {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET)
    }

    @Suppress("DEPRECATION")
    private fun startPickSystemShortcut() {
        val shortcutPickIntent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
            putExtra(
                Intent.EXTRA_INTENT,
                Intent(Intent.ACTION_CREATE_SHORTCUT)
            )
            putExtra(Intent.EXTRA_TITLE, "Scegli scorciatoia (es. Shortcut Maker)")
        }
        startActivityForResult(shortcutPickIntent, REQUEST_PICK_SHORTCUT_APP)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                REQUEST_PICK_APPWIDGET -> {
                    val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (appWidgetId != -1) {
                        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                        if (info?.configure != null) {
                            appWidgetHost.startAppWidgetConfigureActivityForResult(
                                this,
                                appWidgetId,
                                0,
                                REQUEST_CONFIGURE_APPWIDGET,
                                null
                            )
                        } else {
                            addWidgetCardToCanvas(appWidgetId)
                        }
                    }
                }
                REQUEST_CONFIGURE_APPWIDGET -> {
                    val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
                    if (appWidgetId != -1) {
                        addWidgetCardToCanvas(appWidgetId)
                    }
                }
                REQUEST_PICK_SHORTCUT_APP -> {
                    // Avvia l'activity scelta (es. Shortcut Maker o Impostazioni) per configurare la scorciatoia
                    startActivityForResult(data, REQUEST_CREATE_SHORTCUT)
                }
                REQUEST_CREATE_SHORTCUT -> {
                    val shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Scorciatoia"
                    val shortcutIntent = IntentCompat.getParcelableExtra(
                        data,
                        Intent.EXTRA_SHORTCUT_INTENT,
                        Intent::class.java
                    )
                    if (shortcutIntent != null) {
                        val intentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)
                        val defaultColor = StorageManager.getDefaultElementColor(this)
                        canvasItems.add(
                            CanvasItem(
                                id = UUID.randomUUID().toString(),
                                title = shortcutName,
                                content = "Scorciatoia personalizzata",
                                shortcutIntentUri = intentUri,
                                x = 140f,
                                y = 260f,
                                widthDp = 180f,
                                heightDp = 110f,
                                colorHex = defaultColor,
                                type = ItemType.SYSTEM_SHORTCUT
                            )
                        )
                        StorageManager.saveItems(this, canvasItems)
                    }
                }
            }
        } else if (resultCode == Activity.RESULT_CANCELED && data != null) {
            val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (appWidgetId != -1) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
        }
    }

    private fun addWidgetCardToCanvas(appWidgetId: Int) {
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        val label = info?.loadLabel(packageManager) ?: "Widget Sistema"
        val defaultColor = StorageManager.getDefaultElementColor(this)
        canvasItems.add(
            CanvasItem(
                id = UUID.randomUUID().toString(),
                title = label,
                appWidgetId = appWidgetId,
                x = 120f,
                y = 240f,
                widthDp = 260f,
                heightDp = 190f,
                colorHex = defaultColor,
                type = ItemType.SYSTEM_WIDGET
            )
        )
        StorageManager.saveItems(this, canvasItems)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractShareIntent(intent)
    }

    private fun extractShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val type = intent.type
            when {
                type == "text/plain" -> {
                    incomingText = intent.getStringExtra(Intent.EXTRA_TEXT)
                }
                type?.startsWith("image/") == true -> {
                    incomingImageUri = IntentCompat.getParcelableExtra(
                        intent,
                        Intent.EXTRA_STREAM,
                        Uri::class.java
                    )
                }
                type?.startsWith("audio/") == true -> {
                    incomingAudioUri = IntentCompat.getParcelableExtra(
                        intent,
                        Intent.EXTRA_STREAM,
                        Uri::class.java
                    )
                }
            }
        }
    }

    private fun loadInstalledAndArchivedApps() {
        val showSelf = StorageManager.getShowSelfInDrawer(this)
        val loadedMap = linkedMapOf<String, AppInfo>()

        try {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val activityList = launcherApps.getActivityList(null, Process.myUserHandle())
            for (activityInfo in activityList) {
                val pkg = activityInfo.componentName.packageName
                if (!showSelf && pkg == this.packageName) continue

                val label = activityInfo.label.toString()
                val icon = activityInfo.getIcon(0)
                val isArchived = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    activityInfo.applicationInfo.isArchived
                } else {
                    false
                }
                val colorCat = AppColorClassifier.classifyDrawable(icon)
                loadedMap[pkg] = AppInfo(
                    label = label,
                    packageName = pkg,
                    icon = icon,
                    colorCategory = colorCat,
                    isArchived = isArchived,
                    componentName = activityInfo.componentName,
                    userHandle = activityInfo.user
                )
            }
        } catch (_: Exception) {
        }

        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            packageManager.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ARCHIVED_PACKAGES)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(mainIntent, 0)
        }

        for (info in resolvedInfos) {
            val pkg = info.activityInfo.packageName
            if ((!showSelf && pkg == this.packageName) || loadedMap.containsKey(pkg)) continue

            val label = info.loadLabel(packageManager).toString()
            val icon = info.loadIcon(packageManager)
            val isArchived = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                info.activityInfo.applicationInfo.isArchived
            } else {
                false
            }
            val colorCat = AppColorClassifier.classifyDrawable(icon)
            loadedMap[pkg] = AppInfo(
                label = label,
                packageName = pkg,
                icon = icon,
                colorCategory = colorCat,
                isArchived = isArchived
            )
        }

        appList.clear()
        appList.addAll(loadedMap.values.sortedBy { it.label.lowercase() })
    }
}
