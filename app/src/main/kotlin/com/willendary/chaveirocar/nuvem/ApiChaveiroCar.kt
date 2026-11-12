package com.willendary.chaveirocar.nuvem

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP da API ChaveiroCar (contracts/openapi.json).
 * O app local (SQLite) continua offline-first; este cliente
 * entra na fila de sincronização incremental.
 * Refresh transparente: em 401, tenta /renovar uma vez.
 */
class ApiChaveiroCar(private val baseUrl: String = "http://10.0.2.2:8787") {

  @Volatile var tokenAcesso: String? = null
  @Volatile var tokenRefresh: String? = null

  fun entrar(email: String, senha: String): JSONObject {
    val resposta = post("/api/v1/auth/login", JSONObject().apply {
      put("email", email); put("senha", senha)
    })
    guardarTokens(resposta)
    return resposta
  }

  fun renovar(): Boolean {
    val atual = tokenRefresh ?: return false
    return runCatching {
      val resposta = post("/api/v1/auth/renovar", JSONObject().apply { put("refresh", atual) })
      guardarTokens(resposta)
      true
    }.getOrDefault(false)
  }

  fun sair() {
    runCatching {
      val corpo = JSONObject().apply {
        tokenRefresh?.let { put("refresh", it) }
      }
      post("/api/v1/auth/sair", corpo)
    }
    tokenAcesso = null
    tokenRefresh = null
  }

  private fun guardarTokens(resposta: JSONObject) {
    val tokens = resposta.optJSONObject("tokens") ?: return
    tokenAcesso = tokens.optString("acesso").ifBlank { null }
    tokenRefresh = tokens.optString("refresh").ifBlank { null }
  }

  fun eu(): JSONObject = get("/api/v1/eu")

  fun listarEmpresas(): org.json.JSONArray =
    get("/api/v1/empresas").getJSONArray("itens")

  fun obterAtendimento(empresaId: String, atendimentoId: String): JSONObject =
    get("/api/v1/empresas/$empresaId/atendimentos/$atendimentoId")

  fun obterFicha(empresaId: String, fichaId: String): JSONObject =
    get("/api/v1/empresas/$empresaId/fichas/$fichaId")

  /** Upload multipart de foto (campo `arquivo`). */
  fun enviarFoto(
    empresaId: String,
    atendimentoUuid: String,
    fotoUuid: String,
    categoria: String,
    legenda: String,
    ordem: Long,
    bytes: ByteArray,
  ): JSONObject {
    val fronteira = "----chaveirocar${java.util.UUID.randomUUID()}"
    val saida = java.io.ByteArrayOutputStream()
    fun parte(nome: String, valor: String) {
      saida.write("--$fronteira\r\nContent-Disposition: form-data; name=\"$nome\"\r\n\r\n$valor\r\n".toByteArray(Charsets.UTF_8))
    }
    parte("id", fotoUuid)
    parte("categoria", categoria)
    parte("legenda", legenda)
    parte("ordem", ordem.toString())
    saida.write(
      ("--$fronteira\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"foto.jpg\"\r\n" +
        "Content-Type: image/jpeg\r\n\r\n").toByteArray(Charsets.UTF_8),
    )
    saida.write(bytes)
    saida.write("\r\n--$fronteira--\r\n".toByteArray(Charsets.UTF_8))
    val corpo = saida.toByteArray()
    val conexao = (URL(baseUrl + "/api/v1/empresas/$empresaId/atendimentos/$atendimentoUuid/fotos")
      .openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      setRequestProperty("Content-Type", "multipart/form-data; boundary=$fronteira")
      tokenAcesso?.let { setRequestProperty("Authorization", "Bearer $it") }
      doOutput = true
      setFixedLengthStreamingMode(corpo.size)
    }
    conexao.outputStream.use { it.write(corpo) }
    val fluxo = if (conexao.responseCode in 200..299) conexao.inputStream else conexao.errorStream
    val texto = fluxo.bufferedReader().use { it.readText() }
    if (conexao.responseCode !in 200..299) error("API ${conexao.responseCode}: $texto")
    return JSONObject(texto)
  }

  /** Baixa bytes de foto remota (`url` relativo da API). */
  fun baixarFoto(urlRelativa: String): ByteArray {
    val conexao = (URL(baseUrl + urlRelativa).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      tokenAcesso?.let { setRequestProperty("Authorization", "Bearer $it") }
    }
    if (conexao.responseCode !in 200..299) {
      val erro = conexao.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
      error("API ${conexao.responseCode}: $erro")
    }
    return conexao.inputStream.use { it.readBytes() }
  }

  fun listarAtendimentos(empresaId: String, busca: String = ""): JSONObject {
    val query = if (busca.isBlank()) "" else "?busca=${java.net.URLEncoder.encode(busca, "UTF-8")}"
    return get("/api/v1/empresas/$empresaId/atendimentos$query")
  }

  fun sincronizar(empresaId: String, cursor: Long): JSONObject =
    get("/api/v1/empresas/$empresaId/sync?cursor=$cursor")

  fun enviarLote(empresaId: String, operacoes: org.json.JSONArray): JSONObject =
    post("/api/v1/empresas/$empresaId/sync", JSONObject().apply { put("operacoes", operacoes) })

  private fun get(caminho: String): JSONObject = request("GET", caminho, null)

  private fun post(caminho: String, corpo: JSONObject): JSONObject = request("POST", caminho, corpo)

  private fun request(metodo: String, caminho: String, corpo: JSONObject?): JSONObject {
    fun tentar(): JSONObject {
      val url = URL(baseUrl + caminho)
      val conexao = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = metodo
        tokenAcesso?.let { setRequestProperty("Authorization", "Bearer $it") }
        if (corpo != null) {
          setRequestProperty("Content-Type", "application/json")
          doOutput = true
          val bytes = corpo.toString().toByteArray(Charsets.UTF_8)
          setFixedLengthStreamingMode(bytes.size)
          outputStream.use { it.write(bytes) }
        }
      }
      val texto = lerTexto(conexao)
      if (conexao.responseCode !in 200..299) {
        if (conexao.responseCode == 401 && caminho != "/api/v1/auth/renovar" && caminho != "/api/v1/auth/login") {
          throw ExpiradoException(texto)
        }
        error("API ${conexao.responseCode}: $texto")
      }
      return if (texto.isBlank()) JSONObject() else JSONObject(texto)
    }

    return try {
      tentar()
    } catch (e: ExpiradoException) {
      if (tokenRefresh == null || !renovar()) throw e
      tentar()
    }
  }

  private fun lerTexto(conexao: HttpURLConnection): String {
    val fluxo = if (conexao.responseCode in 200..299) conexao.inputStream else conexao.errorStream
    return fluxo.bufferedReader().use { it.readText() }
  }
}

class ExpiradoException(mensagem: String) : IllegalStateException(mensagem)
