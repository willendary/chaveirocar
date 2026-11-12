package com.willendary.chaveirocar.catalogo

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogoFipeTest {

  @Test fun leOpcoesDaApi() {
    val json = """
      [
        {"code": "59", "name": "Volkswagen"},
        {"code": "23", "name": "Ford"},
        {"code": "", "name": "Sem código"},
        {"code": "99"}
      ]
    """.trimIndent()
    assertEquals(
      listOf(
        OpcaoCatalogo("59", "Volkswagen"),
        OpcaoCatalogo("23", "Ford"),
      ),
      CatalogoFipe.interpretarOpcoes(json),
    )
  }

  @Test fun aceitaFormatoAntigo() {
    val json = """[{"codigo":"59","nome":"Volkswagen"}]"""
    assertEquals(listOf(OpcaoCatalogo("59", "Volkswagen")), CatalogoFipe.interpretarOpcoes(json))
  }

  @Test fun leAnosComCombustivel() {
    val json = """[{"code":"2014-1","name":"2014 Gasolina"},{"code":"2015-1","name":"2015 Gasolina"}]"""
    assertEquals(2, CatalogoFipe.interpretarOpcoes(json).size)
    assertEquals("2014", CatalogoFipe.extrairAno("2014 Gasolina"))
  }

  @Test fun semAnoNaoInventaValor() {
    assertEquals("", CatalogoFipe.extrairAno("32000 Zero km"))
    assertEquals("", CatalogoFipe.extrairAno("Gasolina"))
  }

  @Test fun respostaVaziaNaoQuebra() {
    assertEquals(emptyList<OpcaoCatalogo>(), CatalogoFipe.interpretarOpcoes(""))
    assertEquals(emptyList<OpcaoCatalogo>(), CatalogoFipe.interpretarOpcoes("[]"))
  }
}
