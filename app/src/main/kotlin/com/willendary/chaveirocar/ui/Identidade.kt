package com.willendary.chaveirocar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willendary.chaveirocar.R
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.ui.theme.AmareloBandeira
import com.willendary.chaveirocar.ui.theme.AzulMercosul
import com.willendary.chaveirocar.ui.theme.CarvaoMarca
import com.willendary.chaveirocar.ui.theme.Jade
import com.willendary.chaveirocar.ui.theme.JadeProfundo
import com.willendary.chaveirocar.ui.theme.JadeVibrante
import com.willendary.chaveirocar.ui.theme.Navy
import com.willendary.chaveirocar.ui.theme.VerdeBandeira
import com.willendary.chaveirocar.ui.theme.coresEstendidas

/** Ícones vetoriais sob medida (sem depender de material-icons-extended). */
object IconesApp {
  val Chave: ImageVector by lazy {
    ImageVector.Builder("Chave", 24.dp, 24.dp, 24f, 24f).apply {
      path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        moveTo(7.5f, 15.5f)
        arcToRelative(4.5f, 4.5f, 0f, true, true, 0f, -9f)
        arcToRelative(4.5f, 4.5f, 0f, false, true, 0f, 9f)
        close()
        moveTo(12f, 11f)
        lineTo(21f, 11f)
        moveTo(18f, 11f)
        lineTo(18f, 14.5f)
        moveTo(21f, 11f)
        lineTo(21f, 13.5f)
      }
    }.build()
  }

  val Carro: ImageVector by lazy {
    ImageVector.Builder("Carro", 24.dp, 24.dp, 24f, 24f).apply {
      path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        moveTo(5f, 16f)
        lineTo(3.5f, 16f)
        verticalLineTo(12.2f)
        lineTo(5.6f, 7.6f)
        curveTo(6.0f, 6.6f, 7.0f, 6f, 8.1f, 6f)
        horizontalLineTo(15.9f)
        curveTo(17.0f, 6f, 18.0f, 6.6f, 18.4f, 7.6f)
        lineTo(20.5f, 12.2f)
        verticalLineTo(16f)
        horizontalLineTo(19f)
        moveTo(5f, 16f)
        horizontalLineTo(19f)
        moveTo(7f, 16f)
        verticalLineTo(18f)
        moveTo(17f, 16f)
        verticalLineTo(18f)
        moveTo(6.5f, 12f)
        horizontalLineTo(17.5f)
      }
    }.build()
  }

  val Escudo: ImageVector by lazy {
    ImageVector.Builder("Escudo", 24.dp, 24.dp, 24f, 24f).apply {
      path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        moveTo(12f, 3f)
        lineTo(19f, 6f)
        verticalLineTo(11.5f)
        curveTo(19f, 16.2f, 16f, 19.6f, 12f, 21f)
        curveTo(8f, 19.6f, 5f, 16.2f, 5f, 11.5f)
        verticalLineTo(6f)
        close()
      }
    }.build()
  }

  val Copiar: ImageVector by lazy {
    ImageVector.Builder("Copiar", 24.dp, 24.dp, 24f, 24f).apply {
      path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        moveTo(9f, 9f)
        horizontalLineTo(19f)
        verticalLineTo(20f)
        horizontalLineTo(9f)
        close()
        moveTo(5f, 15f)
        horizontalLineTo(4f)
        verticalLineTo(4f)
        horizontalLineTo(15f)
        verticalLineTo(5f)
      }
    }.build()
  }

  val Documento: ImageVector by lazy {
    ImageVector.Builder("Documento", 24.dp, 24.dp, 24f, 24f).apply {
      path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        moveTo(6f, 3f)
        horizontalLineTo(14f)
        lineTo(18f, 7f)
        verticalLineTo(21f)
        horizontalLineTo(6f)
        close()
        moveTo(14f, 3f)
        verticalLineTo(7f)
        horizontalLineTo(18f)
        moveTo(9f, 12f)
        horizontalLineTo(15f)
        moveTo(9f, 16f)
        horizontalLineTo(13f)
      }
    }.build()
  }

  val Mensagem: ImageVector by lazy {
    ImageVector.Builder("Mensagem", 24.dp, 24.dp, 24f, 24f).apply {
      path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        moveTo(4f, 19.5f)
        lineTo(5.3f, 15.8f)
        curveTo(4.5f, 14.5f, 4f, 13f, 4f, 11.4f)
        curveTo(4f, 7f, 7.6f, 3.5f, 12f, 3.5f)
        curveTo(16.4f, 3.5f, 20f, 7f, 20f, 11.4f)
        curveTo(20f, 15.8f, 16.4f, 19.3f, 12f, 19.3f)
        curveTo(10.5f, 19.3f, 9f, 18.9f, 7.8f, 18.2f)
        close()
      }
    }.build()
  }

  val Filtro: ImageVector by lazy {
    ImageVector.Builder("Filtro", 24.dp, 24.dp, 24f, 24f).apply {
      path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
      ) {
        moveTo(4f, 6f)
        horizontalLineTo(20f)
        moveTo(7f, 12f)
        horizontalLineTo(17f)
        moveTo(10f, 18f)
        horizontalLineTo(14f)
      }
    }.build()
  }

  val Camera: ImageVector by lazy {
    ImageVector.Builder("Camera", 24.dp, 24.dp, 24f, 24f).apply {
      path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      ) {
        moveTo(4f, 8f)
        horizontalLineTo(7f)
        lineTo(8.5f, 5.5f)
        horizontalLineTo(15.5f)
        lineTo(17f, 8f)
        horizontalLineTo(20f)
        verticalLineTo(19f)
        horizontalLineTo(4f)
        close()
        moveTo(12f, 16.5f)
        arcToRelative(3.2f, 3.2f, 0f, true, true, 0f, -6.4f)
        arcToRelative(3.2f, 3.2f, 0f, false, true, 0f, 6.4f)
        close()
      }
    }.build()
  }
}

/** Um único símbolo vetorial oficial na abertura, nas telas e no launcher. */
@Composable
fun MarcaChaveiro(tamanho: Dp = 44.dp, elevacao: Dp = 2.dp) {
  val forma = RoundedCornerShape(tamanho * 0.28f)
  Surface(
    shape = forma,
    color = CarvaoMarca,
    shadowElevation = elevacao,
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    modifier = Modifier.size(tamanho),
  ) {
    Image(
      painter = painterResource(R.drawable.marca_chaveiro),
      contentDescription = null,
      modifier = Modifier.fillMaxSize(),
    )
  }
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
  acoesExtra: (@Composable RowScope.() -> Unit)? = null,
) {
  val estendidas = MaterialTheme.coresEstendidas
  Column {
    TopAppBar(
      navigationIcon = { if (aoVoltar != null) BotaoVoltar(aoVoltar) },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          MarcaChaveiro(38.dp, elevacao = 1.dp)
          Column {
            Text(
              text = titulo,
              style = MaterialTheme.typography.titleLarge,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = subtitulo,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      },
      actions = {
        if (acoesExtra != null) {
          acoesExtra()
        } else {
          Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (sincronizando) estendidas.alertaFundo else estendidas.sucessoFundo,
            border = BorderStroke(
              width = 1.dp,
              color = (if (sincronizando) estendidas.alerta else estendidas.sucesso).copy(alpha = 0.25f),
            ),
            modifier = Modifier.padding(end = 14.dp),
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
                  .background(if (sincronizando) estendidas.alerta else estendidas.sucesso),
              )
              Text(
                text = if (sincronizando) "Sincronizando" else "Nuvem ativa",
                style = MaterialTheme.typography.labelSmall,
                color = if (sincronizando) estendidas.alerta else estendidas.sucesso,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }
      },
      colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
  }
}

@Composable
fun BotaoVoltar(aoVoltar: () -> Unit) {
  IconButton(onClick = aoVoltar) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
  }
}

/** Cartão padrão do Design System 2.0 com elevação limpa e borda nítida. */
@Composable
fun CartaoSurface(
  modificador: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  corFundo: Color = MaterialTheme.colorScheme.surface,
  corBorda: Color = MaterialTheme.colorScheme.outlineVariant,
  raio: Dp = 20.dp,
  conteudo: @Composable ColumnScope.() -> Unit,
) {
  val forma = RoundedCornerShape(raio)
  if (onClick != null) {
    Card(
      onClick = onClick,
      modifier = modificador.fillMaxWidth(),
      shape = forma,
      colors = CardDefaults.cardColors(containerColor = corFundo),
      border = BorderStroke(1.dp, corBorda),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
      content = conteudo,
    )
  } else {
    Card(
      modifier = modificador.fillMaxWidth(),
      shape = forma,
      colors = CardDefaults.cardColors(containerColor = corFundo),
      border = BorderStroke(1.dp, corBorda),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
      content = conteudo,
    )
  }
}

/** Placa automotiva estilizada no padrão brasileiro Mercosul. */
@Composable
fun PlacaMercosulBadge(
  placa: String,
  compacto: Boolean = false,
  modificador: Modifier = Modifier,
) {
  val forma = RoundedCornerShape(if (compacto) 8.dp else 11.dp)
  Surface(
    modifier = modificador,
    shape = forma,
    color = Color.White,
    border = BorderStroke(if (compacto) 1.25.dp else 1.5.dp, Color(0xFF1E293B)),
    shadowElevation = if (compacto) 1.dp else 2.dp,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .background(AzulMercosul)
          .padding(horizontal = if (compacto) 7.dp else 12.dp, vertical = if (compacto) 2.dp else 3.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = "BR",
          color = Color.White.copy(alpha = 0.9f),
          fontSize = if (compacto) 7.sp else 9.sp,
          fontWeight = FontWeight.Bold,
          lineHeight = if (compacto) 8.sp else 10.sp,
        )
        Text(
          text = "BRASIL",
          color = Color.White,
          fontSize = if (compacto) 7.5.sp else 9.5.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 1.sp,
          lineHeight = if (compacto) 8.sp else 10.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
          Box(Modifier.size(width = if (compacto) 5.dp else 7.dp, height = if (compacto) 4.dp else 5.dp).background(VerdeBandeira))
          Box(Modifier.size(width = if (compacto) 4.dp else 6.dp, height = if (compacto) 4.dp else 5.dp).background(AmareloBandeira))
        }
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(
            horizontal = if (compacto) 10.dp else 18.dp,
            vertical = if (compacto) 4.dp else 8.dp,
          ),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = placa.uppercase(),
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
          fontSize = if (compacto) 15.sp else 24.sp,
          letterSpacing = if (compacto) 1.2.sp else 2.2.sp,
          color = Color(0xFF0F172A),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

/** Cartão KPI compacto para a Home / Hub. */
@Composable
fun KpiMiniCard(
  valor: String,
  rotulo: String,
  corDestaque: Color,
  fundoDestaque: Color,
  detalhe: String? = null,
  modificador: Modifier = Modifier,
) {
  Surface(
    modifier = modificador,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Box(
        modifier = Modifier
          .size(width = 24.dp, height = 4.dp)
          .clip(RoundedCornerShape(99.dp))
          .background(corDestaque),
      )
      Text(
        text = valor,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = rotulo,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!detalhe.isNullOrBlank()) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = fundoDestaque,
        ) {
          Text(
            text = detalhe,
            style = MaterialTheme.typography.labelSmall,
            color = corDestaque,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
          )
        }
      }
    }
  }
}

/** Tile de ação rápida (usado na grade superior da TelaDetalhe). */
@Composable
fun BotaoAcaoIcone(
  icone: ImageVector,
  rotulo: String,
  corDestaque: Color,
  fundoDestaque: Color,
  habilitado: Boolean = true,
  modificador: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = habilitado,
    modifier = modificador,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(
      modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(if (habilitado) fundoDestaque else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = icone,
          contentDescription = null,
          tint = if (habilitado) corDestaque else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          modifier = Modifier.size(19.dp),
        )
      }
      Text(
        text = rotulo,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (habilitado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/** Trilha visual de progresso por etapas (usada na Consulta Técnica e Catálogo FIPE). */
@Composable
fun IndicadorEtapas(
  etapas: List<String>,
  passoAtual: Int,
  aoTocarEtapa: (Int) -> Unit = {},
  modificador: Modifier = Modifier,
) {
  Row(
    modifier = modificador.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    etapas.forEachIndexed { indice, nome ->
      val concluido = indice < passoAtual
      val ativo = indice == passoAtual
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
          .clip(RoundedCornerShape(8.dp))
          .clickable(enabled = concluido) { aoTocarEtapa(indice) }
          .padding(horizontal = 3.dp, vertical = 4.dp),
      ) {
        Box(
          modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(
              when {
                concluido -> MaterialTheme.colorScheme.primary
                ativo -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
              },
            )
            .border(
              width = if (ativo) 1.5.dp else 0.dp,
              color = if (ativo) MaterialTheme.colorScheme.primary else Color.Transparent,
              shape = CircleShape,
            ),
          contentAlignment = Alignment.Center,
        ) {
          if (concluido) {
            Icon(
              Icons.Default.Check,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(12.dp),
            )
          } else {
            Text(
              text = "${indice + 1}",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = if (ativo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Text(
          text = nome,
          style = MaterialTheme.typography.labelSmall,
          fontSize = 10.5.sp,
          fontWeight = if (ativo || concluido) FontWeight.Bold else FontWeight.Medium,
          color = when {
            ativo -> MaterialTheme.colorScheme.primary
            concluido -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurfaceVariant
          },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (indice < etapas.lastIndex) {
        Box(
          modifier = Modifier
            .weight(1f)
            .height(2.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(
              if (indice < passoAtual) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.outlineVariant,
            ),
        )
      }
    }
  }
}

/** Banner editorial compacto (Design System 2.0) — usado pontualmente onde destaca resumo. */
@Composable
fun PainelDestaque(titulo: String, descricao: String, etiqueta: String? = null) {
  val forma = RoundedCornerShape(20.dp)
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(forma)
      .background(Brush.linearGradient(listOf(Navy, Color(0xFF133C42))))
      .border(1.dp, Color.White.copy(alpha = 0.08f), forma),
  ) {
    // Faixa sutil de brilho lateral Jade
    Box(
      modifier = Modifier
        .align(Alignment.CenterStart)
        .width(4.dp)
        .fillMaxHeight()
        .background(JadeVibrante),
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 14.dp),
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      if (etiqueta != null) {
        Text(
          text = etiqueta.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          letterSpacing = 1.sp,
          fontWeight = FontWeight.Bold,
          color = JadeVibrante,
        )
      }
      Text(
        text = titulo,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
      )
      if (descricao.isNotBlank()) {
        Text(
          text = descricao,
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFFCBD5E1),
        )
      }
    }
  }
}

@Composable
fun EstadoVazio(titulo: String, descricao: String, acao: (@Composable () -> Unit)? = null) {
  CartaoSurface(modificador = Modifier.padding(vertical = 8.dp)) {
    Column(
      Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(RoundedCornerShape(22.dp))
          .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
      ) {
        MarcaChaveiro(52.dp, elevacao = 2.dp)
      }
      Text(
        text = titulo,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
      )
      Text(
        text = descricao,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      if (acao != null) {
        Spacer(Modifier.height(4.dp))
        acao()
      }
    }
  }
}

@Composable
fun SecaoFormulario(numero: String, titulo: String, descricao: String) {
  Row(
    Modifier.fillMaxWidth().padding(bottom = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.primaryContainer,
    ) {
      Text(
        text = numero,
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text(
        descricao,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Barra pill moderna que identifica a área ativa (Veicular em Jade, Residencial em Indigo). */
@Composable
fun BarraModalidadeAtiva(
  modalidade: SegmentoAtendimento,
  onTrocarArea: () -> Unit,
  modificador: Modifier = Modifier,
) {
  val estendidas = MaterialTheme.coresEstendidas
  val residencial = modalidade == SegmentoAtendimento.RESIDENCIAL
  val corAcento = if (residencial) estendidas.residencial else MaterialTheme.colorScheme.primary
  val fundoIcone = if (residencial) estendidas.residencialFundo else MaterialTheme.colorScheme.primaryContainer

  Surface(
    color = MaterialTheme.colorScheme.surface,
    border = BorderStroke(1.dp, corAcento.copy(alpha = 0.28f)),
    shape = RoundedCornerShape(16.dp),
    modifier = modificador.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier = Modifier
          .size(32.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(fundoIcone),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (residencial) Icons.Default.Home else IconesApp.Carro,
          contentDescription = null,
          tint = corAcento,
          modifier = Modifier.size(18.dp),
        )
      }
      Column(Modifier.weight(1f)) {
        Text(
          text = "Área ${modalidade.rotulo}",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = if (residencial) "Fechaduras, cilindros e manuais" else "Veículos, placas e transponders",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Surface(
        onClick = onTrocarArea,
        shape = RoundedCornerShape(10.dp),
        color = fundoIcone,
      ) {
        Text(
          text = "Trocar área",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = corAcento,
          modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
        )
      }
    }
  }
}
