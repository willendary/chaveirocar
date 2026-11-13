package com.willendary.chaveirocar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.R
import com.willendary.chaveirocar.ui.theme.JadeVibrante
import com.willendary.chaveirocar.ui.theme.Navy

/** Um único símbolo vetorial na abertura, nas telas e no launcher. */
@Composable
fun MarcaChaveiro(tamanho: Dp = 44.dp) {
  Image(painterResource(R.drawable.marca_chaveiro), contentDescription = null,
    modifier = Modifier.size(tamanho).clip(RoundedCornerShape(tamanho * 0.28f)))
}

// Mantém o conteúdo centralizado e legível em tablets e janelas largas.
fun Modifier.conteudoResponsivo(maximo: Dp = 840.dp): Modifier =
  this.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maximo)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CabecalhoTela(
  titulo: String,
  subtitulo: String,
  sincronizando: Boolean = false,
) {
  TopAppBar(
    title = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MarcaChaveiro()
        Column {
          Text(titulo, style = MaterialTheme.typography.titleLarge)
          Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    },
    actions = {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (sincronizando) Color(0x33FBBF24) else Color(0x1A0D9488),
        modifier = Modifier.padding(end = 12.dp),
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(if (sincronizando) Color(0xFFF59E0B) else Color(0xFF10B981))
          )
          Text(
            text = if (sincronizando) "Sincronizando..." else "Nuvem ativa",
            style = MaterialTheme.typography.labelSmall,
            color = if (sincronizando) Color(0xFFD97706) else Color(0xFF0D9488),
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
  )
}

@Composable
fun BotaoVoltar(aoVoltar: () -> Unit) {
  IconButton(onClick = aoVoltar) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
  }
}

@Composable
fun PainelDestaque(titulo: String, descricao: String, etiqueta: String? = null) {
  Column(
    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
      .background(Brush.linearGradient(listOf(Navy, Color(0xFF134E4A)))).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (etiqueta != null) Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = JadeVibrante)
    Text(titulo, style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text(descricao, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCBD5E1))
  }
}

@Composable
fun EstadoVazio(titulo: String, descricao: String, acao: (@Composable () -> Unit)? = null) {
  Column(
    Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    MarcaChaveiro(64.dp)
    Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(descricao, style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    acao?.invoke()
  }
}

@Composable
fun SecaoFormulario(numero: String, titulo: String, descricao: String) {
  Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
      Text(numero, Modifier.padding(12.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    Column(Modifier.weight(1f)) {
      Text(titulo, style = MaterialTheme.typography.titleMedium)
      Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
