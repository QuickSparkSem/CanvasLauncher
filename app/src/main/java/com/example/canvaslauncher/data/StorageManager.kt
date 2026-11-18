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

    fun saveItems(context: Context, items: List<CanvasItem>) {
        val jsonArray = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("content", item.content)
                put("packageName", item.packageName ?: "")
                put("imagePath", item.imagePath ?: "")
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
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null) ?: return emptyList()

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
                        x = obj.getDouble("x").toFloat(),
                        y = obj.getDouble("y").toFloat(),
                        widthDp = obj.optDouble("widthDp", 180.0).toFloat(),
                        heightDp = obj.optDouble("heightDp", 130.0).toFloat(),
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
