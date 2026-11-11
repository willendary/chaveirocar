package com.willendary.chaveirocar.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TextoTest {

  @Test
  fun `normaliza placa e remove acento`() {
    assertEquals("abc 1d23", normalizar("ABC-1D23"))
    assertEquals("ignicao", normalizar("Ignição"))
    assertEquals("chave codificada", normalizar("  Chave   Codificada  "))
  }

  @Test
  fun `converte valor digitado para centavos`() {
    assertEquals(15000L, paraCentavos("150"))
    assertEquals(15050L, paraCentavos("150,50"))
    assertEquals(0L, paraCentavos(""))
    assertEquals(123456L, paraCentavos("1234,56"))
  }

  @Test
  fun `formata centavos de volta para real`() {
    assertEquals("R$ 150,00", formatarValor(15000))
    assertEquals("R$ 1.234,56", formatarValor(123456))
    assertEquals("", formatarValor(0))
    assertEquals("150,50", formatarValorEditavel(15050))
  }
}
