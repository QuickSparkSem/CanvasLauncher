package com.example.canvaslauncher.data

import android.content.Context
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

object StorageManager {
    private const val PREFS_NAME = "canvas_launcher_prefs"
    private const val KEY_ITEMS = "saved_canvas_items"
    private const val KEY_BG_ALPHA = "canvas_bg_alpha"
    private const val KEY_DRAWER_ALPHA = "drawer_bg_alpha"
    private const val KEY_DEFAULT_COLOR = "default_element_color"
    private const val KEY_STATUS_BAR_MODE = "status_bar_mode"
    private const val KEY_SHOW_SELF_IN_DRAWER = "show_self_in_drawer"
    private const val KEY_GUIDE_SEEDED = "has_seeded_guide_v3"

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
                        appWidgetId = obj.optInt("appWidgetId", -1),
                        shortcutAction = obj.optString("shortcutAction").takeIf { it.isNotEmpty() },
                        shortcutIntentUri = obj.optString("shortcutIntentUri").takeIf { it.isNotEmpty() },
                        x = obj.getDouble("x").toFloat(),
                        y = obj.getDouble("y").toFloat(),
                        widthDp = obj.optDouble("widthDp", 185.0).toFloat(),
                        heightDp = obj.optDouble("heightDp", 135.0).toFloat(),
                        colorHex = obj.optLong("colorHex", 0xFF1E222D),
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

    fun createDefaultGuideNote(x: Float = 65f, y: Float = 135f): CanvasItem {
        return CanvasItem(
            id = "guide_note_${UUID.randomUUID()}",
            title = "Guida Canvas Launcher & Roadmap",
            content = """
                BENVENUTO NEL TUO DESKTOP SPAZIALE 2D
                
                • TELA INFINITA: Trascina con un dito per muoverti su, giù, destra e sinistra; pizzica con due dita per lo zoom. Tocca il mirino in basso per tornare al centro.
                
                • TIENI PREMUTO SULLO SFONDO: Aggiungi Widget di sistema, Scorciatoie (incluse quelle di Shortcut Maker), Note, Foto (da galleria o scatto al volo), Audio/Memo vocali e File dall'archivio.
                
                • TIENI PREMUTO UN ELEMENTO: Apri il menu dedicato all'elemento per modificarlo, ridimensionarlo trascinando gli angoli o eliminarlo. Se una nota è piccola e ha molto testo, puoi scorrere il testo direttamente dentro la scheda.
                
                • CURSORE & LAZO: Usa 'Cursore' per spostare le schede; usa 'Lazo' per cerchiare più elementi e unirli in una Sessione di Lavoro.
                
                • MENU APP: Tocca un'app per aprirla, tienila premuta per opzioni o selezione multipla. Usa l'indice A-Z laterale o i pallini colore in basso per filtrare le app con onda luminosa.
                
                FUNZIONI IN ARRIVO (ROADMAP):
                1. Frecce e collegamenti visivi tra note, file e app.
                2. Cartelle espandibili direttamente sulla tela.
                3. Mini-mappa radar per orientarsi nelle tele grandi.
                4. Esportazione e backup completo del workspace.
            """.trimIndent(),
            x = x,
            y = y,
            widthDp = 295f,
            heightDp = 310f,
            colorHex = 0xFF1E3A5F,
            type = ItemType.NOTE
        )
    }

    fun getBackgroundAlpha(context: Context): Float =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_BG_ALPHA, 0.85f)

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

    fun getDefaultElementColor(context: Context): Long =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DEFAULT_COLOR, 0xFF1E222D)

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
