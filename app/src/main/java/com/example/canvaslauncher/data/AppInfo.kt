package com.example.canvaslauncher.data

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.UserHandle

data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val colorCategory: AppColorCategory = AppColorCategory.BLUE,
    val isArchived: Boolean = false,
    val componentName: ComponentName? = null,
    val userHandle: UserHandle? = null
)
