package com.willendary.chaveirocar.dados

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Preferências simples do app (não são dados de serviço).
 */
object Preferencias {
  private const val ARQUIVO = "chaveirocar"

  const val ULTIMO_BACKUP = "ultimo_backup"
  const val ULTIMO_BACKUP_AUTO = "ultimo_backup_auto"
  const val ULTIMA_CHECAGEM = "ultima_checagem"
  const val VERSAO_VISTA = "versao_vista"
  const val PIN_HASH = "pin_hash"
  const val PIN_SAL = "pin_sal"
  const val PIN_ATIVO = "pin_ativo"
  const val BIOMETRIA_ATIVA = "biometria_ativa"
  const val CONTA_NUVEM = "conta_nuvem"
  const val DRIVE_IGNORADO = "drive_ignorado"
  const val ULTIMO_ENVIO_NUVEM = "ultimo_envio_nuvem"
  const val MODELOS_FAVORITOS = "modelos_favoritos"
  const val API_BASE_URL = "api_base_url"
  const val API_EMPRESA_ID = "api_empresa_id"
  const val API_CURSOR = "api_cursor"
  const val API_EMAIL = "api_email"

  // ponytail: tokens da API em EncryptedSharedPreferences (persistência simples). Legado sem criptografia é migrado e apagado na leitura.
  const val ARQUIVO_API = "chaveirocar_api"
  const val API_ACESSO = "api_acesso"
  const val API_REFRESH = "api_refresh"

  fun obterCursorApi(preferencias: SharedPreferences, empresaId: String): Long =
    preferencias.getLong("${API_CURSOR}_$empresaId", 0)

  fun guardarCursorApi(preferencias: SharedPreferences, empresaId: String, cursor: Long) {
    preferencias.edit().putLong("${API_CURSOR}_$empresaId", cursor).apply()
  }

  fun obter(contexto: Context): SharedPreferences =
    contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)

  fun obterApi(contexto: Context): SharedPreferences {
    val mestre = MasterKey.Builder(contexto)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
    val criptografado = EncryptedSharedPreferences.create(
      contexto,
      ARQUIVO_API,
      mestre,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    // Migra tokens legados (SharedPreferences simples) para criptografia.
    val legado = contexto.getSharedPreferences(ARQUIVO_API, Context.MODE_PRIVATE)
    val acessoLegado = legado.getString(API_ACESSO, null)
    val refreshLegado = legado.getString(API_REFRESH, null)
    if (acessoLegado != null || refreshLegado != null) {
      criptografado.edit()
        .putString(API_ACESSO, acessoLegado)
        .putString(API_REFRESH, refreshLegado)
        .apply()
      legado.edit().remove(API_ACESSO).remove(API_REFRESH).apply()
    }
    return criptografado
  }
}
