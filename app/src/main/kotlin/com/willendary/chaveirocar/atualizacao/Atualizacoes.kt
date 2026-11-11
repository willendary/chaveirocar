package com.willendary.chaveirocar.atualizacao

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AtualizacaoDisponivel(
  val versao: String,
  val enderecoApk: String,
  val notas: String,
)

/**
 * Usa as releases do GitHub como canal de atualização.
 * O repositório é público, então não existe token nem segredo dentro do app.
 */
object Atualizacoes {

  private const val REPOSITORIO = "willendary/chaveirocar"
  private const val ENDERECO_API = "https://api.github.com/repos/$REPOSITORIO/releases/latest"

  fun versaoInstalada(contexto: Context): String = runCatching {
    contexto.packageManager.getPackageInfo(contexto.packageName, 0).versionName
  }.getOrNull() ?: "0"

  suspend fun verificar(versaoInstalada: String): AtualizacaoDisponivel? = withContext(Dispatchers.IO) {
    val conexao = (URL(ENDERECO_API).openConnection() as HttpURLConnection).apply {
      connectTimeout = 10_000
      readTimeout = 10_000
      setRequestProperty("Accept", "application/vnd.github+json")
      setRequestProperty("User-Agent", "ChaveiroCar")
    }
    try {
      if (conexao.responseCode != 200) return@withContext null
      val corpo = conexao.inputStream.bufferedReader().use { it.readText() }
      val release = JSONObject(corpo)
      val versao = release.optString("tag_name").removePrefix("v")
      if (!maisNova(versao, versaoInstalada)) return@withContext null

      var enderecoApk: String? = null
      val ativos = release.optJSONArray("assets")
      if (ativos != null) {
        for (indice in 0 until ativos.length()) {
          val ativo = ativos.optJSONObject(indice) ?: continue
          if (ativo.optString("name").endsWith(".apk")) {
            enderecoApk = ativo.optString("browser_download_url")
            break
          }
        }
      }
      if (enderecoApk.isNullOrBlank()) return@withContext null
      AtualizacaoDisponivel(versao, enderecoApk, release.optString("body"))
    } finally {
      conexao.disconnect()
    }
  }

  suspend fun baixar(contexto: Context, atualizacao: AtualizacaoDisponivel): File = withContext(Dispatchers.IO) {
    val pasta = File(contexto.cacheDir, "atualizacao").apply { mkdirs() }
    pasta.listFiles()?.forEach { it.delete() }
    val destino = File(pasta, "chaveirocar-${atualizacao.versao}.apk")
    val conexao = (URL(atualizacao.enderecoApk).openConnection() as HttpURLConnection).apply {
      connectTimeout = 15_000
      readTimeout = 120_000
      instanceFollowRedirects = true
      setRequestProperty("User-Agent", "ChaveiroCar")
    }
    try {
      if (conexao.responseCode !in 200..299) error("download falhou (${conexao.responseCode})")
      conexao.inputStream.use { entrada -> destino.outputStream().use { saida -> entrada.copyTo(saida) } }
    } finally {
      conexao.disconnect()
    }
    if (destino.length() == 0L) error("download vazio")
    destino
  }

  fun abrirInstalador(contexto: Context, arquivo: File) {
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo)
    val intencao = Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(uri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    contexto.startActivity(intencao)
  }

  internal fun maisNova(candidata: String, instalada: String): Boolean {
    val nova = partes(candidata)
    val atual = partes(instalada)
    for (indice in 0 until maxOf(nova.size, atual.size)) {
      val comparada = nova.getOrElse(indice) { 0 }
      val referencia = atual.getOrElse(indice) { 0 }
      if (comparada != referencia) return comparada > referencia
    }
    return false
  }

  private fun partes(versao: String): List<Int> =
    versao.split('.').mapNotNull { trecho -> trecho.takeWhile { it.isDigit() }.toIntOrNull() }
}
