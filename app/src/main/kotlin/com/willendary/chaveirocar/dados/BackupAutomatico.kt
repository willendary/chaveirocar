package com.willendary.chaveirocar.dados

import android.content.Context
import com.willendary.chaveirocar.util.diasDesde
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cópia de segurança automática: um ZIP por semana, mantendo as 4 últimas.
 * Roda quando o app abre — sem serviço em segundo plano, sem acordar o celular.
 */
object BackupAutomatico {

  private const val DIAS = 7
  private const val MANTER = 4
  private const val PREFERENCIA = Preferencias.ULTIMO_BACKUP_AUTO

  fun pasta(contexto: Context): File = File(contexto.filesDir, "backups").apply { mkdirs() }

  fun ultimoArquivo(contexto: Context): File? =
    pasta(contexto).listFiles()?.maxByOrNull { it.lastModified() }

  fun executarSeNecessario(banco: BancoDeAtendimentos, contexto: Context): File? {
    val preferencias = Preferencias.obter(contexto)
    if (diasDesde(preferencias.getLong(PREFERENCIA, 0)) < DIAS) return null
    if (banco.contarTodos() == 0) return null

    val nome = "auto-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date()) + ".zip"
    val destino = File(pasta(contexto), nome)
    destino.outputStream().use { Backup.exportar(banco, contexto, it) }

    pasta(contexto).listFiles()
      ?.sortedByDescending { it.lastModified() }
      ?.drop(MANTER)
      ?.forEach { it.delete() }

    preferencias.edit().putLong(PREFERENCIA, System.currentTimeMillis()).apply()
    return destino
  }
}
