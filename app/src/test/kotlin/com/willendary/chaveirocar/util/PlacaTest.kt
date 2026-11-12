package com.willendary.chaveirocar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacaTest {

  @Test fun aceitaPadraoAntigoEMercosul() {
    assertTrue(Placa.valida("ABC1234"))
    assertTrue(Placa.valida("abc1d23"))
    assertFalse(Placa.valida("ABC123"))
    assertFalse(Placa.valida("1ABC234"))
  }

  @Test fun preservaPlacaJaCorreta() {
    assertEquals("ABC1234", Placa.reconstruir("ABC-1234"))
    assertEquals("ABC1D23", Placa.reconstruir("abc 1d23"))
  }

  @Test fun corrigeConfusaoDeLetrasENumeros() {
    assertEquals("ABO1234", Placa.reconstruir("AB0I234"))
  }

  @Test fun extraiPlacaDeTextoDoOcr() {
    assertEquals(listOf("ABC1234"), Placa.extrair("ABC 1234"))
    assertEquals(listOf("ABC1D23"), Placa.extrair("placa ABC-1D23 fim"))
    assertTrue(Placa.extrair("sem placa aqui").isEmpty())
  }
}
