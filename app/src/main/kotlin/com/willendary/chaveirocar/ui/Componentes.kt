package com.willendary.chaveirocar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.dados.Fotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carrega uma foto do armazenamento do app em segundo plano.
 */
@Composable
fun ImagemLocal(
  caminho: String,
  modificador: Modifier = Modifier,
  ladoMaximo: Int = 600,
) {
  val contexto = LocalContext.current
  val modificadoEm = Fotos.arquivo(contexto, caminho).lastModified()
  var bitmap by remember(caminho, modificadoEm) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(caminho, ladoMaximo, modificadoEm) {
    bitmap = withContext(Dispatchers.IO) { Fotos.carregar(contexto, caminho, ladoMaximo)?.asImageBitmap() }
  }
  val atual = bitmap
  if (atual != null) {
    Image(
      bitmap = atual,
      contentDescription = null,
      modifier = modificador,
      contentScale = ContentScale.Crop,
    )
  } else {
    Box(
      modifier = modificador
        .clip(RoundedCornerShape(12.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
    )
  }
}

/**
 * Campo de texto com sugestões do próprio histórico, mostradas enquanto o campo está em foco.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CampoTexto(
  rotulo: String,
  valor: String,
  aoMudar: (String) -> Unit,
  modificador: Modifier = Modifier,
  sugestoes: List<String> = emptyList(),
  dica: String? = null,
  linhaUnica: Boolean = true,
  minimoLinhas: Int = 1,
  teclado: KeyboardOptions = KeyboardOptions.Default,
  estiloTexto: TextStyle = TextStyle.Default,
) {
  var focado by remember { mutableStateOf(false) }
  val visiveis = remember(focado, valor, sugestoes) {
    if (!focado) emptyList()
    else sugestoes.filter { it.isNotBlank() && !it.equals(valor.trim(), ignoreCase = true) }
      .filter { valor.isBlank() || it.contains(valor.trim(), ignoreCase = true) }
      .take(4)
  }

  Column(modifier = modificador) {
    OutlinedTextField(
      value = valor,
      onValueChange = aoMudar,
      modifier = Modifier.fillMaxWidth().onFocusChanged { focado = it.isFocused },
      label = { Text(rotulo) },
      placeholder = dica?.let { { Text(it) } },
      singleLine = linhaUnica,
      minLines = minimoLinhas,
      keyboardOptions = teclado,
      textStyle = estiloTexto,
      shape = MaterialTheme.shapes.medium,
      colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
      ),
    )
    if (visiveis.isNotEmpty()) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        visiveis.forEach { sugestao ->
          SuggestionChip(
            onClick = { aoMudar(sugestao) },
            label = {
              Text(sugestao, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
          )
        }
      }
    }
  }
}

@Composable
fun Selo(texto: String, modificador: Modifier = Modifier, pendente: Boolean = false) {
  Text(
    text = texto,
    style = MaterialTheme.typography.labelSmall,
    color = if (pendente) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
    modifier = modificador
      .clip(RoundedCornerShape(8.dp))
      .background(if (pendente) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer)
      .padding(horizontal = 10.dp, vertical = 5.dp),
  )
}

@Composable
fun TituloSecao(texto: String, modificador: Modifier = Modifier) {
  Text(
    text = texto,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modificador.padding(top = 16.dp, bottom = 6.dp),
  )
}

/**
 * Copiar código evita erro de digitação na hora de passar para a máquina.
 */
fun copiarTexto(contexto: Context, rotulo: String, texto: String) {
  if (texto.isBlank()) return
  val gerenciador = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  gerenciador.setPrimaryClip(ClipData.newPlainText(rotulo, texto))
  Toast.makeText(contexto, "$rotulo copiado", Toast.LENGTH_SHORT).show()
}
