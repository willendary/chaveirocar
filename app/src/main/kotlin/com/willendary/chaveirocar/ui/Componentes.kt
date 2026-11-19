package com.willendary.chaveirocar.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.ui.theme.coresEstendidas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class TomSelo {
  PRIMARIO,
  SUCESSO,
  PENDENTE,
  RESIDENCIAL,
  NEUTRO,
  ERRO,
}

/** Carrega uma foto do armazenamento do app em segundo plano. */
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
        .clip(RoundedCornerShape(14.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
    )
  }
}

/** Campo de texto com sugestões do próprio histórico, mostradas enquanto o campo está em foco. */
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
      placeholder = dica?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)) } },
      singleLine = linhaUnica,
      minLines = minimoLinhas,
      keyboardOptions = teclado,
      textStyle = estiloTexto,
      shape = RoundedCornerShape(14.dp),
      colors = OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = MaterialTheme.coresEstendidas.superficieSutil,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
      ),
    )
    if (visiveis.isNotEmpty()) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp),
      ) {
        visiveis.forEach { sugestao ->
          SuggestionChip(
            onClick = { aoMudar(sugestao) },
            shape = RoundedCornerShape(10.dp),
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
fun Selo(
  texto: String,
  modificador: Modifier = Modifier,
  pendente: Boolean = false,
  tom: TomSelo? = null,
) {
  val estendidas = MaterialTheme.coresEstendidas
  val tomFinal = tom ?: if (pendente) TomSelo.PENDENTE else TomSelo.PRIMARIO
  val (fundo, corTexto) = when (tomFinal) {
    TomSelo.PRIMARIO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    TomSelo.SUCESSO -> estendidas.sucessoFundo to estendidas.sucesso
    TomSelo.PENDENTE -> estendidas.alertaFundo to estendidas.alerta
    TomSelo.RESIDENCIAL -> estendidas.residencialFundo to estendidas.residencial
    TomSelo.NEUTRO -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    TomSelo.ERRO -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
  }
  Surface(
    shape = RoundedCornerShape(999.dp),
    color = fundo,
    border = BorderStroke(0.75.dp, corTexto.copy(alpha = 0.16f)),
    modifier = modificador,
  ) {
    Text(
      text = texto,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Bold,
      color = corTexto,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
fun TituloSecao(
  texto: String,
  modificador: Modifier = Modifier,
  detalhe: String? = null,
) {
  Row(
    modifier = modificador.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier = Modifier
        .width(4.dp)
        .height(18.dp)
        .clip(RoundedCornerShape(99.dp))
        .background(MaterialTheme.colorScheme.primary),
    )
    Text(
      text = texto,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface,
      modifier = Modifier.weight(1f),
    )
    if (!detalhe.isNullOrBlank()) {
      Text(
        text = detalhe,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Copiar código evita erro de digitação na hora de passar para a máquina. */
fun copiarTexto(contexto: Context, rotulo: String, texto: String) {
  if (texto.isBlank()) return
  val gerenciador = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  gerenciador.setPrimaryClip(ClipData.newPlainText(rotulo, texto))
  Toast.makeText(contexto, "$rotulo copiado", Toast.LENGTH_SHORT).show()
}
