package com.willendary.chaveirocar.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willendary.chaveirocar.atualizacao.AtualizacaoDisponivel
import com.willendary.chaveirocar.atualizacao.Atualizacoes
import com.willendary.chaveirocar.dados.Backup
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.util.diasDesde
import com.willendary.chaveirocar.util.formatarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PREFERENCIAS = "chaveirocar"
private const val CHAVE_ULTIMO_BACKUP = "ultimo_backup"
private const val CHAVE_ULTIMA_CHECAGEM = "ultima_checagem"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaLista(
  banco: BancoDeAtendimentos,
  onAbrir: (Long) -> Unit,
  onNovo: () -> Unit,
) {
  val contexto = LocalContext.current
  val escopo = rememberCoroutineScope()
  var busca by remember { mutableStateOf("") }
  var registros by remember { mutableStateOf<List<Atendimento>>(emptyList()) }
  var totalRegistros by remember { mutableStateOf(0) }
  var dialogoBackup by remember { mutableStateOf(false) }
  var uriParaImportar by remember { mutableStateOf<Uri?>(null) }
  var menuImportacao by remember { mutableStateOf(false) }
  var ultimoBackup by remember { mutableStateOf(0L) }
  var atualizacao by remember { mutableStateOf<AtualizacaoDisponivel?>(null) }
  var baixandoAtualizacao by remember { mutableStateOf(false) }

  val preferencias = remember { contexto.getSharedPreferences(PREFERENCIAS, android.content.Context.MODE_PRIVATE) }

  fun recarregar() {
    escopo.launch {
      registros = withContext(Dispatchers.IO) { banco.listar(busca) }
      totalRegistros = withContext(Dispatchers.IO) { banco.contarTodos() }
      ultimoBackup = preferencias.getLong(CHAVE_ULTIMO_BACKUP, 0)
    }
  }

  LaunchedEffect(busca) {
    registros = withContext(Dispatchers.IO) { banco.listar(busca) }
  }
  LaunchedEffect(Unit) {
    totalRegistros = withContext(Dispatchers.IO) { banco.contarTodos() }
    ultimoBackup = preferencias.getLong(CHAVE_ULTIMO_BACKUP, 0)
    val ultimaChecagem = preferencias.getLong(CHAVE_ULTIMA_CHECAGEM, 0)
    if (diasDesde(ultimaChecagem) >= 1) {
      atualizacao = runCatching { Atualizacoes.verificar(Atualizacoes.versaoInstalada(contexto)) }.getOrNull()
      preferencias.edit().putLong(CHAVE_ULTIMA_CHECAGEM, System.currentTimeMillis()).apply()
    }
  }

  val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    escopo.launch {
      val resultado = runCatching {
        withContext(Dispatchers.IO) {
          contexto.contentResolver.openOutputStream(uri)?.use { Backup.exportar(banco, contexto, it) }
            ?: error("Não foi possível abrir o arquivo")
        }
      }
      resultado.onSuccess {
        preferencias.edit().putLong(CHAVE_ULTIMO_BACKUP, System.currentTimeMillis()).apply()
        ultimoBackup = System.currentTimeMillis()
        Toast.makeText(contexto, "Backup salvo", Toast.LENGTH_SHORT).show()
      }.onFailure {
        Toast.makeText(contexto, "Falha no backup: ${it.message}", Toast.LENGTH_LONG).show()
      }
    }
  }

  val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uriParaImportar = uri
    menuImportacao = uri != null
  }

  fun executarImportacao(substituir: Boolean) {
    val uri = uriParaImportar ?: return
    uriParaImportar = null
    escopo.launch {
      val resultado = runCatching {
        withContext(Dispatchers.IO) {
          contexto.contentResolver.openInputStream(uri)?.use { Backup.importar(banco, contexto, it, substituir) }
            ?: error("Não foi possível abrir o arquivo")
        }
      }
      resultado.onSuccess { quantidade ->
        Toast.makeText(contexto, "$quantidade registro(s) importado(s)", Toast.LENGTH_LONG).show()
        recarregar()
      }.onFailure {
        Toast.makeText(contexto, "Falha ao importar: ${it.message}", Toast.LENGTH_LONG).show()
      }
    }
  }

  fun verificarAtualizacao(mostrarAviso: Boolean) {
    escopo.launch {
      val encontrada = runCatching {
        Atualizacoes.verificar(Atualizacoes.versaoInstalada(contexto))
      }.getOrNull()
      atualizacao = encontrada
      preferencias.edit().putLong(CHAVE_ULTIMA_CHECAGEM, System.currentTimeMillis()).apply()
      if (mostrarAviso) {
        Toast.makeText(
          contexto,
          if (encontrada == null) "Você já está na versão mais nova" else "Versão ${encontrada.versao} disponível",
          Toast.LENGTH_SHORT,
        ).show()
      }
    }
  }

  fun baixarAtualizacao() {
    val alvo = atualizacao ?: return
    if (baixandoAtualizacao) return
    baixandoAtualizacao = true
    escopo.launch {
      val resultado = runCatching { Atualizacoes.baixar(contexto, alvo) }
      baixandoAtualizacao = false
      resultado.onSuccess { arquivo ->
        runCatching { Atualizacoes.abrirInstalador(contexto, arquivo) }.onFailure {
          Toast.makeText(contexto, "Autorize a instalação de apps para o ChaveiroCar", Toast.LENGTH_LONG).show()
        }
      }.onFailure {
        Toast.makeText(contexto, "Falha ao baixar: ${it.message}", Toast.LENGTH_LONG).show()
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("ChaveiroCar", fontWeight = FontWeight.Bold) },
        actions = { TextButton(onClick = { dialogoBackup = true }) { Text("Ajustes") } },
      )
    },
    floatingActionButton = {
      FloatingActionButton(onClick = onNovo) {
        Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold)
      }
    },
  ) { espacamento ->
    Column(modifier = Modifier.fillMaxSize().padding(espacamento).padding(horizontal = 12.dp)) {
      OutlinedTextField(
        value = busca,
        onValueChange = { busca = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Buscar placa, modelo, defeito ou solução") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
      )

      val diasDoBackup = diasDesde(ultimoBackup)
      if (totalRegistros > 0 && diasDoBackup > 30) {
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (ultimoBackup == 0L) "Você ainda não fez backup. Se trocar de celular perde tudo."
              else "Último backup há $diasDoBackup dias.",
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = {
              val data = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
              exportar.launch("chaveirocar-backup-$data.zip")
            }) { Text("Fazer") }
          }
        }
      }

      Spacer(Modifier.height(8.dp))

      val novaVersao = atualizacao
      if (novaVersao != null) {
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Versão ${novaVersao.versao} disponível", style = MaterialTheme.typography.titleSmall)
              Text(
                "Baixa o APK novo e abre o instalador. Seus dados continuam.",
                style = MaterialTheme.typography.bodySmall,
              )
            }
            TextButton(onClick = { baixarAtualizacao() }, enabled = !baixandoAtualizacao) {
              Text(if (baixandoAtualizacao) "Baixando..." else "Atualizar")
            }
          }
        }
      }

      if (registros.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Text(
            text = if (busca.isBlank()) "Nenhum carro registrado ainda." else "Nada encontrado para \"$busca\".",
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text = if (busca.isBlank()) "Toque no + para registrar o primeiro serviço."
            else "Tente outro termo ou apague a busca.",
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp),
        ) {
          items(registros, key = { it.id }) { registro ->
            CartaoRegistro(registro = registro, onAbrir = { onAbrir(registro.id) })
          }
        }
      }
    }
  }

  if (dialogoBackup) {
    AlertDialog(
      onDismissRequest = { dialogoBackup = false },
      title = { Text("Ajustes") },
      text = {
        Column {
          Text(
            "Os dados ficam só neste celular. Exporte um arquivo ZIP de vez em quando e guarde no Drive ou mande pra você mesmo.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Spacer(Modifier.height(16.dp))
          TextButton(onClick = {
            val data = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            dialogoBackup = false
            exportar.launch("chaveirocar-backup-$data.zip")
          }) { Text("Exportar backup (ZIP)") }
          TextButton(onClick = {
            dialogoBackup = false
            importar.launch(arrayOf("*/*"))
          }) { Text("Importar backup (ZIP)") }
          TextButton(onClick = {
            dialogoBackup = false
            verificarAtualizacao(mostrarAviso = true)
          }) { Text("Verificar atualização") }
        }
      },
      confirmButton = {
        TextButton(onClick = { dialogoBackup = false }) { Text("Fechar") }
      },
    )
  }

  if (menuImportacao) {
    AlertDialog(
      onDismissRequest = { menuImportacao = false; uriParaImportar = null },
      title = { Text("Importar backup") },
      text = { Text("Substituir todos os registros atuais pelos do arquivo, ou acrescentar aos que já existem?") },
      confirmButton = {
        TextButton(onClick = { menuImportacao = false; executarImportacao(substituir = true) }) {
          Text("Substituir tudo")
        }
      },
      dismissButton = {
        TextButton(onClick = { menuImportacao = false; executarImportacao(substituir = false) }) {
          Text("Acrescentar")
        }
      },
    )
  }
}

@Composable
private fun CartaoRegistro(registro: Atendimento, onAbrir: () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onAbrir)) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = registro.placa,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 20.sp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
          text = listOf(registro.marca, registro.modelo).filter { it.isNotBlank() }.joinToString(" "),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      val detalhe = listOf(
        formatarData(registro.dataServico),
        registro.tipoServico.rotulo,
        if (registro.ano.isNotBlank()) registro.ano else null,
        if (registro.quantidadeFotos > 0) "${registro.quantidadeFotos} foto(s)" else null,
      ).filterNotNull().joinToString(" · ")
      Text(detalhe, style = MaterialTheme.typography.bodySmall)
      if (registro.defeito.isNotBlank()) {
        Spacer(Modifier.height(6.dp))
        HorizontalDivider()
        Spacer(Modifier.height(6.dp))
        Text(
          text = registro.defeito,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
