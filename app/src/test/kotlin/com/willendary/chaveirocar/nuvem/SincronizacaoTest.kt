package com.willendary.chaveirocar.nuvem

import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.modelo.SetaAnotacao
import com.willendary.chaveirocar.modelo.TipoServico
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SincronizacaoTest {

  private val original = Atendimento(
    placa = "ABC1D23",
    marca = "Fiat",
    modelo = "Mobi",
    tiposServico = setOf(TipoServico.CHAVE_CODIFICADA, TipoServico.TRANSPONDER),
    defeito = "não liga",
    solucao = "programada",
    valorCentavos = 25000,
  )

  @Test
  fun `dados viram operação e voltam do remoto`() {
    val op = Sincronizacao.operacaoLocal("op-1", "uuid-1", "upsert", 3, original)
    assertEquals("atendimento", op.getString("entidade"))
    assertEquals("uuid-1", op.getString("id"))
    assertEquals(3L, op.getLong("baseRevisao"))
    val dados = op.getJSONObject("dados")
    assertEquals("ABC1D23", dados.getString("placa"))
    assertEquals(2, dados.getJSONArray("tiposServico").length())

    val remoto = JSONObject().apply {
      put("id", "uuid-1")
      put("revisao", 4)
      put("placa", "ABC1D23")
      put("marca", "Fiat")
      put("tiposServico", org.json.JSONArray().apply { put("TRANSPONDER") })
      put("valorCentavos", 25000)
    }
    val volta = Sincronizacao.atendimentoDeRemoto(remoto)
    assertEquals("ABC1D23", volta.placa)
    assertEquals(setOf(TipoServico.TRANSPONDER), volta.tiposServico)
    assertEquals("uuid-1", Sincronizacao.uuidDeRemoto(remoto))
    assertEquals(4L, Sincronizacao.revisaoDeRemoto(remoto))
    assertTrue(!Sincronizacao.excluidoDeRemoto(remoto))
  }

  @Test
  fun `pendente com revisão divergente é conflito, resto aplica`() {
    assertEquals(Sincronizacao.Decisao.CONFLITO, Sincronizacao.decidir(true, 5, 3, false))
    assertEquals(Sincronizacao.Decisao.APLICAR, Sincronizacao.decidir(true, 3, 3, false))
    assertEquals(Sincronizacao.Decisao.APLICAR, Sincronizacao.decidir(false, 9, 3, false))
    assertEquals(Sincronizacao.Decisao.IGNORAR, Sincronizacao.decidir(true, 5, 3, true))
    assertEquals(Sincronizacao.Decisao.IGNORAR, Sincronizacao.decidir(false, 9, 3, true))
  }

  @Test
  fun `exclusão remota aparece como tombstone`() {
    val remoto = JSONObject().apply { put("id", "u2"); put("revisao", 2); put("excluidoEm", 123L) }
    assertTrue(Sincronizacao.excluidoDeRemoto(remoto))
  }

  @Test
  fun `atendimento residencial e automotivo sem placa preservam segmento e campos`() {
    val residencial = Atendimento(
      segmento = SegmentoAtendimento.RESIDENCIAL,
      tipoLocal = "Apartamento",
      ambientePorta = "Porta social",
      marcaFechadura = "Papaiz",
      modeloFechadura = "Tetra 1000",
      tipoCilindro = "Tetra",
      enderecoLocal = "Bloco B",
      tipoServico = TipoServico.ABERTURA_RESIDENCIAL,
      tiposServico = setOf(TipoServico.ABERTURA_RESIDENCIAL, TipoServico.TROCA_SEGREDO),
    )
    val dados = Sincronizacao.dadosDeAtendimento(residencial)
    assertEquals("RESIDENCIAL", dados.getString("segmento"))
    assertEquals("Apartamento", dados.getString("tipoLocal"))
    assertEquals("Papaiz", dados.getString("marcaFechadura"))

    val deVolta = Sincronizacao.atendimentoDeRemoto(dados)
    assertEquals(SegmentoAtendimento.RESIDENCIAL, deVolta.segmento)
    assertEquals("Apartamento", deVolta.tipoLocal)
    assertEquals("Porta social", deVolta.ambientePorta)
    assertEquals("Papaiz", deVolta.marcaFechadura)
    assertEquals("Tetra 1000", deVolta.modeloFechadura)
    assertEquals("Tetra", deVolta.tipoCilindro)
    assertEquals("Bloco B", deVolta.enderecoLocal)
    assertEquals(setOf(TipoServico.ABERTURA_RESIDENCIAL, TipoServico.TROCA_SEGREDO), deVolta.tiposServico)

    val semPlaca = Atendimento(
      segmento = SegmentoAtendimento.AUTOMOTIVO,
      placa = "",
      marca = "Honda",
      modelo = "Civic",
    )
    val voltaSemPlaca = Sincronizacao.atendimentoDeRemoto(Sincronizacao.dadosDeAtendimento(semPlaca))
    assertEquals("", voltaSemPlaca.placa)
    assertEquals("Civic", voltaSemPlaca.modelo)
    assertEquals(SegmentoAtendimento.AUTOMOTIVO, voltaSemPlaca.segmento)
  }

  @Test
  fun `serializacao de setas de anotacao de fotos preserva coordenadas e estilo`() {
    val setas = listOf(
      SetaAnotacao(x1 = 0.1f, y1 = 0.2f, x2 = 0.8f, y2 = 0.7f, corHex = "#EF4444", espessura = 6f),
      SetaAnotacao(x1 = 0.5f, y1 = 0.5f, x2 = 0.3f, y2 = 0.1f, corHex = "#FACC15", espessura = 3f),
    )
    val json = Fotos.serializarAnotacoes(setas)
    val lidas = Fotos.lerAnotacoes(json)
    assertEquals(2, lidas.size)
    assertEquals("#EF4444", lidas[0].corHex)
    assertEquals(0.8f, lidas[0].x2, 0.001f)
    assertEquals(3f, lidas[1].espessura, 0.001f)
  }
}
