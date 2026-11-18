package com.example.canvaslauncher

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.example.canvaslauncher.data.AppInfo
import com.example.canvaslauncher.data.StorageManager
import com.example.canvaslauncher.model.CanvasItem
import com.example.canvaslauncher.ui.CanvasLauncherApp

class MainActivity : ComponentActivity() {

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

        canvasItems.clear()
        canvasItems.addAll(StorageManager.loadItems(this))
        loadInstalledApps()
        extractShareIntent(intent)

        setContent {
            CanvasLauncherApp(
                items = canvasItems,
                appList = appList,
                incomingText = incomingText,
                incomingImageUri = incomingImageUri,
                onIncomingConsumed = {
                    incomingText = null
                    incomingImageUri = null
                }
            )
        }
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

    private fun loadInstalledApps() {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolvedInfos = packageManager.queryIntentActivities(mainIntent, 0)

        appList.clear()
        for (info in resolvedInfos) {
            val label = info.loadLabel(packageManager).toString()
            val packageName = info.activityInfo.packageName
            if (packageName != this.packageName) {
                val icon = info.loadIcon(packageManager)
                appList.add(AppInfo(label, packageName, icon))
            }
        }
        appList.sortBy { it.label.lowercase() }
    }
}
