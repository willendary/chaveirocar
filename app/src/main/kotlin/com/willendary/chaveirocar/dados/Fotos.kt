package com.willendary.chaveirocar.dados

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.willendary.chaveirocar.modelo.SetaAnotacao
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Fotos ficam como arquivos JPEG na pasta privada do app.
 * O banco guarda só o nome do arquivo.
 */
object Fotos {

  private const val LADO_MAXIMO = 1600
  private const val QUALIDADE = 85

  fun pasta(contexto: Context): File = File(contexto.filesDir, "fotos").apply { mkdirs() }

  fun arquivo(contexto: Context, nome: String): File = File(pasta(contexto), nome)

  fun arquivoOriginal(contexto: Context, nome: String): File = File(pasta(contexto), "orig_$nome")

  fun salvarDeFluxo(contexto: Context, entrada: InputStream): String {
    val bytes = entrada.readBytes()
    val bitmap = reduzir(bytes, LADO_MAXIMO) ?: error("Não foi possível abrir a imagem")
    val nome = "${UUID.randomUUID()}.jpg"
    FileOutputStream(arquivo(contexto, nome)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, QUALIDADE, it) }
    bitmap.recycle()
    return nome
  }

  fun salvarDeArquivo(contexto: Context, origem: File): String =
    origem.inputStream().use { salvarDeFluxo(contexto, it) }

  fun salvarDeBytes(contexto: Context, bytes: ByteArray): String =
    bytes.inputStream().use { salvarDeFluxo(contexto, it) }

  fun excluir(contexto: Context, nome: String) {
    arquivo(contexto, nome).delete()
    arquivoOriginal(contexto, nome).delete()
  }

  fun carregar(contexto: Context, nome: String, ladoMaximo: Int = LADO_MAXIMO): Bitmap? {
    val arq = arquivo(contexto, nome)
    if (!arq.exists()) return null
    return reduzir(arq.readBytes(), ladoMaximo)
  }

  fun carregarBaseParaEdicao(contexto: Context, nome: String, ladoMaximo: Int = LADO_MAXIMO): Bitmap? {
    val orig = arquivoOriginal(contexto, nome)
    val fonte = if (orig.exists()) orig else arquivo(contexto, nome)
    if (!fonte.exists()) return null
    return reduzir(fonte.readBytes(), ladoMaximo)
  }

  /**
   * Preserva o arquivo original sem anotações em `orig_<nome>` e grava a imagem composta em `<nome>`.
   * Se `setas` estiver vazio, restaura a imagem original intacta.
   */
  fun aplicarAnotacoes(contexto: Context, nome: String, setas: List<SetaAnotacao>) {
    val destino = arquivo(contexto, nome)
    if (!destino.exists()) return
    val orig = arquivoOriginal(contexto, nome)
    if (!orig.exists()) {
      destino.copyTo(orig, overwrite = true)
    }
    if (setas.isEmpty()) {
      orig.copyTo(destino, overwrite = true)
      return
    }
    val base = reduzir(orig.readBytes(), LADO_MAXIMO) ?: return
    val mutavel = base.copy(Bitmap.Config.ARGB_8888, true)
    if (mutavel !== base) base.recycle()
    val canvas = Canvas(mutavel)
    val largura = mutavel.width.toFloat()
    val altura = mutavel.height.toFloat()
    val escala = max(largura, altura) / 1000f

    for (seta in setas) {
      val x1 = seta.x1.coerceIn(0f, 1f) * largura
      val y1 = seta.y1.coerceIn(0f, 1f) * altura
      val x2 = seta.x2.coerceIn(0f, 1f) * largura
      val y2 = seta.y2.coerceIn(0f, 1f) * altura
      if (hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()) < 4.0) continue

      val cor = runCatching { Color.parseColor(seta.corHex) }.getOrDefault(Color.RED)
      val traco = (seta.espessura.coerceIn(2f, 20f) * escala).coerceAtLeast(3f)
      val cabeca = traco * 3.8f
      val angulo = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()
      val abertura = Math.toRadians(28.0).toFloat()

      val pEsqX = x2 - cabeca * cos(angulo - abertura)
      val pEsqY = y2 - cabeca * sin(angulo - abertura)
      val pDirX = x2 - cabeca * cos(angulo + abertura)
      val pDirY = y2 - cabeca * sin(angulo + abertura)
      val fimLinhaX = x2 - (cabeca * 0.55f) * cos(angulo)
      val fimLinhaY = y2 - (cabeca * 0.55f) * sin(angulo)

      val caminhoCabeca = Path().apply {
        moveTo(x2, y2)
        lineTo(pEsqX, pEsqY)
        lineTo(pDirX, pDirY)
        close()
      }

      val sombraLinha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = traco + 4f * escala
        strokeCap = Paint.Cap.ROUND
      }
      val sombraCabeca = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 3f * escala
        strokeJoin = Paint.Join.ROUND
      }
      canvas.drawLine(x1, y1, fimLinhaX, fimLinhaY, sombraLinha)
      canvas.drawPath(caminhoCabeca, sombraCabeca)

      val tintaLinha = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cor
        style = Paint.Style.STROKE
        strokeWidth = traco
        strokeCap = Paint.Cap.ROUND
      }
      val tintaCabeca = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cor
        style = Paint.Style.FILL
      }
      canvas.drawLine(x1, y1, fimLinhaX, fimLinhaY, tintaLinha)
      canvas.drawPath(caminhoCabeca, tintaCabeca)
    }

    FileOutputStream(destino).use { mutavel.compress(Bitmap.CompressFormat.JPEG, QUALIDADE, it) }
    mutavel.recycle()
  }

  fun lerAnotacoes(json: String?): List<SetaAnotacao> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
      val arr = JSONArray(json)
      buildList {
        for (i in 0 until arr.length()) {
          val obj = arr.optJSONObject(i) ?: continue
          add(
            SetaAnotacao(
              x1 = obj.optDouble("x1", 0.0).toFloat(),
              y1 = obj.optDouble("y1", 0.0).toFloat(),
              x2 = obj.optDouble("x2", 0.0).toFloat(),
              y2 = obj.optDouble("y2", 0.0).toFloat(),
              corHex = obj.optString("cor", "#EF4444").ifBlank { "#EF4444" },
              espessura = obj.optDouble("espessura", 6.0).toFloat(),
            ),
          )
        }
      }
    }.getOrDefault(emptyList())
  }

  fun serializarAnotacoes(setas: List<SetaAnotacao>): String {
    val arr = JSONArray()
    for (seta in setas) {
      arr.put(
        JSONObject()
          .put("x1", seta.x1.toDouble())
          .put("y1", seta.y1.toDouble())
          .put("x2", seta.x2.toDouble())
          .put("y2", seta.y2.toDouble())
          .put("cor", seta.corHex)
          .put("espessura", seta.espessura.toDouble()),
      )
    }
    return arr.toString()
  }

  private fun reduzir(bytes: ByteArray, ladoMaximo: Int): Bitmap? {
    if (bytes.isEmpty()) return null
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, medidas)
    if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
    var amostra = 1
    while (max(medidas.outWidth, medidas.outHeight) / (amostra * 2) >= ladoMaximo) amostra *= 2
    val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opcoes)
  }
}
