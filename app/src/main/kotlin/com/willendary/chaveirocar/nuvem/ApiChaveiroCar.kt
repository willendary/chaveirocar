package com.willendary.chaveirocar.nuvem

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente mínimo da API ChaveiroCar (contracts/openapi.json).
 * O app local (SQLite) continua como fonte offline; este cliente
 * entra na fila de sincronização incremental.
 */
class ApiChaveiroCar(private val baseUrl: String = "http://10.0.2.2:8787") {

  @Volatile var tokenAcesso: String? = null

  fun entrar(email: String, senha: String): JSONObject {
    val resposta = post("/api/v1/auth/login", JSONObject().apply {
      put("email", email); put("senha", senha)
    })
    val tokens = resposta.getJSONObject("tokens")
    tokenAcesso = tokens.getString("acesso")
    return resposta
  }

  fun listarAtendimentos(empresaId: String, busca: String = ""): JSONObject {
    val query = if (busca.isBlank()) "" else "?busca=${java.net.URLEncoder.encode(busca, "UTF-8")}"
    return get("/api/v1/empresas/$empresaId/atendimentos$query")
  }

  fun sincronizar(empresaId: String, cursor: Long): JSONObject =
    get("/api/v1/empresas/$empresaId/sync?cursor=$cursor")

  fun enviarLote(empresaId: String, operacoes: org.json.JSONArray): JSONObject =
    post("/api/v1/empresas/$empresaId/sync", JSONObject().apply { put("operacoes", operacoes) })

  private fun get(caminho: String): JSONObject {
    val conexao = (URL(baseUrl + caminho).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      tokenAcesso?.let { setRequestProperty("Authorization", "Bearer $it") }
    }
    return ler(conexao)
  }

  private fun post(caminho: String, corpo: JSONObject): JSONObject {
    val bytes = corpo.toString().toByteArray(Charsets.UTF_8)
    val conexao = (URL(baseUrl + caminho).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      setRequestProperty("Content-Type", "application/json")
      tokenAcesso?.let { setRequestProperty("Authorization", "Bearer $it") }
      doOutput = true
      setFixedLengthStreamingMode(bytes.size)
    }
    conexao.outputStream.use { it.write(bytes) }
    return ler(conexao)
  }

  private fun ler(conexao: HttpURLConnection): JSONObject {
    val fluxo = if (conexao.responseCode in 200..299) conexao.inputStream else conexao.errorStream
    val texto = fluxo.bufferedReader().use { it.readText() }
    if (conexao.responseCode !in 200..299) error("API ${conexao.responseCode}: $texto")
    return JSONObject(texto)
  }
}
