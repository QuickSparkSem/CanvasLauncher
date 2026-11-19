package com.example.canvaslauncher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.canvaslauncher.data.AppInfo
import com.example.canvaslauncher.data.StatusBarMode
import com.example.canvaslauncher.data.StorageManager
import com.example.canvaslauncher.model.CanvasItem
import com.example.canvaslauncher.model.ItemType
import com.example.canvaslauncher.shizuku.VirtualDisplayWindowManager
import com.example.canvaslauncher.ui.CanvasLauncherApp
import java.util.UUID

class MainActivity : ComponentActivity() {

    companion object {
        private const val APPWIDGET_HOST_ID = 2048
    }

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private val canvasItems = mutableStateListOf<CanvasItem>()
    private val installedApps = mutableStateListOf<AppInfo>()

    private var shizukuReadyState by mutableStateOf(false)
    private var incomingTextState by mutableStateOf<String?>(null)
    private var incomingImageUriState by mutableStateOf<Uri?>(null)
    private var incomingAudioUriState by mutableStateOf<Uri?>(null)

    private var pendingWidgetId: Int = -1

    private val pickWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        if (result.resultCode == RESULT_OK && widgetId != -1) {
            configureOrAddWidget(widgetId)
        } else if (widgetId != -1) {
            runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
        }
        pendingWidgetId = -1
    }

    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val widgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        if (result.resultCode == RESULT_OK && widgetId != -1) {
            addWidgetItemToCanvas(widgetId)
        } else if (widgetId != -1) {
            runCatching { appWidgetHost.deleteAppWidgetId(widgetId) }
        }
        pendingWidgetId = -1
    }

    private val pickShortcutLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            @Suppress("DEPRECATION")
            val shortcutIntent = result.data?.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
            @Suppress("DEPRECATION")
            val name = result.data?.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Scorciatoia"
            if (shortcutIntent != null) {
                canvasItems.add(
                    CanvasItem(
                        id = UUID.randomUUID().toString(),
                        title = name,
                        shortcutIntentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME),
                        x = 160f,
                        y = 260f,
                        widthDp = 105f,
                        heightDp = 100f,
                        type = ItemType.SYSTEM_SHORTCUT
                    )
                )
                StorageManager.saveItems(this, canvasItems)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { enableEdgeToEdge() }

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)
        runCatching { appWidgetHost.startListening() }

        canvasItems.clear()
        canvasItems.addAll(StorageManager.loadItems(this))
        refreshInstalledApps()
        shizukuReadyState = VirtualDisplayWindowManager.isShizukuReady()

        handleIncomingIntent(intent)

        setContent {
            CanvasLauncherApp(
                items = canvasItems,
                appList = installedApps,
                appWidgetHost = appWidgetHost,
                appWidgetManager = appWidgetManager,
                shizukuReady = shizukuReadyState,
                onRequestShizukuPermission = {
                    VirtualDisplayWindowManager.requestShizukuPermission()
                    shizukuReadyState = VirtualDisplayWindowManager.isShizukuReady()
                },
                onRequestPickSystemWidget = { launchSystemWidgetPicker() },
                onRequestPickSystemShortcut = { launchSystemShortcutPicker() },
                onStatusBarModeChanged = { mode -> applyStatusBarMode(mode) },
                onShowSelfInDrawerChanged = { refreshInstalledApps() },
                incomingText = incomingTextState,
                incomingImageUri = incomingImageUriState,
                incomingAudioUri = incomingAudioUriState,
                onIncomingConsumed = {
                    incomingTextState = null
                    incomingImageUriState = null
                    incomingAudioUriState = null
                }
            )
        }

        window.decorView.post {
            applyStatusBarMode(StorageManager.getStatusBarMode(this))
        }
    }

    override fun onResume() {
        super.onResume()
        shizukuReadyState = VirtualDisplayWindowManager.isShizukuReady()
        refreshInstalledApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { appWidgetHost.stopListening() }
        VirtualDisplayWindowManager.releaseAll()
    }

    private fun applyStatusBarMode(mode: StatusBarMode) {
        runCatching {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (mode == StatusBarMode.HIDDEN) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    private fun refreshInstalledApps() {
        runCatching {
            val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            val userManager = getSystemService(USER_SERVICE) as UserManager
            val showSelf = StorageManager.getShowSelfInDrawer(this)
            val result = mutableListOf<AppInfo>()
            val seen = mutableSetOf<String>()

            for (profile in userManager.userProfiles) {
                val activities = runCatching {
                    launcherApps.getActivityList(null, profile)
                }.getOrDefault(emptyList())

                for (info in activities) {
                    val pkg = info.applicationInfo.packageName
                    if (!showSelf && pkg == packageName) continue
                    val key = "$pkg:${profile.hashCode()}"
                    if (!seen.add(key)) continue

                    val isArchived = if (Build.VERSION.SDK_INT >= 35) {
                        runCatching { info.applicationInfo.isArchived }.getOrDefault(false)
                    } else {
                        false
                    }

                    result.add(
                        AppInfo(
                            label = info.label?.toString() ?: pkg,
                            packageName = pkg,
                            icon = info.getBadgedIcon(0),
                            componentName = info.componentName,
                            userHandle = profile,
                            isArchived = isArchived
                        )
                    )
                }
            }

            if (showSelf && result.none { it.packageName == packageName }) {
                runCatching {
                    val selfInfo = packageManager.getApplicationInfo(packageName, 0)
                    result.add(
                        AppInfo(
                            label = packageManager.getApplicationLabel(selfInfo).toString(),
                            packageName = packageName,
                            icon = packageManager.getApplicationIcon(selfInfo)
                        )
                    )
                }
            }

            installedApps.clear()
            installedApps.addAll(result.sortedBy { it.label.lowercase() })
        }
    }

    private fun launchSystemWidgetPicker() {
        runCatching {
            val widgetId = appWidgetHost.allocateAppWidgetId()
            pendingWidgetId = widgetId
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            pickWidgetLauncher.launch(pickIntent)
        }.onFailure {
            Toast.makeText(this, "Selettore widget non disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun configureOrAddWidget(widgetId: Int) {
        val info = runCatching { appWidgetManager.getAppWidgetInfo(widgetId) }.getOrNull()
        if (info != null && info.configure != null) {
            pendingWidgetId = widgetId
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            configureWidgetLauncher.launch(configIntent)
        } else {
            addWidgetItemToCanvas(widgetId)
        }
    }

    private fun addWidgetItemToCanvas(widgetId: Int) {
        val info = runCatching { appWidgetManager.getAppWidgetInfo(widgetId) }.getOrNull()
        val label = info?.loadLabel(packageManager) ?: "Widget"
        val wDp = (info?.minWidth?.toFloat()?.div(resources.displayMetrics.density) ?: 220f).coerceIn(140f, 340f)
        val hDp = (info?.minHeight?.toFloat()?.div(resources.displayMetrics.density) ?: 140f).coerceIn(100f, 320f)

        canvasItems.add(
            CanvasItem(
                id = UUID.randomUUID().toString(),
                title = label,
                appWidgetId = widgetId,
                x = 120f,
                y = 220f,
                widthDp = wDp,
                heightDp = hDp,
                type = ItemType.SYSTEM_WIDGET
            )
        )
        StorageManager.saveItems(this, canvasItems)
    }

    private fun launchSystemShortcutPicker() {
        runCatching {
            pickShortcutLauncher.launch(Intent(Intent.ACTION_CREATE_SHORTCUT))
        }.onFailure {
            Toast.makeText(this, "Nessuna scorciatoia disponibile", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val type = intent.type ?: ""
            when {
                type.startsWith("text/") -> {
                    incomingTextState = intent.getStringExtra(Intent.EXTRA_TEXT)
                }
                type.startsWith("image/") -> {
                    @Suppress("DEPRECATION")
                    incomingImageUriState = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                type.startsWith("audio/") -> {
                    @Suppress("DEPRECATION")
                    incomingAudioUriState = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
        }
    }
}
