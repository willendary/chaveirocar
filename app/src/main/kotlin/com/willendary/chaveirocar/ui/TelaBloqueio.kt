package com.willendary.chaveirocar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.util.Biometria
import com.willendary.chaveirocar.util.Seguranca
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Tela de bloqueio estilo bancário: teclado circular tátil, PIN sempre disponível e biometria como atalho.
 */
@Composable
fun TelaBloqueio(
  biometriaAtiva: Boolean,
  aoDesbloquear: () -> Unit,
) {
  val contexto = LocalContext.current
  val atividade = contexto as? FragmentActivity
  val escopo = rememberCoroutineScope()
  var pin by remember { mutableStateOf("") }
  var erro by remember { mutableStateOf(false) }
  var verificando by remember { mutableStateOf(false) }

  fun tentarDigital() {
    if (atividade != null && Biometria.disponivel(atividade)) {
      Biometria.autenticar(
        atividade = atividade,
        titulo = "Desbloquear o ChaveiroCar",
        aoSucesso = { aoDesbloquear() },
      )
    }
  }

  LaunchedEffect(Unit) {
    if (biometriaAtiva) tentarDigital()
  }

  fun conferir() {
    if (pin.length < 4 || verificando) return
    verificando = true
    escopo.launch {
      val preferencias = Preferencias.obter(contexto)
      val sal = preferencias.getString(Preferencias.PIN_SAL, null)
      val hash = preferencias.getString(Preferencias.PIN_HASH, null)
      val correto = withContext(Dispatchers.Default) {
        sal != null && hash != null && Seguranca.conferir(pin, sal, hash)
      }
      verificando = false
      if (correto) {
        aoDesbloquear()
      } else {
        erro = true
        pin = ""
      }
    }
  }

  fun digitar(digito: String) {
    if (pin.length >= 6) return
    erro = false
    pin += digito
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .safeDrawingPadding(),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier = Modifier
        .conteudoResponsivo(400.dp)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      // Halo institucional com logo oficial
      Box(
        modifier = Modifier
          .size(88.dp)
          .clip(RoundedCornerShape(26.dp))
          .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
      ) {
        MarcaChaveiro(64.dp, elevacao = 4.dp)
      }

      Spacer(Modifier.height(16.dp))
      Text(
        text = "Bem-vindo de volta",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = "Digite seu PIN para desbloquear a bancada",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )

      Spacer(Modifier.height(26.dp))

      // Indicadores circulares táteis do PIN
      Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val total = max(4, pin.length)
        repeat(total) { indice ->
          val preenchido = indice < pin.length
          Box(
            modifier = Modifier
              .size(if (preenchido) 16.dp else 14.dp)
              .clip(CircleShape)
              .background(
                when {
                  erro -> MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                  preenchido -> MaterialTheme.colorScheme.primary
                  else -> Color.Transparent
                },
              )
              .border(
                width = 2.dp,
                color = when {
                  erro -> MaterialTheme.colorScheme.error
                  preenchido -> MaterialTheme.colorScheme.primary
                  else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                },
                shape = CircleShape,
              ),
          )
        }
      }

      Spacer(Modifier.height(10.dp))
      Text(
        text = if (erro) "PIN incorreto. Tente novamente." else " ",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.error,
      )

      Spacer(Modifier.height(14.dp))

      // Teclado numérico circular estilo aplicativo financeiro
      val teclas = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
      )
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        teclas.forEach { linha ->
          Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            linha.forEach { tecla ->
              if (tecla.isBlank()) {
                Spacer(Modifier.size(70.dp))
              } else {
                val apagar = tecla == "⌫"
                Surface(
                  onClick = {
                    if (apagar) {
                      pin = pin.dropLast(1)
                      erro = false
                    } else {
                      digitar(tecla)
                    }
                  },
                  shape = CircleShape,
                  color = if (apagar) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                  border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                  ),
                  shadowElevation = if (apagar) 0.dp else 1.dp,
                  modifier = Modifier.size(70.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text = tecla,
                      fontSize = if (apagar) 22.sp else 24.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = if (apagar) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                  }
                }
              }
            }
          }
        }
      }

      Spacer(Modifier.height(22.dp))
      Button(
        onClick = { conferir() },
        enabled = pin.length >= 4 && !verificando,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
      ) {
        if (verificando) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(Modifier.width(10.dp))
        }
        Text(
          text = if (verificando) "Verificando..." else "Entrar",
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
        )
      }

      if (biometriaAtiva) {
        Spacer(Modifier.height(10.dp))
        FilledTonalButton(
          onClick = { tentarDigital() },
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(17.dp))
          Spacer(Modifier.width(8.dp))
          Text("Usar digital", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
