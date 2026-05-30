package com.example.keeper.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto (FontFamily.Default on Android) at Google Keep's text sizes, following
// the Material 3 type scale. A title sits one step larger than the body it heads
// so it reads as clearly bigger, and both titles use the scale's Medium (500)
// weight — emphasis comes from weight, not tracking. (The scale's letter spacing
// is sub-0.5sp everywhere, e.g. 0.15sp on Title Medium: imperceptible, so it is
// left at the default and SemiBold was dropped as too heavy.)
val Typography = Typography(
    titleLarge = TextStyle(            // note editor title
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 32.sp,
    ),
    titleMedium = TextStyle(           // note tile title
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(            // link preview chip title
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(             // note editor body / list items
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(            // note tile body / checklist preview
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(             // link domain / "+N more"
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(           // chips (reminder / label)
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
)
