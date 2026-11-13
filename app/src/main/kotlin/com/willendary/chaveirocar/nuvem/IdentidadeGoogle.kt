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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CredencialGoogle(val token: String, val email: String?)

/** `pendente` é a tela de consentimento que o usuário ainda precisa confirmar. */
data class AutorizacaoGoogle(val credencial: CredencialGoogle?, val pendente: PendingIntent?)

/**
 * Autenticação de identidade Google (apenas escopos `email`, `profile` e `openid`),
 * usada para entrar ou criar conta na API ChaveiroCar.
 *
 * Os métodos públicos bloqueiam a thread — chamar sempre em Dispatchers.IO.
 */
object IdentidadeGoogle {

  private const val ESCOPO_EMAIL = "email"
  private const val ESCOPO_PERFIL = "profile"
  private const val ESCOPO_ABERTO = "openid"
  private const val USERINFO = "https://www.googleapis.com/oauth2/v3/userinfo"

  fun autenticarConta(contexto: Context): AutorizacaoGoogle {
    val pedido = AuthorizationRequest.builder()
      .setRequestedScopes(listOf(Scope(ESCOPO_EMAIL), Scope(ESCOPO_PERFIL), Scope(ESCOPO_ABERTO)))
      .build()
    return converter(Tasks.await(Identity.getAuthorizationClient(contexto).authorize(pedido)))
  }

  fun concluirAutorizacao(contexto: Context, dados: Intent): AutorizacaoGoogle =
    converter(Identity.getAuthorizationClient(contexto).getAuthorizationResultFromIntent(dados))

  fun esquecerToken(contexto: Context, token: String) {
    if (token.isBlank()) return
    val pedido = ClearTokenRequest.builder().setToken(token).build()
    runCatching { Tasks.await(Identity.getAuthorizationClient(contexto).clearToken(pedido)) }
  }

  private fun converter(resultado: AuthorizationResult): AutorizacaoGoogle {
    val token = resultado.accessToken
    return AutorizacaoGoogle(
      credencial = token?.let { CredencialGoogle(it, emailDo(token)) },
      pendente = resultado.pendingIntent,
    )
  }

  private fun emailDo(token: String): String? {
    val conexao = (URL(USERINFO).openConnection() as HttpURLConnection).apply {
      requestMethod = "GET"
      connectTimeout = 15_000
      readTimeout = 30_000
      instanceFollowRedirects = true
      setRequestProperty("Authorization", "Bearer $token")
      setRequestProperty("Accept", "application/json")
      setRequestProperty("User-Agent", "ChaveiroCar")
    }
    return try {
      val codigo = conexao.responseCode
      if (codigo !in 200..299) return null
      val texto = conexao.inputStream?.bufferedReader()?.use { it.readText() }.orEmpty()
      JSONObject(texto).optString("email").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
      null
    } finally {
      conexao.disconnect()
    }
  }
}
