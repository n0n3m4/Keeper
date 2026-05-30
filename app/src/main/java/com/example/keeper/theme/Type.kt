package com.example.keeper.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto (FontFamily.Default on Android) at Google Keep's text sizes, following
// the Material 3 type scale. Titles use Medium (500) — the weight Keep gives a
// note title — and sit one step larger than the body they head, so a title reads
// as clearly bolder *and* bigger than its note text, both on a tile and in the
// editor (Material titleMedium 16sp over bodyMedium 14sp; titleLarge 22sp over
// bodyLarge 16sp).
val Typography = Typography(
    titleLarge = TextStyle(            // note editor title
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 28.sp,
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
