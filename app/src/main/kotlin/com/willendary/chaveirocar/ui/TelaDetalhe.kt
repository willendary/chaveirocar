package com.willendary.chaveirocar.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
        title = { Text("Atendimento", style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { BotaoVoltar(onVoltar) },
        actions = {
          IconButton(onClick = { compartilhar() }, enabled = atendimento != null) { Icon(Icons.Default.Share, "Compartilhar atendimento") }
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
        .conteudoResponsivo()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      PainelDestaque(registro.placa,
        listOf(registro.marca, registro.modelo, registro.ano).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Veículo sem modelo" },
        registro.tipoServico.rotulo)
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Selo(if (registro.pago) "Pago" else "Pagamento pendente", pendente = !registro.pago)
            if (registro.valorCentavos > 0) Text(formatarValor(registro.valorCentavos),
              style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
          }
          Text("Serviço em ${formatarData(registro.dataServico)}", style = MaterialTheme.typography.bodyMedium)
          if (registro.chassi.isNotBlank()) Text("Chassi ${registro.chassi}", style = MaterialTheme.typography.bodySmall)
          if (registro.atualizadoEm > registro.dataServico) Text("Última correção em ${formatarData(registro.atualizadoEm)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          if (registro.clienteNome.isNotBlank() || registro.clienteTelefone.isNotBlank()) {
            Text("Cliente", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(listOf(registro.clienteNome, registro.clienteTelefone).filter { it.isNotBlank() }.joinToString(" · "),
              style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
      if (registro.abertura.isNotBlank()) {
        TituloSecao("Técnica de abertura")
        Card(modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer)) {
          Text(registro.abertura, Modifier.padding(20.dp), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
      }

      if (registro.codigoChave.isNotBlank() || registro.codigoConfiguracao.isNotBlank() || registro.chipTransponder.isNotBlank()) {
        TituloSecao("Códigos do veículo")
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.padding(20.dp),
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
            if (registro.chipTransponder.isNotBlank()) {
              CodigoCopiado(
                rotulo = "Chip / Transponder",
                valor = registro.chipTransponder,
                onCopiar = { copiarTexto(contexto, "Chip / Transponder", registro.chipTransponder) },
              )
            }
          }
        }
      }

      if (registro.defeito.isNotBlank() || registro.solucao.isNotBlank() || registro.ferramenta.isNotBlank()) {
      TituloSecao("Diagnóstico e solução")
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onEditar(registro.id) }, modifier = Modifier.fillMaxWidth()) { Text("Editar atendimento") }
        OutlinedButton(onClick = { onDuplicar(registro) }, modifier = Modifier.fillMaxWidth()) {
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
