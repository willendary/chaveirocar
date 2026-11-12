package com.willendary.chaveirocar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.willendary.chaveirocar.dados.Backup
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.modelo.FichaTecnica
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FichaTecnicaTest {
  private lateinit var contexto: Context

  @Before fun preparar() {
    contexto = ApplicationProvider.getApplicationContext()
    contexto.deleteDatabase(BancoDeAtendimentos.NOME)
  }

  @After fun limpar() {
    contexto.deleteDatabase(BancoDeAtendimentos.NOME)
  }

  private fun exemplo() = FichaTecnica(
    equipamento = "VVDI",
    marca = "Volkswagen",
    modelo = "Gol",
    ano = "2010",
    sistema = "G5",
    descricao = "Imobilizador com chave codificada",
    chip = "ID48",
    chave = "Lâmina HU66",
    telecomando = "3 botões",
    frequencia = "433 MHz",
    avisos = "Não apagar chaves sem conferir o número de chaves cadastradas.",
    funcoes = "Ler chaves, programar telecomando",
    manual = "Passo 1: ligar a ignição. Passo 2: aguardar o painel.",
    confirmado = true,
    origem = "Manual do equipamento",
  )

  @Test fun salvaBuscaEditaEExclui() {
    val banco = BancoDeAtendimentos(contexto)
    try {
      val id = banco.salvarFicha(exemplo())
      assertTrue(id > 0)
      val salva = banco.obterFicha(id)
      assertNotNull(salva)
      assertEquals("ID48", salva!!.chip)
      assertEquals("G5", salva.sistema)
      assertEquals(1, banco.listarFichas("id48").size)
      assertEquals(1, banco.listarFichas("volkswagen gol").size)
      assertEquals(1, banco.listarFichas("passo 2").size)

      banco.salvarFicha(salva.copy(chip = "PCF7936", confirmado = false))
      val editada = banco.obterFicha(id)!!
      assertEquals("PCF7936", editada.chip)
      assertTrue(banco.listarFichas("id48").isEmpty())
      assertEquals(1, banco.listarFichas("pcf7936").size)

      banco.excluirFicha(id)
      assertEquals(0, banco.listarFichas().size)
    } finally {
      banco.close()
    }
  }

  @Test fun backupPreservaFichas() {
    var banco = BancoDeAtendimentos(contexto)
    val dados: ByteArray
    try {
      banco.salvarFicha(exemplo())
      dados = ByteArrayOutputStream().also { Backup.exportar(banco, contexto, it) }.toByteArray()
    } finally {
      banco.close()
    }

    contexto.deleteDatabase(BancoDeAtendimentos.NOME)
    banco = BancoDeAtendimentos(contexto)
    try {
      assertEquals(1, Backup.importar(banco, contexto, ByteArrayInputStream(dados), substituir = true))
      val ficha = banco.listarFichas().single()
      assertEquals("ID48", ficha.chip)
      assertEquals("Passo 1: ligar a ignição. Passo 2: aguardar o painel.", ficha.manual)
      assertTrue(ficha.confirmado)
    } finally {
      banco.close()
    }
  }

  @Test fun migracaoCriaTabelaDeFichas() {
    val antigo = contexto.openOrCreateDatabase(BancoDeAtendimentos.NOME, Context.MODE_PRIVATE, null)
    try {
      antigo.version = 5
    } finally {
      antigo.close()
    }
    val banco = BancoDeAtendimentos(contexto)
    try {
      val id = banco.salvarFicha(FichaTecnica(marca = "Fiat", modelo = "Uno", chip = "ID46"))
      assertTrue(id > 0)
      assertEquals("ID46", banco.obterFicha(id)!!.chip)
    } finally {
      banco.close()
    }
  }
}
