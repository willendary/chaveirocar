package com.willendary.chaveirocar.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.nuvem.GoogleDrive
import com.willendary.chaveirocar.util.Erros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Porta de entrada obrigatória: o app só abre depois de conectar a
 * conta Google. Sem botão de pular e sem voltar.
 */
@Composable
fun TelaLoginNuvem(aoConectar: (String?) -> Unit) {
  val contexto = LocalContext.current
  val escopo = rememberCoroutineScope()
  var conectando by remember { mutableStateOf(false) }

  val resolverAutorizacao = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { resultado ->
    conectando = false
    val dados = resultado.data
    if (resultado.resultCode == Activity.RESULT_OK && dados != null) {
      runCatching { GoogleDrive.concluirAutorizacao(contexto, dados) }
        .onSuccess { autorizacao ->
          val credencial = autorizacao.credencial
          if (credencial != null) {
            aoConectar(credencial.email)
          } else {
            Toast.makeText(contexto, "Não autorizado. Tente de novo.", Toast.LENGTH_LONG).show()
          }
        }
        .onFailure { erro ->
          Erros.registrar(contexto, "Login Google (abertura)", erro)
          Toast.makeText(contexto, "Não foi possível autorizar. Tente de novo.", Toast.LENGTH_LONG).show()
        }
    } else {
      Toast.makeText(contexto, "Autorização cancelada. Conecte a conta para continuar.", Toast.LENGTH_LONG).show()
    }
  }

  fun conectar() {
    if (conectando) return
    conectando = true
    escopo.launch {
      val autorizacao = runCatching {
        withContext(Dispatchers.IO) { GoogleDrive.autorizar(contexto) }
      }.getOrElse { erro ->
        conectando = false
        Erros.registrar(contexto, "Login Google (abertura)", erro)
        Toast.makeText(contexto, "Não foi possível conectar. Verifique a internet e tente de novo.", Toast.LENGTH_LONG).show()
        return@launch
      }
      val pendente = autorizacao.pendente
      if (pendente != null) {
        resolverAutorizacao.launch(IntentSenderRequest.Builder(pendente.intentSender).build())
      } else {
        conectando = false
        val credencial = autorizacao.credencial
        if (credencial != null) {
          aoConectar(credencial.email)
        } else {
          Toast.makeText(contexto, "Não autorizado. Tente de novo.", Toast.LENGTH_LONG).show()
        }
      }
    }
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
      imageVector = Icons.Default.Build,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(48.dp),
    )
    Spacer(Modifier.height(16.dp))
    Text("ChaveiroCar", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
      text = "Conecte sua conta Google para abrir o app.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(24.dp))
    Button(onClick = { conectar() }, enabled = !conectando, modifier = Modifier.fillMaxWidth()) {
      Text(if (conectando) "Conectando..." else "Continuar com Google")
    }
    Spacer(Modifier.height(16.dp))
    Text(
      text = "O backup fica na pasta oculta do app no Drive " +
        "(appDataFolder) — só o app alcança e o arquivo não aparece no seu Drive. " +
        "Sem conta conectada, o app não abre.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}
