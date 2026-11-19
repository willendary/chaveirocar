package com.willendary.chaveirocar.ui

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.nuvem.ApiChaveiroCar
import com.willendary.chaveirocar.util.formatarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class EstadoAssinaturaUi(
  val planoCodigo: String = "COMPLETO",
  val status: String = "ATIVA",
  val modulosAtivos: Set<SegmentoAtendimento> = SegmentoAtendimento.entries.toSet(),
  val modulosContratados: Set<SegmentoAtendimento> = SegmentoAtendimento.entries.toSet(),
  val proximaRenovacaoEm: Long? = null,
  val mudancaAgendada: String = "",
  val podeGerenciar: Boolean = false,
  val ambienteHomologacao: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecaoPlanoAssinatura(
  empresaId: String,
  preferencias: SharedPreferences,
  criarApi: () -> ApiChaveiroCar,
  guardarTokens: (ApiChaveiroCar) -> Unit,
  aoAtualizarAssinatura: () -> Unit = {},
) {
  val escopo = rememberCoroutineScope()
  var carregando by remember { mutableStateOf(false) }
  var mensagem by remember { mutableStateOf("") }
  var dados by remember(empresaId) {
    mutableStateOf(
      EstadoAssinaturaUi(
        planoCodigo = Preferencias.obterPlanoEmpresa(preferencias, empresaId),
        status = Preferencias.obterStatusAssinatura(preferencias, empresaId),
        modulosAtivos = Preferencias.obterModulosAtivos(preferencias, empresaId),
        modulosContratados = Preferencias.obterModulosAtivos(preferencias, empresaId),
        proximaRenovacaoEm = Preferencias.obterProximaRenovacao(preferencias, empresaId),
      ),
    )
  }

  fun interpretarResposta(json: JSONObject): EstadoAssinaturaUi {
    val ativosArr = json.optJSONArray("modulosAtivos")
    val ativos = buildSet {
      if (ativosArr != null) {
        for (i in 0 until ativosArr.length()) {
          val nome = ativosArr.optString(i)
          if (nome == SegmentoAtendimento.AUTOMOTIVO.name) add(SegmentoAtendimento.AUTOMOTIVO)
          if (nome == SegmentoAtendimento.RESIDENCIAL.name) add(SegmentoAtendimento.RESIDENCIAL)
        }
      }
    }
    val contratadosArr = json.optJSONArray("modulosContratados")
    val contratados = buildSet {
      if (contratadosArr != null) {
        for (i in 0 until contratadosArr.length()) {
          val nome = contratadosArr.optString(i)
          if (nome == SegmentoAtendimento.AUTOMOTIVO.name) add(SegmentoAtendimento.AUTOMOTIVO)
          if (nome == SegmentoAtendimento.RESIDENCIAL.name) add(SegmentoAtendimento.RESIDENCIAL)
        }
      }
    }
    val plano = json.optString("planoCodigo", "COMPLETO")
    val status = json.optString("status", "ATIVA")
    val renovacao = if (json.isNull("proximaRenovacaoEm")) null else json.optLong("proximaRenovacaoEm").takeIf { it > 0L }
    val mudancaObj = json.optJSONObject("mudancaAgendada")
    val mudancaTexto = if (mudancaObj != null) {
      val acao = mudancaObj.optString("acao")
      val mods = mudancaObj.optJSONArray("modulos")?.let { arr ->
        (0 until arr.length()).map { arr.optString(it) }
          .map { if (it == "RESIDENCIAL") "Residencial" else "Veicular" }
          .joinToString(" + ")
      }.orEmpty()
      if (acao == "CANCELAR") "Cancelamento agendado (aguardando confirmação)"
      else "Alteração solicitada ($mods · aguardando confirmação de pagamento)"
    } else ""

    Preferencias.guardarAssinaturaEmpresa(
      preferencias = preferencias,
      empresaId = empresaId,
      planoCodigo = plano,
      status = status,
      modulosAtivos = ativos.map { it.name },
      proximaRenovacaoEm = renovacao,
    )

    return EstadoAssinaturaUi(
      planoCodigo = plano,
      status = status,
      modulosAtivos = ativos,
      modulosContratados = contratados,
      proximaRenovacaoEm = renovacao,
      mudancaAgendada = mudancaTexto,
      podeGerenciar = json.optBoolean("podeGerenciar", false),
      ambienteHomologacao = json.optBoolean("ambienteHomologacao", false),
    )
  }

  fun atualizar(aviso: String = "") {
    if (empresaId.isBlank() || carregando) return
    carregando = true
    escopo.launch {
      val api = criarApi()
      val res = runCatching { withContext(Dispatchers.IO) { api.obterAssinatura(empresaId) } }
      guardarTokens(api)
      carregando = false
      res.onSuccess { json ->
        dados = interpretarResposta(json)
        mensagem = aviso
        aoAtualizarAssinatura()
      }.onFailure { erro ->
        mensagem = aviso.ifBlank { "Offline: exibindo últimos direitos salvos (${erro.message ?: "sem conexão"})." }
      }
    }
  }

  fun executar(avisoSucesso: String, chamada: suspend (ApiChaveiroCar) -> JSONObject) {
    if (empresaId.isBlank() || carregando) return
    carregando = true
    escopo.launch {
      val api = criarApi()
      val res = runCatching { withContext(Dispatchers.IO) { chamada(api) } }
      guardarTokens(api)
      carregando = false
      res.onSuccess { json ->
        dados = interpretarResposta(json)
        mensagem = avisoSucesso
        aoAtualizarAssinatura()
      }.onFailure { erro ->
        mensagem = erro.message ?: "Não foi possível atualizar o plano."
      }
    }
  }

  LaunchedEffect(empresaId) {
    if (empresaId.isNotBlank()) atualizar()
  }

  CartaoSurface(raio = 20.dp) {
    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Plano e módulos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Selo(
          texto = rotuloStatus(dados.status),
          tom = if (dados.status == "ATIVA") TomSelo.SUCESSO else TomSelo.PENDENTE,
        )
      }
      Text(
        text = "Plano atual: ${rotuloPlano(dados.planoCodigo)}",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Selo(
          texto = if (SegmentoAtendimento.AUTOMOTIVO in dados.modulosAtivos) "Veicular ativo" else "Veicular inativo",
          tom = if (SegmentoAtendimento.AUTOMOTIVO in dados.modulosAtivos) TomSelo.PRIMARIO else TomSelo.PENDENTE,
        )
        Selo(
          texto = if (SegmentoAtendimento.RESIDENCIAL in dados.modulosAtivos) "Residencial ativo" else "Residencial inativo",
          tom = if (SegmentoAtendimento.RESIDENCIAL in dados.modulosAtivos) TomSelo.RESIDENCIAL else TomSelo.PENDENTE,
        )
      }
      dados.proximaRenovacaoEm?.let { data ->
        Text(
          text = "Próxima renovação: ${formatarData(data)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (dados.mudancaAgendada.isNotBlank()) {
        Text(
          text = dados.mudancaAgendada,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      Text(
        text = "Módulos contratados ficam vinculados à empresa. Alterações de plano nunca apagam atendimentos, fichas ou fotos.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (dados.podeGerenciar) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        Text("Solicitar contratação ou troca de plano", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            enabled = !carregando,
            shape = RoundedCornerShape(12.dp),
            onClick = {
              executar("Solicitação registrada para Somente Veicular (aguarda confirmação).") {
                it.solicitarAssinatura(empresaId, "CONTRATAR", listOf("AUTOMOTIVO"))
              }
            },
          ) { Text("Somente Veicular") }
          OutlinedButton(
            enabled = !carregando,
            shape = RoundedCornerShape(12.dp),
            onClick = {
              executar("Solicitação registrada para Somente Residencial (aguarda confirmação).") {
                it.solicitarAssinatura(empresaId, "CONTRATAR", listOf("RESIDENCIAL"))
              }
            },
          ) { Text("Somente Residencial") }
          FilledTonalButton(
            enabled = !carregando,
            shape = RoundedCornerShape(12.dp),
            onClick = {
              executar("Solicitação registrada para Veicular + Residencial (aguarda confirmação).") {
                it.solicitarAssinatura(empresaId, "ALTERAR", listOf("AUTOMOTIVO", "RESIDENCIAL"))
              }
            },
          ) { Text("Veicular + Residencial") }
        }
        if (dados.ambienteHomologacao) {
          Text(
            "Ambiente de teste/homologação (sem cobrança real):",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(
              enabled = !carregando,
              shape = RoundedCornerShape(10.dp),
              onClick = {
                executar("Pagamento confirmado no backend. Módulos atualizados.") {
                  it.processarEventoAssinatura(empresaId, "app-${UUID.randomUUID()}", "PAGAMENTO_CONFIRMADO")
                }
              },
            ) { Text("Confirmar pagamento") }
            OutlinedButton(
              enabled = !carregando,
              shape = RoundedCornerShape(10.dp),
              onClick = {
                executar("Renovação confirmada.") {
                  it.processarEventoAssinatura(empresaId, "app-${UUID.randomUUID()}", "RENOVACAO_CONFIRMADA")
                }
              },
            ) { Text("Renovar") }
            OutlinedButton(
              enabled = !carregando,
              shape = RoundedCornerShape(10.dp),
              onClick = {
                executar("Falha de pagamento simulada; acesso suspenso.") {
                  it.processarEventoAssinatura(empresaId, "app-${UUID.randomUUID()}", "FALHA_PAGAMENTO")
                }
              },
            ) { Text("Simular falha") }
            TextButton(
              enabled = !carregando,
              onClick = {
                executar("Assinatura cancelada; dados preservados.") {
                  it.processarEventoAssinatura(empresaId, "app-${UUID.randomUUID()}", "ASSINATURA_CANCELADA")
                }
              },
            ) { Text("Cancelar plano", color = MaterialTheme.colorScheme.error) }
          }
        }
      } else {
        Text(
          text = "Somente proprietário ou administrador pode contratar ou alterar o plano.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (mensagem.isNotBlank()) {
        Text(mensagem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
      }
      TextButton(onClick = { atualizar("Status do plano atualizado.") }, enabled = !carregando) {
        Text(if (carregando) "Atualizando..." else "Atualizar status do plano")
      }
    }
  }
}

@Composable
fun DialogoPlanoAssinatura(
  onFechar: () -> Unit,
  aoAtualizarAssinatura: () -> Unit,
) {
  val contexto = LocalContext.current
  val preferencias = remember { Preferencias.obter(contexto) }
  val preferenciasApi = remember { Preferencias.obterApi(contexto) }
  val empresaId = preferencias.getString(Preferencias.API_EMPRESA_ID, "").orEmpty()
  val baseSalva = preferencias.getString(Preferencias.API_BASE_URL, null).orEmpty()
  val baseUrl = if (baseSalva.isBlank() || baseSalva.contains("10.0.2.2") || baseSalva.contains("localhost")) {
    ApiChaveiroCar.URL_PADRAO
  } else baseSalva

  fun criarApi(): ApiChaveiroCar = ApiChaveiroCar(baseUrl).apply {
    tokenAcesso = preferenciasApi.getString(Preferencias.API_ACESSO, null)?.ifBlank { null }
    tokenRefresh = preferenciasApi.getString(Preferencias.API_REFRESH, null)?.ifBlank { null }
  }

  fun guardarTokens(api: ApiChaveiroCar) {
    preferenciasApi.edit()
      .putString(Preferencias.API_ACESSO, api.tokenAcesso ?: "")
      .putString(Preferencias.API_REFRESH, api.tokenRefresh ?: "")
      .apply()
  }

  AlertDialog(
    onDismissRequest = onFechar,
    shape = RoundedCornerShape(24.dp),
    title = { Text("Planos e módulos", fontWeight = FontWeight.Bold) },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (empresaId.isBlank()) {
          Text("Conecte-se à uma empresa para consultar e gerenciar módulos.")
        } else {
          SecaoPlanoAssinatura(
            empresaId = empresaId,
            preferencias = preferencias,
            criarApi = ::criarApi,
            guardarTokens = ::guardarTokens,
            aoAtualizarAssinatura = aoAtualizarAssinatura,
          )
        }
      }
    },
    confirmButton = {
      Button(onClick = onFechar, shape = RoundedCornerShape(12.dp)) { Text("Fechar") }
    },
  )
}

private fun rotuloPlano(codigo: String): String = when (codigo) {
  "SOMENTE_RESIDENCIAL" -> "Somente Residencial"
  "SOMENTE_VEICULAR" -> "Somente Veicular"
  "COMPLETO" -> "Veicular + Residencial"
  "SEM_PLANO" -> "Sem plano ativo"
  else -> codigo
}

private fun rotuloStatus(status: String): String = when (status) {
  "ATIVA" -> "Ativa"
  "PENDENTE_PAGAMENTO" -> "Pagamento pendente"
  "FALHA_PAGAMENTO" -> "Falha no pagamento"
  "CANCELADA" -> "Cancelada"
  else -> status
}
