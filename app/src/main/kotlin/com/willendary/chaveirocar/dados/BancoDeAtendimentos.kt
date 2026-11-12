package com.willendary.chaveirocar.dados

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.CategoriaFoto
import com.willendary.chaveirocar.modelo.Foto
import com.willendary.chaveirocar.modelo.ResumoModelo
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
        abertura TEXT NOT NULL DEFAULT '',
        codigo_chave TEXT NOT NULL DEFAULT '',
        codigo_configuracao TEXT NOT NULL DEFAULT '',
        chip_transponder TEXT NOT NULL DEFAULT '',
        ferramenta TEXT NOT NULL DEFAULT '',
        valor_centavos INTEGER NOT NULL DEFAULT 0,
        cliente_nome TEXT NOT NULL DEFAULT '',
        cliente_telefone TEXT NOT NULL DEFAULT '',
        pago INTEGER NOT NULL DEFAULT 1,
        busca TEXT NOT NULL DEFAULT '',
        criado_em INTEGER NOT NULL,
        atualizado_em INTEGER NOT NULL
      )
      """.trimIndent(),
    )
    banco.execSQL("CREATE INDEX idx_atendimentos_placa ON atendimentos(placa)")
    banco.execSQL("CREATE INDEX idx_atendimentos_data ON atendimentos(data_servico)")
    criarTabelaFotos(banco)
    banco.execSQL("CREATE INDEX idx_fotos_atendimento ON fotos(atendimento_id)")
  }

  private fun criarTabelaFotos(banco: SQLiteDatabase) {
    banco.execSQL(
      """
      CREATE TABLE fotos (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        atendimento_id INTEGER NOT NULL REFERENCES atendimentos(id) ON DELETE CASCADE,
        caminho TEXT NOT NULL,
        categoria TEXT NOT NULL DEFAULT 'CARRO',
        legenda TEXT NOT NULL DEFAULT '',
        ordem INTEGER NOT NULL DEFAULT 0,
        criado_em INTEGER NOT NULL
      )
      """.trimIndent(),
    )
  }

  override fun onUpgrade(banco: SQLiteDatabase, versaoAntiga: Int, versaoNova: Int) {
    if (versaoAntiga < 2) {
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN abertura TEXT NOT NULL DEFAULT ''")
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN codigo_chave TEXT NOT NULL DEFAULT ''")
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN codigo_configuracao TEXT NOT NULL DEFAULT ''")
      banco.execSQL("ALTER TABLE fotos ADD COLUMN categoria TEXT NOT NULL DEFAULT 'CARRO'")
      banco.execSQL("ALTER TABLE fotos ADD COLUMN legenda TEXT NOT NULL DEFAULT ''")
      banco.execSQL("ALTER TABLE fotos ADD COLUMN ordem INTEGER NOT NULL DEFAULT 0")
    }
    if (versaoAntiga < 3) {
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN cliente_nome TEXT NOT NULL DEFAULT ''")
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN cliente_telefone TEXT NOT NULL DEFAULT ''")
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN pago INTEGER NOT NULL DEFAULT 1")
    }
    if (versaoAntiga < 4) {
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN chip_transponder TEXT NOT NULL DEFAULT ''")
    }
  }

  fun listar(
    consulta: String,
    tipo: TipoServico? = null,
    desde: Long? = null,
    ate: Long? = null,
    somenteComFotos: Boolean = false,
  ): List<Atendimento> {
    val tokens = normalizar(consulta).split(' ').filter { it.isNotBlank() }
    val condicoes = mutableListOf<String>()
    val argumentos = mutableListOf<String>()
    for (token in tokens) {
      condicoes.add("a.busca LIKE ?")
      argumentos.add("%$token%")
    }
    if (tipo != null) {
      condicoes.add("a.tipo_servico = ?")
      argumentos.add(tipo.name)
    }
    if (desde != null) {
      condicoes.add("a.data_servico >= ?")
      argumentos.add(desde.toString())
    }
    if (ate != null) {
      condicoes.add("a.data_servico <= ?")
      argumentos.add(ate.toString())
    }
    if (somenteComFotos) {
      condicoes.add("EXISTS (SELECT 1 FROM fotos f WHERE f.atendimento_id = a.id)")
    }
    val onde = if (condicoes.isEmpty()) "" else " WHERE " + condicoes.joinToString(" AND ")
    return consultar(
      "SELECT a.*, (SELECT COUNT(*) FROM fotos f WHERE f.atendimento_id = a.id) AS total_fotos " +
        "FROM atendimentos a$onde ORDER BY a.data_servico DESC, a.id DESC",
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

  /**
   * Serviços anteriores da mesma marca/modelo — abertura e código costumam repetir.
   */
  fun servicosDoModelo(modelo: String, excetoId: Long = 0): List<Atendimento> {
    if (modelo.isBlank()) return emptyList()
    return consultar(
      "SELECT a.*, (SELECT COUNT(*) FROM fotos f WHERE f.atendimento_id = a.id) AS total_fotos " +
        "FROM atendimentos a WHERE a.modelo = ? AND a.id <> ? ORDER BY a.data_servico DESC LIMIT 5",
      arrayOf(modelo.trim(), excetoId.toString()),
    )
  }

  /**
   * Todos os serviços de um veículo do fichário (marca + modelo + ano quando informado).
   */
  fun servicosDoVeiculo(marca: String, modelo: String, ano: String): List<Atendimento> {
    val condicoes = mutableListOf("lower(a.marca) = lower(?)", "lower(a.modelo) = lower(?)")
    val argumentos = mutableListOf(marca.trim(), modelo.trim())
    if (ano.isNotBlank()) {
      condicoes.add("a.ano = ?")
      argumentos.add(ano.trim())
    }
    return consultar(
      "SELECT a.*, (SELECT COUNT(*) FROM fotos f WHERE f.atendimento_id = a.id) AS total_fotos " +
        "FROM atendimentos a WHERE ${condicoes.joinToString(" AND ")} ORDER BY a.data_servico DESC",
      argumentos.toTypedArray(),
    )
  }

  /**
   * Modelos já usados com a marca escolhida — alimenta a sugestão de modelo no formulário.
   */
  fun modelosDaMarca(marca: String, limite: Int = 30): List<String> {
    if (marca.isBlank()) return valoresUsados("modelo", limite)
    return readableDatabase.rawQuery(
      "SELECT modelo FROM atendimentos WHERE modelo <> '' AND lower(marca) = lower(?) " +
        "GROUP BY modelo ORDER BY COUNT(*) DESC, modelo LIMIT $limite",
      arrayOf(marca.trim()),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.getString(0))
      }
    }
  }

  /**
   * Soluções já aplicadas em defeitos parecidos com o que está sendo digitado.
   */
  fun solucoesPorDefeito(defeito: String, limite: Int = 3): List<String> {
    val termo = normalizar(defeito)
    if (termo.length < 4) return emptyList()
    return readableDatabase.rawQuery(
      "SELECT solucao FROM atendimentos WHERE solucao <> '' AND busca LIKE ? " +
        "GROUP BY solucao ORDER BY COUNT(*) DESC, MAX(data_servico) DESC LIMIT $limite",
      arrayOf("%$termo%"),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.getString(0))
      }
    }
  }

  fun resumoPorModelo(): List<ResumoModelo> =
    readableDatabase.rawQuery(
      "SELECT marca, modelo, ano, COUNT(*) AS quantidade, MAX(data_servico) AS ultima, " +
        "MAX(CASE WHEN abertura <> '' OR codigo_chave <> '' OR codigo_configuracao <> '' OR chip_transponder <> '' THEN 1 " +
        "WHEN EXISTS (SELECT 1 FROM fotos f WHERE f.atendimento_id = atendimentos.id " +
        "AND f.categoria = 'PROCEDIMENTO') THEN 1 ELSE 0 END) AS tem_procedimento " +
        "FROM atendimentos WHERE marca <> '' OR modelo <> '' " +
        "GROUP BY lower(marca), lower(modelo), ano ORDER BY ultima DESC",
      null,
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            ResumoModelo(
              marca = cursor.getString(0),
              modelo = cursor.getString(1),
              ano = cursor.getString(2),
              quantidade = cursor.getInt(3),
              ultimaData = cursor.getLong(4),
              temProcedimento = cursor.getInt(5) == 1,
            ),
          )
        }
      }
    }

  /**
   * Sugestões de autocomplete vindas do próprio histórico.
   */
  fun valoresUsados(coluna: String, limite: Int = 30): List<String> {
    val colunaSegura = when (coluna) {
      "marca", "modelo", "defeito", "solucao", "abertura", "ferramenta" -> coluna
      else -> return emptyList()
    }
    return readableDatabase.rawQuery(
      "SELECT $colunaSegura FROM atendimentos WHERE $colunaSegura <> '' " +
        "GROUP BY $colunaSegura ORDER BY COUNT(*) DESC, $colunaSegura LIMIT $limite",
      null,
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.getString(0))
      }
    }
  }

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
      put("abertura", atendimento.abertura.trim())
      put("codigo_chave", atendimento.codigoChave.trim().uppercase())
      put("codigo_configuracao", atendimento.codigoConfiguracao.trim())
      put("chip_transponder", atendimento.chipTransponder.trim())
      put("ferramenta", atendimento.ferramenta.trim())
      put("valor_centavos", atendimento.valorCentavos)
      put("cliente_nome", atendimento.clienteNome.trim())
      put("cliente_telefone", atendimento.clienteTelefone.trim())
      put("pago", if (atendimento.pago) 1 else 0)
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
      "SELECT id, atendimento_id, caminho, categoria, legenda, ordem FROM fotos " +
        "WHERE atendimento_id = ? ORDER BY categoria DESC, ordem, id",
      arrayOf(atendimentoId.toString()),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            Foto(
              id = cursor.getLong(0),
              atendimentoId = cursor.getLong(1),
              caminho = cursor.getString(2),
              categoria = CategoriaFoto.de(cursor.getString(3)),
              legenda = cursor.getString(4),
              ordem = cursor.getInt(5),
            ),
          )
        }
      }
    }

  fun adicionarFoto(
    atendimentoId: Long,
    caminho: String,
    categoria: CategoriaFoto = CategoriaFoto.CARRO,
    legenda: String = "",
  ): Long {
    val proximaOrdem = readableDatabase.rawQuery(
      "SELECT COALESCE(MAX(ordem), 0) + 1 FROM fotos WHERE atendimento_id = ?",
      arrayOf(atendimentoId.toString()),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 1 }
    val valores = ContentValues().apply {
      put("atendimento_id", atendimentoId)
      put("caminho", caminho)
      put("categoria", categoria.name)
      put("legenda", legenda)
      put("ordem", proximaOrdem)
      put("criado_em", System.currentTimeMillis())
    }
    return writableDatabase.insert("fotos", null, valores)
  }

  fun atualizarFoto(foto: Foto) {
    val valores = ContentValues().apply {
      put("categoria", foto.categoria.name)
      put("legenda", foto.legenda)
      put("ordem", foto.ordem)
    }
    writableDatabase.update("fotos", valores, "id = ?", arrayOf(foto.id.toString()))
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
      abertura = getString(getColumnIndexOrThrow("abertura")),
      codigoChave = getString(getColumnIndexOrThrow("codigo_chave")),
      codigoConfiguracao = getString(getColumnIndexOrThrow("codigo_configuracao")),
      chipTransponder = getString(getColumnIndexOrThrow("chip_transponder")),
      ferramenta = getString(getColumnIndexOrThrow("ferramenta")),
      valorCentavos = getLong(getColumnIndexOrThrow("valor_centavos")),
      clienteNome = getString(getColumnIndexOrThrow("cliente_nome")),
      clienteTelefone = getString(getColumnIndexOrThrow("cliente_telefone")),
      pago = getInt(getColumnIndexOrThrow("pago")) == 1,
      quantidadeFotos = getInt(getColumnIndexOrThrow("total_fotos")),
      atualizadoEm = getLong(getColumnIndexOrThrow("atualizado_em")),
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
      atendimento.abertura,
      atendimento.codigoChave,
      atendimento.codigoConfiguracao,
      atendimento.chipTransponder,
      atendimento.ferramenta,
      atendimento.clienteNome,
      atendimento.clienteTelefone,
    )
    return normalizar(partes.joinToString(" "))
  }

  companion object {
    const val NOME = "chaveirocar.db"
    const val VERSAO = 4
  }
}
