package com.willendary.chaveirocar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Claro = lightColorScheme(
  primary = Jade, onPrimary = Color.White,
  primaryContainer = Color(0xFFCCFBF1), onPrimaryContainer = Color(0xFF115E59),
  secondary = Navy, onSecondary = Color.White,
  secondaryContainer = Color(0xFFF1F5F9), onSecondaryContainer = Navy,
  tertiary = JadeEscuro, onTertiary = Color.White,
  tertiaryContainer = Color(0xFFE2E8F0), onTertiaryContainer = Navy,
  background = FundoClaro, onBackground = Navy,
  surface = Color.White, onSurface = Navy,
  surfaceVariant = Color(0xFFF1F5F9), onSurfaceVariant = TextoSecundarioClaro,
  surfaceDim = Color(0xFFE2E8F0), surfaceBright = Color.White,
  surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
  surfaceContainer = Color(0xFFF8FAFC), surfaceContainerHigh = Color(0xFFF1F5F9),
  surfaceContainerHighest = Color(0xFFE2E8F0),
  outline = Color(0xFF94A3B8), outlineVariant = BordaClara,
  error = Color(0xFFDC2626), onError = Color.White,
  errorContainer = Color(0xFFFEE2E2), onErrorContainer = Color(0xFF991B1B),
)
private val Escuro = darkColorScheme(
  primary = JadeVibrante, onPrimary = Color(0xFF042F2E),
  primaryContainer = Color(0xFF134E4A), onPrimaryContainer = Color(0xFFCCFBF1),
  secondary = Color(0xFFCBD5E1), onSecondary = Navy,
  secondaryContainer = Color(0xFF1E293B), onSecondaryContainer = Color(0xFFF8FAFC),
  tertiary = Jade, onTertiary = Color.White,
  tertiaryContainer = Color(0xFF1E293B), onTertiaryContainer = Color(0xFFF1F5F9),
  background = FundoEscuro, onBackground = Color(0xFFF8FAFC),
  surface = Color(0xFF0F172A), onSurface = Color(0xFFF8FAFC),
  surfaceVariant = Color(0xFF1E293B), onSurfaceVariant = TextoSecundarioEscuro,
  surfaceDim = FundoEscuro, surfaceBright = Color(0xFF334155),
  surfaceContainerLowest = FundoEscuro, surfaceContainerLow = Color(0xFF0F172A),
  surfaceContainer = Color(0xFF1E293B), surfaceContainerHigh = Color(0xFF334155),
  surfaceContainerHighest = Color(0xFF475569),
  outline = Color(0xFF64748B), outlineVariant = BordaEscura,
  error = Color(0xFFF87171), onError = Color(0xFF450A0A),
  errorContainer = Color(0xFF7F1D1D), onErrorContainer = Color(0xFFFEE2E2),
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
