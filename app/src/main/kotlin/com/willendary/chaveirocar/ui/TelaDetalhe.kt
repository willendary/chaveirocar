package com.willendary.chaveirocar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.Foto
import com.willendary.chaveirocar.util.formatarData
import com.willendary.chaveirocar.util.formatarValor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaDetalhe(
  banco: BancoDeAtendimentos,
  id: Long,
  onEditar: (Long) -> Unit,
  onDuplicar: (Atendimento) -> Unit,
  onExcluido: () -> Unit,
  onVoltar: () -> Unit,
) {
  val contexto = LocalContext.current
  val escopo = rememberCoroutineScope()
  var atendimento by remember { mutableStateOf<Atendimento?>(null) }
  var fotos by remember { mutableStateOf<List<Foto>>(emptyList()) }
  var fotoAberta by remember { mutableStateOf<String?>(null) }
  var confirmandoExclusao by remember { mutableStateOf(false) }

  LaunchedEffect(id) {
    withContext(Dispatchers.IO) {
      atendimento = banco.obter(id)
      fotos = banco.listarFotos(id)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Registro") },
        navigationIcon = { TextButton(onClick = onVoltar) { Text("Voltar") } },
      )
    },
  ) { espacamento ->
    val registro = atendimento
    if (registro == null) {
      Box(Modifier.fillMaxSize().padding(espacamento), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      return@Scaffold
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        text = registro.placa,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
      )
      Text(
        text = listOf(registro.marca, registro.modelo, registro.ano)
          .filter { it.isNotBlank() }
          .joinToString(" "),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text = "${formatarData(registro.dataServico)} · ${registro.tipoServico.rotulo}",
        style = MaterialTheme.typography.bodyMedium,
      )
      if (registro.chassi.isNotBlank()) {
        Text(
          text = "Chassi: ${registro.chassi}",
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
        )
      }
      if (registro.valorCentavos > 0) {
        Text("Valor: ${formatarValor(registro.valorCentavos)}", style = MaterialTheme.typography.bodyMedium)
      }

      if (registro.defeito.isNotBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp)) {
            Text("Defeito encontrado", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(registro.defeito, style = MaterialTheme.typography.bodyLarge)
          }
        }
      }

      if (registro.solucao.isNotBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp)) {
            Text("Solução aplicada", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(registro.solucao, style = MaterialTheme.typography.bodyLarge)
          }
        }
      }

      if (registro.ferramenta.isNotBlank()) {
        Text("Ferramenta: ${registro.ferramenta}", style = MaterialTheme.typography.bodyMedium)
      }

      if (fotos.isNotEmpty()) {
        Text("Fotos", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          fotos.forEach { foto ->
            ImagemLocal(
              caminho = foto.caminho,
              ladoMaximo = 800,
              modificador = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable { fotoAberta = foto.caminho },
            )
          }
        }
      }

      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onEditar(registro.id) }, modifier = Modifier.weight(1f)) { Text("Editar") }
        OutlinedButton(onClick = { onDuplicar(registro) }, modifier = Modifier.weight(1f)) { Text("Novo p/ este carro") }
      }
      TextButton(onClick = { confirmandoExclusao = true }) {
        Text("Excluir registro", color = MaterialTheme.colorScheme.error)
      }
    }
  }

  val foto = fotoAberta
  if (foto != null) {
    Dialog(onDismissRequest = { fotoAberta = null }) {
      Card {
        ImagemLocal(
          caminho = foto,
          ladoMaximo = 2000,
          modificador = Modifier.fillMaxWidth().clickable { fotoAberta = null },
        )
      }
    }
  }

  if (confirmandoExclusao && atendimento != null) {
    AlertDialog(
      onDismissRequest = { confirmandoExclusao = false },
      title = { Text("Excluir registro?") },
      text = { Text("O histórico e as fotos deste serviço serão apagados. Não tem como desfazer.") },
      confirmButton = {
        TextButton(onClick = {
          confirmandoExclusao = false
          escopo.launch {
            val caminhos = withContext(Dispatchers.IO) { banco.excluir(id) }
            caminhos.forEach { Fotos.excluir(contexto, it) }
            onExcluido()
          }
        }) { Text("Excluir") }
      },
      dismissButton = {
        TextButton(onClick = { confirmandoExclusao = false }) { Text("Cancelar") }
      },
    )
  }
}
