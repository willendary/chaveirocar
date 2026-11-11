package com.willendary.chaveirocar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EsquemaClaro = lightColorScheme(
  primary = AzulMarinho,
  onPrimary = SuperficieClara,
  primaryContainer = AzulClaro,
  onPrimaryContainer = AzulMarinho,
  secondary = Ambar,
  onSecondary = AmbarEscuro,
  secondaryContainer = Color(0xFFFFE9B8),
  onSecondaryContainer = AmbarEscuro,
  tertiary = AzulOficina,
  onTertiary = SuperficieClara,
  background = FundoClaro,
  onBackground = TextoClaro,
  surface = SuperficieClara,
  onSurface = TextoClaro,
  surfaceVariant = Color(0xFFEDF1F5),
  onSurfaceVariant = TextoSuaveClaro,
  outline = ContornoClaro,
  error = ErroClaro,
)

private val EsquemaEscuro = darkColorScheme(
  primary = AzulNoite,
  onPrimary = AzulMarinho,
  primaryContainer = AzulOficina,
  onPrimaryContainer = Color(0xFFE8F2FB),
  secondary = Ambar,
  onSecondary = AmbarEscuro,
  secondaryContainer = Color(0xFF574200),
  onSecondaryContainer = Color(0xFFFFE9B8),
  tertiary = Color(0xFFB9D6E8),
  onTertiary = AzulMarinho,
  background = FundoEscuro,
  onBackground = TextoEscuro,
  surface = SuperficieEscura,
  onSurface = TextoEscuro,
  surfaceVariant = SuperficieEscuraAlta,
  onSurfaceVariant = TextoSuaveEscuro,
  outline = ContornoEscuro,
  error = ErroEscuro,
)

@Composable
fun ChaveiroCarTheme(
  escuro: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (escuro) EsquemaEscuro else EsquemaClaro,
    typography = Tipografia,
    content = content,
  )
}
