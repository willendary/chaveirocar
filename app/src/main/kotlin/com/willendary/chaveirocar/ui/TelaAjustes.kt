package com.willendary.chaveirocar.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.willendary.chaveirocar.atualizacao.AtualizacaoDisponivel
import com.willendary.chaveirocar.atualizacao.Atualizacoes
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.nuvem.ApiChaveiroCar
import com.willendary.chaveirocar.ui.theme.coresEstendidas
import com.willendary.chaveirocar.util.Biometria
import com.willendary.chaveirocar.util.Erros
import com.willendary.chaveirocar.util.Seguranca

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaAjustes(
  banco: BancoDeAtendimentos,
  atualizacao: AtualizacaoDisponivel?,
  baixandoAtualizacao: Boolean,
  onAtualizarApp: () -> Unit,
  onProcurarAtualizacao: () -> Unit,
  onVerNovidades: () -> Unit,
  onDadosRestaurados: () -> Unit = {},
  onSairDaConta: () -> Unit = {},
  aoAtualizarAssinatura: () -> Unit = {},
  onVoltar: (() -> Unit)? = null,
) {
  val contexto = LocalContext.current
  val estendidas = MaterialTheme.coresEstendidas
  val atividade = contexto as? FragmentActivity
  val preferencias = remember { Preferencias.obter(contexto) }
  val preferenciasApi = remember { Preferencias.obterApi(contexto) }
  var empresaAtualId by remember {
    mutableStateOf(preferencias.getString(Preferencias.API_EMPRESA_ID, "").orEmpty())
  }

  fun notificarMudancaAssinatura() {
    empresaAtualId = preferencias.getString(Preferencias.API_EMPRESA_ID, "").orEmpty()
    aoAtualizarAssinatura()
  }

  fun criarApiAssinatura(): ApiChaveiroCar {
    val baseSalva = preferencias.getString(Preferencias.API_BASE_URL, null).orEmpty()
    val baseUrl = if (baseSalva.isBlank() || baseSalva.contains("10.0.2.2") || baseSalva.contains("localhost")) {
      ApiChaveiroCar.URL_PADRAO
    } else baseSalva
    return ApiChaveiroCar(baseUrl).apply {
      tokenAcesso = preferenciasApi.getString(Preferencias.API_ACESSO, null)?.ifBlank { null }
      tokenRefresh = preferenciasApi.getString(Preferencias.API_REFRESH, null)?.ifBlank { null }
    }
  }

  fun guardarTokensAssinatura(api: ApiChaveiroCar) {
    preferenciasApi.edit()
      .putString(Preferencias.API_ACESSO, api.tokenAcesso ?: "")
      .putString(Preferencias.API_REFRESH, api.tokenRefresh ?: "")
      .apply()
  }

  var pinAtivo by remember { mutableStateOf(preferencias.getBoolean(Preferencias.PIN_ATIVO, false)) }
  var biometriaAtiva by remember {
    mutableStateOf(preferencias.getBoolean(Preferencias.BIOMETRIA_ATIVA, false))
  }
  var definindoPin by remember { mutableStateOf(false) }
  var pedindoPinAtual by remember { mutableStateOf<AcaoComPin?>(null) }
  var erroRegistrado by remember { mutableStateOf(Erros.ultimo(contexto)) }
  var mostrandoErro by remember { mutableStateOf(false) }

  val versao = remember { Atualizacoes.versaoInstalada(contexto) }
  val aparelhoTemBiometria = remember { Biometria.disponivel(contexto) }

  Scaffold(
    topBar = { CabecalhoTela("Ajustes", "Preferências, segurança e conta", aoVoltar = onVoltar) },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    Column(
      modifier = Modifier
        .conteudoResponsivo()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Cartão compacto de identidade do app (sem bloco escuro gigante)
      CartaoSurface(raio = 20.dp) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          MarcaChaveiro(52.dp, elevacao = 2.dp)
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ChaveiroCar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(
              "Gestão veicular e residencial",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Selo("v$versao", tom = TomSelo.PRIMARIO)
        }
      }

      TituloSecao("Segurança")
      CartaoSurface(raio = 20.dp) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          CabecalhoCardAjuste(
            icone = IconesApp.Escudo,
            titulo = "Proteção de acesso",
            subtitulo = "Bloqueio rápido para uso na bancada",
            cor = MaterialTheme.colorScheme.primary,
            fundo = MaterialTheme.colorScheme.primaryContainer,
          )
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
          LinhaInterruptor(
            titulo = "Exigir PIN ao abrir",
            descricao = "Protege o histórico quando o celular fica na bancada.",
            ativo = pinAtivo,
            aoMudar = { ligar ->
              if (ligar) definindoPin = true else pedindoPinAtual = AcaoComPin.DESATIVAR
            },
          )
          if (pinAtivo) {
            FilledTonalButton(
              onClick = { pedindoPinAtual = AcaoComPin.ALTERAR },
              shape = RoundedCornerShape(12.dp),
            ) {
              Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Alterar PIN", fontWeight = FontWeight.SemiBold)
            }
            if (aparelhoTemBiometria) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
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

      TituloSecao("Conta e equipe")
      SecaoContaApi(
        banco = banco,
        aoSair = onSairDaConta,
        aoAtualizarAssinatura = ::notificarMudancaAssinatura,
      )

      if (empresaAtualId.isNotBlank()) {
        TituloSecao("Plano e assinatura")
        SecaoPlanoAssinatura(
          empresaId = empresaAtualId,
          preferencias = preferencias,
          criarApi = ::criarApiAssinatura,
          guardarTokens = ::guardarTokensAssinatura,
          aoAtualizarAssinatura = ::notificarMudancaAssinatura,
        )
      }

      TituloSecao("Atualizações")
      CartaoSurface(raio = 20.dp) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          CabecalhoCardAjuste(
            icone = Icons.Default.Refresh,
            titulo = if (atualizacao != null) "Nova versão ${atualizacao.versao}" else "Aplicativo atualizado",
            subtitulo = if (atualizacao != null) "Pronta para baixar e instalar."
            else "Versão instalada: $versao",
            cor = if (atualizacao != null) estendidas.alerta else estendidas.sucesso,
            fundo = if (atualizacao != null) estendidas.alertaFundo else estendidas.sucessoFundo,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
              onClick = onVerNovidades,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f),
            ) { Text("Ver novidades") }
            if (atualizacao != null) {
              Button(
                onClick = onAtualizarApp,
                enabled = !baixandoAtualizacao,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
              ) { Text(if (baixandoAtualizacao) "Baixando..." else "Atualizar", fontWeight = FontWeight.Bold) }
            } else {
              FilledTonalButton(
                onClick = onProcurarAtualizacao,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
              ) { Text("Procurar", fontWeight = FontWeight.SemiBold) }
            }
          }
        }
      }

      TituloSecao("Sobre")
      CartaoSurface(raio = 20.dp) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          CabecalhoCardAjuste(
            icone = Icons.Default.Info,
            titulo = "ChaveiroCar $versao",
            subtitulo = "Sincronizado entre Android, iOS e Windows",
            cor = estendidas.residencial,
            fundo = estendidas.residencialFundo,
          )
          Text(
            text = "Seus atendimentos, fichas técnicas, códigos e fotos ficam protegidos e sincronizados automaticamente pela sua conta ChaveiroCar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (erroRegistrado != null) {
            OutlinedButton(
              onClick = { mostrandoErro = true },
              shape = RoundedCornerShape(12.dp),
            ) { Text("Ver último erro do app") }
          }
        }
      }
      Spacer(Modifier.height(20.dp))
    }
  }

  if (mostrandoErro) {
    val texto = erroRegistrado.orEmpty()
    AlertDialog(
      onDismissRequest = { mostrandoErro = false },
      shape = RoundedCornerShape(22.dp),
      title = { Text("Último erro do app", fontWeight = FontWeight.Bold) },
      text = {
        Column(modifier = Modifier.height(320.dp).verticalScroll(rememberScrollState())) {
          Text(text = texto, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            copiarTexto(contexto, "Erro do ChaveiroCar", texto)
            mostrandoErro = false
          },
          shape = RoundedCornerShape(12.dp),
        ) { Text("Copiar") }
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

@Composable
private fun CabecalhoCardAjuste(
  icone: ImageVector,
  titulo: String,
  subtitulo: String,
  cor: Color,
  fundo: Color,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(fundo),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icone, contentDescription = null, tint = cor, modifier = Modifier.size(20.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
    shape = RoundedCornerShape(22.dp),
    title = { Text("Definir PIN", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Use de 4 a 6 números que você não esqueça.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
          value = pin,
          onValueChange = { pin = it.filter { letra -> letra.isDigit() }.take(6) },
          label = { Text("PIN") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          visualTransformation = PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = confirmacao,
          onValueChange = { confirmacao = it.filter { letra -> letra.isDigit() }.take(6) },
          label = { Text("Confirme o PIN") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
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
      Button(
        onClick = {
          erro = when {
            pin.length < 4 || pin.length > 6 -> "O PIN precisa ter de 4 a 6 números."
            pin != confirmacao -> "Os PINs não são iguais."
            else -> null
          }
          if (erro == null) onSalvar(pin)
        },
        shape = RoundedCornerShape(12.dp),
      ) { Text("Ativar") }
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
    shape = RoundedCornerShape(22.dp),
    title = { Text(titulo, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = pin,
          onValueChange = { pin = it.filter { letra -> letra.isDigit() }.take(6) },
          label = { Text("PIN atual") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
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
      Button(
        onClick = {
          val sal = preferencias.getString(Preferencias.PIN_SAL, null)
          val hash = preferencias.getString(Preferencias.PIN_HASH, null)
          val correto = sal != null && hash != null && Seguranca.conferir(pin, sal, hash)
          if (correto) onConfirmado() else {
            erro = "PIN incorreto."
            pin = ""
          }
        },
        shape = RoundedCornerShape(12.dp),
      ) { Text("Confirmar") }
    },
    dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
  )
}
