package com.omniforge.app.core.model

import androidx.compose.ui.graphics.Color
import com.omniforge.app.core.theme.AccentImage
import com.omniforge.app.core.theme.AccentPdf
import com.omniforge.app.core.theme.AccentUtility
import com.omniforge.app.core.theme.AccentVideo
import java.io.File

enum class ToolCategory(val displayName: String, val color: Color) {
    PDF("PDF Tools", AccentPdf),
    VIDEO("Video & Audio", AccentVideo),
    IMAGE("Photo & Image", AccentImage),
    UTILITIES("Quick Utilities", AccentUtility)
}

data class ToolItem(
    val id: String,
    val title: String,
    val description: String,
    val category: ToolCategory,
    val route: String,
    val badge: String? = null
)

sealed interface ProcessState {
    data object Idle : ProcessState
    data class Processing(val progress: Float = 0f, val message: String = "Processing...") : ProcessState
    data class Success(val outputFile: File, val message: String = "Operation completed successfully!") : ProcessState
    data class Error(val error: String) : ProcessState
}
