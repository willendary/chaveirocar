package com.willendary.chaveirocar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.modelo.Atendimento
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BancoChipTransponderTest {
  private lateinit var contexto: Context

  @Before
  fun preparar() {
    contexto = ApplicationProvider.getApplicationContext()
    contexto.deleteDatabase(BancoDeAtendimentos.NOME)
  }

  @After
  fun limpar() {
    contexto.deleteDatabase(BancoDeAtendimentos.NOME)
  }

  @Test
  fun salvaERecuperaChipEPermiteBuscar() {
    val banco = BancoDeAtendimentos(contexto)
    try {
      val id = banco.salvar(Atendimento(placa = "ABC1D23", chipTransponder = "ID46 / PCF7936"))
      assertTrue(id > 0L)
      assertEquals("ID46 / PCF7936", banco.obter(id)?.chipTransponder)
      assertEquals(id, banco.listar("PCF7936").single().id)
      banco.salvar(banco.obter(id)!!.copy(chipTransponder = "4D-63"))
      assertEquals("4D-63", banco.obter(id)?.chipTransponder)
    } finally {
      banco.close()
    }
  }

  @Test
  fun migracaoDaVersao3PreservaRegistrosExistentes() {
    val antigo = contexto.openOrCreateDatabase(BancoDeAtendimentos.NOME, Context.MODE_PRIVATE, null)
    try {
      antigo.execSQL(
        """CREATE TABLE atendimentos (
          id INTEGER PRIMARY KEY AUTOINCREMENT, placa TEXT NOT NULL,
          marca TEXT NOT NULL DEFAULT '', modelo TEXT NOT NULL DEFAULT '',
          ano TEXT NOT NULL DEFAULT '', chassi TEXT NOT NULL DEFAULT '',
          data_servico INTEGER NOT NULL, tipo_servico TEXT NOT NULL,
          defeito TEXT NOT NULL DEFAULT '', solucao TEXT NOT NULL DEFAULT '',
          abertura TEXT NOT NULL DEFAULT '', codigo_chave TEXT NOT NULL DEFAULT '',
          codigo_configuracao TEXT NOT NULL DEFAULT '', ferramenta TEXT NOT NULL DEFAULT '',
          valor_centavos INTEGER NOT NULL DEFAULT 0, cliente_nome TEXT NOT NULL DEFAULT '',
          cliente_telefone TEXT NOT NULL DEFAULT '', pago INTEGER NOT NULL DEFAULT 1,
          busca TEXT NOT NULL DEFAULT '', criado_em INTEGER NOT NULL, atualizado_em INTEGER NOT NULL
        )""".trimIndent(),
      )
      antigo.execSQL(
        """CREATE TABLE fotos (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          atendimento_id INTEGER NOT NULL REFERENCES atendimentos(id) ON DELETE CASCADE,
          caminho TEXT NOT NULL, categoria TEXT NOT NULL DEFAULT 'CARRO',
          legenda TEXT NOT NULL DEFAULT '', ordem INTEGER NOT NULL DEFAULT 0,
          criado_em INTEGER NOT NULL
        )""".trimIndent(),
      )
      antigo.execSQL(
        "INSERT INTO atendimentos (placa, data_servico, tipo_servico, criado_em, atualizado_em) " +
          "VALUES ('ABC1D23', 1, 'TRANSPONDER', 1, 1)",
      )
      antigo.version = 3
    } finally {
      antigo.close()
    }
    val banco = BancoDeAtendimentos(contexto)
    try {
      val registro = banco.obter(1L)!!
      assertEquals("ABC1D23", registro.placa)
      assertEquals("", registro.chipTransponder)
      banco.salvar(registro.copy(chipTransponder = "ID48"))
      assertEquals("ID48", banco.obter(1L)?.chipTransponder)
    } finally {
      banco.close()
    }
  }
}
