package com.willendary.chaveirocar.nuvem

import android.content.Context
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.dados.aplicarFichaRemota
import com.willendary.chaveirocar.dados.aplicarFotoRemota
import com.willendary.chaveirocar.dados.excluirFichaPorUuid
import com.willendary.chaveirocar.dados.excluirFotoPorUuid
import com.willendary.chaveirocar.dados.marcarFichaEnviada
import com.willendary.chaveirocar.dados.marcarFotoEnviada
import com.willendary.chaveirocar.dados.obterFichaPorUuid
import com.willendary.chaveirocar.dados.obterFotoPorUuid
import com.willendary.chaveirocar.dados.pendentesFichas
import com.willendary.chaveirocar.dados.pendentesFotos
import com.willendary.chaveirocar.dados.revisaoRemotaFicha
import com.willendary.chaveirocar.dados.revisaoRemotaFoto
import com.willendary.chaveirocar.dados.sincPendenteFicha
import com.willendary.chaveirocar.dados.uuidFicha
import com.willendary.chaveirocar.dados.uuidFoto
import org.json.JSONArray
import java.util.UUID

data class ResumoSync(
  val enviadas: Int = 0,
  val recebidas: Int = 0,
  val conflitos: Int = 0,
  val cursor: Long = 0,
  val temMais: Boolean = false,
)

/**
 * Motor de sync: push (fila local por entidade) + pull (cursor).
 * Sem rede, o local continua funcionando; divergência vira conflito visível.
 */
object MotorSinc {

  fun sincronizar(
    banco: BancoDeAtendimentos,
    api: ApiChaveiroCar,
    empresaId: String,
    cursorInicial: Long,
    contexto: Context? = null,
  ): ResumoSync {
    var enviadas = 0
    var recebidas = 0
    var conflitos = 0

    // PUSH atendimentos + fichas (lotes idempotentes).
    val ops = JSONArray()
    val pendentes = banco.pendentesSync()
    for (item in pendentes) {
      val uuid = banco.uuidDe(item.id)
      if (uuid.isBlank()) throw IllegalStateException("Registro sem UUID não pode sincronizar.")
      ops.put(Sincronizacao.operacaoLocal(UUID.randomUUID().toString(), uuid, "upsert", banco.revisaoRemotaDe(item.id), atendimento = item))
    }
    val pendentesF = banco.pendentesFichas()
    for (item in pendentesF) {
      val uuid = banco.uuidFicha(item.id)
      if (uuid.isBlank()) throw IllegalStateException("Ficha sem UUID não pode sincronizar.")
      ops.put(Sincronizacao.operacaoLocal(UUID.randomUUID().toString(), uuid, "upsert", banco.revisaoRemotaFicha(item.id), ficha = item, entidade = "ficha"))
    }
    val saidas = banco.saidasExcluidos()
    for (saida in saidas) {
      ops.put(Sincronizacao.operacaoLocal(saida.opId, saida.uuid, "exclusao", saida.baseRevisao, entidade = saida.entidade))
    }
    if (ops.length() > 0) {
      val resposta = api.enviarLote(empresaId, ops)
      val resultados = resposta.optJSONArray("resultados") ?: JSONArray()
      for (i in 0 until resultados.length()) {
        val r = resultados.optJSONObject(i) ?: continue
        val status = r.optString("status")
        val opId = r.optString("opId")
        val registro = r.optJSONObject("registro")
        val revisao = registro?.optLong("revisao") ?: 0L
        when (status) {
          "aplicado" -> {
            enviadas++
            val uuid = opsParaUuid(ops, opId)
            val entidade = opsParaEntidade(ops, opId)
            when (entidade) {
              "ficha" -> {
                val local = banco.obterFichaPorUuid(uuid ?: "") ?: continue
                banco.marcarFichaEnviada(local.id, revisao)
              }
              "foto" -> {
                val saida = saidas.firstOrNull { it.opId == opId }
                if (saida != null) banco.confirmarSaida(opId, saida.uuid)
              }
              else -> {
                val local = banco.obterPorUuid(uuid ?: "")
                if (local != null) banco.marcarEnviado(local.id, revisao)
                val saida = saidas.firstOrNull { it.opId == opId }
                if (saida != null) banco.confirmarSaida(opId, saida.uuid)
              }
            }
          }
          "conflito" -> {
            conflitos++
            val uuid = opsParaUuid(ops, opId) ?: continue
            val entidade = opsParaEntidade(ops, opId)
            registrarConflito(banco, entidade, uuid, revisao)
          }
          else -> Unit // erro: mantém pendente; próxima chamada tenta de novo
        }
      }
    }

    // PUSH fotos novas (bytes): pai já subiu no push acima.
    if (contexto != null) {
      for (foto in banco.pendentesFotos()) {
        val uuid = banco.uuidFoto(foto.id)
        val uuidAtendimento = banco.uuidDe(foto.atendimentoId)
        if (uuid.isBlank() || uuidAtendimento.isBlank()) continue
        val arquivo = Fotos.arquivo(contexto, foto.caminho)
        if (!arquivo.exists()) continue
        runCatching {
          val bytes = arquivo.readBytes()
          val criada = api.enviarFoto(
            empresaId, uuidAtendimento, uuid, foto.categoria.name, foto.legenda, foto.ordem.toLong(), bytes, foto.anotacoes,
          )
          banco.marcarFotoEnviada(foto.id, criada.optLong("revisao", 1))
          enviadas++
        }.onFailure {
          // Mantém pendente: tenta de novo na próxima sync. Foto nunca é perdida.
        }
      }
    }

    // PULL
    var cursor = cursorInicial
    var temMais = true
    while (temMais) {
      val pagina = api.sincronizar(empresaId, cursor)
      cursor = pagina.optLong("cursor", cursor)
      temMais = pagina.optBoolean("temMais", false)
      recebidas += aplicarPagina(banco, api, contexto, pagina)
    }
    return ResumoSync(enviadas, recebidas, conflitos, cursor, temMais)
  }

  private fun aplicarPagina(
    banco: BancoDeAtendimentos,
    api: ApiChaveiroCar,
    contexto: Context?,
    pagina: org.json.JSONObject,
  ): Int {
    var recebidas = 0
    val lista = pagina.optJSONArray("atendimentos") ?: JSONArray()
    for (i in 0 until lista.length()) {
      val remoto = lista.optJSONObject(i) ?: continue
      val uuid = Sincronizacao.uuidDeRemoto(remoto)
      if (uuid.isBlank() || banco.jaExcluidoConhecido(uuid)) continue
      val rev = Sincronizacao.revisaoDeRemoto(remoto)
      val emSaida = banco.saidasExcluidos().any { it.uuid == uuid && it.entidade == "atendimento" }
      val local = banco.obterPorUuid(uuid)
      val pendente = local != null && banco.sincPendenteDe(local.id)
      val conhecida = local?.let { banco.revisaoRemotaDe(it.id) } ?: 0L
      when (aplicarRemotoAtendimento(banco, remoto, uuid, rev, pendente, conhecida, emSaida)) {
        true -> recebidas++
        false -> Unit
      }
    }

    val fichas = pagina.optJSONArray("fichas") ?: JSONArray()
    for (i in 0 until fichas.length()) {
      val remoto = fichas.optJSONObject(i) ?: continue
      val uuid = Sincronizacao.uuidDeRemoto(remoto)
      if (uuid.isBlank() || banco.jaExcluidoConhecido(uuid)) continue
      val rev = Sincronizacao.revisaoDeRemoto(remoto)
      val emSaida = banco.saidasExcluidos().any { it.uuid == uuid && it.entidade == "ficha" }
      val local = banco.obterFichaPorUuid(uuid)
      val pendente = local != null && banco.sincPendenteFicha(local.id)
      val conhecida = local?.let { banco.revisaoRemotaFicha(it.id) } ?: 0L
      val excluido = Sincronizacao.excluidoDeRemoto(remoto)
      when (Sincronizacao.decidir(pendente, rev, conhecida, emSaida)) {
        Sincronizacao.Decisao.APLICAR -> {
          if (excluido) {
            if (local != null) banco.excluirFichaPorUuid(uuid)
          } else {
            banco.aplicarFichaRemota(Sincronizacao.fichaDeRemoto(remoto), uuid, rev)
          }
          recebidas++
        }
        Sincronizacao.Decisao.CONFLITO -> {
          if (local != null) banco.registrarConflito(local.id, uuid, rev, "ficha")
        }
        Sincronizacao.Decisao.IGNORAR -> Unit
      }
    }

    val fotos = pagina.optJSONArray("fotos") ?: JSONArray()
    for (i in 0 until fotos.length()) {
      val remoto = fotos.optJSONObject(i) ?: continue
      val uuid = Sincronizacao.uuidDeRemoto(remoto)
      val atendimentoUuid = remoto.optString("atendimentoId")
      if (uuid.isBlank() || atendimentoUuid.isBlank() || banco.jaExcluidoConhecido(uuid)) continue
      val atendimento = banco.obterPorUuid(atendimentoUuid) ?: continue
      val excluido = Sincronizacao.excluidoDeRemoto(remoto)
      val rev = Sincronizacao.revisaoDeRemoto(remoto)
      if (excluido) {
        banco.excluirFotoPorUuid(uuid)
        recebidas++
        continue
      }
      // Baixa bytes quando o registro ainda não está no aparelho ou foi atualizado no servidor.
      val local = banco.obterFotoPorUuid(uuid)
      val caminho = if (local == null) {
        if (contexto == null) continue
        val url = remoto.optString("url")
        if (url.isBlank()) continue
        val bytes = runCatching { api.baixarFoto(url) }.getOrNull() ?: continue
        Fotos.salvarDeBytes(contexto, bytes)
      } else {
        if (contexto != null && rev > banco.revisaoRemotaFoto(local.id)) {
          val url = remoto.optString("url")
          if (url.isNotBlank()) {
            runCatching {
              val bytes = api.baixarFoto(url)
              Fotos.arquivo(contexto, local.caminho).writeBytes(bytes)
            }
          }
        }
        local.caminho
      }
      banco.aplicarFotoRemota(
        uuid, atendimento.id, caminho, remoto.optString("categoria", "CARRO"),
        remoto.optString("legenda", ""), remoto.optInt("ordem", 0), rev,
        remoto.optString("anotacoes", "[]"),
      )
      recebidas++
    }
    return recebidas
  }

  private fun aplicarRemotoAtendimento(
    banco: BancoDeAtendimentos,
    remoto: org.json.JSONObject,
    uuid: String,
    rev: Long,
    pendente: Boolean,
    conhecida: Long,
    emSaida: Boolean,
  ): Boolean {
    val excluido = Sincronizacao.excluidoDeRemoto(remoto)
    return when (Sincronizacao.decidir(pendente, rev, conhecida, emSaida)) {
      Sincronizacao.Decisao.APLICAR -> {
        if (excluido) {
          banco.excluirPorUuid(uuid)
        } else {
          banco.aplicarRemoto(Sincronizacao.atendimentoDeRemoto(remoto), uuid, rev)
        }
        true
      }
      Sincronizacao.Decisao.CONFLITO -> {
        val local = banco.obterPorUuid(uuid)
        if (local != null) banco.registrarConflito(local.id, uuid, rev, "atendimento")
        false
      }
      Sincronizacao.Decisao.IGNORAR -> false
    }
  }

  private fun registrarConflito(
    banco: BancoDeAtendimentos,
    entidade: String,
    uuid: String,
    revisaoServidor: Long,
  ) {
    when (entidade) {
      "ficha" -> banco.obterFichaPorUuid(uuid)?.let { banco.registrarConflito(it.id, uuid, revisaoServidor, "ficha") }
      else -> banco.obterPorUuid(uuid)?.let { banco.registrarConflito(it.id, uuid, revisaoServidor, "atendimento") }
    }
  }

  /** "Manter meu aparelho": sobe a versão local por cima da revisão atual do servidor. */
  fun manterLocal(
    banco: BancoDeAtendimentos,
    api: ApiChaveiroCar,
    empresaId: String,
    uuid: String,
    baseServidor: Long,
    entidade: String = "atendimento",
  ) {
    if (entidade == "ficha") {
      val local = banco.obterFichaPorUuid(uuid) ?: return
      val op = Sincronizacao.operacaoLocal(
        UUID.randomUUID().toString(), uuid, "upsert", baseServidor, ficha = local, entidade = "ficha",
      )
      val resposta = api.enviarLote(empresaId, JSONArray().apply { put(op) })
      val resultado = resposta.optJSONArray("resultados")?.optJSONObject(0) ?: return
      if (resultado.optString("status") == "aplicado") {
        banco.marcarFichaEnviada(local.id, resultado.optJSONObject("registro")?.optLong("revisao") ?: baseServidor)
        banco.limparConflito(local.id)
      }
      return
    }
    val local = banco.obterPorUuid(uuid) ?: return
    val op = Sincronizacao.operacaoLocal(
      UUID.randomUUID().toString(), uuid, "upsert", baseServidor, atendimento = local,
    )
    val resposta = api.enviarLote(empresaId, JSONArray().apply { put(op) })
    val resultado = resposta.optJSONArray("resultados")?.optJSONObject(0) ?: return
    if (resultado.optString("status") == "aplicado") {
      banco.marcarEnviado(local.id, resultado.optJSONObject("registro")?.optLong("revisao") ?: baseServidor)
      banco.limparConflito(local.id)
    }
  }

  fun aceitarServidor(
    banco: BancoDeAtendimentos,
    api: ApiChaveiroCar,
    empresaId: String,
    uuid: String,
    entidade: String = "atendimento",
  ) {
    if (entidade == "ficha") {
      val remoto = api.obterFicha(empresaId, uuid)
      if (Sincronizacao.excluidoDeRemoto(remoto)) {
        banco.excluirFichaPorUuid(uuid)
      } else {
        banco.aplicarFichaRemota(Sincronizacao.fichaDeRemoto(remoto), uuid, Sincronizacao.revisaoDeRemoto(remoto))
      }
      banco.obterFichaPorUuid(uuid)?.let { banco.limparConflito(it.id) }
      return
    }
    val remoto = api.obterAtendimento(empresaId, uuid)
    if (Sincronizacao.excluidoDeRemoto(remoto)) {
      banco.excluirPorUuid(uuid)
    } else {
      banco.aplicarRemoto(Sincronizacao.atendimentoDeRemoto(remoto), uuid, Sincronizacao.revisaoDeRemoto(remoto))
    }
    banco.obterPorUuid(uuid)?.let { banco.limparConflito(it.id) }
  }

  private fun opsParaUuid(ops: JSONArray, opId: String): String? {
    for (i in 0 until ops.length()) {
      val op = ops.optJSONObject(i) ?: continue
      if (op.optString("opId") == opId) return op.optString("id")
    }
    return null
  }

  private fun opsParaEntidade(ops: JSONArray, opId: String): String {
    for (i in 0 until ops.length()) {
      val op = ops.optJSONObject(i) ?: continue
      if (op.optString("opId") == opId) return op.optString("entidade", "atendimento")
    }
    return "atendimento"
  }
}
