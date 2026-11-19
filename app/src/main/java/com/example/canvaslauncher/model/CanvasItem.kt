package com.example.canvaslauncher.model

enum class ItemType(val badgeLabel: String, val accentHex: Long) {
    APP_SHORTCUT("APP", 0xFF38BDF8),
    APP_WINDOW("FINESTRA APP", 0xFF60A5FA),
    WEB_APP("WEB APP", 0xFF34D399),
    NOTE("NOTA", 0xFFFBBF24),
    IMAGE_SNAPSHOT("FOTO", 0xFFA78BFA),
    AUDIO_MEMO("AUDIO", 0xFFF43F5E),
    FILE_LINK("FILE", 0xFF2DD4BF),
    SESSION_GROUP("SESSIONE", 0xFF34D399),
    SYSTEM_WIDGET("WIDGET", 0xFFF472B6),
    SYSTEM_SHORTCUT("SISTEMA", 0xFFFB923C)
}

data class CanvasItem(
    val id: String,
    val title: String,
    val content: String = "",
    val packageName: String? = null,
    val imagePath: String? = null,
    val audioPath: String? = null,
    val fileUri: String? = null,
    val fileMimeType: String? = null,
    val url: String? = null,
    val lastSnapshotPath: String? = null,
    val imeFallbackDisplay: Boolean = true,
    val appWidgetId: Int = -1,
    val shortcutAction: String? = null,
    val shortcutIntentUri: String? = null,
    val x: Float,
    val y: Float,
    val widthDp: Float = 110f,
    val heightDp: Float = 110f,
    val colorHex: Long = 0x00000000L,
    val type: ItemType = ItemType.NOTE,
    val sessionPackages: List<String> = emptyList()
)
