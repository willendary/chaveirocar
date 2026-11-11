package com.willendary.chaveirocar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    title = { Text("Novidades da versão $versao") },
    text = {
      Column(
        modifier = Modifier
          .heightIn(max = 360.dp)
          .verticalScroll(rememberScrollState()),
      ) {
        when {
          carregando -> {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
          }

          notas.isNullOrBlank() -> Text(
            text = "Atualizado para a versão $versao. Tudo pronto para continuar o serviço.",
            style = MaterialTheme.typography.bodyMedium,
          )

          else -> Text(text = notas, style = MaterialTheme.typography.bodyMedium)
        }
      }
    },
    confirmButton = { TextButton(onClick = onFechar) { Text("Fechar") } },
  )
}
