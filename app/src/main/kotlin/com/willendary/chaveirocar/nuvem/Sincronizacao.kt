package com.willendary.chaveirocar.nuvem

import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.modelo.TipoServico
import org.json.JSONArray
import org.json.JSONObject

/**
 * Mapeamento puro atendimento local <-> API (sem Android), para teste unitário.
 * UUID é gerado no cliente; revisao remota é do servidor.
 */
object Sincronizacao {

  /** Payload `dados` de uma operação upsert. */
  fun dadosDeAtendimento(atendimento: Atendimento): JSONObject =
    JSONObject().apply {
      put("segmento", atendimento.segmento.name)
      put("placa", atendimento.placa)
      put("marca", atendimento.marca)
      put("modelo", atendimento.modelo)
      put("ano", atendimento.ano)
      put("chassi", atendimento.chassi)
      put("tipoLocal", atendimento.tipoLocal)
      put("ambientePorta", atendimento.ambientePorta)
      put("marcaFechadura", atendimento.marcaFechadura)
      put("modeloFechadura", atendimento.modeloFechadura)
      put("tipoCilindro", atendimento.tipoCilindro)
      put("enderecoLocal", atendimento.enderecoLocal)
      put("dataServico", atendimento.dataServico)
      put("tiposServico", JSONArray(atendimento.tiposOrdenados.map { it.name }))
      put("defeito", atendimento.defeito)
      put("solucao", atendimento.solucao)
      put("abertura", atendimento.abertura)
      put("codigoChave", atendimento.codigoChave)
      put("codigoConfiguracao", atendimento.codigoConfiguracao)
      put("chipTransponder", atendimento.chipTransponder)
      put("ferramenta", atendimento.ferramenta)
      put("valorCentavos", atendimento.valorCentavos)
      put("clienteNome", atendimento.clienteNome)
      put("clienteTelefone", atendimento.clienteTelefone)
      put("pago", atendimento.pago)
    }

  fun dadosDeFicha(ficha: com.willendary.chaveirocar.modelo.FichaTecnica): JSONObject =
    JSONObject().apply {
      put("equipamento", ficha.equipamento)
      put("marca", ficha.marca)
      put("modelo", ficha.modelo)
      put("ano", ficha.ano)
      put("sistema", ficha.sistema)
      put("descricao", ficha.descricao)
      put("chip", ficha.chip)
      put("chave", ficha.chave)
      put("telecomando", ficha.telecomando)
      put("frequencia", ficha.frequencia)
      put("avisos", ficha.avisos)
      put("funcoes", ficha.funcoes)
      put("manual", ficha.manual)
      put("confirmado", ficha.confirmado)
      put("origem", ficha.origem)
    }

  fun fichaDeRemoto(remoto: JSONObject): com.willendary.chaveirocar.modelo.FichaTecnica =
    com.willendary.chaveirocar.modelo.FichaTecnica(
      equipamento = remoto.optString("equipamento"),
      marca = remoto.optString("marca"),
      modelo = remoto.optString("modelo"),
      ano = remoto.optString("ano"),
      sistema = remoto.optString("sistema"),
      descricao = remoto.optString("descricao"),
      chip = remoto.optString("chip"),
      chave = remoto.optString("chave"),
      telecomando = remoto.optString("telecomando"),
      frequencia = remoto.optString("frequencia"),
      avisos = remoto.optString("avisos"),
      funcoes = remoto.optString("funcoes"),
      manual = remoto.optString("manual"),
      confirmado = remoto.optBoolean("confirmado", false),
      origem = remoto.optString("origem"),
      atualizadoEm = remoto.optLong("atualizadoEm"),
    )

  /** Operação de push para um registro local. */
  fun operacaoLocal(
    opId: String,
    uuid: String,
    tipo: String,
    baseRevisao: Long,
    atendimento: Atendimento? = null,
    ficha: com.willendary.chaveirocar.modelo.FichaTecnica? = null,
    entidade: String = "atendimento",
  ): JSONObject =
    JSONObject().apply {
      put("opId", opId)
      put("entidade", entidade)
      put("tipo", tipo)
      put("id", uuid)
      put("baseRevisao", baseRevisao)
      if (atendimento != null) put("dados", dadosDeAtendimento(atendimento))
      if (ficha != null) put("dados", dadosDeFicha(ficha))
    }

  /** Converte registro remoto (sync/pull) para [Atendimento] local (id 0 = novo). */
  fun atendimentoDeRemoto(remoto: JSONObject): Atendimento {
    val tiposNomes = mutableListOf<String>()
    val lista = remoto.optJSONArray("tiposServico")
    if (lista != null) for (i in 0 until lista.length()) tiposNomes.add(lista.optString(i))
    val tipoPadrao = TipoServico.de(remoto.optString("tipoServico"))
    return Atendimento(
      segmento = SegmentoAtendimento.de(remoto.optString("segmento")),
      placa = remoto.optString("placa"),
      marca = remoto.optString("marca"),
      modelo = remoto.optString("modelo"),
      ano = remoto.optString("ano"),
      chassi = remoto.optString("chassi"),
      tipoLocal = remoto.optString("tipoLocal"),
      ambientePorta = remoto.optString("ambientePorta"),
      marcaFechadura = remoto.optString("marcaFechadura"),
      modeloFechadura = remoto.optString("modeloFechadura"),
      tipoCilindro = remoto.optString("tipoCilindro"),
      enderecoLocal = remoto.optString("enderecoLocal"),
      dataServico = remoto.optLong("dataServico"),
      tipoServico = tipoPadrao,
      tiposServico = TipoServico.deNomes(tiposNomes, tipoPadrao),
      defeito = remoto.optString("defeito"),
      solucao = remoto.optString("solucao"),
      abertura = remoto.optString("abertura"),
      codigoChave = remoto.optString("codigoChave"),
      codigoConfiguracao = remoto.optString("codigoConfiguracao"),
      chipTransponder = remoto.optString("chipTransponder"),
      ferramenta = remoto.optString("ferramenta"),
      valorCentavos = remoto.optLong("valorCentavos"),
      clienteNome = remoto.optString("clienteNome"),
      clienteTelefone = remoto.optString("clienteTelefone"),
      pago = remoto.optBoolean("pago", true),
      atualizadoEm = remoto.optLong("atualizadoEm"),
    )
  }

  fun uuidDeRemoto(remoto: JSONObject): String = remoto.optString("id")

  fun revisaoDeRemoto(remoto: JSONObject): Long = remoto.optLong("revisao")

  fun excluidoDeRemoto(remoto: JSONObject): Boolean = !remoto.isNull("excluidoEm")

  /** Operação de push para um registro local. */
  fun operacaoLocal(
    opId: String,
    uuid: String,
    tipo: String,
    baseRevisao: Long,
    atendimento: Atendimento? = null,
  ): JSONObject =
    JSONObject().apply {
      put("opId", opId)
      put("entidade", "atendimento")
      put("tipo", tipo)
      put("id", uuid)
      put("baseRevisao", baseRevisao)
      if (atendimento != null) put("dados", dadosDeAtendimento(atendimento))
    }

  /**
   * Decisão de mescla do pull. `pendente` = há edição local não enviada.
   * Regra: pendente + revisão divergente = conflito (mantém local, registra).
   */
  enum class Decisao { APLICAR, CONFLITO, IGNORAR }

  fun decidir(
    pendente: Boolean,
    revisaoRemota: Long,
    revisaoConhecida: Long,
    jaExcluidoLocal: Boolean,
  ): Decisao {
    if (jaExcluidoLocal) return Decisao.IGNORAR
    if (pendente && revisaoRemota != revisaoConhecida) return Decisao.CONFLITO
    return Decisao.APLICAR
  }
}
