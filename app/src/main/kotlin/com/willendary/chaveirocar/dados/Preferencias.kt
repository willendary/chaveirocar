package com.willendary.chaveirocar.dados

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import org.json.JSONArray

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
  const val MODELOS_FAVORITOS = "modelos_favoritos"
  const val API_BASE_URL = "api_base_url"
  const val API_EMPRESA_ID = "api_empresa_id"
  const val API_CURSOR = "api_cursor"
  const val API_EMAIL = "api_email"
  private const val MODULOS_EMPRESA = "modulos_empresa"
  private const val PLANO_EMPRESA = "plano_empresa"
  private const val ASSINATURA_STATUS = "assinatura_status"
  private const val ASSINATURA_RENOVACAO = "assinatura_renovacao"

  // ponytail: tokens da API em EncryptedSharedPreferences (persistência simples). Legado sem criptografia é migrado e apagado na leitura.
  const val ARQUIVO_API = "chaveirocar_api"
  const val API_ACESSO = "api_acesso"
  const val API_REFRESH = "api_refresh"

  fun obterCursorApi(preferencias: SharedPreferences, empresaId: String): Long =
    preferencias.getLong("${API_CURSOR}_$empresaId", 0)

  fun guardarCursorApi(preferencias: SharedPreferences, empresaId: String, cursor: Long) {
    preferencias.edit().putLong("${API_CURSOR}_$empresaId", cursor).apply()
  }

  /**
   * Direitos comerciais por módulo salvos para consulta offline.
   * Clientes sem registro prévio preservam os dois módulos até a sincronização da assinatura.
   */
  fun obterModulosAtivos(
    preferencias: SharedPreferences,
    empresaId: String,
  ): Set<SegmentoAtendimento> {
    if (empresaId.isBlank()) return SegmentoAtendimento.entries.toSet()
    val bruto = preferencias.getString("${MODULOS_EMPRESA}_$empresaId", null)
      ?: return SegmentoAtendimento.entries.toSet()
    return runCatching {
      val json = JSONArray(bruto)
      buildSet {
        for (i in 0 until json.length()) {
          val valor = json.optString(i)
          if (valor == SegmentoAtendimento.AUTOMOTIVO.name) add(SegmentoAtendimento.AUTOMOTIVO)
          if (valor == SegmentoAtendimento.RESIDENCIAL.name) add(SegmentoAtendimento.RESIDENCIAL)
        }
      }
    }.getOrDefault(SegmentoAtendimento.entries.toSet())
  }

  fun obterStatusAssinatura(preferencias: SharedPreferences, empresaId: String): String {
    if (empresaId.isBlank()) return "ATIVA"
    return preferencias.getString("${ASSINATURA_STATUS}_$empresaId", "ATIVA").orEmpty().ifBlank { "ATIVA" }
  }

  fun obterPlanoEmpresa(preferencias: SharedPreferences, empresaId: String): String {
    if (empresaId.isBlank()) return "COMPLETO"
    return preferencias.getString("${PLANO_EMPRESA}_$empresaId", "COMPLETO").orEmpty().ifBlank { "COMPLETO" }
  }

  fun obterProximaRenovacao(preferencias: SharedPreferences, empresaId: String): Long? {
    if (empresaId.isBlank()) return null
    val valor = preferencias.getLong("${ASSINATURA_RENOVACAO}_$empresaId", 0L)
    return valor.takeIf { it > 0L }
  }

  fun guardarAssinaturaEmpresa(
    preferencias: SharedPreferences,
    empresaId: String,
    planoCodigo: String?,
    status: String?,
    modulosAtivos: Collection<String>?,
    proximaRenovacaoEm: Long? = null,
  ) {
    if (empresaId.isBlank()) return
    val anteriores = obterModulosAtivos(preferencias, empresaId)
    val novos = (modulosAtivos ?: emptyList()).mapNotNull {
      when (it) {
        SegmentoAtendimento.AUTOMOTIVO.name -> SegmentoAtendimento.AUTOMOTIVO
        SegmentoAtendimento.RESIDENCIAL.name -> SegmentoAtendimento.RESIDENCIAL
        else -> null
      }
    }.toSet()
    val array = JSONArray()
    novos.forEach { array.put(it.name) }
    val edit = preferencias.edit()
      .putString("${MODULOS_EMPRESA}_$empresaId", array.toString())
      .putString("${PLANO_EMPRESA}_$empresaId", planoCodigo.orEmpty().ifBlank { "COMPLETO" })
      .putString("${ASSINATURA_STATUS}_$empresaId", status.orEmpty().ifBlank { "ATIVA" })
    if (proximaRenovacaoEm != null && proximaRenovacaoEm > 0L) {
      edit.putLong("${ASSINATURA_RENOVACAO}_$empresaId", proximaRenovacaoEm)
    } else {
      edit.remove("${ASSINATURA_RENOVACAO}_$empresaId")
    }
    if ((novos - anteriores).isNotEmpty()) {
      edit.putLong("${API_CURSOR}_$empresaId", 0L)
    }
    edit.apply()
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
