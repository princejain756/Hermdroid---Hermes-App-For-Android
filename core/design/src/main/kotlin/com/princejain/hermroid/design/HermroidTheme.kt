package com.princejain.hermroid.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary=Color(0xFF161616), onPrimary=Color.White, background=Color(0xFFFAFAFC), surface=Color(0xFFF2F1F5), onSurface=Color(0xFF171719), secondary=Color(0xFFC58B12))
private val Dark = darkColorScheme(primary=Color(0xFFF6F6F7), onPrimary=Color(0xFF151517), background=Color(0xFF101012), surface=Color(0xFF202024), onSurface=Color(0xFFF3F3F5), secondary=Color(0xFFFFC857))
@Composable fun HermroidTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme=if (isSystemInDarkTheme()) Dark else Light, content=content) }
