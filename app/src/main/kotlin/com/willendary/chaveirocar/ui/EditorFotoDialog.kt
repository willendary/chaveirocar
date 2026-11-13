package com.willendary.chaveirocar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.modelo.SetaAnotacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

private val CORES_SETA = listOf(
  "#EF4444" to Color(0xFFEF4444),
  "#FACC15" to Color(0xFFFACC15),
  "#22C55E" to Color(0xFF22C55E),
  "#06B6D4" to Color(0xFF06B6D4),
  "#FFFFFF" to Color(0xFFFFFFFF),
)

private val ESPESSURAS_SETA = listOf(
  "Fina" to 4f,
  "Média" to 7f,
  "Grossa" to 11f,
)

@Composable
fun EditorFotoDialog(
  caminho: String,
  anotacoesIniciais: String,
  onCancelar: () -> Unit = {},
  onSalvar: (String) -> Unit = {},
  aoFechar: () -> Unit = onCancelar,
  aoSalvar: (String) -> Unit = onSalvar,
) {
  val contexto = LocalContext.current
  val escopo = rememberCoroutineScope()
  var bitmap by remember(caminho) { mutableStateOf<ImageBitmap?>(null) }
  val setas = remember(caminho, anotacoesIniciais) {
    mutableStateListOf<SetaAnotacao>().apply {
      addAll(Fotos.lerAnotacoes(anotacoesIniciais))
    }
  }
  val refazerPilha = remember(caminho) { mutableStateListOf<SetaAnotacao>() }
  var corAtual by remember { mutableStateOf(CORES_SETA.first().first) }
  var espessuraAtual by remember { mutableFloatStateOf(ESPESSURAS_SETA[1].second) }
  var rascunho by remember { mutableStateOf<SetaAnotacao?>(null) }
  var salvando by remember { mutableStateOf(false) }

  LaunchedEffect(caminho) {
    bitmap = withContext(Dispatchers.IO) {
      Fotos.carregarBaseParaEdicao(contexto, caminho, 1600)?.asImageBitmap()
    }
  }

  Dialog(
    onDismissRequest = { if (!salvando) aoFechar() },
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = Color(0xFF111318),
      contentColor = Color.White,
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (!salvando) aoFechar() }) {
              Icon(Icons.Default.Close, contentDescription = "Fechar", tint = Color.White)
            }
            Column {
              Text(
                "Anotar foto com setas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
              )
              Text(
                "Arraste na imagem para apontar o componente",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
              )
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(
              onClick = {
                if (setas.isNotEmpty()) {
                  val ultima = setas.removeAt(setas.lastIndex)
                  refazerPilha.add(ultima)
                }
              },
              enabled = setas.isNotEmpty() && !salvando,
            ) {
              Text(
                "Desfazer",
                color = if (setas.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.35f),
              )
            }
            TextButton(
              onClick = {
                if (refazerPilha.isNotEmpty()) {
                  val item = refazerPilha.removeAt(refazerPilha.lastIndex)
                  setas.add(item)
                }
              },
              enabled = refazerPilha.isNotEmpty() && !salvando,
            ) {
              Text(
                "Refazer",
                color = if (refazerPilha.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.35f),
              )
            }
            TextButton(
              onClick = {
                if (setas.isNotEmpty()) {
                  refazerPilha.clear()
                  setas.clear()
                }
              },
              enabled = setas.isNotEmpty() && !salvando,
            ) {
              Text(
                "Limpar",
                color = if (setas.isNotEmpty()) Color(0xFFFCA5A5) else Color.White.copy(alpha = 0.35f),
              )
            }
          }
        }

        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
          contentAlignment = Alignment.Center,
        ) {
          val img = bitmap
          if (img != null && img.width > 0 && img.height > 0) {
            val proporcao = img.width.toFloat() / img.height.toFloat()
            Box(
              modifier = Modifier
                .aspectRatio(proporcao, matchHeightConstraintsFirst = maxWidth / maxHeight > proporcao)
                .fillMaxSize(),
            ) {
              Image(
                bitmap = img,
                contentDescription = "Imagem em edição",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
              )
              Canvas(
                modifier = Modifier
                  .fillMaxSize()
                  .pointerInput(corAtual, espessuraAtual) {
                    detectDragGestures(
                      onDragStart = { inicio ->
                        if (size.width > 0 && size.height > 0) {
                          val nx = (inicio.x / size.width).coerceIn(0f, 1f)
                          val ny = (inicio.y / size.height).coerceIn(0f, 1f)
                          rascunho = SetaAnotacao(
                            x1 = nx,
                            y1 = ny,
                            x2 = nx,
                            y2 = ny,
                            corHex = corAtual,
                            espessura = espessuraAtual,
                          )
                        }
                      },
                      onDrag = { change, _ ->
                        change.consume()
                        val atual = rascunho ?: return@detectDragGestures
                        if (size.width > 0 && size.height > 0) {
                          val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                          val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                          rascunho = atual.copy(x2 = nx, y2 = ny)
                        }
                      },
                      onDragEnd = {
                        val finalizada = rascunho
                        rascunho = null
                        if (finalizada != null) {
                          val dist = hypot(
                            (finalizada.x2 - finalizada.x1).toDouble(),
                            (finalizada.y2 - finalizada.y1).toDouble(),
                          )
                          if (dist >= 0.02) {
                            setas.add(finalizada)
                            refazerPilha.clear()
                          }
                        }
                      },
                      onDragCancel = { rascunho = null },
                    )
                  },
              ) {
                for (seta in setas) {
                  desenharSeta(seta)
                }
                rascunho?.let { desenharSeta(it) }
              }
            }
          } else {
            Text("Carregando imagem...", color = Color.White.copy(alpha = 0.7f))
          }
        }

        Spacer(Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            CORES_SETA.forEach { (hex, cor) ->
              val selecionada = corAtual == hex
              Box(
                modifier = Modifier
                  .size(if (selecionada) 34.dp else 28.dp)
                  .clip(CircleShape)
                  .background(cor)
                  .border(
                    width = if (selecionada) 3.dp else 1.dp,
                    color = if (selecionada) Color.White else Color.White.copy(alpha = 0.4f),
                    shape = CircleShape,
                  )
                  .clickable { corAtual = hex },
              )
            }
          }

          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ESPESSURAS_SETA.forEach { (rotulo, valor) ->
              FilterChip(
                selected = espessuraAtual == valor,
                onClick = { espessuraAtual = valor },
                label = { Text(rotulo) },
              )
            }
          }
        }

        Spacer(Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          TextButton(
            onClick = { if (!salvando) aoFechar() },
            modifier = Modifier.weight(1f),
          ) {
            Text("Cancelar", color = Color.White)
          }
          Button(
            onClick = {
              if (salvando) return@Button
              salvando = true
              val copia = setas.toList()
              escopo.launch {
                val json = withContext(Dispatchers.IO) {
                  Fotos.aplicarAnotacoes(contexto, caminho, copia)
                  Fotos.serializarAnotacoes(copia)
                }
                aoSalvar(json)
              }
            },
            enabled = !salvando,
            modifier = Modifier.weight(1f),
          ) {
            Text(if (salvando) "Salvando..." else "Salvar anotações")
          }
        }
      }
    }
  }
}

private fun DrawScope.desenharSeta(seta: SetaAnotacao) {
  val x1 = seta.x1.coerceIn(0f, 1f) * size.width
  val y1 = seta.y1.coerceIn(0f, 1f) * size.height
  val x2 = seta.x2.coerceIn(0f, 1f) * size.width
  val y2 = seta.y2.coerceIn(0f, 1f) * size.height
  if (hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()) < 4.0) return

  val cor = CORES_SETA.firstOrNull { it.first.equals(seta.corHex, ignoreCase = true) }?.second
    ?: Color(0xFFEF4444)
  val escala = max(size.width, size.height) / 1000f
  val traco = (seta.espessura.coerceIn(2f, 20f) * escala).coerceAtLeast(3f)
  val cabeca = traco * 3.8f
  val angulo = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()
  val abertura = Math.toRadians(28.0).toFloat()

  val pEsqX = x2 - cabeca * cos(angulo - abertura)
  val pEsqY = y2 - cabeca * sin(angulo - abertura)
  val pDirX = x2 - cabeca * cos(angulo + abertura)
  val pDirY = y2 - cabeca * sin(angulo + abertura)
  val fimLinhaX = x2 - (cabeca * 0.55f) * cos(angulo)
  val fimLinhaY = y2 - (cabeca * 0.55f) * sin(angulo)

  val caminhoCabeca = Path().apply {
    moveTo(x2, y2)
    lineTo(pEsqX, pEsqY)
    lineTo(pDirX, pDirY)
    close()
  }

  drawLine(
    color = Color.Black.copy(alpha = 0.65f),
    start = Offset(x1, y1),
    end = Offset(fimLinhaX, fimLinhaY),
    strokeWidth = traco + 4f * escala,
    cap = StrokeCap.Round,
  )
  drawPath(
    path = caminhoCabeca,
    color = Color.Black.copy(alpha = 0.65f),
    style = Stroke(width = 3f * escala, join = StrokeJoin.Round),
  )
  drawLine(
    color = cor,
    start = Offset(x1, y1),
    end = Offset(fimLinhaX, fimLinhaY),
    strokeWidth = traco,
    cap = StrokeCap.Round,
  )
  drawPath(
    path = caminhoCabeca,
    color = cor,
  )
}
