package com.willendary.chaveirocar.ui

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.CategoriaFoto
import com.willendary.chaveirocar.modelo.Foto
import com.willendary.chaveirocar.util.Compartilhamento
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
  var gerandoPdf by remember { mutableStateOf(false) }

  LaunchedEffect(id) {
    withContext(Dispatchers.IO) {
      atendimento = banco.obter(id)
      fotos = banco.listarFotos(id)
    }
  }

  val registro = atendimento

  fun compartilhar() {
    if (registro == null) return
    Compartilhamento.compartilharTexto(
      contexto,
      Compartilhamento.textoDoAtendimento(registro),
    )
  }

  fun gerarPdf() {
    if (registro == null || gerandoPdf) return
    gerandoPdf = true
    escopo.launch {
      runCatching {
        val arquivo = withContext(Dispatchers.IO) {
          Compartilhamento.gerarPdf(
            contexto,
            registro,
            fotos.sortedBy { if (it.categoria == CategoriaFoto.CARRO) 0 else 1 },
          )
        }
        Compartilhamento.compartilharPdf(contexto, arquivo)
      }.onFailure {
        Toast.makeText(contexto, "Não foi possível gerar o PDF", Toast.LENGTH_SHORT).show()
      }
      gerandoPdf = false
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Registro") },
        navigationIcon = { TextButton(onClick = onVoltar) { Text("Voltar") } },
        actions = {
          TextButton(onClick = { compartilhar() }, enabled = atendimento != null) { Text("Compartilhar") }
          TextButton(onClick = { gerarPdf() }, enabled = atendimento != null && !gerandoPdf) {
            Text(if (gerandoPdf) "Gerando..." else "PDF")
          }
        },
      )
    },
  ) { espacamento ->
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
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = registro.placa,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 32.sp,
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Selo(registro.tipoServico.rotulo)
      }
      Text(
        text = listOf(registro.marca, registro.modelo, registro.ano)
          .filter { it.isNotBlank() }
          .joinToString(" "),
        style = MaterialTheme.typography.titleMedium,
      )
      Text(
        text = formatarData(registro.dataServico) +
          if (registro.chassi.isNotBlank()) " · Chassi ${registro.chassi}" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (registro.valorCentavos > 0) {
        Text("Valor: ${formatarValor(registro.valorCentavos)}", style = MaterialTheme.typography.bodyMedium)
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
      ) {
        Column(Modifier.padding(14.dp)) {
          Text("Abertura", style = MaterialTheme.typography.labelLarge)
          Spacer(Modifier.height(4.dp))
          Text(registro.abertura, style = MaterialTheme.typography.bodyLarge)
        }
      }

      if (registro.codigoChave.isNotBlank() || registro.codigoConfiguracao.isNotBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            if (registro.codigoChave.isNotBlank()) {
              CodigoCopiado(
                rotulo = "Código de chave",
                valor = registro.codigoChave,
                onCopiar = { copiarTexto(contexto, "Código de chave", registro.codigoChave) },
              )
            }
            if (registro.codigoConfiguracao.isNotBlank()) {
              CodigoCopiado(
                rotulo = "Código de configuração",
                valor = registro.codigoConfiguracao,
                onCopiar = {
                  copiarTexto(contexto, "Código de configuração", registro.codigoConfiguracao)
                },
              )
            }
          }
        }
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (registro.defeito.isNotBlank()) {
            Text("Defeito encontrado", style = MaterialTheme.typography.labelLarge)
            Text(registro.defeito, style = MaterialTheme.typography.bodyMedium)
          }
          if (registro.solucao.isNotBlank()) {
            Text("Solução aplicada", style = MaterialTheme.typography.labelLarge)
            Text(registro.solucao, style = MaterialTheme.typography.bodyMedium)
          }
          if (registro.ferramenta.isNotBlank()) {
            Text("Ferramenta: ${registro.ferramenta}", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }

      val fotosDoCarro = fotos.filter { it.categoria == CategoriaFoto.CARRO }
      val fotosDeProcedimento = fotos.filter { it.categoria == CategoriaFoto.PROCEDIMENTO }

      if (fotosDoCarro.isNotEmpty()) {
        TituloSecao("Fotos do carro")
        GradeDeFotos(fotosDoCarro) { fotoAberta = it }
      }

      if (fotosDeProcedimento.isNotEmpty()) {
        TituloSecao("Como se faz")
        GradeDeFotos(fotosDeProcedimento, mostrarPasso = true) { fotoAberta = it }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onEditar(registro.id) }, modifier = Modifier.weight(1f)) { Text("Editar") }
        OutlinedButton(onClick = { onDuplicar(registro) }, modifier = Modifier.weight(1f)) {
          Text("Novo atendimento (mesmo carro)")
        }
      }
      TextButton(onClick = { confirmandoExclusao = true }) {
        Text("Excluir registro", color = MaterialTheme.colorScheme.error)
      }
      Spacer(Modifier.height(24.dp))
    }
  }

  val foto = fotoAberta
  if (foto != null) {
    Dialog(onDismissRequest = { fotoAberta = null }) {
      Card {
        Column {
          ImagemLocal(
            caminho = foto,
            ladoMaximo = 2000,
            modificador = Modifier
              .fillMaxWidth()
              .heightIn(max = 520.dp)
              .clickable { fotoAberta = null },
          )
          TextButton(onClick = { fotoAberta = null }, modifier = Modifier.fillMaxWidth()) { Text("Fechar") }
        }
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

@Composable
private fun CodigoCopiado(rotulo: String, valor: String, onCopiar: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(
        text = valor,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    TextButton(onClick = onCopiar) { Text("Copiar") }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeDeFotos(
  fotos: List<Foto>,
  mostrarPasso: Boolean = false,
  aoAbrir: (String) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    fotos.forEachIndexed { indice, foto ->
      Column(modifier = Modifier.width(140.dp)) {
        Box {
          ImagemLocal(
            caminho = foto.caminho,
            ladoMaximo = 800,
            modificador = Modifier
              .width(140.dp)
              .height(140.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable { aoAbrir(foto.caminho) },
          )
          if (mostrarPasso) {
            Text(
              text = (indice + 1).toString(),
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
        if (foto.legenda.isNotBlank()) {
          Spacer(Modifier.height(2.dp))
          Text(
            text = foto.legenda,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
