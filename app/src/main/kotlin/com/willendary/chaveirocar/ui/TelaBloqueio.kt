package com.willendary.chaveirocar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
 * Tela de bloqueio: PIN sempre disponível, digital como atalho.
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

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Default.Lock,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(40.dp),
    )
    Spacer(Modifier.height(8.dp))
    Text("ChaveiroCar", style = MaterialTheme.typography.headlineSmall)
    Text(
      text = "Digite seu PIN para abrir",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      val total = max(4, pin.length)
      repeat(total) { indice ->
        Box(
          modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(
              if (indice < pin.length) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
      }
    }

    Spacer(Modifier.height(12.dp))
    Text(
      text = if (erro) "PIN incorreto. Tente de novo." else " ",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
    )

    Spacer(Modifier.height(8.dp))
    val teclas = listOf(
      listOf("1", "2", "3"),
      listOf("4", "5", "6"),
      listOf("7", "8", "9"),
      listOf("", "0", "⌫"),
    )
    teclas.forEach { linha ->
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        linha.forEach { tecla ->
          if (tecla.isBlank()) {
            Spacer(Modifier.width(76.dp).height(56.dp))
          } else {
            OutlinedButton(
              onClick = {
                if (tecla == "⌫") {
                  pin = pin.dropLast(1)
                  erro = false
                } else {
                  digitar(tecla)
                }
              },
              modifier = Modifier.width(76.dp).height(56.dp),
            ) {
              Text(tecla, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
          }
        }
      }
      Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(8.dp))
    Button(
      onClick = { conferir() },
      enabled = pin.length >= 4 && !verificando,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(if (verificando) "Verificando..." else "Entrar")
    }

    if (biometriaAtiva) {
      TextButton(onClick = { tentarDigital() }) { Text("Usar digital") }
    }
  }
}
