package com.willendary.chaveirocar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Claro = lightColorScheme(
  primary = Jade, onPrimary = Color.White,
  primaryContainer = Color(0xFFD4F5EA), onPrimaryContainer = Color(0xFF064F42),
  secondary = Tinta, onSecondary = Color.White,
  secondaryContainer = Color(0xFFE4EDF3), onSecondaryContainer = Tinta,
  tertiary = Color(0xFF856000), onTertiary = Color.White,
  tertiaryContainer = Color(0xFFFFEFC8), onTertiaryContainer = Color(0xFF614500),
  background = FundoClaro, onBackground = Tinta,
  surface = Color.White, onSurface = Tinta,
  surfaceVariant = Color(0xFFEAF0F3), onSurfaceVariant = Color(0xFF526573),
  surfaceDim = Color(0xFFE2E9ED), surfaceBright = Color.White,
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
  surfaceContainer = Color(0xFFF0F4F6), surfaceContainerHigh = Color(0xFFEAF0F3),
  surfaceContainerHighest = Color(0xFFE3EBEF),
  outline = Color(0xFF71838E), outlineVariant = Color(0xFFDCE5EA),
  error = Color(0xFFB32632), onError = Color.White,
  errorContainer = Color(0xFFFFDAD9), onErrorContainer = Color(0xFF74141D),
)
private val Escuro = darkColorScheme(
  primary = Menta, onPrimary = Color(0xFF00382D),
  primaryContainer = Color(0xFF124D41), onPrimaryContainer = Color(0xFFBDF5E2),
  secondary = Color(0xFFB2CCDD), onSecondary = Tinta,
  secondaryContainer = Color(0xFF263E4E), onSecondaryContainer = Color(0xFFD9E9F3),
  tertiary = Color(0xFFECC66A), onTertiary = Color(0xFF402E00),
  tertiaryContainer = Color(0xFF4D3B14), onTertiaryContainer = Color(0xFFFFE6A7),
  background = FundoEscuro, onBackground = Color(0xFFE4EDF3),
  surface = Color(0xFF152630), onSurface = Color(0xFFE4EDF3),
  surfaceVariant = Color(0xFF213541), onSurfaceVariant = Color(0xFFAFBFCA),
  surfaceDim = FundoEscuro, surfaceBright = Color(0xFF2B414E),
  surfaceContainerLowest = FundoEscuro, surfaceContainerLow = Color(0xFF152630),
  surfaceContainer = Color(0xFF1B2E39), surfaceContainerHigh = Color(0xFF213541),
  surfaceContainerHighest = Color(0xFF2B414E),
  outline = Color(0xFF8396A3), outlineVariant = Color(0xFF304652),
  error = Color(0xFFFFB3B5), onError = Color(0xFF660D1C),
  errorContainer = Color(0xFF7E2430), onErrorContainer = Color(0xFFFFDAD9),
)

@Composable
fun ChaveiroCarTheme(escuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (escuro) Escuro else Claro,
    typography = Tipografia,
    shapes = Shapes(
      extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
      medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp),
      extraLarge = RoundedCornerShape(28.dp),
    ),
    content = content,
  )
}
