package com.remora.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.ui.graphics.vector.ImageVector
import com.remora.core.model.entity.ContentType

/** Human-readable Vietnamese label instead of the raw enum name ("THEORY"). */
fun ContentType.label(): String = when (this) {
    ContentType.THEORY -> "Lý thuyết"
    ContentType.PROBLEM -> "Bài tập"
    ContentType.SOLUTION -> "Bài giải"
    ContentType.CODE -> "Code"
    ContentType.NOTES -> "Ghi chú"
    ContentType.UNKNOWN -> "Khác"
}

fun ContentType.icon(): ImageVector = when (this) {
    ContentType.THEORY -> Icons.Outlined.MenuBook
    ContentType.PROBLEM -> Icons.Outlined.Quiz
    ContentType.SOLUTION -> Icons.Outlined.TaskAlt
    ContentType.CODE -> Icons.Outlined.Code
    ContentType.NOTES -> Icons.Outlined.StickyNote2
    ContentType.UNKNOWN -> Icons.Outlined.Description
}
