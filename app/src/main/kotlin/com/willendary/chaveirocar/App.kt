package com.willendary.chaveirocar

import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.ui.MarcaChaveiro
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.willendary.chaveirocar.atualizacao.AtualizacaoDisponivel
import com.willendary.chaveirocar.atualizacao.Atualizacoes
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.BackupAutomatico
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.FichaTecnica
import com.willendary.chaveirocar.nuvem.ApiChaveiroCar
import com.willendary.chaveirocar.nuvem.GoogleDrive
import com.willendary.chaveirocar.nuvem.MotorSinc
import com.willendary.chaveirocar.ui.DialogoNovidades
import com.willendary.chaveirocar.ui.EstadoConsulta
import com.willendary.chaveirocar.ui.FiltroLista
import com.willendary.chaveirocar.ui.TelaAjustes
import com.willendary.chaveirocar.ui.TelaConsultaTecnica
import com.willendary.chaveirocar.ui.TelaBloqueio
import com.willendary.chaveirocar.ui.TelaDetalhe
import com.willendary.chaveirocar.ui.TelaFichario
import com.willendary.chaveirocar.ui.TelaFicharioModelo
import com.willendary.chaveirocar.ui.TelaFichaFormulario
import com.willendary.chaveirocar.ui.TelaFichaTecnica
import com.willendary.chaveirocar.ui.TelaFormulario
import com.willendary.chaveirocar.ui.TelaLista
import com.willendary.chaveirocar.ui.TelaLoginNuvem
import com.willendary.chaveirocar.util.Erros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Aba { HISTORICO, FICHARIO, CONSULTA, AJUSTES }

private const val TELA_PRINCIPAL = ""
private const val TELA_FORMULARIO = "formulario"
private const val TELA_DETALHE = "detalhe"
private const val TELA_FICHARIO_MODELO = "fichario_modelo"
private const val TELA_FICHA = "ficha"
private const val TELA_FICHA_FORMULARIO = "ficha_formulario"

private const val AVISO_INSTALACAO =
  "Autorize \"Instalar apps desconhecidos\" para o ChaveiroCar e toque em Atualizar de novo."

@Composable
fun ChaveiroCarApp() {
  val contexto = LocalContext.current
  val banco = remember { BancoDeAtendimentos(contexto) }
  val preferencias = remember { Preferencias.obter(contexto) }
  val escopo = rememberCoroutineScope()
  val cicloDeVida = LocalLifecycleOwner.current

  var aba by rememberSaveable { mutableStateOf(Aba.HISTORICO.name) }
  var tela by rememberSaveable { mutableStateOf(TELA_PRINCIPAL) }
  var idSelecionado by rememberSaveable { mutableStateOf(0L) }
  var baseParaNovo by remember { mutableStateOf<Atendimento?>(null) }
  var filtro by rememberSaveable(stateSaver = FiltroLista.Saver) { mutableStateOf(FiltroLista()) }

  var modeloMarca by rememberSaveable { mutableStateOf("") }
  var modeloNome by rememberSaveable { mutableStateOf("") }
  var modeloAno by rememberSaveable { mutableStateOf("") }
  var voltarParaFichario by rememberSaveable { mutableStateOf(false) }
  var idFicha by rememberSaveable { mutableStateOf(0L) }
  var baseFicha by remember { mutableStateOf<FichaTecnica?>(null) }
  var estadoConsulta by remember { mutableStateOf(EstadoConsulta()) }

  var bloqueado by remember { mutableStateOf(preferencias.getBoolean(Preferencias.PIN_ATIVO, false)) }
  var biometriaAtiva by remember {
    mutableStateOf(preferencias.getBoolean(Preferencias.BIOMETRIA_ATIVA, false))
  }

  var loginNuvemObrigatorio by remember {
    mutableStateOf(
      preferencias.getString(Preferencias.CONTA_NUVEM, null).isNullOrBlank() &&
        !preferencias.getBoolean(Preferencias.DRIVE_IGNORADO, false),
    )
  }

  var atualizacao by remember { mutableStateOf<AtualizacaoDisponivel?>(null) }
  var mostradorAtualizacao by remember { mutableStateOf(true) }
  var baixandoAtualizacao by remember { mutableStateOf(false) }
  var apkBaixado by remember { mutableStateOf<File?>(null) }

  fun instalarAtualizacao() {
    val disponivel = atualizacao ?: return
    if (baixandoAtualizacao) return
    val pronto = apkBaixado
    if (pronto != null && pronto.exists()) {
      if (!Atualizacoes.instalar(contexto, pronto)) {
        Toast.makeText(contexto, AVISO_INSTALACAO, Toast.LENGTH_LONG).show()
      }
      return
    }
    baixandoAtualizacao = true
    escopo.launch {
      runCatching { withContext(Dispatchers.IO) { Atualizacoes.baixar(contexto, disponivel) } }
        .onSuccess { arquivo ->
          apkBaixado = arquivo
          if (!Atualizacoes.instalar(contexto, arquivo)) {
            Toast.makeText(contexto, AVISO_INSTALACAO, Toast.LENGTH_LONG).show()
          }
        }
        .onFailure {
          Toast.makeText(contexto, "Não foi possível baixar a atualização", Toast.LENGTH_LONG).show()
        }
      baixandoAtualizacao = false
    }
  }

  val versaoAtual = remember { Atualizacoes.versaoInstalada(contexto) }
  var dialogoNovidades by rememberSaveable { mutableStateOf(false) }
  var notasNovidades by remember { mutableStateOf<String?>(null) }
  var carregandoNovidades by remember { mutableStateOf(false) }

  suspend fun abrirNovidades() {
    dialogoNovidades = true
    if (notasNovidades != null) return
    carregandoNovidades = true
    notasNovidades = withContext(Dispatchers.IO) { Atualizacoes.notasDaVersao(versaoAtual) }
    carregandoNovidades = false
  }

  fun fecharNovidades() {
    dialogoNovidades = false
    preferencias.edit().putString(Preferencias.VERSAO_VISTA, versaoAtual).apply()
  }

  DisposableEffect(cicloDeVida) {
    val observador = LifecycleEventObserver { _, evento ->
      when (evento) {
        Lifecycle.Event.ON_START -> {
          biometriaAtiva = preferencias.getBoolean(Preferencias.BIOMETRIA_ATIVA, false)
        }

        Lifecycle.Event.ON_STOP -> {
          if (preferencias.getBoolean(Preferencias.PIN_ATIVO, false)) bloqueado = true
        }

        else -> Unit
      }
    }
    cicloDeVida.lifecycle.addObserver(observador)
    onDispose { cicloDeVida.lifecycle.removeObserver(observador) }
  }

  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      val arquivo = runCatching { BackupAutomatico.executarSeNecessario(banco, contexto) }.getOrNull()
      val driveConectado = !preferencias.getString(Preferencias.CONTA_NUVEM, null).isNullOrBlank()
      if (arquivo != null && driveConectado) {
        runCatching { GoogleDrive.enviarAutomatico(contexto, arquivo) }
          .onFailure { Erros.registrar(contexto, "Envio automático para o Google Drive", it) }
      }
    }
  }

  LaunchedEffect(Unit) {
    atualizacao = withContext(Dispatchers.IO) {
      runCatching { Atualizacoes.verificar(versaoAtual) }.getOrNull()
    }
  }

  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      val acesso = Preferencias.obterApi(contexto).getString(Preferencias.API_ACESSO, "").orEmpty()
      val empresaId = preferencias.getString(Preferencias.API_EMPRESA_ID, "").orEmpty()
      val baseSalva = preferencias.getString(Preferencias.API_BASE_URL, null).orEmpty()
      val baseUrl = if (baseSalva.isBlank() || baseSalva.contains("10.0.2.2") || baseSalva.contains("localhost")) {
        ApiChaveiroCar.URL_PADRAO
      } else baseSalva
      if (acesso.isBlank() || empresaId.isBlank()) return@withContext
      runCatching {
        val api = ApiChaveiroCar(baseUrl.ifBlank { ApiChaveiroCar.URL_PADRAO })
        api.tokenAcesso = acesso
        api.tokenRefresh = Preferencias.obterApi(contexto).getString(Preferencias.API_REFRESH, null)?.ifBlank { null }
        val cursor = Preferencias.obterCursorApi(preferencias, empresaId)
        val resumo = MotorSinc.sincronizar(banco, api, empresaId, cursor, contexto)
        Preferencias.guardarCursorApi(preferencias, empresaId, resumo.cursor)
        Preferencias.obterApi(contexto).edit()
          .putString(Preferencias.API_ACESSO, api.tokenAcesso ?: "")
          .putString(Preferencias.API_REFRESH, api.tokenRefresh ?: "")
          .apply()
      }.onFailure { Erros.registrar(contexto, "Sincronização inicial", it) }
    }
  }

  LaunchedEffect(Unit) {
    val vista = preferencias.getString(Preferencias.VERSAO_VISTA, "")
    when {
      vista.isNullOrBlank() -> {
        preferencias.edit().putString(Preferencias.VERSAO_VISTA, versaoAtual).apply()
        // 1.0/1.1 não guardavam a versão vista: quem já tem histórico veio de atualização.
        val veioDeVersaoAnterior = withContext(Dispatchers.IO) { banco.contarTodos() > 0 }
        if (veioDeVersaoAnterior) abrirNovidades()
      }

      vista != versaoAtual -> abrirNovidades()
    }
  }

  fun voltarDoDetalhe() {
    tela = if (voltarParaFichario) TELA_FICHARIO_MODELO else TELA_PRINCIPAL
    if (!voltarParaFichario) idSelecionado = 0L
  }

  BackHandler(enabled = loginNuvemObrigatorio) {}
  BackHandler(enabled = bloqueado) {}

  BackHandler(
    enabled = !bloqueado && tela == TELA_FORMULARIO,
  ) {
    tela = if (idSelecionado > 0L) TELA_DETALHE else TELA_PRINCIPAL
  }

  BackHandler(
    enabled = !bloqueado && (tela == TELA_DETALHE || tela == TELA_FICHARIO_MODELO),
  ) {
    if (tela == TELA_DETALHE) voltarDoDetalhe() else tela = TELA_PRINCIPAL
  }

  BackHandler(enabled = !bloqueado && tela == TELA_FICHA) { tela = TELA_PRINCIPAL }

  BackHandler(
    enabled = !bloqueado && tela == TELA_PRINCIPAL && aba != Aba.HISTORICO.name,
  ) {
    aba = Aba.HISTORICO.name
  }

  Box(modifier = Modifier.fillMaxSize()) {
    when (tela) {
      TELA_FORMULARIO ->
        TelaFormulario(
          banco = banco,
          id = idSelecionado,
          base = if (idSelecionado == 0L) baseParaNovo else null,
          onSalvo = { id ->
            idSelecionado = id
            baseParaNovo = null
            tela = TELA_DETALHE
          },
          onVoltar = {
            tela = if (idSelecionado > 0L) TELA_DETALHE else TELA_PRINCIPAL
          },
        )

      TELA_DETALHE ->
        TelaDetalhe(
          banco = banco,
          id = idSelecionado,
          onEditar = { tela = TELA_FORMULARIO },
          onDuplicar = { registro ->
            baseParaNovo = registro.copy(
              id = 0,
              dataServico = System.currentTimeMillis(),
              defeito = "",
              solucao = "",
              ferramenta = "",
              valorCentavos = 0,
              quantidadeFotos = 0,
            )
            voltarParaFichario = false
            idSelecionado = 0L
            tela = TELA_FORMULARIO
          },
          onExcluido = {
            baseParaNovo = null
            idSelecionado = 0L
            tela = if (voltarParaFichario) TELA_FICHARIO_MODELO else TELA_PRINCIPAL
          },
          onVoltar = { voltarDoDetalhe() },
        )

      TELA_FICHA ->
        TelaFichaTecnica(
          banco = banco,
          id = idFicha,
          onEditar = { tela = TELA_FICHA_FORMULARIO },
          onExcluido = {
            idFicha = 0L
            baseFicha = null
            tela = TELA_PRINCIPAL
          },
          onVoltar = { tela = TELA_PRINCIPAL },
        )

      TELA_FICHA_FORMULARIO ->
        TelaFichaFormulario(
          banco = banco,
          id = idFicha,
          base = if (idFicha == 0L) baseFicha else null,
          onSalvo = { novoId ->
            idFicha = novoId
            baseFicha = null
            tela = TELA_FICHA
          },
          onVoltar = { tela = if (idFicha > 0L) TELA_FICHA else TELA_PRINCIPAL },
        )

      TELA_FICHARIO_MODELO ->
        TelaFicharioModelo(
          banco = banco,
          marca = modeloMarca,
          modelo = modeloNome,
          ano = modeloAno,
          onAbrirServico = { id ->
            idSelecionado = id
            voltarParaFichario = true
            tela = TELA_DETALHE
          },
          onVoltar = { tela = TELA_PRINCIPAL },
        )

      else ->
        Scaffold(
          bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
              NavigationBarItem(
                selected = aba == Aba.HISTORICO.name,
                onClick = { aba = Aba.HISTORICO.name },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                label = { Text("Serviços") },
              )
              NavigationBarItem(
                selected = aba == Aba.FICHARIO.name,
                onClick = { aba = Aba.FICHARIO.name },
                icon = { Icon(Icons.Default.Build, contentDescription = null) },
                label = { Text("Fichário") },
              )
              NavigationBarItem(
                selected = aba == Aba.CONSULTA.name,
                onClick = { aba = Aba.CONSULTA.name },
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Consulta") },
              )
              NavigationBarItem(
                selected = aba == Aba.AJUSTES.name,
                onClick = { aba = Aba.AJUSTES.name },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                label = { Text("Ajustes") },
              )
            }
          },
          contentWindowInsets = WindowInsets(0, 0, 0, 0),
          containerColor = MaterialTheme.colorScheme.background,
        ) { espacamento ->
          Box(modifier = Modifier.fillMaxSize().padding(espacamento)) {
            when (aba) {
              Aba.FICHARIO.name ->
                TelaFichario(
                  banco = banco,
                  onAbrirModelo = { modelo ->
                    modeloMarca = modelo.marca
                    modeloNome = modelo.modelo
                    modeloAno = modelo.ano
                    tela = TELA_FICHARIO_MODELO
                  },
                )

              Aba.CONSULTA.name ->
                TelaConsultaTecnica(
                  banco = banco,
                  estadoInicial = estadoConsulta,
                  aoMudarEstado = { estadoConsulta = it },
                  onAbrir = { id ->
                    idFicha = id
                    tela = TELA_FICHA
                  },
                  onNova = { nova ->
                    baseFicha = nova
                    idFicha = 0L
                    tela = TELA_FICHA_FORMULARIO
                  },
                )

              Aba.AJUSTES.name ->
                TelaAjustes(
                  banco = banco,
                  atualizacao = atualizacao,
                  baixandoAtualizacao = baixandoAtualizacao,
                  onAtualizarApp = { instalarAtualizacao() },
                  onProcurarAtualizacao = {
                    escopo.launch {
                      val encontrada = withContext(Dispatchers.IO) {
                        runCatching { Atualizacoes.verificar(versaoAtual) }.getOrNull()
                      }
                      atualizacao = encontrada
                      if (encontrada == null) {
                        Toast.makeText(contexto, "Você já está na versão mais nova", Toast.LENGTH_SHORT).show()
                      }
                    }
                  },
                  onVerNovidades = { escopo.launch { abrirNovidades() } },
                  onDadosRestaurados = {
                    aba = Aba.HISTORICO.name
                    filtro = FiltroLista()
                  },
                )

              else ->
                TelaLista(
                  banco = banco,
                  filtro = filtro,
                  aoMudarFiltro = { filtro = it },
                  onAbrir = { id ->
                    idSelecionado = id
                    voltarParaFichario = false
                    tela = TELA_DETALHE
                  },
                  onNovo = {
                    baseParaNovo = null
                    voltarParaFichario = false
                    idSelecionado = 0L
                    tela = TELA_FORMULARIO
                  },
                )
            }
          }
        }
    }

    if (!bloqueado && dialogoNovidades) {
      DialogoNovidades(
        versao = versaoAtual,
        notas = notasNovidades,
        carregando = carregandoNovidades,
        onFechar = { fecharNovidades() },
      )
    }

    val disponivel = atualizacao
    if (!loginNuvemObrigatorio && !bloqueado && disponivel != null && mostradorAtualizacao) {
      AlertDialog(
        onDismissRequest = {
          if (!baixandoAtualizacao && apkBaixado == null) mostradorAtualizacao = false
        },
        title = { Text("Nova versão disponível: ${disponivel.versao}") },
        text = {
          Column {
            val notas = disponivel.notas.trim()
            if (notas.isNotBlank()) {
              Text(
                text = notas.take(600),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 8,
              )
            } else {
              Text(
                "Traga a versão ${disponivel.versao} para continuar com as correções.",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { instalarAtualizacao() }, enabled = !baixandoAtualizacao) {
            Text(if (baixandoAtualizacao) "Baixando..." else "Atualizar agora")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { mostradorAtualizacao = false },
            enabled = !baixandoAtualizacao,
          ) {
            Text("Depois")
          }
        },
      )
    }

    if (loginNuvemObrigatorio) {
      TelaLoginNuvem(
        aoConectar = { email ->
          preferencias.edit()
            .putString(Preferencias.CONTA_NUVEM, email.orEmpty())
            .putBoolean(Preferencias.DRIVE_IGNORADO, email.isNullOrBlank())
            .apply()
          loginNuvemObrigatorio = false
        },
      )
    }

    if (bloqueado) {
      TelaBloqueio(
        biometriaAtiva = biometriaAtiva,
        aoDesbloquear = { bloqueado = false },
      )
    }
  }
}
