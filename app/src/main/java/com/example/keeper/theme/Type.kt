package com.example.keeper.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto (FontFamily.Default on Android) at Google Keep's actual text sizes.
val Typography = Typography(
    titleLarge = TextStyle(            // note editor title
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 20.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(           // note tile title
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
    labelMedium = TextStyle(           // chips (reminder / label)
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
)
