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
import com.example.canvaslauncher.data.AppColorClassifier
import com.example.canvaslauncher.data.AppInfo
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
    }

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private val canvasItems = mutableStateListOf<CanvasItem>()
    private val appList = mutableListOf<AppInfo>()
    private var incomingText by mutableStateOf<String?>(null)
    private var incomingImageUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

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
                incomingText = incomingText,
                incomingImageUri = incomingImageUri,
                onIncomingConsumed = {
                    incomingText = null
                    incomingImageUri = null
                }
            )
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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
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
            } else if (requestCode == REQUEST_CONFIGURE_APPWIDGET) {
                val appWidgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
                if (appWidgetId != -1) {
                    addWidgetCardToCanvas(appWidgetId)
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
            if (type == "text/plain") {
                incomingText = intent.getStringExtra(Intent.EXTRA_TEXT)
            } else if (type?.startsWith("image/") == true) {
                incomingImageUri = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java
                )
            }
        }
    }

    private fun loadInstalledAndArchivedApps() {
        val loadedMap = linkedMapOf<String, AppInfo>()

        // 1. LauncherApps include nativamente sia le app attive che le app archiviate dal sistema
        try {
            val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val activityList = launcherApps.getActivityList(null, Process.myUserHandle())
            for (activityInfo in activityList) {
                val pkg = activityInfo.componentName.packageName
                if (pkg == this.packageName) continue

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

        // 2. Fallback / integrazione tramite PackageManager (incluso flag pacchetti archiviati)
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
            if (pkg == this.packageName || loadedMap.containsKey(pkg)) continue

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
