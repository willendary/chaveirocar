package com.willendary.chaveirocar.nuvem

import com.willendary.chaveirocar.modelo.Atendimento
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
}
