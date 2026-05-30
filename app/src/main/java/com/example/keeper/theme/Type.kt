package com.example.keeper.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Roboto (FontFamily.Default on Android) at Google Keep's text sizes, following
// the Material 3 type scale. A title sits one step larger than the body it heads
// so it reads as clearly bigger. Weights match Keep: the compact tile title is
// SemiBold (600) so it stays emphatic at 16sp, while the larger editor title
// carries enough weight at Medium (500). (minSdk 31 ships Roboto as a variable
// font, so 600 renders as a true SemiBold rather than rounding to Bold.)
val Typography = Typography(
    titleLarge = TextStyle(            // note editor title
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 24.sp, lineHeight = 32.sp,
    ),
    titleMedium = TextStyle(           // note tile title
        fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,
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
