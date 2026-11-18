package com.example.canvaslauncher.data

import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.toBitmap

enum class AppColorCategory(val label: String, val swatchHex: Long) {
    ALL("Tutti", 0xFFFFFFFF),
    RED("Rosso", 0xFFEF4444),
    ORANGE("Arancione", 0xFFF97316),
    YELLOW("Giallo", 0xFFEAB308),
    GREEN("Verde", 0xFF22C55E),
    CYAN("Ciano", 0xFF06B6D4),
    BLUE("Blu", 0xFF3B82F6),
    PURPLE("Viola", 0xFFA855F7),
    PINK("Rosa", 0xFFEC4899),
    LIGHT("Chiari", 0xFFCBD5E1),
    DARK("Scuri", 0xFF334155)
}

object AppColorClassifier {

    fun classifyDrawable(drawable: Drawable): AppColorCategory {
        return try {
            val bmp = drawable.toBitmap(width = 32, height = 32)
            val buckets = mutableMapOf<AppColorCategory, Float>()
            val hsv = FloatArray(3)

            for (x in 0 until bmp.width) {
                for (y in 0 until bmp.height) {
                    val pixel = bmp.getPixel(x, y)
                    val alpha = Color.alpha(pixel)
                    if (alpha < 140) continue

                    Color.colorToHSV(pixel, hsv)
                    val hue = hsv[0]
                    val sat = hsv[1]
                    val valBright = hsv[2]

                    val category = when {
                        valBright < 0.22f -> AppColorCategory.DARK
                        sat < 0.18f && valBright >= 0.72f -> AppColorCategory.LIGHT
                        sat < 0.18f -> AppColorCategory.DARK
                        hue < 16f || hue >= 342f -> AppColorCategory.RED
                        hue < 42f -> AppColorCategory.ORANGE
                        hue < 68f -> AppColorCategory.YELLOW
                        hue < 162f -> AppColorCategory.GREEN
                        hue < 198f -> AppColorCategory.CYAN
                        hue < 258f -> AppColorCategory.BLUE
                        hue < 305f -> AppColorCategory.PURPLE
                        else -> AppColorCategory.PINK
                    }

                    // Diamo maggior peso ai colori saturi rispetto agli sfondi neutri delle icone
                    val weight = if (sat > 0.35f) 2.5f else 0.7f
                    buckets[category] = (buckets[category] ?: 0f) + weight
                }
            }

            buckets.maxByOrNull { it.value }?.key ?: AppColorCategory.BLUE
        } catch (_: Exception) {
            AppColorCategory.BLUE
        }
    }
}
