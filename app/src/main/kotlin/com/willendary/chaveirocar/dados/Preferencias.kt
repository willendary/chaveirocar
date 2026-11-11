package com.willendary.chaveirocar.dados

import android.content.Context
import android.content.SharedPreferences

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

  fun obter(contexto: Context): SharedPreferences =
    contexto.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
}
