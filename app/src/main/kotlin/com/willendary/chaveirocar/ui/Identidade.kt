package com.willendary.chaveirocar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import com.willendary.chaveirocar.R
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.ui.theme.JadeVibrante
import com.willendary.chaveirocar.ui.theme.Navy

/** Um único símbolo vetorial na abertura, nas telas e no launcher. */
@Composable
fun MarcaChaveiro(tamanho: Dp = 44.dp) {
  val forma = RoundedCornerShape(tamanho * 0.26f)
  Image(
    painter = painterResource(R.drawable.marca_chaveiro),
    contentDescription = null,
    modifier = Modifier
      .size(tamanho)
      .clip(forma)
      .border(0.75.dp, Color.White.copy(alpha = 0.16f), forma),
  )
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
  aoVoltar: (() -> Unit)? = null,
) {
  Column {
    TopAppBar(
      navigationIcon = { if (aoVoltar != null) BotaoVoltar(aoVoltar) },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          MarcaChaveiro(42.dp)
          Column {
            Text(titulo, style = MaterialTheme.typography.titleLarge)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      },
      actions = {
        Surface(
          shape = RoundedCornerShape(999.dp),
          color = if (sincronizando) Color(0x26F59E0B) else Color(0x1A0D9488),
          border = BorderStroke(
            width = 1.dp,
            color = if (sincronizando) Color(0x55F59E0B) else Color(0x400D9488),
          ),
          modifier = Modifier.padding(end = 12.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Box(
              modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (sincronizando) Color(0xFFF59E0B) else Color(0xFF10B981)),
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
      colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
  }
}

@Composable
fun BotaoVoltar(aoVoltar: () -> Unit) {
  IconButton(onClick = aoVoltar) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
  }
}

@Composable
fun PainelDestaque(titulo: String, descricao: String, etiqueta: String? = null) {
  val forma = RoundedCornerShape(18.dp)
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clip(forma)
      .background(Brush.linearGradient(listOf(Navy, Color(0xFF134E4A))))
      .border(1.dp, Color.White.copy(alpha = 0.09f), forma)
      .padding(horizontal = 20.dp, vertical = 18.dp),
    verticalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    if (etiqueta != null) {
      Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.08f),
      ) {
        Text(
          text = etiqueta,
          style = MaterialTheme.typography.labelSmall,
          letterSpacing = 0.8.sp,
          fontWeight = FontWeight.Bold,
          color = JadeVibrante,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
      }
    }
    Text(titulo, style = MaterialTheme.typography.headlineSmall, color = Color.White)
    Text(descricao, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCBD5E1))
  }
}

@Composable
fun EstadoVazio(titulo: String, descricao: String, acao: (@Composable () -> Unit)? = null) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
  ) {
    Column(
      Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      MarcaChaveiro(60.dp)
      Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
      Text(
        descricao,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      acao?.invoke()
    }
  }
}

@Composable
fun SecaoFormulario(numero: String, titulo: String, descricao: String) {
  Row(
    Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
    ) {
      Text(
        numero,
        Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Column(Modifier.weight(1f)) {
      Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** Barra discreta que mantém visível a modalidade ativa e permite retornar à tela de escolha. */
@Composable
fun BarraModalidadeAtiva(
  modalidade: SegmentoAtendimento,
  onTrocarArea: () -> Unit,
  modificador: Modifier = Modifier,
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape = RoundedCornerShape(14.dp),
    modifier = modificador.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(10.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.primary),
      )
      Column(Modifier.weight(1f)) {
        Text(
          text = "Área ${modalidade.rotulo}",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = if (modalidade == SegmentoAtendimento.RESIDENCIAL)
            "Serviços, fichário e fichas residenciais"
          else "Serviços, fichário e fichas técnicas veiculares",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      FilledTonalButton(
        onClick = onTrocarArea,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(10.dp),
      ) {
        Text("Trocar área", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}
