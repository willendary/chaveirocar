package com.willendary.chaveirocar.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.Composable
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
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.util.Biometria
import com.willendary.chaveirocar.util.Erros
import com.willendary.chaveirocar.util.Seguranca
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  var definindoPin by remember { mutableStateOf(false) }
  var pedindoPinAtual by remember { mutableStateOf<AcaoComPin?>(null) }
  var erroRegistrado by remember { mutableStateOf(Erros.ultimo(contexto)) }
  var mostrandoErro by remember { mutableStateOf(false) }

  var ultimoBackupLocal by remember {
    mutableStateOf(
      maxOf(
        preferencias.getLong(Preferencias.ULTIMO_BACKUP, 0L),
        preferencias.getLong(Preferencias.ULTIMO_BACKUP_AUTO, 0L),
      ),
    )
  }
  var processandoBackupArquivo by remember { mutableStateOf(false) }

  val exportadorArquivo = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip"),
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    processandoBackupArquivo = true
    escopo.launch {
      val resultado = runCatching {
        withContext(Dispatchers.IO) {
          contexto.contentResolver.openOutputStream(uri)?.use { saida ->
            Backup.exportar(banco, contexto, saida)
          } ?: error("Não foi possível criar o arquivo.")
        }
        System.currentTimeMillis()
      }
      processandoBackupArquivo = false
      resultado.onSuccess { momento ->
        ultimoBackupLocal = momento
        preferencias.edit().putLong(Preferencias.ULTIMO_BACKUP, momento).apply()
        Toast.makeText(contexto, "Backup exportado com sucesso.", Toast.LENGTH_LONG).show()
      }.onFailure { erro ->
        Erros.registrar(contexto, "Exportar backup local", erro)
        Toast.makeText(contexto, "Não foi possível exportar o backup.", Toast.LENGTH_LONG).show()
      }
    }
  }

  val importadorArquivo = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri ->
    if (uri == null) return@rememberLauncherForActivityResult
    processandoBackupArquivo = true
    escopo.launch {
      val resultado = runCatching {
        withContext(Dispatchers.IO) {
          contexto.contentResolver.openInputStream(uri)?.use { entrada ->
            Backup.importar(banco, contexto, entrada, substituir = false)
          } ?: error("Não foi possível abrir o arquivo.")
        }
      }
      processandoBackupArquivo = false
      resultado.onSuccess { total ->
        Toast.makeText(contexto, "$total registro(s) importado(s) do backup.", Toast.LENGTH_LONG).show()
        onDadosRestaurados()
      }.onFailure { erro ->
        Erros.registrar(contexto, "Importar backup local", erro)
        Toast.makeText(contexto, "Arquivo de backup inválido ou corrompido.", Toast.LENGTH_LONG).show()
      }
    }
  }

  val versao = remember { Atualizacoes.versaoInstalada(contexto) }
  val aparelhoTemBiometria = remember { Biometria.disponivel(contexto) }

  Scaffold(
    topBar = {
      CabecalhoTela("Ajustes", "Seu aplicativo, do seu jeito")
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    Column(
      modifier = Modifier
        .conteudoResponsivo()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      PainelDestaque("Tudo em ordem.", "Gerencie sua conta, proteja seus registros e mantenha o app atualizado.", "CHAVEIROCAR · $versao")
      TituloSecao("Segurança")
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

      TituloSecao("Conta e backup")
      SecaoContaApi(banco = banco)
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Cópia de segurança em arquivo (.zip)", style = MaterialTheme.typography.titleSmall)
          Text(
            text = if (ultimoBackupLocal == 0L) "Nenhuma cópia local exportada ainda."
            else "Última cópia local: ${formatarDataHora(ultimoBackupLocal)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                val nome = "chaveirocar-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date()) + ".zip"
                exportadorArquivo.launch(nome)
              },
              enabled = !processandoBackupArquivo,
              modifier = Modifier.weight(1f),
            ) { Text("Exportar (.zip)") }
            OutlinedButton(
              onClick = {
                importadorArquivo.launch(arrayOf("application/zip", "application/octet-stream"))
              },
              enabled = !processandoBackupArquivo,
              modifier = Modifier.weight(1f),
            ) { Text("Importar (.zip)") }
          }
          Text(
            "Gera ou restaura um arquivo ZIP completo (atendimentos, fichas técnicas e fotos) " +
              "como cópia de segurança independente no aparelho.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      TituloSecao("Atualizações")
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = if (atualizacao != null) "Versão ${atualizacao.versao} disponível para baixar."
            else "Versão instalada: $versao. Verifique se há novidades.",
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
        Column(modifier = Modifier.padding(20.dp)) {
          Text("ChaveiroCar $versao", style = MaterialTheme.typography.titleSmall)
          Spacer(Modifier.height(4.dp))
          Text(
            text = "Seus registros, códigos e fotos ficam salvos neste aparelho com cópia automática local e " +
              "sincronização segura pela sua conta ChaveiroCar entre Android, iOS e Windows.",
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
}

private enum class AcaoComPin { ALTERAR, DESATIVAR }

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
