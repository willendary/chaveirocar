package com.willendary.chaveirocar.dados

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.CategoriaFoto
import com.willendary.chaveirocar.modelo.FichaTecnica
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
        tipos_servico TEXT NOT NULL DEFAULT '',
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
    criarTabelaFichas(banco)
    banco.execSQL("CREATE INDEX idx_fichas_veiculo ON fichas_tecnicas(marca, modelo)")
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

  private fun criarTabelaFichas(banco: SQLiteDatabase) {
    banco.execSQL(
      """
      CREATE TABLE fichas_tecnicas (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        equipamento TEXT NOT NULL DEFAULT '',
        marca TEXT NOT NULL DEFAULT '',
        modelo TEXT NOT NULL DEFAULT '',
        ano TEXT NOT NULL DEFAULT '',
        sistema TEXT NOT NULL DEFAULT '',
        descricao TEXT NOT NULL DEFAULT '',
        chip TEXT NOT NULL DEFAULT '',
        chave TEXT NOT NULL DEFAULT '',
        telecomando TEXT NOT NULL DEFAULT '',
        frequencia TEXT NOT NULL DEFAULT '',
        avisos TEXT NOT NULL DEFAULT '',
        funcoes TEXT NOT NULL DEFAULT '',
        manual TEXT NOT NULL DEFAULT '',
        foto TEXT NOT NULL DEFAULT '',
        confirmado INTEGER NOT NULL DEFAULT 0,
        origem TEXT NOT NULL DEFAULT '',
        busca TEXT NOT NULL DEFAULT '',
        criado_em INTEGER NOT NULL,
        atualizado_em INTEGER NOT NULL
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
    if (versaoAntiga < 5) {
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN tipos_servico TEXT NOT NULL DEFAULT ''")
      banco.execSQL("UPDATE atendimentos SET tipos_servico = tipo_servico WHERE tipos_servico = ''")
    }
    if (versaoAntiga < 6) {
      criarTabelaFichas(banco)
      banco.execSQL("CREATE INDEX idx_fichas_veiculo ON fichas_tecnicas(marca, modelo)")
    }
    if (versaoAntiga < 7) {
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN revisao_remota INTEGER NOT NULL DEFAULT 0")
      banco.execSQL("ALTER TABLE atendimentos ADD COLUMN sinc_pendente INTEGER NOT NULL DEFAULT 0")
      banco.execSQL("ALTER TABLE fichas_tecnicas ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
      banco.execSQL("ALTER TABLE fichas_tecnicas ADD COLUMN revisao_remota INTEGER NOT NULL DEFAULT 0")
      banco.execSQL("ALTER TABLE fichas_tecnicas ADD COLUMN sinc_pendente INTEGER NOT NULL DEFAULT 0")
      banco.execSQL("ALTER TABLE fotos ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
      banco.execSQL("ALTER TABLE fotos ADD COLUMN revisao_remota INTEGER NOT NULL DEFAULT 0")
      banco.execSQL("ALTER TABLE fotos ADD COLUMN sinc_pendente INTEGER NOT NULL DEFAULT 0")
      banco.execSQL(
        """CREATE TABLE sync_saidas (
          op_id TEXT PRIMARY KEY,
          entidade TEXT NOT NULL DEFAULT 'atendimento',
          uuid TEXT NOT NULL,
          tipo TEXT NOT NULL,
          base_revisao INTEGER NOT NULL,
          criado_em INTEGER NOT NULL
        )""",
      )
      banco.execSQL(
        """CREATE TABLE sync_conflitos (
          local_id INTEGER PRIMARY KEY,
          entidade TEXT NOT NULL DEFAULT 'atendimento',
          uuid TEXT NOT NULL,
          revisao_servidor INTEGER NOT NULL,
          criado_em INTEGER NOT NULL
        )""",
      )
      banco.execSQL(
        """CREATE TABLE sync_excluidos (
          uuid TEXT PRIMARY KEY,
          criado_em INTEGER NOT NULL
        )""",
      )
      // Registros antigos ganham UUID e entram na fila de envio uma vez.
      banco.query("atendimentos", arrayOf("id", "uuid"), "uuid = ''", null, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val idLocal = cursor.getLong(0)
          banco.update(
            "atendimentos",
            ContentValues().apply {
              put("uuid", java.util.UUID.randomUUID().toString())
              put("sinc_pendente", 1)
            },
            "id = ?",
            arrayOf(idLocal.toString()),
          )
        }
      }
      banco.query("fichas_tecnicas", arrayOf("id", "uuid"), "uuid = ''", null, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val idLocal = cursor.getLong(0)
          banco.update(
            "fichas_tecnicas",
            ContentValues().apply {
              put("uuid", java.util.UUID.randomUUID().toString())
              put("sinc_pendente", 1)
            },
            "id = ?",
            arrayOf(idLocal.toString()),
          )
        }
      }
      banco.query("fotos", arrayOf("id", "uuid"), "uuid = ''", null, null, null, null).use { cursor ->
        while (cursor.moveToNext()) {
          val idLocal = cursor.getLong(0)
          banco.update(
            "fotos",
            ContentValues().apply {
              put("uuid", java.util.UUID.randomUUID().toString())
              put("sinc_pendente", 1)
            },
            "id = ?",
            arrayOf(idLocal.toString()),
          )
        }
      }
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
      // A lista é delimitada por vírgulas; a busca mantém correspondência exata.
      condicoes.add("instr(',' || a.tipos_servico || ',', ',' || ? || ',') > 0")
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
      put("tipo_servico", atendimento.tiposOrdenados.first().name)
      put("tipos_servico", atendimento.tiposOrdenados.joinToString(",") { it.name })
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
      put("sinc_pendente", 1)
    }
    val banco = writableDatabase
    if (atendimento.id == 0L) {
      valores.put("criado_em", agora)
      valores.put("uuid", java.util.UUID.randomUUID().toString())
      valores.put("revisao_remota", 0)
      return banco.insert("atendimentos", null, valores)
    }
    banco.update("atendimentos", valores, "id = ?", arrayOf(atendimento.id.toString()))
    return atendimento.id
  }

  fun excluir(id: Long): List<String> {
    val caminhos = listarFotos(id).map { it.caminho }
    // Saída de sync precisa sobreviver ao delete local.
    val sqlite = writableDatabase
    sqlite.query("atendimentos", arrayOf("uuid", "revisao_remota"), "id = ?", arrayOf(id.toString()),
      null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        val uuid = cursor.getString(0)
        val revisao = cursor.getLong(1)
        if (uuid.isNotBlank()) {
          sqlite.insert("sync_saidas", null, ContentValues().apply {
            put("op_id", java.util.UUID.randomUUID().toString())
            put("entidade", "atendimento")
            put("uuid", uuid)
            put("tipo", "exclusao")
            put("base_revisao", revisao)
            put("criado_em", System.currentTimeMillis())
          })
        }
      }
    }
    sqlite.delete("atendimentos", "id = ?", arrayOf(id.toString()))
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
      put("uuid", java.util.UUID.randomUUID().toString())
      put("revisao_remota", 0)
      put("sinc_pendente", 1)
    }
    return writableDatabase.insert("fotos", null, valores)
  }

  fun atualizarFoto(foto: Foto) {
    val valores = ContentValues().apply {
      put("categoria", foto.categoria.name)
      put("legenda", foto.legenda)
      put("ordem", foto.ordem)
      put("sinc_pendente", 1)
    }
    writableDatabase.update("fotos", valores, "id = ?", arrayOf(foto.id.toString()))
  }

  fun excluirFoto(id: Long): String? {
    val caminho = readableDatabase.rawQuery(
      "SELECT caminho FROM fotos WHERE id = ?",
      arrayOf(id.toString()),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    val sqlite = writableDatabase
    sqlite.query("fotos", arrayOf("uuid", "revisao_remota"), "id = ?", arrayOf(id.toString()),
      null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        val uuid = cursor.getString(0)
        if (uuid.isNotBlank()) {
          sqlite.insert("sync_saidas", null, ContentValues().apply {
            put("op_id", java.util.UUID.randomUUID().toString())
            put("entidade", "foto")
            put("uuid", uuid)
            put("tipo", "exclusao")
            put("base_revisao", cursor.getLong(1))
            put("criado_em", System.currentTimeMillis())
          })
        }
      }
    }
    sqlite.delete("fotos", "id = ?", arrayOf(id.toString()))
    return caminho
  }

  fun contarTodos(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM atendimentos", null).use { cursor ->
    if (cursor.moveToFirst()) cursor.getInt(0) else 0
  }

  fun listarFichas(consulta: String = ""): List<FichaTecnica> {
    val tokens = normalizar(consulta).split(' ').filter { it.isNotBlank() }
    val condicoes = tokens.map { "busca LIKE ?" }
    val onde = if (condicoes.isEmpty()) "" else " WHERE " + condicoes.joinToString(" AND ")
    return readableDatabase.rawQuery(
      "SELECT * FROM fichas_tecnicas$onde " +
        "ORDER BY lower(marca), lower(modelo), ano, lower(sistema), id",
      tokens.map { "%$it%" }.toTypedArray(),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.paraFicha())
      }
    }
  }

  fun obterFicha(id: Long): FichaTecnica? =
    readableDatabase.rawQuery(
      "SELECT * FROM fichas_tecnicas WHERE id = ?",
      arrayOf(id.toString()),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.paraFicha() else null }

  fun salvarFicha(ficha: FichaTecnica): Long {
    val agora = System.currentTimeMillis()
    val valores = ContentValues().apply {
      put("equipamento", ficha.equipamento.trim())
      put("marca", ficha.marca.trim())
      put("modelo", ficha.modelo.trim())
      put("ano", ficha.ano.trim())
      put("sistema", ficha.sistema.trim())
      put("descricao", ficha.descricao.trim())
      put("chip", ficha.chip.trim().uppercase())
      put("chave", ficha.chave.trim())
      put("telecomando", ficha.telecomando.trim())
      put("frequencia", ficha.frequencia.trim())
      put("avisos", ficha.avisos.trim())
      put("funcoes", ficha.funcoes.trim())
      put("manual", ficha.manual.trim())
      put("foto", ficha.foto)
      put("confirmado", if (ficha.confirmado) 1 else 0)
      put("origem", ficha.origem.trim())
      put("busca", normalizar(montarBuscaDaFicha(ficha)))
      put("atualizado_em", agora)
      put("sinc_pendente", 1)
    }
    val banco = writableDatabase
    if (ficha.id == 0L) {
      valores.put("criado_em", agora)
      valores.put("uuid", java.util.UUID.randomUUID().toString())
      valores.put("revisao_remota", 0)
      return banco.insert("fichas_tecnicas", null, valores)
    }
    banco.update("fichas_tecnicas", valores, "id = ?", arrayOf(ficha.id.toString()))
    return ficha.id
  }

  fun excluirFicha(id: Long): String? {
    val foto = readableDatabase.rawQuery(
      "SELECT foto FROM fichas_tecnicas WHERE id = ?",
      arrayOf(id.toString()),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    val sqlite = writableDatabase
    sqlite.query("fichas_tecnicas", arrayOf("uuid", "revisao_remota"), "id = ?", arrayOf(id.toString()),
      null, null, null).use { cursor ->
      if (cursor.moveToFirst()) {
        val uuid = cursor.getString(0)
        if (uuid.isNotBlank()) {
          sqlite.insert("sync_saidas", null, ContentValues().apply {
            put("op_id", java.util.UUID.randomUUID().toString())
            put("entidade", "ficha")
            put("uuid", uuid)
            put("tipo", "exclusao")
            put("base_revisao", cursor.getLong(1))
            put("criado_em", System.currentTimeMillis())
          })
        }
      }
    }
    sqlite.delete("fichas_tecnicas", "id = ?", arrayOf(id.toString()))
    return foto
  }

  private fun montarBuscaDaFicha(ficha: FichaTecnica): String = listOf(
    ficha.equipamento,
    ficha.marca,
    ficha.modelo,
    ficha.ano,
    ficha.sistema,
    ficha.descricao,
    ficha.chip,
    ficha.chave,
    ficha.telecomando,
    ficha.frequencia,
    ficha.avisos,
    ficha.funcoes,
    ficha.manual,
    ficha.origem,
  ).joinToString(" ")

  private fun Cursor.paraFicha(): FichaTecnica =
    FichaTecnica(
      id = getLong(getColumnIndexOrThrow("id")),
      equipamento = getString(getColumnIndexOrThrow("equipamento")),
      marca = getString(getColumnIndexOrThrow("marca")),
      modelo = getString(getColumnIndexOrThrow("modelo")),
      ano = getString(getColumnIndexOrThrow("ano")),
      sistema = getString(getColumnIndexOrThrow("sistema")),
      descricao = getString(getColumnIndexOrThrow("descricao")),
      chip = getString(getColumnIndexOrThrow("chip")),
      chave = getString(getColumnIndexOrThrow("chave")),
      telecomando = getString(getColumnIndexOrThrow("telecomando")),
      frequencia = getString(getColumnIndexOrThrow("frequencia")),
      avisos = getString(getColumnIndexOrThrow("avisos")),
      funcoes = getString(getColumnIndexOrThrow("funcoes")),
      manual = getString(getColumnIndexOrThrow("manual")),
      foto = getString(getColumnIndexOrThrow("foto")),
      confirmado = getInt(getColumnIndexOrThrow("confirmado")) == 1,
      origem = getString(getColumnIndexOrThrow("origem")),
      atualizadoEm = getLong(getColumnIndexOrThrow("atualizado_em")),
    )

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
      tiposServico = TipoServico.deNomes(
        getString(getColumnIndexOrThrow("tipos_servico")).split(","),
        TipoServico.de(getString(getColumnIndexOrThrow("tipo_servico"))),
      ),
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
      atendimento.tiposEmTexto,
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

  // ---- Sincronização (v7): UUID, revisão remota, saída e conflitos ----

  fun uuidDe(id: Long): String =
    readableDatabase.query("atendimentos", arrayOf("uuid"), "id = ?", arrayOf(id.toString()),
      null, null, null).use { if (it.moveToFirst()) it.getString(0) else "" }

  fun revisaoRemotaDe(id: Long): Long =
    readableDatabase.query("atendimentos", arrayOf("revisao_remota"), "id = ?", arrayOf(id.toString()),
      null, null, null).use { if (it.moveToFirst()) it.getLong(0) else 0L }

  fun sincPendenteDe(id: Long): Boolean =
    readableDatabase.query("atendimentos", arrayOf("sinc_pendente"), "id = ?", arrayOf(id.toString()),
      null, null, null).use { if (it.moveToFirst()) it.getInt(0) == 1 else false }

  fun obterPorUuid(uuid: String): Atendimento? =
    readableDatabase.query("atendimentos", arrayOf("id"), "uuid = ?", arrayOf(uuid),
      null, null, null).use { if (it.moveToFirst()) obter(it.getLong(0)) else null }

  fun pendentesSync(): List<Atendimento> =
    consultar(
      "SELECT a.*, (SELECT COUNT(*) FROM fotos f WHERE f.atendimento_id = a.id) AS total_fotos " +
        "FROM atendimentos a WHERE a.sinc_pendente = 1 ORDER BY a.id",
      emptyArray(),
    )

  fun saidasExcluidos(): List<SaidaSync> =
    readableDatabase.query("sync_saidas", arrayOf("op_id", "entidade", "uuid", "base_revisao"), null, null, null, null, "criado_em").use { cursor ->
      buildList { while (cursor.moveToNext()) add(SaidaSync(cursor.getString(0), cursor.getString(1), cursor.getString(2), cursor.getLong(3))) }
    }

  fun confirmarSaida(opId: String, uuid: String) {
    val db = writableDatabase
    db.delete("sync_saidas", "op_id = ?", arrayOf(opId))
    db.insertWithOnConflict("sync_excluidos", null, ContentValues().apply {
      put("uuid", uuid); put("criado_em", System.currentTimeMillis())
    }, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
  }

  fun naoPublicar(opId: String) {
    writableDatabase.delete("sync_saidas", "op_id = ?", arrayOf(opId))
  }

  fun jaExcluidoConhecido(uuid: String): Boolean =
    readableDatabase.query("sync_excluidos", arrayOf("uuid"), "uuid = ?", arrayOf(uuid),
      null, null, null).use { it.moveToFirst() }

  /** Aplica registro remoto sem gerar nova saída; limpa pendência. */
  fun aplicarRemoto(atendimento: Atendimento, uuid: String, revisao: Long): Long {
    val local = obterPorUuid(uuid)
    if (local == null) {
      val valores = valoresDeSync(atendimento, uuid, revisao)
      valores.put("criado_em", System.currentTimeMillis())
      return writableDatabase.insert("atendimentos", null, valores)
    }
    writableDatabase.update(
      "atendimentos", valoresDeSync(atendimento, uuid, revisao),
      "id = ?", arrayOf(local.id.toString()),
    )
    return local.id
  }

  fun marcarEnviado(id: Long, revisao: Long) {
    writableDatabase.update(
      "atendimentos",
      ContentValues().apply {
        put("revisao_remota", revisao)
        put("sinc_pendente", 0)
      },
      "id = ?", arrayOf(id.toString()),
    )
  }

  fun excluirPorUuid(uuid: String) {
    val local = obterPorUuid(uuid) ?: return
    writableDatabase.delete("atendimentos", "id = ?", arrayOf(local.id.toString()))
  }

  fun conflitos(): List<ConflitoSync> =
    readableDatabase.rawQuery(
      "SELECT c.local_id, c.uuid, c.revisao_servidor, c.entidade, a.placa, f.equipamento FROM sync_conflitos c " +
        "LEFT JOIN atendimentos a ON a.id = c.local_id " +
        "LEFT JOIN fichas_tecnicas f ON f.id = c.local_id ORDER BY c.criado_em",
      null,
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          val entidade = cursor.getString(3) ?: "atendimento"
          val placa = cursor.getString(4) ?: cursor.getString(5) ?: ""
          add(ConflitoSync(cursor.getLong(0), cursor.getString(1), cursor.getLong(2), placa, entidade))
        }
      }
    }

  fun registrarConflito(localId: Long, uuid: String, revisaoServidor: Long, entidade: String = "atendimento") {
    writableDatabase.insertWithOnConflict("sync_conflitos", null, ContentValues().apply {
      put("local_id", localId)
      put("entidade", entidade)
      put("uuid", uuid)
      put("revisao_servidor", revisaoServidor)
      put("criado_em", System.currentTimeMillis())
    }, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
  }

  fun limparConflito(localId: Long) {
    writableDatabase.delete("sync_conflitos", "local_id = ?", arrayOf(localId.toString()))
  }

  private fun valoresDeSync(atendimento: Atendimento, uuid: String, revisao: Long): ContentValues =
    ContentValues().apply {
      put("placa", atendimento.placa.trim().uppercase())
      put("marca", atendimento.marca.trim())
      put("modelo", atendimento.modelo.trim())
      put("ano", atendimento.ano.trim())
      put("chassi", atendimento.chassi.trim().uppercase())
      put("data_servico", atendimento.dataServico)
      put("tipo_servico", atendimento.tiposOrdenados.first().name)
      put("tipos_servico", atendimento.tiposOrdenados.joinToString(",") { it.name })
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
      put("atualizado_em", System.currentTimeMillis())
      put("uuid", uuid)
      put("revisao_remota", revisao)
      put("sinc_pendente", 0)
    }

  companion object {
    const val NOME = "chaveirocar.db"
    const val VERSAO = 7
  }
}

// ---- Fichas e fotos no sync (v7) ----

fun BancoDeAtendimentos.uuidFicha(id: Long): String =
  writableDatabase.query("fichas_tecnicas", arrayOf("uuid"), "id = ?", arrayOf(id.toString()),
    null, null, null).use { if (it.moveToFirst()) it.getString(0) else "" }

fun BancoDeAtendimentos.revisaoRemotaFicha(id: Long): Long =
  writableDatabase.query("fichas_tecnicas", arrayOf("revisao_remota"), "id = ?", arrayOf(id.toString()),
    null, null, null).use { if (it.moveToFirst()) it.getLong(0) else 0L }

fun BancoDeAtendimentos.sincPendenteFicha(id: Long): Boolean =
  writableDatabase.query("fichas_tecnicas", arrayOf("sinc_pendente"), "id = ?", arrayOf(id.toString()),
    null, null, null).use { if (it.moveToFirst()) it.getInt(0) == 1 else false }

fun BancoDeAtendimentos.obterFichaPorUuid(uuid: String): FichaTecnica? =
  writableDatabase.query("fichas_tecnicas", arrayOf("id"), "uuid = ?", arrayOf(uuid),
    null, null, null).use { if (it.moveToFirst()) obterFicha(it.getLong(0)) else null }

fun BancoDeAtendimentos.pendentesFichas(): List<FichaTecnica> =
  this.listarFichas().filter { sincPendenteFicha(it.id) }

fun BancoDeAtendimentos.aplicarFichaRemota(ficha: FichaTecnica, uuid: String, revisao: Long): Long {
  val valores = valoresFichaDeSync(ficha, uuid, revisao)
  val local = obterFichaPorUuid(uuid)
  if (local == null) {
    valores.put("criado_em", System.currentTimeMillis())
    return writableDatabase.insert("fichas_tecnicas", null, valores)
  }
  writableDatabase.update("fichas_tecnicas", valores, "id = ?", arrayOf(local.id.toString()))
  return local.id
}

fun BancoDeAtendimentos.marcarFichaEnviada(id: Long, revisao: Long) {
  writableDatabase.update(
    "fichas_tecnicas",
    ContentValues().apply { put("revisao_remota", revisao); put("sinc_pendente", 0) },
    "id = ?", arrayOf(id.toString()),
  )
}

fun BancoDeAtendimentos.excluirFichaPorUuid(uuid: String) {
  val local = obterFichaPorUuid(uuid) ?: return
  writableDatabase.delete("fichas_tecnicas", "id = ?", arrayOf(local.id.toString()))
}

fun BancoDeAtendimentos.uuidFoto(id: Long): String =
  writableDatabase.query("fotos", arrayOf("uuid"), "id = ?", arrayOf(id.toString()),
    null, null, null).use { if (it.moveToFirst()) it.getString(0) else "" }

fun BancoDeAtendimentos.obterFoto(id: Long): com.willendary.chaveirocar.modelo.Foto? =
  writableDatabase.query("fotos", arrayOf("id", "atendimento_id", "caminho", "categoria", "legenda", "ordem"),
    "id = ?", arrayOf(id.toString()), null, null, null).use { cursor ->
    if (!cursor.moveToFirst()) null
    else com.willendary.chaveirocar.modelo.Foto(
      id = cursor.getLong(0), atendimentoId = cursor.getLong(1),
      caminho = cursor.getString(2), categoria = CategoriaFoto.de(cursor.getString(3)),
      legenda = cursor.getString(4), ordem = cursor.getInt(5),
    )
  }

fun BancoDeAtendimentos.obterFotoPorUuid(uuid: String): com.willendary.chaveirocar.modelo.Foto? =
  writableDatabase.query("fotos", arrayOf("id"), "uuid = ?", arrayOf(uuid),
    null, null, null).use { if (it.moveToFirst()) obterFoto(it.getLong(0)) else null }

fun BancoDeAtendimentos.pendentesFotos(): List<com.willendary.chaveirocar.modelo.Foto> =
  writableDatabase.query("fotos", arrayOf("id", "atendimento_id", "caminho", "categoria", "legenda", "ordem"),
    "sinc_pendente = 1", null, null, null, "id").use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(com.willendary.chaveirocar.modelo.Foto(
          id = cursor.getLong(0), atendimentoId = cursor.getLong(1),
          caminho = cursor.getString(2), categoria = CategoriaFoto.de(cursor.getString(3)),
          legenda = cursor.getString(4), ordem = cursor.getInt(5),
        ))
      }
    }
  }

fun BancoDeAtendimentos.revisaoRemotaFoto(id: Long): Long =
  writableDatabase.query("fotos", arrayOf("revisao_remota"), "id = ?", arrayOf(id.toString()),
    null, null, null).use { if (it.moveToFirst()) it.getLong(0) else 0L }

fun BancoDeAtendimentos.marcarFotoEnviada(id: Long, revisao: Long) {
  writableDatabase.update(
    "fotos",
    ContentValues().apply { put("revisao_remota", revisao); put("sinc_pendente", 0) },
    "id = ?", arrayOf(id.toString()),
  )
}

fun BancoDeAtendimentos.aplicarFotoRemota(
  uuid: String, atendimentoId: Long, caminho: String, categoria: String,
  legenda: String, ordem: Int, revisao: Long,
): Long {
  val local = obterFotoPorUuid(uuid)
  val valores = ContentValues().apply {
    put("atendimento_id", atendimentoId)
    put("caminho", caminho)
    put("categoria", categoria)
    put("legenda", legenda)
    put("ordem", ordem)
    put("uuid", uuid)
    put("revisao_remota", revisao)
    put("sinc_pendente", 0)
  }
  if (local == null) {
    valores.put("criado_em", System.currentTimeMillis())
    return writableDatabase.insert("fotos", null, valores)
  }
  writableDatabase.update("fotos", valores, "id = ?", arrayOf(local.id.toString()))
  return local.id
}

fun BancoDeAtendimentos.excluirFotoPorUuid(uuid: String) {
  val local = obterFotoPorUuid(uuid) ?: return
  writableDatabase.delete("fotos", "id = ?", arrayOf(local.id.toString()))
}

private fun valoresFichaDeSync(ficha: FichaTecnica, uuid: String, revisao: Long): ContentValues =
  ContentValues().apply {
    put("equipamento", ficha.equipamento.trim())
    put("marca", ficha.marca.trim())
    put("modelo", ficha.modelo.trim())
    put("ano", ficha.ano.trim())
    put("sistema", ficha.sistema.trim())
    put("descricao", ficha.descricao.trim())
    put("chip", ficha.chip.trim().uppercase())
    put("chave", ficha.chave.trim())
    put("telecomando", ficha.telecomando.trim())
    put("frequencia", ficha.frequencia.trim())
    put("avisos", ficha.avisos.trim())
    put("funcoes", ficha.funcoes.trim())
    put("manual", ficha.manual.trim())
    put("foto", ficha.foto)
    put("confirmado", if (ficha.confirmado) 1 else 0)
    put("origem", ficha.origem.trim())
    put("busca", com.willendary.chaveirocar.util.normalizar(
      listOf(ficha.equipamento, ficha.marca, ficha.modelo, ficha.ano, ficha.sistema,
        ficha.descricao, ficha.chip, ficha.chave, ficha.telecomando, ficha.frequencia,
        ficha.avisos, ficha.funcoes, ficha.manual, ficha.origem).joinToString(" ")))
    put("atualizado_em", System.currentTimeMillis())
    put("uuid", uuid)
    put("revisao_remota", revisao)
    put("sinc_pendente", 0)
  }

data class SaidaSync(val opId: String, val entidade: String, val uuid: String, val baseRevisao: Long)

data class ConflitoSync(
  val localId: Long,
  val uuid: String,
  val revisaoServidor: Long,
  val placa: String,
  val entidade: String = "atendimento",
)
