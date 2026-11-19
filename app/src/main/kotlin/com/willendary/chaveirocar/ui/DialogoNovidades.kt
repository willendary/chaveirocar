package com.willendary.chaveirocar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Mostrado uma vez por versão instalada. Sem internet, cai no texto padrão.
 */
@Composable
fun DialogoNovidades(
  versao: String,
  notas: String?,
  carregando: Boolean,
  onFechar: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onFechar,
    shape = RoundedCornerShape(24.dp),
    icon = {
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(RoundedCornerShape(22.dp))
          .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
      ) {
        MarcaChaveiro(54.dp, elevacao = 3.dp)
      }
    },
    title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Selo("Versão $versao", tom = TomSelo.PRIMARIO)
        Text(
          "Novidades do ChaveiroCar",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.ExtraBold,
          textAlign = TextAlign.Center,
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .heightIn(max = 360.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        when {
          carregando -> {
            Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          }
          notas.isNullOrBlank() -> Text(
            text = "Atualizado para a versão $versao. Tudo pronto para continuar o serviço.",
            style = MaterialTheme.typography.bodyMedium,
          )
          else -> Text(text = notas, style = MaterialTheme.typography.bodyMedium)
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onFechar,
        shape = RoundedCornerShape(12.dp),
      ) { Text("Vamos lá", fontWeight = FontWeight.Bold) }
    },
  )
}
