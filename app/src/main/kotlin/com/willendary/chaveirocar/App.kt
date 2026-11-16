package com.willendary.chaveirocar

import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.ui.MarcaChaveiro
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.FichaTecnica
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.modelo.TipoServico
import com.willendary.chaveirocar.nuvem.ApiChaveiroCar
import com.willendary.chaveirocar.nuvem.MotorSinc
import com.willendary.chaveirocar.ui.DialogoNovidades
import com.willendary.chaveirocar.ui.DialogoPlanoAssinatura
import com.willendary.chaveirocar.ui.EstadoConsulta
import com.willendary.chaveirocar.ui.FiltroLista
import com.willendary.chaveirocar.ui.TelaAjustes
import com.willendary.chaveirocar.ui.TelaAutenticacaoObrigatoria
import com.willendary.chaveirocar.ui.TelaConsultaTecnica
import com.willendary.chaveirocar.ui.TelaBloqueio
import com.willendary.chaveirocar.ui.TelaDetalhe
import com.willendary.chaveirocar.ui.TelaEscolhaModalidade
import com.willendary.chaveirocar.ui.TelaFichario
import com.willendary.chaveirocar.ui.TelaFicharioModelo
import com.willendary.chaveirocar.ui.TelaFicharioResidencial
import com.willendary.chaveirocar.ui.TelaFichaFormulario
import com.willendary.chaveirocar.ui.TelaFichaTecnica
import com.willendary.chaveirocar.ui.TelaFormulario
import com.willendary.chaveirocar.ui.TelaLista
import com.willendary.chaveirocar.util.Erros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Aba { HISTORICO, FICHARIO, CONSULTA, AJUSTES }

private const val TELA_PRINCIPAL = ""
private const val TELA_FORMULARIO = "formulario"
private const val TELA_DETALHE = "detalhe"
private const val TELA_FICHARIO_MODELO = "fichario_modelo"
private const val TELA_FICHARIO_RESIDENCIAL = "fichario_residencial"
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
  var resTipoLocal by rememberSaveable { mutableStateOf("") }
  var resAmbientePorta by rememberSaveable { mutableStateOf("") }
  var resMarcaFechadura by rememberSaveable { mutableStateOf("") }
  var resModeloFechadura by rememberSaveable { mutableStateOf("") }
  var resTipoCilindro by rememberSaveable { mutableStateOf("") }
  var voltarParaFichario by rememberSaveable { mutableStateOf(false) }
  var voltarParaFicharioResidencial by rememberSaveable { mutableStateOf(false) }
  var idFicha by rememberSaveable { mutableStateOf(0L) }
  var idFichaAntesEdicao by rememberSaveable { mutableStateOf(0L) }
  var telaAnteriorFicha by rememberSaveable { mutableStateOf(TELA_PRINCIPAL) }
  var baseFicha by remember { mutableStateOf<FichaTecnica?>(null) }
  var estadoConsulta by remember { mutableStateOf(EstadoConsulta()) }

  var modalidadeAtiva by rememberSaveable { mutableStateOf<SegmentoAtendimento?>(null) }
  var ajustesDeEscolha by rememberSaveable { mutableStateOf(false) }
  var mostrarPlanoDialogo by remember { mutableStateOf(false) }
  var empresaAtualId by remember {
    mutableStateOf(preferencias.getString(Preferencias.API_EMPRESA_ID, "").orEmpty())
  }
  var modulosAtivos by remember {
    mutableStateOf(Preferencias.obterModulosAtivos(preferencias, empresaAtualId))
  }
  var statusAssinatura by remember {
    mutableStateOf(Preferencias.obterStatusAssinatura(preferencias, empresaAtualId))
  }
  var papelEmpresaAtual by remember { mutableStateOf("") }
  val podeEditar = papelEmpresaAtual != "leitor"

  var fichaSobreFormularioId by remember { mutableStateOf<Long?>(null) }
  var consultaSobreFormulario by remember { mutableStateOf<EstadoConsulta?>(null) }
  var consultaSobreFormularioSegmento by remember { mutableStateOf<SegmentoAtendimento?>(null) }
  var listaFichasFormulario by remember { mutableStateOf<List<FichaTecnica>?>(null) }
  var listaFichasNavegacao by remember { mutableStateOf<Pair<List<FichaTecnica>, String>?>(null) }

  var bloqueado by remember { mutableStateOf(preferencias.getBoolean(Preferencias.PIN_ATIVO, false)) }
  var biometriaAtiva by remember {
    mutableStateOf(preferencias.getBoolean(Preferencias.BIOMETRIA_ATIVA, false))
  }
  var autenticado by remember {
    mutableStateOf(!Preferencias.obterApi(contexto).getString(Preferencias.API_ACESSO, "").isNullOrBlank())
  }
  var versaoSincronia by remember { mutableStateOf(0) }
  var sincronizandoNuvem by remember { mutableStateOf(false) }

  fun atualizarModulosLocais() {
    val idAtual = preferencias.getString(Preferencias.API_EMPRESA_ID, "").orEmpty()
    empresaAtualId = idAtual
    modulosAtivos = Preferencias.obterModulosAtivos(preferencias, idAtual)
    statusAssinatura = Preferencias.obterStatusAssinatura(preferencias, idAtual)
    if (modalidadeAtiva != null && modalidadeAtiva !in modulosAtivos) {
      modalidadeAtiva = null
      if (tela != TELA_PRINCIPAL) tela = TELA_PRINCIPAL
    }
  }

  suspend fun dispararSincronizacao() {
    if (sincronizandoNuvem) return
    sincronizandoNuvem = true
    try {
      val pacote = withContext(Dispatchers.IO) {
        val acesso = Preferencias.obterApi(contexto).getString(Preferencias.API_ACESSO, "").orEmpty()
        var empresaId = preferencias.getString(Preferencias.API_EMPRESA_ID, "").orEmpty()
        val baseSalva = preferencias.getString(Preferencias.API_BASE_URL, null).orEmpty()
        val baseUrl = if (baseSalva.isBlank() || baseSalva.contains("10.0.2.2") || baseSalva.contains("localhost")) {
          ApiChaveiroCar.URL_PADRAO
        } else baseSalva
        if (acesso.isBlank()) return@withContext null
        runCatching {
          val api = ApiChaveiroCar(baseUrl.ifBlank { ApiChaveiroCar.URL_PADRAO })
          api.tokenAcesso = acesso
          api.tokenRefresh = Preferencias.obterApi(contexto).getString(Preferencias.API_REFRESH, null)?.ifBlank { null }
          api.aoRenovarToken = { novoAcesso, novoRefresh ->
            Preferencias.obterApi(contexto).edit()
              .putString(Preferencias.API_ACESSO, novoAcesso)
              .putString(Preferencias.API_REFRESH, novoRefresh)
              .apply()
          }
          var papelEncontrado = ""
          val listaEmpresas = runCatching { api.listarEmpresas() }.getOrNull()
          if (listaEmpresas != null) {
            for (i in 0 until listaEmpresas.length()) {
              val emp = listaEmpresas.optJSONObject(i) ?: continue
              val empId = emp.optString("id")
              if (empId.isBlank()) continue
              val arr = emp.optJSONArray("modulosAtivos")
              val mods = if (arr != null) {
                buildList {
                  for (j in 0 until arr.length()) {
                    arr.optString(j).takeIf { it.isNotBlank() }?.let(::add)
                  }
                }
              } else listOf("AUTOMOTIVO", "RESIDENCIAL")
              val renov = if (emp.isNull("proximaRenovacaoEm")) null
              else emp.optLong("proximaRenovacaoEm").takeIf { it > 0L }
              Preferencias.guardarAssinaturaEmpresa(
                preferencias = preferencias,
                empresaId = empId,
                planoCodigo = emp.optString("planoCodigo", "COMPLETO"),
                status = emp.optString("assinaturaStatus", "ATIVA"),
                modulosAtivos = mods,
                proximaRenovacaoEm = renov,
              )
              if (empresaId.isBlank() && i == 0) {
                empresaId = empId
                preferencias.edit().putString(Preferencias.API_EMPRESA_ID, empresaId).apply()
              }
              if (empId == empresaId) {
                papelEncontrado = emp.optString("papel")
              }
            }
          }
          if (empresaId.isBlank()) return@withContext null
          val cursor = Preferencias.obterCursorApi(preferencias, empresaId)
          val resultado = MotorSinc.sincronizar(banco, api, empresaId, cursor, contexto)
          Preferencias.guardarCursorApi(preferencias, empresaId, resultado.cursor)
          Preferencias.obterApi(contexto).edit()
            .putString(Preferencias.API_ACESSO, api.tokenAcesso ?: "")
            .putString(Preferencias.API_REFRESH, api.tokenRefresh ?: "")
            .apply()
          Pair(resultado, papelEncontrado)
        }.onFailure { ex ->
          if (ex is com.willendary.chaveirocar.nuvem.ExpiradoException) {
            withContext(Dispatchers.Main) {
              autenticado = false
              Preferencias.obterApi(contexto).edit()
                .remove(Preferencias.API_ACESSO)
                .remove(Preferencias.API_REFRESH)
                .apply()
              Toast.makeText(contexto, "Sua sessão expirou. Faça login novamente.", Toast.LENGTH_LONG).show()
            }
          } else {
            Erros.registrar(contexto, "Sincronização automática", ex)
          }
        }.getOrNull()
      }
      if (pacote != null) {
        val (resumo, papelEncontrado) = pacote
        if (papelEncontrado.isNotBlank()) papelEmpresaAtual = papelEncontrado
        atualizarModulosLocais()
        if (resumo.enviadas > 0 || resumo.recebidas > 0 || resumo.conflitos > 0) {
          versaoSincronia++
        }
      } else {
        atualizarModulosLocais()
      }
    } finally {
      sincronizandoNuvem = false
    }
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

  DisposableEffect(cicloDeVida, autenticado) {
    val observador = LifecycleEventObserver { _, evento ->
      when (evento) {
        Lifecycle.Event.ON_START -> {
          biometriaAtiva = preferencias.getBoolean(Preferencias.BIOMETRIA_ATIVA, false)
          if (autenticado) {
            escopo.launch { dispararSincronizacao() }
          }
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
    atualizacao = withContext(Dispatchers.IO) {
      runCatching { Atualizacoes.verificar(versaoAtual) }.getOrNull()
    }
  }

  LaunchedEffect(autenticado) {
    if (!autenticado) return@LaunchedEffect
    while (isActive) {
      dispararSincronizacao()
      delay(25_000)
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
    tela = when {
      voltarParaFichario -> TELA_FICHARIO_MODELO
      voltarParaFicharioResidencial -> TELA_FICHARIO_RESIDENCIAL
      else -> TELA_PRINCIPAL
    }
    if (!voltarParaFichario && !voltarParaFicharioResidencial) idSelecionado = 0L
  }

  fun alvoFichaDe(atendimento: Atendimento): Pair<String, String> =
    if (atendimento.segmento == SegmentoAtendimento.RESIDENCIAL) {
      atendimento.marcaFechadura to atendimento.modeloFechadura
    } else {
      atendimento.marca to atendimento.modelo
    }

  fun abrirFichaContextual(atendimento: Atendimento, origem: String) {
    escopo.launch {
      val (marcaAlvo, modeloAlvo) = alvoFichaDe(atendimento)
      val fichas = withContext(Dispatchers.IO) {
        banco.buscarFichasDoVeiculo(marcaAlvo, modeloAlvo, atendimento.segmento)
      }
      when {
        fichas.size == 1 -> {
          idFicha = fichas.first().id
          telaAnteriorFicha = origem
          tela = TELA_FICHA
        }
        fichas.size > 1 -> {
          listaFichasNavegacao = fichas to origem
        }
        else -> {
          if (atendimento.segmento in modulosAtivos) {
            modalidadeAtiva = atendimento.segmento
            estadoConsulta = EstadoConsulta(
              busca = listOf(marcaAlvo, modeloAlvo).filter { it.isNotBlank() }.joinToString(" "),
            )
            aba = Aba.CONSULTA.name
            tela = TELA_PRINCIPAL
            Toast.makeText(contexto, "Nenhuma ficha exata encontrada. Consulta aberta com sua busca.", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(contexto, "Nenhuma ficha técnica encontrada para este atendimento.", Toast.LENGTH_SHORT).show()
          }
        }
      }
    }
  }

  fun abrirFichaSobreFormulario(atendimento: Atendimento) {
    escopo.launch {
      val (marcaAlvo, modeloAlvo) = alvoFichaDe(atendimento)
      val fichas = withContext(Dispatchers.IO) {
        banco.buscarFichasDoVeiculo(marcaAlvo, modeloAlvo, atendimento.segmento)
      }
      when {
        fichas.size == 1 -> fichaSobreFormularioId = fichas.first().id
        fichas.size > 1 -> listaFichasFormulario = fichas
        else -> {
          consultaSobreFormularioSegmento = atendimento.segmento
          consultaSobreFormulario = EstadoConsulta(
            busca = listOf(marcaAlvo, modeloAlvo).filter { it.isNotBlank() }.joinToString(" "),
          )
        }
      }
    }
  }

  val trocarArea = {
    modalidadeAtiva = null
    ajustesDeEscolha = false
    tela = TELA_PRINCIPAL
  }

  val atualizarStatusAssinatura = {
    atualizarModulosLocais()
    escopo.launch { dispararSincronizacao() }
    Unit
  }

  val sairDaConta = {
    autenticado = false
    modalidadeAtiva = null
    ajustesDeEscolha = false
    aba = Aba.HISTORICO.name
    tela = TELA_PRINCIPAL
  }

  BackHandler(enabled = bloqueado) {}

  BackHandler(enabled = !bloqueado && tela == TELA_FORMULARIO) {
    when {
      fichaSobreFormularioId != null -> fichaSobreFormularioId = null
      consultaSobreFormulario != null -> consultaSobreFormulario = null
      else -> tela = if (idSelecionado > 0L) TELA_DETALHE else TELA_PRINCIPAL
    }
  }

  BackHandler(
    enabled = !bloqueado && (tela == TELA_DETALHE || tela == TELA_FICHARIO_MODELO || tela == TELA_FICHARIO_RESIDENCIAL),
  ) {
    if (tela == TELA_DETALHE) voltarDoDetalhe() else tela = TELA_PRINCIPAL
  }

  BackHandler(enabled = !bloqueado && tela == TELA_FICHA) { tela = telaAnteriorFicha }

  BackHandler(enabled = !bloqueado && tela == TELA_FICHA_FORMULARIO) {
    if (idFicha > 0L) {
      tela = TELA_FICHA
    } else if (idFichaAntesEdicao < 0L) {
      idFicha = idFichaAntesEdicao
      idFichaAntesEdicao = 0L
      tela = TELA_FICHA
    } else {
      tela = telaAnteriorFicha
    }
  }

  BackHandler(
    enabled = !bloqueado && tela == TELA_PRINCIPAL && modalidadeAtiva == null && ajustesDeEscolha,
  ) {
    ajustesDeEscolha = false
  }

  BackHandler(
    enabled = !bloqueado && tela == TELA_PRINCIPAL && modalidadeAtiva != null && aba != Aba.HISTORICO.name,
  ) {
    aba = Aba.HISTORICO.name
  }

  Box(modifier = Modifier.fillMaxSize()) {
    if (!autenticado) {
      TelaAutenticacaoObrigatoria(
        banco = banco,
        aoAutenticar = {
          atualizarModulosLocais()
          modalidadeAtiva = null
          ajustesDeEscolha = false
          autenticado = true
        },
      )
    } else when (tela) {
      TELA_FORMULARIO ->
        Box(modifier = Modifier.fillMaxSize()) {
          TelaFormulario(
            banco = banco,
            id = idSelecionado,
            base = if (idSelecionado == 0L) baseParaNovo else null,
            onSalvo = { id ->
              fichaSobreFormularioId = null
              consultaSobreFormulario = null
              listaFichasFormulario = null
              idSelecionado = id
              baseParaNovo = null
              tela = TELA_DETALHE
              escopo.launch { dispararSincronizacao() }
            },
            onVoltar = {
              fichaSobreFormularioId = null
              consultaSobreFormulario = null
              listaFichasFormulario = null
              tela = if (idSelecionado > 0L) TELA_DETALHE else TELA_PRINCIPAL
            },
            segmentoFixo = modalidadeAtiva != null,
            onVerFicha = { reg -> abrirFichaSobreFormulario(reg) },
          )

          val idSobreForm = fichaSobreFormularioId
          val consultaSobre = consultaSobreFormulario
          if (idSobreForm != null) {
            BackHandler(enabled = !bloqueado) { fichaSobreFormularioId = null }
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
              TelaFichaTecnica(
                banco = banco,
                id = idSobreForm,
                onEditar = {},
                onExcluido = { fichaSobreFormularioId = null },
                onVoltar = { fichaSobreFormularioId = null },
                podeEditar = false,
              )
            }
          } else if (consultaSobre != null) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
              TelaConsultaTecnica(
                banco = banco,
                estadoInicial = consultaSobre,
                aoMudarEstado = { consultaSobreFormulario = it },
                onAbrir = { idF -> fichaSobreFormularioId = idF },
                onNova = {},
                versaoSincronia = versaoSincronia,
                segmento = consultaSobreFormularioSegmento,
                incluirCatalogoPadrao = true,
                podeEditar = false,
                onVoltar = { consultaSobreFormulario = null },
              )
            }
          }
        }

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
            voltarParaFicharioResidencial = false
            idSelecionado = 0L
            tela = TELA_FORMULARIO
          },
          onExcluido = {
            baseParaNovo = null
            idSelecionado = 0L
            tela = when {
              voltarParaFichario -> TELA_FICHARIO_MODELO
              voltarParaFicharioResidencial -> TELA_FICHARIO_RESIDENCIAL
              else -> TELA_PRINCIPAL
            }
            escopo.launch { dispararSincronizacao() }
          },
          onVoltar = { voltarDoDetalhe() },
          onVerFicha = { reg -> abrirFichaContextual(reg, origem = TELA_DETALHE) },
        )

      TELA_FICHA ->
        TelaFichaTecnica(
          banco = banco,
          id = idFicha,
          onEditar = {
            idFichaAntesEdicao = idFicha
            tela = TELA_FICHA_FORMULARIO
          },
          onExcluido = {
            idFicha = 0L
            idFichaAntesEdicao = 0L
            baseFicha = null
            tela = telaAnteriorFicha
            escopo.launch { dispararSincronizacao() }
          },
          onVoltar = { tela = telaAnteriorFicha },
          onEditarOficial = { oficial ->
            idFichaAntesEdicao = idFicha
            baseFicha = oficial.copy(
              id = 0L,
              origem = "Anotação da empresa (baseada no catálogo oficial)",
              atualizadoEm = 0L,
            )
            idFicha = 0L
            tela = TELA_FICHA_FORMULARIO
          },
          onCriarServico = { ficha ->
            val padrao = TipoServico.padraoDoSegmento(ficha.segmento)
            modalidadeAtiva = ficha.segmento
            baseParaNovo = if (ficha.segmento == SegmentoAtendimento.RESIDENCIAL) {
              Atendimento(
                segmento = ficha.segmento,
                marcaFechadura = ficha.marca,
                modeloFechadura = ficha.modelo,
                ferramenta = ficha.equipamento,
                abertura = listOf(ficha.sistema, ficha.chave).filter { it.isNotBlank() }.joinToString(" · "),
                tipoServico = padrao,
                tiposServico = setOf(padrao),
              )
            } else {
              Atendimento(
                segmento = ficha.segmento,
                marca = ficha.marca,
                modelo = ficha.modelo,
                ano = ficha.ano,
                ferramenta = ficha.equipamento,
                abertura = listOf(ficha.sistema, ficha.chip, ficha.chave).filter { it.isNotBlank() }.joinToString(" · "),
                tipoServico = padrao,
                tiposServico = setOf(padrao),
              )
            }
            voltarParaFichario = false
            voltarParaFicharioResidencial = false
            idSelecionado = 0L
            tela = TELA_FORMULARIO
          },
          podeEditar = podeEditar,
        )

      TELA_FICHA_FORMULARIO ->
        TelaFichaFormulario(
          banco = banco,
          id = idFicha,
          base = if (idFicha == 0L) baseFicha else null,
          onSalvo = { novoId ->
            idFicha = novoId
            idFichaAntesEdicao = 0L
            baseFicha = null
            tela = TELA_FICHA
            escopo.launch { dispararSincronizacao() }
          },
          onVoltar = {
            if (idFicha > 0L) {
              tela = TELA_FICHA
            } else if (idFichaAntesEdicao < 0L) {
              idFicha = idFichaAntesEdicao
              idFichaAntesEdicao = 0L
              tela = TELA_FICHA
            } else {
              tela = telaAnteriorFicha
            }
          },
          segmentoFixo = if (idFicha == 0L) (baseFicha?.segmento ?: modalidadeAtiva) else null,
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
            voltarParaFicharioResidencial = false
            tela = TELA_DETALHE
          },
          onVoltar = { tela = TELA_PRINCIPAL },
          onVerFicha = {
            abrirFichaContextual(
              Atendimento(
                segmento = SegmentoAtendimento.AUTOMOTIVO,
                marca = modeloMarca,
                modelo = modeloNome,
                ano = modeloAno,
              ),
              origem = TELA_FICHARIO_MODELO,
            )
          },
        )

      TELA_FICHARIO_RESIDENCIAL ->
        TelaFicharioResidencial(
          banco = banco,
          tipoLocal = resTipoLocal,
          marcaFechadura = resMarcaFechadura,
          modeloFechadura = resModeloFechadura,
          onAbrirServico = { id ->
            idSelecionado = id
            voltarParaFichario = false
            voltarParaFicharioResidencial = true
            tela = TELA_DETALHE
          },
          onVoltar = { tela = TELA_PRINCIPAL },
        )

      else -> {
        val modalidade = modalidadeAtiva
        if (modalidade == null) {
          if (ajustesDeEscolha) {
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
              onSairDaConta = sairDaConta,
              aoAtualizarAssinatura = atualizarStatusAssinatura,
              onVoltar = { ajustesDeEscolha = false },
            )
          } else {
            TelaEscolhaModalidade(
              modulosAtivos = modulosAtivos,
              statusAssinatura = statusAssinatura,
              sincronizando = sincronizandoNuvem,
              onSelecionarArea = { area ->
                modalidadeAtiva = area
                aba = Aba.HISTORICO.name
              },
              onAbrirConsulta = { area ->
                modalidadeAtiva = area
                aba = Aba.CONSULTA.name
              },
              onGerenciarPlano = { mostrarPlanoDialogo = true },
              onAbrirAjustes = { ajustesDeEscolha = true },
            )
          }
        } else {
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
                    onAbrirResidencial = { item ->
                      resTipoLocal = item.tipoLocal
                      resMarcaFechadura = item.marcaFechadura
                      resModeloFechadura = item.modeloFechadura
                      tela = TELA_FICHARIO_RESIDENCIAL
                    },
                    versaoSincronia = versaoSincronia,
                    segmentoFixo = modalidade,
                    onTrocarArea = trocarArea,
                  )

                Aba.CONSULTA.name ->
                  TelaConsultaTecnica(
                    banco = banco,
                    estadoInicial = estadoConsulta,
                    aoMudarEstado = { estadoConsulta = it },
                    onAbrir = { id ->
                      idFicha = id
                      telaAnteriorFicha = TELA_PRINCIPAL
                      tela = TELA_FICHA
                    },
                    onNova = { nova ->
                      baseFicha = nova.copy(segmento = modalidade)
                      idFicha = 0L
                      idFichaAntesEdicao = 0L
                      telaAnteriorFicha = TELA_PRINCIPAL
                      tela = TELA_FICHA_FORMULARIO
                    },
                    versaoSincronia = versaoSincronia,
                    segmento = modalidade,
                    incluirCatalogoPadrao = true,
                    podeEditar = podeEditar,
                    onTrocarArea = trocarArea,
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
                    onSairDaConta = sairDaConta,
                    aoAtualizarAssinatura = atualizarStatusAssinatura,
                  )

                else ->
                  TelaLista(
                    banco = banco,
                    filtro = filtro,
                    aoMudarFiltro = { filtro = it },
                    onAbrir = { id ->
                      idSelecionado = id
                      voltarParaFichario = false
                      voltarParaFicharioResidencial = false
                      tela = TELA_DETALHE
                    },
                    onNovo = {
                      val padrao = TipoServico.padraoDoSegmento(modalidade)
                      baseParaNovo = Atendimento(
                        segmento = modalidade,
                        tipoServico = padrao,
                        tiposServico = setOf(padrao),
                      )
                      voltarParaFichario = false
                      voltarParaFicharioResidencial = false
                      idSelecionado = 0L
                      tela = TELA_FORMULARIO
                    },
                    versaoSincronia = versaoSincronia,
                    sincronizando = sincronizandoNuvem,
                    modalidadeAtiva = modalidade,
                    onTrocarArea = trocarArea,
                  )
              }
            }
          }
        }
      }
    }

    if (mostrarPlanoDialogo) {
      DialogoPlanoAssinatura(
        onFechar = { mostrarPlanoDialogo = false },
        aoAtualizarAssinatura = atualizarStatusAssinatura,
      )
    }

    listaFichasFormulario?.let { opcoes ->
      DialogoEscolhaFicha(
        fichas = opcoes,
        aoEscolher = { escolhida ->
          listaFichasFormulario = null
          fichaSobreFormularioId = escolhida.id
        },
        aoFechar = { listaFichasFormulario = null },
      )
    }

    listaFichasNavegacao?.let { (opcoes, origem) ->
      DialogoEscolhaFicha(
        fichas = opcoes,
        aoEscolher = { escolhida ->
          listaFichasNavegacao = null
          idFicha = escolhida.id
          telaAnteriorFicha = origem
          tela = TELA_FICHA
        },
        aoFechar = { listaFichasNavegacao = null },
      )
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
    if (!bloqueado && disponivel != null && mostradorAtualizacao) {
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

    if (bloqueado) {
      TelaBloqueio(
        biometriaAtiva = biometriaAtiva,
        aoDesbloquear = { bloqueado = false },
      )
    }
  }
}

@Composable
private fun DialogoEscolhaFicha(
  fichas: List<FichaTecnica>,
  aoEscolher: (FichaTecnica) -> Unit,
  aoFechar: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = aoFechar,
    title = { Text("Escolha a ficha técnica") },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        fichas.forEach { ficha ->
          val titulo = listOf(ficha.marca, ficha.modelo, ficha.ano).filter { it.isNotBlank() }.joinToString(" ")
          val detalhe = listOf(ficha.equipamento, ficha.sistema, ficha.chip).filter { it.isNotBlank() }.joinToString(" · ")
          OutlinedButton(
            onClick = { aoEscolher(ficha) },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.fillMaxWidth()) {
              Text(titulo.ifBlank { ficha.sistema.ifBlank { "Ficha" } })
              if (detalhe.isNotBlank()) {
                Text(
                  text = detalhe,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = aoFechar) { Text("Cancelar") }
    },
  )
}
