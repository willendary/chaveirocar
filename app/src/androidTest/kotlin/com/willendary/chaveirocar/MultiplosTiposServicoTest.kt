package com.willendary.chaveirocar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.willendary.chaveirocar.dados.Backup
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.TipoServico
import com.willendary.chaveirocar.util.Compartilhamento
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MultiplosTiposServicoTest {
  private lateinit var contexto: Context
  private val atividades = setOf(
    TipoServico.ABERTURA, TipoServico.CONSERTO, TipoServico.PROGRAMACAO_CHAVE,
  )

  @Before fun preparar() {
    contexto = ApplicationProvider.getApplicationContext()
    contexto.deleteDatabase(BancoDeAtendimentos.NOME)
  }

  @After fun limpar() {
    contexto.deleteDatabase(BancoDeAtendimentos.NOME)
  }

  @Test fun salvaBuscaFiltraEEditaSemDuplicarAtendimento() {
    val banco = BancoDeAtendimentos(contexto)
    try {
      val id = banco.salvar(Atendimento(
        placa = "ABC1D23",
        tipoServico = TipoServico.ABERTURA,
        tiposServico = atividades,
      ))
      assertTrue(id > 0)
      assertEquals(atividades, banco.obter(id)!!.tiposServico)
      atividades.forEach { tipo ->
        assertEquals(id, banco.listar("", tipo = tipo).single().id)
      }
      assertTrue(banco.listar("", tipo = TipoServico.TELECOMANDO).isEmpty())
      assertEquals(id, banco.listar("programação").single().id)
      assertEquals(id, banco.listar("conserto").single().id)

      banco.salvar(banco.obter(id)!!.copy(
        tipoServico = TipoServico.CONSERTO,
        tiposServico = setOf(TipoServico.CONSERTO),
      ))
      assertEquals(setOf(TipoServico.CONSERTO), banco.obter(id)!!.tiposServico)
      assertTrue(banco.listar("", tipo = TipoServico.ABERTURA).isEmpty())
      assertEquals(1, banco.contarTodos())
    } finally {
      banco.close()
    }
  }

  @Test fun backupRestauraListaECompartilhamentoExibeCategorias() {
    var banco = BancoDeAtendimentos(contexto)
    val dados: ByteArray
    try {
      banco.salvar(Atendimento(
        placa = "ABC1D23", tipoServico = TipoServico.ABERTURA,
        tiposServico = atividades,
      ))
      dados = ByteArrayOutputStream().also { Backup.exportar(banco, contexto, it) }.toByteArray()
    } finally {
      banco.close()
    }

    contexto.deleteDatabase(BancoDeAtendimentos.NOME)
    banco = BancoDeAtendimentos(contexto)
    try {
      assertEquals(1, Backup.importar(banco, contexto, ByteArrayInputStream(dados), substituir = false))
      val registro = banco.listar("").single()
      assertEquals(atividades, registro.tiposServico)
      val compartilhamento = Compartilhamento.textoDoAtendimento(registro)
      assertTrue(compartilhamento.contains("Abertura"))
      assertTrue(compartilhamento.contains("Conserto"))
      assertTrue(compartilhamento.contains("Programação de chave"))
    } finally {
      banco.close()
    }
  }

  @Test fun importaBackupAntigoComApenasUmTipo() {
    val zipBytes = ByteArrayOutputStream().apply {
      ZipOutputStream(this).use { zip ->
        zip.putNextEntry(ZipEntry("dados.json"))
        val json = JSONObject().put("versao", 4).put(
          "atendimentos",
          JSONArray().put(JSONObject().put("placa", "XYZ9A87").put("tipoServico", "TELECOMANDO")),
        )
        zip.write(json.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
      }
    }.toByteArray()
    val banco = BancoDeAtendimentos(contexto)
    try {
      assertEquals(1, Backup.importar(banco, contexto, ByteArrayInputStream(zipBytes), substituir = false))
      val registro = banco.listar("").single()
      assertEquals(setOf(TipoServico.TELECOMANDO), registro.tiposServico)
      assertEquals(registro.id, banco.listar("", tipo = TipoServico.TELECOMANDO).single().id)
    } finally {
      banco.close()
    }
  }

  @Test fun migracaoVersao4PreservaTipoOriginal() {
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
          codigo_configuracao TEXT NOT NULL DEFAULT '',
          chip_transponder TEXT NOT NULL DEFAULT '', ferramenta TEXT NOT NULL DEFAULT '',
          valor_centavos INTEGER NOT NULL DEFAULT 0,
          cliente_nome TEXT NOT NULL DEFAULT '', cliente_telefone TEXT NOT NULL DEFAULT '',
          pago INTEGER NOT NULL DEFAULT 1, busca TEXT NOT NULL DEFAULT '',
          criado_em INTEGER NOT NULL, atualizado_em INTEGER NOT NULL
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
          "VALUES ('ABC1D23', 1, 'ABERTURA', 1, 1)",
      )
      antigo.version = 4
    } finally { antigo.close() }
    val banco = BancoDeAtendimentos(contexto)
    try {
      assertEquals(setOf(TipoServico.ABERTURA), banco.obter(1L)!!.tiposServico)
      assertEquals(1L, banco.listar("", tipo = TipoServico.ABERTURA).single().id)
    } finally {
      banco.close()
    }
  }
}
