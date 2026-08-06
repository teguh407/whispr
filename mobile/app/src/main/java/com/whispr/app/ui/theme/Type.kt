package com.whispr.app.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

val Typography = Typography(
    displayLarge = FontFamily.Default,
    headlineLarge = FontFamily.Default.copy(fontWeight = FontWeight.Bold),
    headlineMedium = FontFamily.Default.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = FontFamily.Default.copy(fontWeight = FontWeight.Medium),
    titleLarge = FontFamily.Default.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = FontFamily.Default.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = FontFamily.Default.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = FontFamily.Default.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = FontFamily.Default.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = FontFamily.Default.copy(fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = FontFamily.Default.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = FontFamily.Default.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = FontFamily.Default.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp)
)
