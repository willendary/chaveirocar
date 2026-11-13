package com.willendary.chaveirocar.nuvem

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.willendary.chaveirocar.dados.Preferencias
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class CredencialGoogle(val token: String, val email: String?)

/** `pendente` é a tela de consentimento que o usuário ainda precisa confirmar. */
data class AutorizacaoGoogle(val credencial: CredencialGoogle?, val pendente: PendingIntent?)

data class BackupNaNuvem(
  val id: String,
  val nome: String,
  val criadoEm: Long,
  val tamanho: Long,
)

/**
 * Backup na pasta oculta do app no Google Drive (`appDataFolder`).
 * Não usa servidor próprio: fala direto com a API REST do Drive.
 *
 * Os métodos públicos bloqueiam a thread — chamar sempre em Dispatchers.IO.
 */
object GoogleDrive {

  private const val PASTA = "appDataFolder"
  private const val ESCOPO = "https://www.googleapis.com/auth/drive.appdata"
  private const val ESCOPO_EMAIL = "email"
  private const val ESCOPO_PERFIL = "profile"
  private const val ESCOPO_ABERTO = "openid"
  private const val USERINFO = "https://www.googleapis.com/oauth2/v3/userinfo"
  private const val BASE = "https://www.googleapis.com/drive/v3"
  private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"

  fun autorizar(contexto: Context): AutorizacaoGoogle {
    val pedido = AuthorizationRequest.builder()
      .setRequestedScopes(listOf(Scope(ESCOPO), Scope(ESCOPO_EMAIL), Scope(ESCOPO_PERFIL), Scope(ESCOPO_ABERTO)))
      .build()
    return converter(autorizacaoDe(contexto, pedido))
  }

  /**
   * Autorização mínima de identidade Google (e-mail + perfil), sem escopo de Drive,
   * usada para login/cadastro rápido na conta da API ChaveiroCar.
   */
  fun autenticarConta(contexto: Context): AutorizacaoGoogle {
    val pedido = AuthorizationRequest.builder()
      .setRequestedScopes(listOf(Scope(ESCOPO_EMAIL), Scope(ESCOPO_PERFIL), Scope(ESCOPO_ABERTO)))
      .build()
    return converter(autorizacaoDe(contexto, pedido))
  }

  fun concluirAutorizacao(contexto: Context, dados: Intent): AutorizacaoGoogle =
    converter(Identity.getAuthorizationClient(contexto).getAuthorizationResultFromIntent(dados))

  /**
   * Apaga o token guardado em cache. A permissão continua valendo na conta;
   * quando o usuário quer sair, ele revoga em
   * https://myaccount.google.com/permissions
   */
  fun esquecerToken(contexto: Context, token: String) {
    if (token.isBlank()) return
    val pedido = ClearTokenRequest.builder().setToken(token).build()
    runCatching { Tasks.await(Identity.getAuthorizationClient(contexto).clearToken(pedido)) }
  }

  fun enviar(token: String, arquivo: File, nome: String): BackupNaNuvem {
    val fronteira = "chaveirocar_${UUID.randomUUID()}"
    val metadados = JSONObject()
      .put("name", nome)
      .put("parents", JSONArray().put(PASTA))
      .toString()
      .toByteArray(Charsets.UTF_8)
    val abertura = "--$fronteira\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray()
    val separador = "\r\n--$fronteira\r\nContent-Type: application/zip\r\n\r\n".toByteArray()
    val fechamento = "\r\n--$fronteira--\r\n".toByteArray()
    val total = abertura.size.toLong() + metadados.size + separador.size + arquivo.length() + fechamento.size

    val conexao = conexao("$UPLOAD/files?uploadType=multipart&fields=id,name,createdTime,size", token, "POST")
    conexao.setRequestProperty("Content-Type", "multipart/related; boundary=$fronteira")
    conexao.setFixedLengthStreamingMode(total)
    conexao.doOutput = true
    try {
      conexao.outputStream.use { saida ->
        saida.write(abertura)
        saida.write(metadados)
        saida.write(separador)
        arquivo.inputStream().use { it.copyTo(saida) }
        saida.write(fechamento)
      }
      val json = JSONObject(lerResposta(conexao))
      return BackupNaNuvem(
        id = json.optString("id"),
        nome = json.optString("name", nome),
        criadoEm = dataDrive(json.optString("createdTime")),
        tamanho = json.optLong("size", arquivo.length()),
      )
    } finally {
      conexao.disconnect()
    }
  }

  fun listar(token: String): List<BackupNaNuvem> {
    val endereco = "$BASE/files?spaces=$PASTA&pageSize=20&orderBy=createdTime%20desc" +
      "&fields=files(id,name,createdTime,size)"
    val conexao = conexao(endereco, token, "GET")
    try {
      val arquivos = JSONObject(lerResposta(conexao)).optJSONArray("files") ?: return emptyList()
      return (0 until arquivos.length()).mapNotNull { indice ->
        val item = arquivos.optJSONObject(indice) ?: return@mapNotNull null
        BackupNaNuvem(
          id = item.optString("id"),
          nome = item.optString("name"),
          criadoEm = dataDrive(item.optString("createdTime")),
          tamanho = item.optLong("size"),
        )
      }.filter { it.id.isNotBlank() }
    } finally {
      conexao.disconnect()
    }
  }

  fun baixar(token: String, id: String, destino: File) {
    val conexao = conexao("$BASE/files/$id?alt=media", token, "GET")
    try {
      if (conexao.responseCode !in 200..299) {
        error("Google Drive HTTP ${conexao.responseCode}: ${conexao.errorStream?.bufferedReader()?.use { it.readText() }?.take(300).orEmpty()}")
      }
      conexao.inputStream.use { entrada -> destino.outputStream().use { entrada.copyTo(it) } }
    } finally {
      conexao.disconnect()
    }
  }

  fun excluir(token: String, id: String) {
    val conexao = conexao("$BASE/files/$id", token, "DELETE")
    try {
      val codigo = conexao.responseCode
      if (codigo !in 200..299 && codigo != 404) {
        error("Google Drive HTTP $codigo ao excluir backup")
      }
    } finally {
      conexao.disconnect()
    }
  }

  fun limparAntigos(token: String, manter: Int) {
    listar(token).drop(manter.coerceAtLeast(1)).forEach { backup ->
      runCatching { excluir(token, backup.id) }
    }
  }

  /**
   * Envia sem interface quando a conta já foi conectada. Usado pela cópia
   * automática semanal; se precisar de consentimento, simplesmente não envia.
   */
  fun enviarAutomatico(contexto: Context, arquivo: File): Boolean {
    val preferencias = Preferencias.obter(contexto)
    preferencias.getString(Preferencias.CONTA_NUVEM, null) ?: return false
    val token = autorizar(contexto).credencial?.token ?: return false
    val nome = "chaveirocar-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date()) + ".zip"
    enviar(token, arquivo, nome)
    limparAntigos(token, 4)
    preferencias.edit().putLong(Preferencias.ULTIMO_ENVIO_NUVEM, System.currentTimeMillis()).apply()
    return true
  }

  private fun autorizacaoDe(contexto: Context, pedido: AuthorizationRequest): AuthorizationResult =
    Tasks.await(Identity.getAuthorizationClient(contexto).authorize(pedido))

  private fun converter(resultado: AuthorizationResult): AutorizacaoGoogle {
    val token = resultado.accessToken
    return AutorizacaoGoogle(
      credencial = token?.let { CredencialGoogle(it, emailDo(token)) },
      pendente = resultado.pendingIntent,
    )
  }

  /** O retorno da autorização não traz a conta; o e-mail sai do userinfo. */
  private fun emailDo(token: String): String? {
    val conexao = conexao(USERINFO, token, "GET")
    return try {
      JSONObject(lerResposta(conexao)).optString("email").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
      null
    } finally {
      conexao.disconnect()
    }
  }

  private fun conexao(endereco: String, token: String, metodo: String): HttpURLConnection =
    (URL(endereco).openConnection() as HttpURLConnection).apply {
      requestMethod = metodo
      connectTimeout = 15_000
      readTimeout = 180_000
      instanceFollowRedirects = true
      setRequestProperty("Authorization", "Bearer $token")
      setRequestProperty("Accept", "application/json")
      setRequestProperty("User-Agent", "ChaveiroCar")
    }

  private fun lerResposta(conexao: HttpURLConnection): String {
    val codigo = conexao.responseCode
    val fluxo = if (codigo in 200..299) conexao.inputStream else conexao.errorStream
    val texto = fluxo?.bufferedReader()?.use { it.readText() }.orEmpty()
    if (codigo !in 200..299) error("Google Drive HTTP $codigo: ${texto.take(300)}")
    return texto
  }

  private fun dataDrive(texto: String): Long {
    if (texto.isBlank()) return 0L
    return runCatching {
      SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .parse(texto)?.time
    }.getOrNull() ?: 0L
  }
}
