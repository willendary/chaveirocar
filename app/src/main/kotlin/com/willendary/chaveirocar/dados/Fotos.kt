package com.willendary.chaveirocar.dados

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

/**
 * Fotos ficam como arquivos JPEG na pasta privada do app.
 * O banco guarda só o nome do arquivo.
 */
object Fotos {

  private const val LADO_MAXIMO = 1600
  private const val QUALIDADE = 85

  fun pasta(contexto: Context): File = File(contexto.filesDir, "fotos").apply { mkdirs() }

  fun arquivo(contexto: Context, nome: String): File = File(pasta(contexto), nome)

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
  }

  fun carregar(contexto: Context, nome: String, ladoMaximo: Int = LADO_MAXIMO): Bitmap? =
    reduzir(arquivo(contexto, nome).readBytes(), ladoMaximo)

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
