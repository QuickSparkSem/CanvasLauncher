package com.example.canvaslauncher.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.example.canvaslauncher.model.CanvasItem
import com.example.canvaslauncher.model.ItemType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class StatusBarMode(val label: String) {
    TRANSPARENT("Trasparente"),
    HIDDEN("Nascosta")
}

data class CanvasWaypoint(
    val id: String,
    val name: String,
    val offsetX: Float,
    val offsetY: Float,
    val zoomScale: Float
)

data class WebQuickPreset(
    val id: String,
    val name: String,
    val url: String,
    val browserPackage: String? = null
)

object StorageManager {
    private const val PREFS_NAME = "canvas_launcher_prefs"
    private const val KEY_ITEMS = "saved_canvas_items"
    private const val KEY_BG_ALPHA = "canvas_bg_alpha"
    private const val KEY_DRAWER_ALPHA = "drawer_bg_alpha"
    private const val KEY_DEFAULT_COLOR = "default_element_color"
    private const val KEY_STATUS_BAR_MODE = "status_bar_mode"
    private const val KEY_SHOW_SELF_IN_DRAWER = "show_self_in_drawer"
    private const val KEY_DRAWER_HAPTIC_STRENGTH = "drawer_haptic_strength"
    private const val KEY_WAYPOINTS = "saved_canvas_waypoints"
    private const val KEY_WEB_PRESETS = "saved_web_quick_presets"
    private const val KEY_WEB_PRESETS_SEEDED = "web_presets_seeded_v1"
    private const val KEY_GUIDE_SEEDED = "has_seeded_guide_v4"

    fun saveItems(context: Context, items: List<CanvasItem>) {
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("content", item.content)
                put("packageName", item.packageName ?: "")
                put("imagePath", item.imagePath ?: "")
                put("audioPath", item.audioPath ?: "")
                put("fileUri", item.fileUri ?: "")
                put("fileMimeType", item.fileMimeType ?: "")
                put("url", item.url ?: "")
                put("browserPackage", item.browserPackage ?: "")
                put("adBlockEnabled", item.adBlockEnabled)
                put("lastSnapshotPath", item.lastSnapshotPath ?: "")
                put("imeFallbackDisplay", item.imeFallbackDisplay)
                put("appWidgetId", item.appWidgetId)
                put("shortcutAction", item.shortcutAction ?: "")
                put("shortcutIntentUri", item.shortcutIntentUri ?: "")
                put("x", item.x.toDouble())
                put("y", item.y.toDouble())
                put("widthDp", item.widthDp.toDouble())
                put("heightDp", item.heightDp.toDouble())
                put("colorHex", item.colorHex)
                put("type", item.type.name)

                val pkgArray = JSONArray()
                item.sessionPackages.forEach { pkgArray.put(it) }
                put("sessionPackages", pkgArray)
            }
            jsonArray.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, jsonArray.toString())
            .apply()
    }

    fun loadItems(context: Context): List<CanvasItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ITEMS, null)

        if (raw == null && !prefs.getBoolean(KEY_GUIDE_SEEDED, false)) {
            val initialGuide = listOf(createDefaultGuideNote())
            prefs.edit().putBoolean(KEY_GUIDE_SEEDED, true).apply()
            saveItems(context, initialGuide)
            return initialGuide
        }

        if (raw == null) return emptyList()

        val result = mutableListOf<CanvasItem>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val pkgList = mutableListOf<String>()
                val pkgArray = obj.optJSONArray("sessionPackages")
                if (pkgArray != null) {
                    for (j in 0 until pkgArray.length()) {
                        pkgList.add(pkgArray.getString(j))
                    }
                }
                val typeName = obj.optString("type", ItemType.NOTE.name)
                val itemType = try {
                    ItemType.valueOf(typeName)
                } catch (_: Exception) {
                    ItemType.NOTE
                }
                result.add(
                    CanvasItem(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        content = obj.optString("content", ""),
                        packageName = obj.optString("packageName").takeIf { it.isNotEmpty() },
                        imagePath = obj.optString("imagePath").takeIf { it.isNotEmpty() },
                        audioPath = obj.optString("audioPath").takeIf { it.isNotEmpty() },
                        fileUri = obj.optString("fileUri").takeIf { it.isNotEmpty() },
                        fileMimeType = obj.optString("fileMimeType").takeIf { it.isNotEmpty() },
                        url = obj.optString("url").takeIf { it.isNotEmpty() },
                        browserPackage = obj.optString("browserPackage").takeIf { it.isNotEmpty() },
                        adBlockEnabled = obj.optBoolean("adBlockEnabled", true),
                        lastSnapshotPath = obj.optString("lastSnapshotPath").takeIf { it.isNotEmpty() },
                        imeFallbackDisplay = obj.optBoolean("imeFallbackDisplay", true),
                        appWidgetId = obj.optInt("appWidgetId", -1),
                        shortcutAction = obj.optString("shortcutAction").takeIf { it.isNotEmpty() },
                        shortcutIntentUri = obj.optString("shortcutIntentUri").takeIf { it.isNotEmpty() },
                        x = obj.getDouble("x").toFloat(),
                        y = obj.getDouble("y").toFloat(),
                        widthDp = obj.optDouble("widthDp", 140.0).toFloat(),
                        heightDp = obj.optDouble("heightDp", 120.0).toFloat(),
                        colorHex = obj.optLong("colorHex", 0x00000000L),
                        type = itemType,
                        sessionPackages = pkgList
                    )
                )
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return result
    }

    // --- COORDINATE SALVATE (WAYPOINTS) ---
    fun loadWaypoints(context: Context): List<CanvasWaypoint> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WAYPOINTS, null) ?: return emptyList()
        val list = mutableListOf<CanvasWaypoint>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    CanvasWaypoint(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        offsetX = o.getDouble("offsetX").toFloat(),
                        offsetY = o.getDouble("offsetY").toFloat(),
                        zoomScale = o.optDouble("zoomScale", 1.0).toFloat()
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun saveWaypoints(context: Context, waypoints: List<CanvasWaypoint>) {
        val arr = JSONArray()
        for (wp in waypoints) {
            arr.put(
                JSONObject().apply {
                    put("id", wp.id)
                    put("name", wp.name)
                    put("offsetX", wp.offsetX.toDouble())
                    put("offsetY", wp.offsetY.toDouble())
                    put("zoomScale", wp.zoomScale.toDouble())
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_WAYPOINTS, arr.toString()).apply()
    }

    // --- OPZIONI RAPIDE WEB APP PERSONALIZZABILI ---
    fun loadWebQuickPresets(context: Context): List<WebQuickPreset> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_WEB_PRESETS, null)
        if (raw == null && !prefs.getBoolean(KEY_WEB_PRESETS_SEEDED, false)) {
            val defaults = listOf(
                WebQuickPreset(UUID.randomUUID().toString(), "YouTube", "https://m.youtube.com"),
                WebQuickPreset(UUID.randomUUID().toString(), "ChatGPT", "https://chatgpt.com"),
                WebQuickPreset(UUID.randomUUID().toString(), "Google Docs", "https://docs.google.com"),
                WebQuickPreset(UUID.randomUUID().toString(), "Wikipedia", "https://it.m.wikipedia.org"),
                WebQuickPreset(UUID.randomUUID().toString(), "Google", "https://www.google.com")
            )
            prefs.edit().putBoolean(KEY_WEB_PRESETS_SEEDED, true).apply()
            saveWebQuickPresets(context, defaults)
            return defaults
        }
        if (raw == null) return emptyList()
        val list = mutableListOf<WebQuickPreset>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    WebQuickPreset(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        url = o.getString("url"),
                        browserPackage = o.optString("browserPackage").takeIf { it.isNotEmpty() }
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    fun saveWebQuickPresets(context: Context, presets: List<WebQuickPreset>) {
        val arr = JSONArray()
        for (p in presets) {
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("url", p.url)
                    put("browserPackage", p.browserPackage ?: "")
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_WEB_PRESETS, arr.toString()).apply()
    }

    fun createDefaultGuideNote(x: Float = 55f, y: Float = 135f): CanvasItem {
        return CanvasItem(
            id = "guide_note_${UUID.randomUUID()}",
            title = "Guida Desktop Spaziale",
            content = """
                • NAVIGAZIONE LIBERA: Scorri liberamente dentro le Web App e le App; trascina la barra titolo per spostare la finestra oppure usa 'Usa' per bloccare la tela.
                • COORDINATE & SEGNALIBRI: Tocca il mirino a sinistra per tornare a (0,0), oppure tienilo premuto per salvare la posizione corrente e saltare tra i luoghi salvati.
                • WEB APP & BROWSER: Usa il motore interno con AdBlock stile Brave oppure scegli un browser installato sul telefono.
                • RIDIMENSIONA TUTTO: Tieni premuto su qualsiasi elemento o finestra e scegli 'Ridimensiona'.
            """.trimIndent(),
            x = x,
            y = y,
            widthDp = 290f,
            heightDp = 260f,
            colorHex = 0x00000000L,
            type = ItemType.NOTE
        )
    }

    fun getBackgroundAlpha(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_BG_ALPHA, 0.72f)

    fun setBackgroundAlpha(context: Context, alpha: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_BG_ALPHA, alpha.coerceIn(0f, 1f)).apply()
    }

    fun getDrawerAlpha(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DRAWER_ALPHA, 0.86f)

    fun setDrawerAlpha(context: Context, alpha: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_DRAWER_ALPHA, alpha.coerceIn(0.2f, 1f)).apply()
    }

    fun getDrawerHapticStrength(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DRAWER_HAPTIC_STRENGTH, 0.55f)

    fun setDrawerHapticStrength(context: Context, strength: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_DRAWER_HAPTIC_STRENGTH, strength.coerceIn(0f, 1f)).apply()
    }

    fun getDefaultElementColor(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DEFAULT_COLOR, 0x00000000L)

    fun setDefaultElementColor(context: Context, colorHex: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_DEFAULT_COLOR, colorHex).apply()
    }

    fun getStatusBarMode(context: Context): StatusBarMode {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_STATUS_BAR_MODE, StatusBarMode.TRANSPARENT.name)
        return try {
            StatusBarMode.valueOf(name ?: StatusBarMode.TRANSPARENT.name)
        } catch (_: Exception) {
            StatusBarMode.TRANSPARENT
        }
    }

    fun setStatusBarMode(context: Context, mode: StatusBarMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_STATUS_BAR_MODE, mode.name).apply()
    }

    fun getShowSelfInDrawer(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_SELF_IN_DRAWER, false)

    fun setShowSelfInDrawer(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_SELF_IN_DRAWER, show).apply()
    }

    fun saveWindowSnapshotBitmap(context: Context, itemId: String, bitmap: Bitmap): String? {
        return try {
            val dir = File(context.filesDir, "snapshots")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "win_$itemId.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun copyUriToInternalStorage(
        context: Context,
        uri: Uri,
        subDir: String = "snapshots",
        extension: String = "jpg"
    ): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val dir = File(context.filesDir, subDir)
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "media_${UUID.randomUUID()}.$extension")
            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun getDisplayNameFromUri(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    val name = cursor.getString(nameIndex)
                    if (!name.isNullOrBlank()) return name
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment ?: "File collegato"
    }
}
