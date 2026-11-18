package com.example.canvaslauncher.data

import android.content.Context
import android.net.Uri
import com.example.canvaslauncher.model.CanvasItem
import com.example.canvaslauncher.model.ItemType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object StorageManager {
    private const val PREFS_NAME = "canvas_launcher_prefs"
    private const val KEY_ITEMS = "saved_canvas_items"
    private const val KEY_BG_ALPHA = "canvas_bg_alpha"
    private const val KEY_DEFAULT_COLOR = "default_element_color"
    private const val KEY_GUIDE_SEEDED = "has_seeded_guide_v2"

    fun saveItems(context: Context, items: List<CanvasItem>) {
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("content", item.content)
                put("packageName", item.packageName ?: "")
                put("imagePath", item.imagePath ?: "")
                put("appWidgetId", item.appWidgetId)
                put("shortcutAction", item.shortcutAction ?: "")
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
                        appWidgetId = obj.optInt("appWidgetId", -1),
                        shortcutAction = obj.optString("shortcutAction").takeIf { it.isNotEmpty() },
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

    fun createDefaultGuideNote(x: Float = 70f, y: Float = 140f): CanvasItem {
        return CanvasItem(
            id = "guide_note_${UUID.randomUUID()}",
            title = "Guida Canvas Launcher & Roadmap",
            content = """
                BENVENUTO NEL TUO DESKTOP SPAZIALE 2D
                
                • TELA INFINITA: Trascina con un dito per muoverti ovunque (su, giù, destra, sinistra) e usa due dita per lo zoom. Tocca il mirino in basso per tornare al centro.
                
                • SFONDO, WIDGET & IMPOSTAZIONI: Tieni premuto su un punto vuoto dello sfondo per cambiare lo sfondo del telefono, aggiungere Widget di sistema o regolare la trasparenza e i colori.
                
                • CURSORE & LAZO: Usa 'Cursore' per spostare le schede; usa 'Lazo' per cerchiare più elementi e unirli in una Sessione di Lavoro. Con mouse fisico funziona il tasto destro.
                
                • SEZIONE APP INTELLIGENTE: Tocca un'app per aprirla, tienila premuta per fissarla sulla tela. Usa l'indice A-Z a destra o filtra le app per colore in basso (incluse le app archiviate dal sistema).
                
                • CONDIVISIONE RAPIDA: Da browser, galleria o note usa 'Condividi -> Canvas Launcher' per fissare contenuti sulla tela.
                
                FUNZIONI IN ARRIVO (ROADMAP):
                1. Frecce e collegamenti visivi tra note e app.
                2. Cartelle espandibili direttamente sulla tela.
                3. Mini-mappa radar per orientarsi nelle tele grandi.
                4. Backup ed esportazione dell'intera area di lavoro.
            """.trimIndent(),
            x = x,
            y = y,
            widthDp = 295f,
            heightDp = 330f,
            colorHex = 0xFF1E3A5F,
            type = ItemType.NOTE
        )
    }

    fun getBackgroundAlpha(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_BG_ALPHA, 0.85f)
    }

    fun setBackgroundAlpha(context: Context, alpha: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_BG_ALPHA, alpha.coerceIn(0f, 1f))
            .apply()
    }

    fun getDefaultElementColor(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_DEFAULT_COLOR, 0xFF1E222D)
    }

    fun setDefaultElementColor(context: Context, colorHex: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DEFAULT_COLOR, colorHex)
            .apply()
    }

    fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val dir = File(context.filesDir, "snapshots")
            if (!dir.exists()) dir.mkdirs()
            val destFile = File(dir, "snap_${UUID.randomUUID()}.jpg")
            FileOutputStream(destFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            destFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
