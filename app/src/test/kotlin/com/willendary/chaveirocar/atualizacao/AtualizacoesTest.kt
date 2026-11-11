package com.willendary.chaveirocar.atualizacao

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtualizacoesTest {

  @Test
  fun `detecta versao mais nova`() {
    assertTrue(Atualizacoes.maisNova("1.1", "1.0"))
    assertTrue(Atualizacoes.maisNova("2.0", "1.9"))
    assertTrue(Atualizacoes.maisNova("1.10", "1.9"))
    assertTrue(Atualizacoes.maisNova("1.0.1", "1.0"))
  }

  @Test
  fun `nao atualiza para versao igual ou antiga`() {
    assertFalse(Atualizacoes.maisNova("1.0", "1.0"))
    assertFalse(Atualizacoes.maisNova("1.0", "1.1"))
    assertFalse(Atualizacoes.maisNova("0.9", "1.0"))
  }
}
