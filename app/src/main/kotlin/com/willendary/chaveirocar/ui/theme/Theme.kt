package com.willendary.chaveirocar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class CoresEstendidas(
  val escuro: Boolean,
  val sucesso: Color,
  val sucessoFundo: Color,
  val alerta: Color,
  val alertaFundo: Color,
  val residencial: Color,
  val residencialFundo: Color,
  val superficieSutil: Color,
  val bordaSuave: Color,
)

private val EstendidasClaras = CoresEstendidas(
  escuro = false,
  sucesso = Esmeralda,
  sucessoFundo = EsmeraldaClaro,
  alerta = Ambar,
  alertaFundo = AmbarClaro,
  residencial = IndigoResidencial,
  residencialFundo = IndigoResidencialClaro,
  superficieSutil = SuperficieElevadaClara,
  bordaSuave = BordaClara,
)

private val EstendidasEscuras = CoresEstendidas(
  escuro = true,
  sucesso = EsmeraldaEscuro,
  sucessoFundo = EsmeraldaFundoEscuro,
  alerta = AmbarEscuro,
  alertaFundo = AmbarFundoEscuro,
  residencial = IndigoResidencialEscuro,
  residencialFundo = IndigoResidencialFundoEscuro,
  superficieSutil = SuperficieElevadaEscura,
  bordaSuave = BordaEscura,
)

val LocalCoresEstendidas = staticCompositionLocalOf { EstendidasClaras }

val MaterialTheme.coresEstendidas: CoresEstendidas
  @Composable get() = LocalCoresEstendidas.current

private val Claro = lightColorScheme(
  primary = Jade,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFD6F8F3),
  onPrimaryContainer = JadeProfundo,
  secondary = Navy,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFE7EEF6),
  onSecondaryContainer = Navy,
  tertiary = Ambar,
  onTertiary = Color.White,
  tertiaryContainer = AmbarClaro,
  onTertiaryContainer = Color(0xFF92400E),
  background = FundoClaro,
  onBackground = Navy,
  surface = SuperficieClara,
  onSurface = Navy,
  surfaceVariant = Color(0xFFEBF0F6),
  onSurfaceVariant = TextoSecundarioClaro,
  surfaceDim = Color(0xFFE2E8F0),
  surfaceBright = Color.White,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = SuperficieElevadaClara,
  surfaceContainer = Color(0xFFF1F5F9),
  surfaceContainerHigh = Color(0xFFE8EEF5),
  surfaceContainerHighest = Color(0xFFDCE4EE),
  outline = Color(0xFF94A3B8),
  outlineVariant = BordaClara,
  error = VermelhoErro,
  onError = Color.White,
  errorContainer = VermelhoClaro,
  onErrorContainer = Color(0xFF991B1B),
)

private val Escuro = darkColorScheme(
  primary = JadeVibrante,
  onPrimary = Color(0xFF042F2E),
  primaryContainer = Color(0xFF113C3B),
  onPrimaryContainer = Color(0xFFCCFBF1),
  secondary = Color(0xFFE2E8F0),
  onSecondary = Navy,
  secondaryContainer = SuperficieElevadaEscura,
  onSecondaryContainer = Color(0xFFF8FAFC),
  tertiary = AmbarEscuro,
  onTertiary = Color(0xFF451A03),
  tertiaryContainer = AmbarFundoEscuro,
  onTertiaryContainer = Color(0xFFFDE68A),
  background = FundoEscuro,
  onBackground = Color(0xFFF8FAFC),
  surface = SuperficieEscura,
  onSurface = Color(0xFFF8FAFC),
  surfaceVariant = SuperficieElevadaEscura,
  onSurfaceVariant = TextoSecundarioEscuro,
  surfaceDim = FundoEscuro,
  surfaceBright = Color(0xFF283754),
  surfaceContainerLowest = FundoEscuro,
  surfaceContainerLow = SuperficieEscura,
  surfaceContainer = SuperficieElevadaEscura,
  surfaceContainerHigh = Color(0xFF22314D),
  surfaceContainerHighest = Color(0xFF334463),
  outline = Color(0xFF64748B),
  outlineVariant = BordaEscura,
  error = Color(0xFFF87171),
  onError = Color(0xFF450A0A),
  errorContainer = Color(0xFF5C1515),
  onErrorContainer = Color(0xFFFEE2E2),
)

@Composable
fun ChaveiroCarTheme(escuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val estendidas = if (escuro) EstendidasEscuras else EstendidasClaras
  CompositionLocalProvider(LocalCoresEstendidas provides estendidas) {
    MaterialTheme(
      colorScheme = if (escuro) Escuro else Claro,
      typography = Tipografia,
      shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(22.dp),
        extraLarge = RoundedCornerShape(28.dp),
      ),
      content = content,
    )
  }
}
