package com.willendary.chaveirocar.catalogo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OpcaoCatalogo(val codigo: String, val nome: String)

data class RespostaCatalogo(
  val opcoes: List<OpcaoCatalogo>,
  val aviso: String? = null,
)

/**
 * Catálogo FIPE (Parallelum) usado só para preencher marca, modelo e ano.
 * É um catálogo por marca/modelo/ano; não consulta placa, proprietário nem preço.
 */
object CatalogoFipe {
  private const val BASE = "https://fipe.parallelum.com.br/api/v2"
  private const val DIA = 24L * 60 * 60 * 1000
  private const val VALIDADE_MARCAS = 30 * DIA
  private const val VALIDADE_MODELOS = 30 * DIA
  private const val VALIDADE_ANOS = 90 * DIA

  suspend fun marcas(contexto: Context): RespostaCatalogo =
    consultar(contexto, "marcas", "$BASE/cars/brands", VALIDADE_MARCAS)

  suspend fun modelos(contexto: Context, marca: String): RespostaCatalogo =
    consultar(contexto, "marcas_$marca", "$BASE/cars/brands/$marca/models", VALIDADE_MODELOS)

  suspend fun anos(contexto: Context, marca: String, modelo: String): RespostaCatalogo =
    consultar(contexto, "anos_${marca}_$modelo", "$BASE/cars/brands/$marca/models/$modelo/years", VALIDADE_ANOS)

  /** Lê a resposta da API (v2 usa code/name) e ignora itens sem código ou nome. */
  fun interpretarOpcoes(json: String): List<OpcaoCatalogo> {
    if (json.isBlank()) return emptyList()
    val lista = JSONArray(json)
    return buildList {
      for (indice in 0 until lista.length()) {
        val item = lista.optJSONObject(indice) ?: continue
        val codigo = item.optString("code").ifBlank { item.optString("codigo") }.trim()
        val nome = item.optString("name").ifBlank { item.optString("nome") }.trim()
        if (codigo.isNotBlank() && nome.isNotBlank()) add(OpcaoCatalogo(codigo, nome))
      }
    }
  }

  /** "2014 Gasolina" -> "2014". A FIPE informa ano-modelo, não o ano de fabricação. */
  fun extrairAno(nome: String): String =
    Regex("""\b(?:19|20)\d{2}\b""").find(nome)?.value.orEmpty()

  private suspend fun consultar(
    contexto: Context,
    chave: String,
    url: String,
    validade: Long,
  ): RespostaCatalogo = withContext(Dispatchers.IO) {
    val arquivo = File(File(contexto.cacheDir, "catalogo_fipe").apply { mkdirs() }, "$chave.json")
    val cache = lerCache(arquivo)
    if (cache != null && System.currentTimeMillis() - cache.first <= validade) {
      return@withContext RespostaCatalogo(cache.second)
    }
    runCatching { baixar(url) }.fold(
      onSuccess = { bruto ->
        val opcoes = interpretarOpcoes(bruto)
        if (opcoes.isEmpty()) error("O catálogo não retornou resultados.")
        gravarCache(arquivo, opcoes)
        RespostaCatalogo(opcoes)
      },
      onFailure = { erro ->
        if (cache != null) {
          RespostaCatalogo(cache.second, "Sem conexão com o catálogo: usando a cópia salva.")
        } else {
          throw IllegalStateException(mensagemDeErro(erro), erro)
        }
      },
    )
  }

  private fun baixar(url: String): String {
    val conexao = (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = 8_000
      readTimeout = 12_000
      setRequestProperty("Accept", "application/json")
      setRequestProperty("User-Agent", "ChaveiroCar")
    }
    try {
      val codigo = conexao.responseCode
      if (codigo == 429) error("Limite diário do catálogo atingido. Digite os dados manualmente.")
      if (codigo !in 200..299) error("O catálogo está indisponível agora (erro $codigo).")
      return conexao.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
      conexao.disconnect()
    }
  }

  private fun lerCache(arquivo: File): Pair<Long, List<OpcaoCatalogo>>? {
    if (!arquivo.exists()) return null
    return runCatching {
      val raiz = JSONObject(arquivo.readText(Charsets.UTF_8))
      val lista = raiz.optJSONArray("opcoes") ?: return null
      raiz.getLong("baixadoEm") to interpretarOpcoes(lista.toString())
    }.getOrNull()?.takeIf { it.second.isNotEmpty() }
  }

  private fun gravarCache(arquivo: File, opcoes: List<OpcaoCatalogo>) {
    val lista = JSONArray()
    opcoes.forEach { opcao ->
      lista.put(JSONObject().put("codigo", opcao.codigo).put("nome", opcao.nome))
    }
    runCatching {
      arquivo.writeText(
        JSONObject().put("baixadoEm", System.currentTimeMillis()).put("opcoes", lista).toString(),
        Charsets.UTF_8,
      )
    }
  }

  private fun mensagemDeErro(erro: Throwable): String =
    erro.message?.takeIf { it.isNotBlank() } ?: "Não foi possível carregar o catálogo. Digite os dados manualmente."
}
