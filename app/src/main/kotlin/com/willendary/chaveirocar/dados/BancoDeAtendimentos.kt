package com.willendary.chaveirocar.dados

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.Foto
import com.willendary.chaveirocar.modelo.TipoServico
import com.willendary.chaveirocar.util.normalizar

/**
 * Banco local do aparelho (SQLite). Nada sai do celular.
 */
class BancoDeAtendimentos(contexto: Context) :
  SQLiteOpenHelper(contexto.applicationContext, NOME, null, VERSAO) {

  override fun onConfigure(banco: SQLiteDatabase) {
    banco.setForeignKeyConstraintsEnabled(true)
  }

  override fun onCreate(banco: SQLiteDatabase) {
    banco.execSQL(
      """
      CREATE TABLE atendimentos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        placa TEXT NOT NULL,
        marca TEXT NOT NULL DEFAULT '',
        modelo TEXT NOT NULL DEFAULT '',
        ano TEXT NOT NULL DEFAULT '',
        chassi TEXT NOT NULL DEFAULT '',
        data_servico INTEGER NOT NULL,
        tipo_servico TEXT NOT NULL,
        defeito TEXT NOT NULL DEFAULT '',
        solucao TEXT NOT NULL DEFAULT '',
        ferramenta TEXT NOT NULL DEFAULT '',
        valor_centavos INTEGER NOT NULL DEFAULT 0,
        busca TEXT NOT NULL DEFAULT '',
        criado_em INTEGER NOT NULL,
        atualizado_em INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    banco.execSQL("CREATE INDEX idx_atendimentos_placa ON atendimentos(placa)")
    banco.execSQL("CREATE INDEX idx_atendimentos_data ON atendimentos(data_servico)")
    banco.execSQL(
      """
      CREATE TABLE fotos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        atendimento_id INTEGER NOT NULL REFERENCES atendimentos(id) ON DELETE CASCADE,
        caminho TEXT NOT NULL,
        criado_em INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    banco.execSQL("CREATE INDEX idx_fotos_atendimento ON fotos(atendimento_id)")
  }

  override fun onUpgrade(banco: SQLiteDatabase, versaoAntiga: Int, versaoNova: Int) = Unit

  fun listar(consulta: String): List<Atendimento> {
    val tokens = normalizar(consulta).split(' ').filter { it.isNotBlank() }
    val selecao = StringBuilder()
    val argumentos = mutableListOf<String>()
    for (token in tokens) {
      if (selecao.isNotEmpty()) selecao.append(" AND ")
      selecao.append("a.busca LIKE ?")
      argumentos.add("%$token%")
    }
    val onde = if (selecao.isEmpty()) null else selecao.toString()
    return consultar(
      "SELECT a.*, (SELECT COUNT(*) FROM fotos f WHERE f.atendimento_id = a.id) AS total_fotos " +
        "FROM atendimentos a" + (onde?.let { " WHERE $it" } ?: "") +
        " ORDER BY a.data_servico DESC, a.id DESC",
      argumentos.toTypedArray(),
    )
  }

  fun obter(id: Long): Atendimento? =
    consultar(
      "SELECT a.*, (SELECT COUNT(*) FROM fotos f WHERE f.atendimento_id = a.id) AS total_fotos " +
        "FROM atendimentos a WHERE a.id = ?",
      arrayOf(id.toString()),
    ).firstOrNull()

  /**
   * Serviços anteriores do mesmo carro — usado pra avisar "já trabalhei nesse".
   */
  fun servicosDaPlaca(placa: String, excetoId: Long = 0): List<Atendimento> =
    consultar(
      "SELECT a.*, (SELECT COUNT(*) FROM fotos f WHERE f.atendimento_id = a.id) AS total_fotos " +
        "FROM atendimentos a WHERE a.placa = ? AND a.id <> ? ORDER BY a.data_servico DESC",
      arrayOf(placa, excetoId.toString()),
    )

  fun salvar(atendimento: Atendimento): Long {
    val agora = System.currentTimeMillis()
    val valores = ContentValues().apply {
      put("placa", atendimento.placa.trim().uppercase())
      put("marca", atendimento.marca.trim())
      put("modelo", atendimento.modelo.trim())
      put("ano", atendimento.ano.trim())
      put("chassi", atendimento.chassi.trim().uppercase())
      put("data_servico", atendimento.dataServico)
      put("tipo_servico", atendimento.tipoServico.name)
      put("defeito", atendimento.defeito.trim())
      put("solucao", atendimento.solucao.trim())
      put("ferramenta", atendimento.ferramenta.trim())
      put("valor_centavos", atendimento.valorCentavos)
      put("busca", montarBusca(atendimento))
      put("atualizado_em", agora)
    }
    val banco = writableDatabase
    if (atendimento.id == 0L) {
      valores.put("criado_em", agora)
      return banco.insert("atendimentos", null, valores)
    }
    banco.update("atendimentos", valores, "id = ?", arrayOf(atendimento.id.toString()))
    return atendimento.id
  }

  fun excluir(id: Long): List<String> {
    val caminhos = listarFotos(id).map { it.caminho }
    writableDatabase.delete("atendimentos", "id = ?", arrayOf(id.toString()))
    return caminhos
  }

  fun apagarTudo(): List<String> {
    val caminhos = mutableListOf<String>()
    readableDatabase.rawQuery("SELECT caminho FROM fotos", null).use { cursor ->
      while (cursor.moveToNext()) caminhos.add(cursor.getString(0))
    }
    writableDatabase.delete("atendimentos", null, null)
    writableDatabase.delete("fotos", null, null)
    return caminhos
  }

  fun listarFotos(atendimentoId: Long): List<Foto> =
    readableDatabase.rawQuery(
      "SELECT id, atendimento_id, caminho FROM fotos WHERE atendimento_id = ? ORDER BY id",
      arrayOf(atendimentoId.toString()),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(Foto(cursor.getLong(0), cursor.getLong(1), cursor.getString(2)))
        }
      }
    }

  fun adicionarFoto(atendimentoId: Long, caminho: String): Long {
    val valores = ContentValues().apply {
      put("atendimento_id", atendimentoId)
      put("caminho", caminho)
      put("criado_em", System.currentTimeMillis())
    }
    return writableDatabase.insert("fotos", null, valores)
  }

  fun excluirFoto(id: Long): String? {
    val caminho = readableDatabase.rawQuery(
      "SELECT caminho FROM fotos WHERE id = ?",
      arrayOf(id.toString()),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    writableDatabase.delete("fotos", "id = ?", arrayOf(id.toString()))
    return caminho
  }

  fun contarTodos(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM atendimentos", null).use { cursor ->
    if (cursor.moveToFirst()) cursor.getInt(0) else 0
  }

  private fun consultar(sql: String, argumentos: Array<String>): List<Atendimento> =
    readableDatabase.rawQuery(sql, argumentos).use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.paraAtendimento())
      }
    }

  private fun Cursor.paraAtendimento(): Atendimento =
    Atendimento(
      id = getLong(getColumnIndexOrThrow("id")),
      placa = getString(getColumnIndexOrThrow("placa")),
      marca = getString(getColumnIndexOrThrow("marca")),
      modelo = getString(getColumnIndexOrThrow("modelo")),
      ano = getString(getColumnIndexOrThrow("ano")),
      chassi = getString(getColumnIndexOrThrow("chassi")),
      dataServico = getLong(getColumnIndexOrThrow("data_servico")),
      tipoServico = TipoServico.de(getString(getColumnIndexOrThrow("tipo_servico"))),
      defeito = getString(getColumnIndexOrThrow("defeito")),
      solucao = getString(getColumnIndexOrThrow("solucao")),
      ferramenta = getString(getColumnIndexOrThrow("ferramenta")),
      valorCentavos = getLong(getColumnIndexOrThrow("valor_centavos")),
      quantidadeFotos = getInt(getColumnIndexOrThrow("total_fotos")),
    )

  private fun montarBusca(atendimento: Atendimento): String {
    val partes = listOf(
      atendimento.placa,
      atendimento.marca,
      atendimento.modelo,
      atendimento.ano,
      atendimento.chassi,
      atendimento.tipoServico.rotulo,
      atendimento.defeito,
      atendimento.solucao,
      atendimento.ferramenta,
    )
    return normalizar(partes.joinToString(" "))
  }

  companion object {
    const val NOME = "chaveirocar.db"
    const val VERSAO = 1
  }
}
