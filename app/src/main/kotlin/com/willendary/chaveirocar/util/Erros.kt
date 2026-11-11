package com.willendary.chaveirocar.util

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Guarda o último erro grave em arquivo para o usuário copiar e enviar.
 * Sem isso, um fechamento no aparelho dele não deixa rastro nenhum.
 */
object Erros {

  private const val LIMITE = 64_000
  private var instalado = false

  private fun pasta(contexto: Context) = File(contexto.filesDir, "erros").apply { mkdirs() }

  fun arquivo(contexto: Context): File = File(pasta(contexto), "ultimo_erro.txt")

  @Synchronized
  fun instalar(contexto: Context) {
    if (instalado) return
    instalado = true
    val anterior = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
      runCatching { registrar(contexto, "Fechamento inesperado", erro) }
      anterior?.uncaughtException(thread, erro)
    }
  }

  fun registrar(contexto: Context, origem: String, erro: Throwable) {
    runCatching {
      val versao = runCatching {
        contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName
      }.getOrNull().orEmpty()
      val quando = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
      val texto = buildString {
        appendLine("ChaveiroCar $versao")
        appendLine(quando)
        appendLine("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Origem: $origem")
        appendLine()
        append(erro.stackTraceToString())
      }
      arquivo(contexto).writeText(texto.take(LIMITE))
    }
  }

  fun ultimo(contexto: Context): String? =
    arquivo(contexto).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

  fun limpar(contexto: Context) {
    arquivo(contexto).delete()
  }
}
