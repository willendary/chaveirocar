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
class ApiChaveiroCar(baseUrl: String = URL_PADRAO) {

  private val baseUrl = baseUrl.trim().trimEnd('/')

  companion object {
    const val URL_PADRAO = "https://chaveirocar-api.onrender.com"
  }

  @Volatile var tokenAcesso: String? = null
  @Volatile var tokenRefresh: String? = null
  var aoRenovarToken: ((acesso: String, refresh: String) -> Unit)? = null

  fun entrar(email: String, senha: String): JSONObject {
    val resposta = post("/api/v1/auth/login", JSONObject().apply {
      put("email", email); put("senha", senha)
    })
    guardarTokens(resposta)
    return resposta
  }

  fun cadastrar(nome: String, email: String, senha: String, empresa: String): JSONObject {
    val resposta = post("/api/v1/auth/cadastro", JSONObject().apply {
      put("nome", nome); put("email", email); put("senha", senha); put("empresa", empresa)
    })
    guardarTokens(resposta)
    return resposta
  }

  fun entrarComGoogle(accessToken: String = "", idToken: String = "", empresa: String = ""): JSONObject {
    val corpo = JSONObject().apply {
      if (accessToken.isNotBlank()) put("accessToken", accessToken)
      if (idToken.isNotBlank()) put("idToken", idToken)
      if (empresa.isNotBlank()) put("empresa", empresa)
    }
    val resposta = post("/api/v1/auth/google-login", corpo)
    guardarTokens(resposta)
    return resposta
  }

  fun solicitarRedefinicao(email: String): JSONObject =
    post("/api/v1/auth/esqueci-senha", JSONObject().put("email", email))

  fun redefinirSenha(token: String, novaSenha: String) =
    post("/api/v1/auth/redefinir-senha", JSONObject().put("token", token).put("senha", novaSenha))

  fun reenviarVerificacao(): JSONObject =
    post("/api/v1/auth/reenviar-verificacao", JSONObject())

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
    val tokens = resposta.optJSONObject("tokens") ?: resposta
    val novoAcesso = tokens.optString("acesso").ifBlank { null }
    val novoRefresh = tokens.optString("refresh").ifBlank { null }
    if (novoAcesso != null) tokenAcesso = novoAcesso
    if (novoRefresh != null) tokenRefresh = novoRefresh
    if (novoAcesso != null && novoRefresh != null) {
      aoRenovarToken?.invoke(novoAcesso, novoRefresh)
    }
  }

  fun eu(): JSONObject = get("/api/v1/eu")

  fun listarEmpresas(): org.json.JSONArray =
    get("/api/v1/empresas").getJSONArray("itens")

  fun previsualizarConvite(token: String): JSONObject =
    get("/api/v1/convites/${java.net.URLEncoder.encode(token, "UTF-8")}")

  fun aceitarConvite(token: String, nome: String = "", senha: String = ""): JSONObject {
    val corpo = JSONObject()
    if (nome.isNotBlank()) corpo.put("nome", nome)
    if (senha.isNotBlank()) corpo.put("senha", senha)
    val resposta = post("/api/v1/convites/${java.net.URLEncoder.encode(token, "UTF-8")}/aceitar", corpo)
    guardarTokens(resposta)
    return resposta
  }

  fun listarMembros(empresaId: String): org.json.JSONArray =
    get("/api/v1/empresas/$empresaId/membros").getJSONArray("itens")

  fun listarConvites(empresaId: String): org.json.JSONArray =
    get("/api/v1/empresas/$empresaId/convites").getJSONArray("itens")

  fun convidar(empresaId: String, email: String, papel: String): JSONObject =
    post("/api/v1/empresas/$empresaId/convites", JSONObject().put("email", email).put("papel", papel))

  fun cancelarConvite(empresaId: String, conviteId: String): JSONObject =
    request("DELETE", "/api/v1/empresas/$empresaId/convites/$conviteId", null)

  fun alterarPapel(empresaId: String, usuarioId: String, papel: String): JSONObject =
    request("PATCH", "/api/v1/empresas/$empresaId/membros/$usuarioId", JSONObject().put("papel", papel))

  fun removerMembro(empresaId: String, usuarioId: String): JSONObject =
    request("DELETE", "/api/v1/empresas/$empresaId/membros/$usuarioId", null)

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
    anotacoes: String = "[]",
  ): JSONObject = enviarFotoTentativa(
    empresaId, atendimentoUuid, fotoUuid, categoria, legenda, ordem, bytes, anotacoes, tentouRenovar = false,
  )

  private fun enviarFotoTentativa(
    empresaId: String,
    atendimentoUuid: String,
    fotoUuid: String,
    categoria: String,
    legenda: String,
    ordem: Long,
    bytes: ByteArray,
    anotacoes: String,
    tentouRenovar: Boolean,
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
    parte("anotacoes", anotacoes.ifBlank { "[]" })
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
    if (conexao.responseCode == 401 && !tentouRenovar && renovar()) {
      return enviarFotoTentativa(
        empresaId, atendimentoUuid, fotoUuid, categoria, legenda, ordem, bytes, anotacoes, tentouRenovar = true,
      )
    }
    if (conexao.responseCode !in 200..299) error("API ${conexao.responseCode}: $texto")
    return JSONObject(texto)
  }

  /** Baixa bytes de foto remota (`url` relativo da API). */
  fun baixarFoto(urlRelativa: String): ByteArray = baixarFotoTentativa(urlRelativa, tentouRenovar = false)

  private fun baixarFotoTentativa(urlRelativa: String, tentouRenovar: Boolean): ByteArray {
    val conexao = (URL(baseUrl + urlRelativa).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      tokenAcesso?.let { setRequestProperty("Authorization", "Bearer $it") }
    }
    if (conexao.responseCode == 401 && !tentouRenovar && renovar()) {
      return baixarFotoTentativa(urlRelativa, tentouRenovar = true)
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

  /** Envia backup ZIP do Android para importação idempotente na API. */
  fun importarBackupAndroid(empresaId: String, bytes: ByteArray): JSONObject =
    importarBackupTentativa(empresaId, bytes, tentouRenovar = false)

  private fun importarBackupTentativa(
    empresaId: String,
    bytes: ByteArray,
    tentouRenovar: Boolean,
  ): JSONObject {
    val fronteira = "----chaveirocarBackup${java.util.UUID.randomUUID()}"
    val saida = java.io.ByteArrayOutputStream()
    saida.write(
      ("--$fronteira\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"backup.zip\"\r\n" +
        "Content-Type: application/zip\r\n\r\n").toByteArray(Charsets.UTF_8),
    )
    saida.write(bytes)
    saida.write("\r\n--$fronteira--\r\n".toByteArray(Charsets.UTF_8))
    val corpo = saida.toByteArray()
    val conexao = (URL(baseUrl + "/api/v1/empresas/$empresaId/importacoes/backup")
      .openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      connectTimeout = 30_000
      readTimeout = 180_000
      setRequestProperty("Content-Type", "multipart/form-data; boundary=$fronteira")
      tokenAcesso?.let { setRequestProperty("Authorization", "Bearer $it") }
      doOutput = true
      setFixedLengthStreamingMode(corpo.size)
    }
    conexao.outputStream.use { it.write(corpo) }
    val fluxo = if (conexao.responseCode in 200..299) conexao.inputStream else conexao.errorStream
    val texto = fluxo?.bufferedReader()?.use { it.readText() }.orEmpty()
    if (conexao.responseCode == 401 && !tentouRenovar && renovar()) {
      return importarBackupTentativa(empresaId, bytes, tentouRenovar = true)
    }
    if (conexao.responseCode !in 200..299) error("API ${conexao.responseCode}: $texto")
    return JSONObject(texto)
  }

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
        if (
          conexao.responseCode == 401 &&
          caminho != "/api/v1/auth/renovar" &&
          caminho != "/api/v1/auth/login" &&
          caminho != "/api/v1/auth/google-login"
        ) {
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
