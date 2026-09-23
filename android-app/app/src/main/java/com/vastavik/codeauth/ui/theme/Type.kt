package com.vastavik.codeauth.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val AppTypography = Typography(
    displayLarge = TextStyle(FontFamily.Default, FontWeight.Bold, 32.sp, 40.sp),
    headlineMedium = TextStyle(FontFamily.Default, FontWeight.SemiBold, 24.sp, 32.sp),
    titleLarge = TextStyle(FontFamily.Default, FontWeight.SemiBold, 20.sp, 28.sp),
    titleMedium = TextStyle(FontFamily.Default, FontWeight.Medium, 16.sp, 24.sp),
    bodyLarge = TextStyle(FontFamily.Default, FontWeight.Normal, 16.sp, 24.sp),
    bodyMedium = TextStyle(FontFamily.Default, FontWeight.Normal, 14.sp, 20.sp),
    bodySmall = TextStyle(FontFamily.Default, FontWeight.Normal, 12.sp, 16.sp),
    labelLarge = TextStyle(FontFamily.Default, FontWeight.Medium, 14.sp, 20.sp)
)
