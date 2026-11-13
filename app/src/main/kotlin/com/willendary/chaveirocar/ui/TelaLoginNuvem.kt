package com.willendary.chaveirocar.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
 * Conexão com Drive legado é opcional durante a migração para a conta ChaveiroCar.
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
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
      .safeDrawingPadding().imePadding().conteudoResponsivo(480.dp)
      .verticalScroll(rememberScrollState()).padding(28.dp),
    horizontalAlignment = Alignment.Start,
    verticalArrangement = Arrangement.Center,
  ) {
    MarcaChaveiro(88.dp)
    Spacer(Modifier.height(28.dp))
    Text("ChaveiroCar", style = MaterialTheme.typography.headlineLarge)
    Spacer(Modifier.height(12.dp))
     Text("Seu histórico.\nSempre com você.", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(16.dp))
    Text("Guarde serviços, códigos e soluções. Encontre o que precisa no próximo atendimento.",
      style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(32.dp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
      Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
         Text("Escolha como começar", style = MaterialTheme.typography.titleMedium)
         Text("O Google Drive fica disponível para restaurar cópias antigas. Você também pode continuar e entrar na sua conta ChaveiroCar.",
           style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = { conectar() }, enabled = !conectando,
      modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = MaterialTheme.shapes.medium) {
      if (conectando) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
      }
       Text(if (conectando) "Conectando…" else "Conectar Google Drive legado")
    }
    Spacer(Modifier.height(16.dp))
    androidx.compose.material3.OutlinedButton(
      onClick = { aoConectar(null) },
      enabled = !conectando,
      modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) { Text("Continuar sem Drive") }
    Spacer(Modifier.height(16.dp))
    Text("Você pode conectar a conta ChaveiroCar em Ajustes. Não desinstale o app nem apague seus dados durante a migração.",
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
