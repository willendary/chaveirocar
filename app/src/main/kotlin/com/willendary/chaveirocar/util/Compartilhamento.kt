package com.willendary.chaveirocar.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.Foto
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Compartilhamento {

  fun textoDoAtendimento(atendimento: Atendimento): String = buildString {
    val carro = listOf(atendimento.marca, atendimento.modelo, atendimento.ano)
      .filter { it.isNotBlank() }
      .joinToString(" ")
    appendLine("*${atendimento.placa}* ${carro}".trim())
    appendLine("${formatarData(atendimento.dataServico)} · ${atendimento.tipoServico.rotulo}")
    if (atendimento.defeito.isNotBlank()) appendLine("Defeito: ${atendimento.defeito}")
    if (atendimento.solucao.isNotBlank()) appendLine("Solução: ${atendimento.solucao}")
    if (atendimento.abertura.isNotBlank()) appendLine("Abertura: ${atendimento.abertura}")
    if (atendimento.codigoChave.isNotBlank()) appendLine("Código de chave: ${atendimento.codigoChave}")
    if (atendimento.codigoConfiguracao.isNotBlank()) appendLine("Código de configuração: ${atendimento.codigoConfiguracao}")
    if (atendimento.ferramenta.isNotBlank()) appendLine("Ferramenta: ${atendimento.ferramenta}")
    if (atendimento.clienteNome.isNotBlank()) appendLine("Cliente: ${atendimento.clienteNome}")
    if (atendimento.valorCentavos > 0) appendLine("Valor: ${formatarValor(atendimento.valorCentavos)}")
    if (!atendimento.pago) appendLine("Pagamento: Pendente")
    append("Enviado pelo ChaveiroCar")
  }

  fun compartilharTexto(contexto: Context, texto: String) {
    val intencao = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_TEXT, texto)
    }
    contexto.startActivity(Intent.createChooser(intencao, "Compartilhar registro"))
  }

  /**
   * PDF de uma página com os dados do serviço e as duas primeiras fotos.
   * ponytail: uma página só; se precisar de mais fotos, gerar várias páginas.
   */
  fun gerarPdf(contexto: Context, atendimento: Atendimento, fotos: List<Foto>): File {
    val documento = PdfDocument()
    val largura = 595
    val altura = 842
    val pagina = documento.startPage(PdfDocument.PageInfo.Builder(largura, altura, 1).create())
    val tela = pagina.canvas

    val titulo = Paint().apply {
      color = Color.BLACK
      textSize = 20f
      isAntiAlias = true
      isFakeBoldText = true
    }
    val texto = Paint().apply {
      color = Color.BLACK
      textSize = 12f
      isAntiAlias = true
    }

    var y = 56f
    tela.drawText(atendimento.placa, 40f, y, titulo)
    y += 24f
    val carro = listOf(atendimento.marca, atendimento.modelo, atendimento.ano)
      .filter { it.isNotBlank() }
      .joinToString(" ")
    tela.drawText(carro, 40f, y, texto)
    y += 18f
    tela.drawText("${formatarData(atendimento.dataServico)} · ${atendimento.tipoServico.rotulo}", 40f, y, texto)
    y += 28f

    fun paragrafo(rotulo: String, valor: String) {
      if (valor.isBlank()) return
      tela.drawText(rotulo, 40f, y, Paint(texto).apply { isFakeBoldText = true })
      y += 16f
      y = escreverQuebrado(tela, valor, texto, 40f, y, largura - 80f)
      y += 10f
    }

    paragrafo("Defeito encontrado", atendimento.defeito)
    paragrafo("Solução aplicada", atendimento.solucao)
    paragrafo("Abertura", atendimento.abertura)
    paragrafo("Código de chave", atendimento.codigoChave)
    paragrafo("Código de configuração", atendimento.codigoConfiguracao)
    paragrafo("Ferramenta", atendimento.ferramenta)
    if (atendimento.valorCentavos > 0) paragrafo("Valor", formatarValor(atendimento.valorCentavos))

    for (foto in fotos.take(2)) {
      if (y > altura - 240f) break
      val bitmap = Fotos.carregar(contexto, foto.caminho, 700) ?: continue
      val proporcao = bitmap.width.toFloat() / bitmap.height.toFloat()
      val larguraFoto = 320f
      val alturaFoto = larguraFoto / proporcao
      val destino = android.graphics.RectF(40f, y, 40f + larguraFoto, y + alturaFoto)
      tela.drawBitmap(bitmap, null, destino, null)
      bitmap.recycle()
      y += alturaFoto + 20f
    }

    documento.finishPage(pagina)
    val pasta = File(contexto.cacheDir, "compartilhar").apply { mkdirs() }
    val nome = "chaveirocar-${atendimento.placa.ifBlank { "registro" }}-" +
      SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date()) + ".pdf"
    val arquivo = File(pasta, nome)
    arquivo.outputStream().use { documento.writeTo(it) }
    documento.close()
    return arquivo
  }

  fun compartilharPdf(contexto: Context, arquivo: File) {
    compartilharArquivo(contexto, arquivo, "application/pdf", "Compartilhar PDF")
  }

  fun compartilharArquivo(contexto: Context, arquivo: File, tipo: String, titulo: String) {
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo)
    val intencao = Intent(Intent.ACTION_SEND).apply {
      type = tipo
      putExtra(Intent.EXTRA_STREAM, uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    contexto.startActivity(Intent.createChooser(intencao, titulo))
  }

  private fun escreverQuebrado(
    tela: android.graphics.Canvas,
    conteudo: String,
    pincel: Paint,
    x: Float,
    inicioY: Float,
    larguraMaxima: Float,
  ): Float {
    var y = inicioY
    val palavras = conteudo.split(' ')
    var linha = StringBuilder()
    for (palavra in palavras) {
      val tentativa = if (linha.isEmpty()) palavra else "$linha $palavra"
      if (pincel.measureText(tentativa) > larguraMaxima && linha.isNotEmpty()) {
        tela.drawText(linha.toString(), x, y, pincel)
        y += 16f
        linha = StringBuilder(palavra)
      } else {
        linha = StringBuilder(tentativa)
      }
    }
    if (linha.isNotEmpty()) {
      tela.drawText(linha.toString(), x, y, pincel)
      y += 16f
    }
    return y
  }
}
