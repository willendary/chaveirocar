package com.willendary.chaveirocar.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.willendary.chaveirocar.atualizacao.AtualizacaoDisponivel
import com.willendary.chaveirocar.atualizacao.Atualizacoes
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Backup
import com.willendary.chaveirocar.dados.BackupAutomatico
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.nuvem.BackupNaNuvem
import com.willendary.chaveirocar.nuvem.CredencialGoogle
import com.willendary.chaveirocar.nuvem.GoogleDrive
import com.willendary.chaveirocar.util.Biometria
import com.willendary.chaveirocar.util.Compartilhamento
import com.willendary.chaveirocar.util.Erros
import com.willendary.chaveirocar.util.Seguranca
import com.willendary.chaveirocar.util.formatarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaAjustes(
  banco: BancoDeAtendimentos,
  atualizacao: AtualizacaoDisponivel?,
  baixandoAtualizacao: Boolean,
  onAtualizarApp: () -> Unit,
  onProcurarAtualizacao: () -> Unit,
  onVerNovidades: () -> Unit,
  onDadosRestaurados: () -> Unit,
) {
  val contexto = LocalContext.current
  val atividade = contexto as? FragmentActivity
  val escopo = rememberCoroutineScope()
  val preferencias = remember { Preferencias.obter(contexto) }

  var pinAtivo by remember { mutableStateOf(preferencias.getBoolean(Preferencias.PIN_ATIVO, false)) }
  var biometriaAtiva by remember {
    mutableStateOf(preferencias.getBoolean(Preferencias.BIOMETRIA_ATIVA, false))
  }
  var ultimoBackup by remember { mutableStateOf(preferencias.getLong(Preferencias.ULTIMO_BACKUP, 0)) }
  var ultimoAutomatico by remember { mutableStateOf(BackupAutomatico.ultimoArquivo(contexto)) }

  var definindoPin by remember { mutableStateOf(false) }
  var pedindoPinAtual by remember { mutableStateOf<AcaoComPin?>(null) }
  var uriParaImportar by remember { mutableStateOf<Uri?>(null) }
  var importando by remember { mutableStateOf(false) }
  var erroRegistrado by remember { mutableStateOf(Erros.ultimo(contexto)) }
  var mostrandoErro by remember { mutableStateOf(false) }

  fun recarregarBackups() {
    ultimoBackup = preferencias.getLong(Preferencias.ULTIMO_BACKUP, 0)
    ultimoAutomatico = BackupAutomatico.ultimoArquivo(contexto)
  }

  var contaNuvem by remember { mutableStateOf(preferencias.getString(Preferencias.CONTA_NUVEM, null)) }
  var ultimoEnvioNuvem by remember { mutableStateOf(preferencias.getLong(Preferencias.ULTIMO_ENVIO_NUVEM, 0)) }
  var tokenNuvem by remember { mutableStateOf<String?>(null) }
  var conectandoNuvem by remember { mutableStateOf(false) }
  var enviandoNuvem by remember { mutableStateOf(false) }
  var restaurandoNuvem by remember { mutableStateOf(false) }
  var backupsNuvem by remember { mutableStateOf<List<BackupNaNuvem>>(emptyList()) }

  fun avisarErroNuvem(acao: String, erro: Throwable) {
    Erros.registrar(contexto, "Nuvem: $acao", erro)
    Toast.makeText(contexto, "Não foi possível $acao. Tente de novo.", Toast.LENGTH_LONG).show()
  }

  fun guardarCredencial(credencial: CredencialGoogle?) {
    if (credencial == null) {
      Toast.makeText(contexto, "Não foi possível autorizar a conta Google", Toast.LENGTH_LONG).show()
      return
    }
    tokenNuvem = credencial.token
    val email = credencial.email
    if (!email.isNullOrBlank()) {
      contaNuvem = email
      preferencias.edit().putString(Preferencias.CONTA_NUVEM, email).apply()
    } else if (contaNuvem == null) {
      contaNuvem = ""
      preferencias.edit().putString(Preferencias.CONTA_NUVEM, "").apply()
    }
    Toast.makeText(contexto, "Conta Google conectada", Toast.LENGTH_SHORT).show()
  }

  val resolverAutorizacao = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { resultado ->
    conectandoNuvem = false
    val dados = resultado.data
    if (resultado.resultCode == Activity.RESULT_OK && dados != null) {
      runCatching { GoogleDrive.concluirAutorizacao(contexto, dados) }
        .onSuccess { autorizacao -> guardarCredencial(autorizacao.credencial) }
        .onFailure { erro -> avisarErroNuvem("conectar sua conta", erro) }
    } else {
      Toast.makeText(contexto, "Autorização cancelada", Toast.LENGTH_SHORT).show()
    }
  }

  fun conectarContaGoogle() {
    if (conectandoNuvem) return
    conectandoNuvem = true
    escopo.launch {
      val autorizacao = runCatching {
        withContext(Dispatchers.IO) { GoogleDrive.autorizar(contexto) }
      }.getOrElse { erro ->
        conectandoNuvem = false
        avisarErroNuvem("conectar sua conta", erro)
        return@launch
      }
      val pendente = autorizacao.pendente
      if (pendente != null) {
        resolverAutorizacao.launch(IntentSenderRequest.Builder(pendente.intentSender).build())
      } else {
        conectandoNuvem = false
        guardarCredencial(autorizacao.credencial)
      }
    }
  }

  suspend fun tokenDaNuvem(): String? {
    tokenNuvem?.let { return it }
    val autorizacao = withContext(Dispatchers.IO) { GoogleDrive.autorizar(contexto) }
    val token = autorizacao.credencial?.token
    if (token != null) {
      tokenNuvem = token
    } else if (autorizacao.pendente != null) {
      Toast.makeText(
        contexto,
        "Toque em \"Conectar conta Google\" para autorizar de novo.",
        Toast.LENGTH_LONG,
      ).show()
    }
    return token
  }

  fun enviarParaNuvem() {
    if (enviandoNuvem) return
    enviandoNuvem = true
    escopo.launch {
      val resultado = runCatching {
        val token = tokenDaNuvem() ?: error("sem autorização da conta Google")
        withContext(Dispatchers.IO) {
          val arquivo = File(contexto.cacheDir, "nuvem.zip")
          try {
            arquivo.outputStream().use { Backup.exportar(banco, contexto, it) }
            val nome = "chaveirocar-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date()) + ".zip"
            GoogleDrive.enviar(token, arquivo, nome)
            GoogleDrive.limparAntigos(token, 4)
          } finally {
            arquivo.delete()
          }
        }
        System.currentTimeMillis()
      }
      enviandoNuvem = false
      resultado
        .onSuccess { momento ->
          ultimoEnvioNuvem = momento
          preferencias.edit().putLong(Preferencias.ULTIMO_ENVIO_NUVEM, momento).apply()
          Toast.makeText(contexto, "Backup enviado para o Google Drive", Toast.LENGTH_LONG).show()
        }
        .onFailure { erro -> avisarErroNuvem("enviar o backup", erro) }
    }
  }

  fun procurarBackupNuvem() {
    if (restaurandoNuvem) return
    restaurandoNuvem = true
    escopo.launch {
      val resultado = runCatching {
        val token = tokenDaNuvem() ?: error("sem autorização da conta Google")
        withContext(Dispatchers.IO) { GoogleDrive.listar(token) }
      }
      restaurandoNuvem = false
      resultado
        .onSuccess { lista ->
          if (lista.isEmpty()) {
            Toast.makeText(contexto, "Nenhum backup na nuvem ainda", Toast.LENGTH_LONG).show()
          } else {
            backupsNuvem = lista
          }
        }
        .onFailure { erro -> avisarErroNuvem("buscar o backup", erro) }
    }
  }

  fun restaurarBackupNuvem(backup: BackupNaNuvem) {
    backupsNuvem = emptyList()
    if (restaurandoNuvem) return
    restaurandoNuvem = true
    escopo.launch {
      val resultado = runCatching {
        val token = tokenDaNuvem() ?: error("sem autorização da conta Google")
        withContext(Dispatchers.IO) {
          val arquivo = File(contexto.cacheDir, "restaurar-nuvem.zip")
          try {
            GoogleDrive.baixar(token, backup.id, arquivo)
            arquivo.inputStream().use { Backup.importar(banco, contexto, it, substituir = true) }
          } finally {
            arquivo.delete()
          }
        }
      }
      restaurandoNuvem = false
      resultado
        .onSuccess { total ->
          Toast.makeText(contexto, "$total registro(s) restaurados da nuvem", Toast.LENGTH_LONG).show()
          recarregarBackups()
          onDadosRestaurados()
        }
        .onFailure { erro -> avisarErroNuvem("restaurar o backup", erro) }
    }
  }

  fun desconectarNuvem() {
    val token = tokenNuvem
    contaNuvem = null
    tokenNuvem = null
    preferencias.edit().remove(Preferencias.CONTA_NUVEM).apply()
    if (!token.isNullOrBlank()) {
      escopo.launch {
        withContext(Dispatchers.IO) { GoogleDrive.esquecerToken(contexto, token) }
      }
    }
    Toast.makeText(contexto, "Conta desconectada. Na próxima abertura, será preciso conectar de novo.", Toast.LENGTH_LONG).show()
  }

  val versao = remember { Atualizacoes.versaoInstalada(contexto) }
  val aparelhoTemBiometria = remember { Biometria.disponivel(contexto) }

  val criarBackup = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip"),
  ) { uri ->
    if (uri != null) {
      escopo.launch {
        val ok = runCatching {
          withContext(Dispatchers.IO) {
            contexto.contentResolver.openOutputStream(uri)?.use { Backup.exportar(banco, contexto, it) }
              ?: error("não foi possível abrir o arquivo")
          }
        }.isSuccess
        if (ok) {
          preferencias.edit().putLong(Preferencias.ULTIMO_BACKUP, System.currentTimeMillis()).apply()
          recarregarBackups()
          Toast.makeText(contexto, "Backup salvo", Toast.LENGTH_SHORT).show()
        } else {
          Toast.makeText(contexto, "Não foi possível salvar o backup", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  val abrirBackup = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri != null) uriParaImportar = uri
  }

  fun importar(uri: Uri, substituir: Boolean) {
    if (importando) return
    importando = true
    escopo.launch {
      val resultado = runCatching {
        withContext(Dispatchers.IO) {
          contexto.contentResolver.openInputStream(uri)?.use {
            Backup.importar(banco, contexto, it, substituir)
          } ?: 0
        }
      }
      importando = false
      uriParaImportar = null
      resultado
        .onSuccess { total ->
          Toast.makeText(contexto, "$total registro(s) importados", Toast.LENGTH_LONG).show()
          recarregarBackups()
          onDadosRestaurados()
        }
        .onFailure {
          Toast.makeText(contexto, "Arquivo de backup inválido", Toast.LENGTH_LONG).show()
        }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Ajustes", style = MaterialTheme.typography.titleLarge)
            Text(
              text = "Versão $versao",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      TituloSecao("Segurança")
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          LinhaInterruptor(
            titulo = "Exigir PIN ao abrir",
            descricao = "Protege o histórico quando o celular fica na bancada.",
            ativo = pinAtivo,
            aoMudar = { ligar ->
              if (ligar) {
                definindoPin = true
              } else {
                pedindoPinAtual = AcaoComPin.DESATIVAR
              }
            },
          )
          if (pinAtivo) {
            TextButton(onClick = { pedindoPinAtual = AcaoComPin.ALTERAR }) { Text("Alterar PIN") }
            if (aparelhoTemBiometria) {
              LinhaInterruptor(
                titulo = "Usar digital",
                descricao = "A digital abre o app; o PIN continua valendo.",
                ativo = biometriaAtiva,
                aoMudar = { ligar ->
                  if (ligar) {
                    if (atividade != null) {
                      Biometria.autenticar(
                        atividade = atividade,
                        titulo = "Ativar entrada por digital",
                        aoSucesso = {
                          preferencias.edit().putBoolean(Preferencias.BIOMETRIA_ATIVA, true).apply()
                          biometriaAtiva = true
                        },
                        aoErro = {
                          Toast.makeText(contexto, "Digital não confirmada", Toast.LENGTH_SHORT).show()
                        },
                      )
                    }
                  } else {
                    preferencias.edit().putBoolean(Preferencias.BIOMETRIA_ATIVA, false).apply()
                    biometriaAtiva = false
                  }
                },
              )
            }
          }
        }
      }

      TituloSecao("Backup")
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = if (ultimoBackup == 0L) "Nenhum backup manual ainda."
            else "Último backup manual: ${formatarDataHora(ultimoBackup)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = ultimoAutomatico?.let { "Cópia automática: ${formatarDataHora(it.lastModified())}" }
              ?: "Cópia automática ainda não gerada. Ela é feita sozinha a cada 7 dias.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                val nome = "chaveirocar-" + SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()) + ".zip"
                criarBackup.launch(nome)
              },
              modifier = Modifier.weight(1f),
            ) { Text("Fazer backup") }
            OutlinedButton(
              onClick = { abrirBackup.launch(arrayOf("application/zip", "application/octet-stream")) },
              enabled = !importando,
              modifier = Modifier.weight(1f),
            ) { Text("Restaurar") }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = {
                val arquivo = ultimoAutomatico ?: return@OutlinedButton
                uriParaImportar = Uri.fromFile(arquivo)
              },
              enabled = ultimoAutomatico != null,
              modifier = Modifier.weight(1f),
            ) { Text("Restaurar cópia") }
            OutlinedButton(
              onClick = {
                val arquivo = ultimoAutomatico ?: return@OutlinedButton
                Compartilhamento.compartilharArquivo(contexto, arquivo, "application/zip", "Compartilhar backup")
              },
              enabled = ultimoAutomatico != null,
              modifier = Modifier.weight(1f),
            ) { Text("Compartilhar cópia") }
          }
          Text(
            text = "O backup é um arquivo ZIP com os dados e as fotos. Guarde no Drive ou mande para você mesmo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      TituloSecao("Nuvem (Google Drive)")
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          if (contaNuvem == null) {
            Text(
              "Conecte sua conta Google para enviar o backup para a pasta oculta do app no Drive e restaurar em outro celular. Sem servidor e sem custo.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { conectarContaGoogle() }, enabled = !conectandoNuvem) {
              Text(if (conectandoNuvem) "Conectando..." else "Conectar conta Google")
            }
          } else {
            Text(
              text = if (contaNuvem.isNullOrBlank()) "Conta Google conectada" else "Conectado como $contaNuvem",
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              text = if (ultimoEnvioNuvem == 0L) "Nenhum envio para a nuvem ainda."
              else "Último envio: ${formatarDataHora(ultimoEnvioNuvem)}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(
                onClick = { enviarParaNuvem() },
                enabled = !enviandoNuvem && !restaurandoNuvem,
                modifier = Modifier.weight(1f),
              ) { Text(if (enviandoNuvem) "Enviando..." else "Enviar backup") }
              OutlinedButton(
                onClick = { procurarBackupNuvem() },
                enabled = !enviandoNuvem && !restaurandoNuvem,
                modifier = Modifier.weight(1f),
              ) { Text(if (restaurandoNuvem) "Buscando..." else "Restaurar") }
            }
            Text(
              "O backup fica na pasta oculta do app no Drive. Outro celular com a mesma conta pode restaurar. " +
                "Enviar cria uma cópia nova; as 4 mais recentes ficam guardadas. Enquanto a conta estiver conectada, " +
                "um backup sobe junto com a cópia automática semanal. Desconectar só limpa este aparelho; " +
                "para revogar tudo, use https://myaccount.google.com/permissions",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { desconectarNuvem() }) { Text("Desconectar conta") }
            TextButton(onClick = { conectarContaGoogle() }, enabled = !conectandoNuvem) {
              Text(if (conectandoNuvem) "Conectando..." else "Reconectar")
            }
          }
        }
      }

      TituloSecao("Atualizações")
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = if (atualizacao != null) "Versão ${atualizacao.versao} disponível para baixar."
            else "Você está na versão mais nova.",
            style = MaterialTheme.typography.bodyMedium,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onVerNovidades, modifier = Modifier.weight(1f)) {
              Text("Ver novidades")
            }
            if (atualizacao != null) {
              Button(
                onClick = onAtualizarApp,
                enabled = !baixandoAtualizacao,
                modifier = Modifier.weight(1f),
              ) { Text(if (baixandoAtualizacao) "Baixando..." else "Atualizar") }
            } else {
              OutlinedButton(onClick = onProcurarAtualizacao, modifier = Modifier.weight(1f)) {
                Text("Procurar")
              }
            }
          }
        }
      }

      TituloSecao("Sobre")
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text("ChaveiroCar $versao", style = MaterialTheme.typography.titleSmall)
          Spacer(Modifier.height(4.dp))
          Text(
            text = "Tudo fica neste celular: histórico, códigos e fotos. Com a conta Google conectada, o backup " +
              "também vai para a pasta oculta do app no Drive. Faça backup antes de trocar de aparelho.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (erroRegistrado != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { mostrandoErro = true }) {
              Text("Ver último erro do app")
            }
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }

  if (mostrandoErro) {
    val texto = erroRegistrado.orEmpty()
    AlertDialog(
      onDismissRequest = { mostrandoErro = false },
      title = { Text("Último erro do app") },
      text = {
        Column(modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
          Text(
            text = texto,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
          )
        }
      },
      confirmButton = {
        TextButton(onClick = {
          copiarTexto(contexto, "Erro do ChaveiroCar", texto)
          mostrandoErro = false
        }) { Text("Copiar") }
      },
      dismissButton = {
        TextButton(onClick = {
          Erros.limpar(contexto)
          erroRegistrado = null
          mostrandoErro = false
        }) { Text("Apagar") }
      },
    )
  }

  if (definindoPin) {
    DialogoDefinirPin(
      onSalvar = { pin ->
        val sal = Seguranca.novoSal()
        preferencias.edit()
          .putString(Preferencias.PIN_SAL, sal)
          .putString(Preferencias.PIN_HASH, Seguranca.gerarHash(pin, sal))
          .putBoolean(Preferencias.PIN_ATIVO, true)
          .apply()
        pinAtivo = true
        definindoPin = false
        Toast.makeText(contexto, "PIN ativado", Toast.LENGTH_SHORT).show()
      },
      onCancelar = { definindoPin = false },
    )
  }

  pedindoPinAtual?.let { acao ->
    DialogoConfirmarPin(
      titulo = if (acao == AcaoComPin.ALTERAR) "Confirmar PIN atual" else "Desativar bloqueio",
      onConfirmado = {
        pedindoPinAtual = null
        when (acao) {
          AcaoComPin.ALTERAR -> definindoPin = true
          AcaoComPin.DESATIVAR -> {
            preferencias.edit()
              .putBoolean(Preferencias.PIN_ATIVO, false)
              .putBoolean(Preferencias.BIOMETRIA_ATIVA, false)
              .apply()
            pinAtivo = false
            biometriaAtiva = false
            Toast.makeText(contexto, "Bloqueio desativado", Toast.LENGTH_SHORT).show()
          }
        }
      },
      onCancelar = { pedindoPinAtual = null },
    )
  }

  uriParaImportar?.let { uri ->
    AlertDialog(
      onDismissRequest = { uriParaImportar = null },
      title = { Text("Restaurar backup") },
      text = {
        Text(
          "Substituir o histórico atual pelos dados do arquivo, ou somar os registros aos que já existem?",
        )
      },
      confirmButton = {
        Row {
          TextButton(onClick = { importar(uri, substituir = true) }) { Text("Substituir") }
          TextButton(onClick = { importar(uri, substituir = false) }) { Text("Somar") }
        }
      },
      dismissButton = { TextButton(onClick = { uriParaImportar = null }) { Text("Cancelar") } },
    )
  }
  if (backupsNuvem.isNotEmpty()) {
    AlertDialog(
      onDismissRequest = { backupsNuvem = emptyList() },
      title = { Text("Backups na nuvem") },
      text = {
        Column(modifier = Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
          Text(
            "Escolha qual backup restaurar. Os dados deste aparelho serão substituídos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          backupsNuvem.forEach { backup ->
            TextButton(
              onClick = { restaurarBackupNuvem(backup) },
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text("${formatarDataHora(backup.criadoEm)} · ${tamanhoLegivel(backup.tamanho)}")
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { backupsNuvem = emptyList() }) { Text("Cancelar") }
      },
    )
  }
}

private enum class AcaoComPin { ALTERAR, DESATIVAR }

private fun tamanhoLegivel(bytes: Long): String =
  if (bytes < 1_048_576) {
    String.format(Locale.forLanguageTag("pt-BR"), "%.0f KB", bytes / 1024.0)
  } else {
    String.format(Locale.forLanguageTag("pt-BR"), "%.1f MB", bytes / 1_048_576.0)
  }

@Composable
private fun LinhaInterruptor(
  titulo: String,
  descricao: String,
  ativo: Boolean,
  aoMudar: (Boolean) -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(modifier = Modifier.weight(1f)) {
      Text(titulo, style = MaterialTheme.typography.titleSmall)
      Text(
        text = descricao,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = ativo, onCheckedChange = aoMudar)
  }
}

@Composable
private fun DialogoDefinirPin(onSalvar: (String) -> Unit, onCancelar: () -> Unit) {
  var pin by remember { mutableStateOf("") }
  var confirmacao by remember { mutableStateOf("") }
  var erro by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onCancelar,
    title = { Text("Definir PIN") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Use de 4 a 6 números que você não esqueça.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
          value = pin,
          onValueChange = { pin = it.filter { letra -> letra.isDigit() }.take(6) },
          label = { Text("PIN") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = confirmacao,
          onValueChange = { confirmacao = it.filter { letra -> letra.isDigit() }.take(6) },
          label = { Text("Confirme o PIN") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth(),
        )
        erro?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        erro = when {
          pin.length < 4 || pin.length > 6 -> "O PIN precisa ter de 4 a 6 números."
          pin != confirmacao -> "Os PINs não são iguais."
          else -> null
        }
        if (erro == null) onSalvar(pin)
      }) { Text("Ativar") }
    },
    dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
  )
}

@Composable
private fun DialogoConfirmarPin(
  titulo: String,
  onConfirmado: () -> Unit,
  onCancelar: () -> Unit,
) {
  val contexto = LocalContext.current
  var pin by remember { mutableStateOf("") }
  var erro by remember { mutableStateOf<String?>(null) }
  val preferencias = remember { Preferencias.obter(contexto) }

  AlertDialog(
    onDismissRequest = onCancelar,
    title = { Text(titulo) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = pin,
          onValueChange = { pin = it.filter { letra -> letra.isDigit() }.take(6) },
          label = { Text("PIN atual") },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth(),
        )
        erro?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        val sal = preferencias.getString(Preferencias.PIN_SAL, null)
        val hash = preferencias.getString(Preferencias.PIN_HASH, null)
        val correto = sal != null && hash != null && Seguranca.conferir(pin, sal, hash)
        if (correto) onConfirmado() else {
          erro = "PIN incorreto."
          pin = ""
        }
      }) { Text("Confirmar") }
    },
    dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
  )
}

private fun formatarDataHora(millis: Long): String =
  SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).format(Date(millis))
