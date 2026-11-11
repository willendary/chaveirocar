package com.willendary.chaveirocar.dados

import android.content.Context
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.TipoServico
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup completo: um ZIP com dados.json + pasta fotos.
 * Serve pra restaurar depois de trocar de celular ou reinstalar o app.
 */
object Backup {

  private const val PREFIXO_FOTOS = "fotos/"

  fun exportar(banco: BancoDeAtendimentos, contexto: Context, saida: OutputStream) {
    val zip = ZipOutputStream(BufferedOutputStream(saida))
    val raiz = JSONObject()
    raiz.put("versao", 1)
    raiz.put("exportadoEm", System.currentTimeMillis())
    val lista = JSONArray()

    for (atendimento in banco.listar("")) {
      val objeto = JSONObject().apply {
        put("placa", atendimento.placa)
        put("marca", atendimento.marca)
        put("modelo", atendimento.modelo)
        put("ano", atendimento.ano)
        put("chassi", atendimento.chassi)
        put("dataServico", atendimento.dataServico)
        put("tipoServico", atendimento.tipoServico.name)
        put("defeito", atendimento.defeito)
        put("solucao", atendimento.solucao)
        put("ferramenta", atendimento.ferramenta)
        put("valorCentavos", atendimento.valorCentavos)
      }
      val fotos = JSONArray()
      for (foto in banco.listarFotos(atendimento.id)) {
        val arquivo = Fotos.arquivo(contexto, foto.caminho)
        if (!arquivo.exists()) continue
        val nomeDentroDoZip = PREFIXO_FOTOS + foto.caminho
        zip.putNextEntry(ZipEntry(nomeDentroDoZip))
        arquivo.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        fotos.put(foto.caminho)
      }
      objeto.put("fotos", fotos)
      lista.put(objeto)
    }

    raiz.put("atendimentos", lista)
    zip.putNextEntry(ZipEntry("dados.json"))
    zip.write(raiz.toString().toByteArray(Charsets.UTF_8))
    zip.closeEntry()
    zip.finish()
  }

  /**
   * @param substituir true apaga o banco atual antes de importar (restauração);
   *                   false apenas acrescenta os registros do arquivo.
   * @return quantidade de registros importados.
   */
  fun importar(
    banco: BancoDeAtendimentos,
    contexto: Context,
    entrada: InputStream,
    substituir: Boolean,
  ): Int {
    val fotosDoZip = mutableMapOf<String, ByteArray>()
    var json = ""
    ZipInputStream(entrada).use { zip ->
      var entradaZip = zip.nextEntry
      while (entradaZip != null) {
        val nome = entradaZip.name
        if (nome == "dados.json") {
          json = zip.readBytes().toString(Charsets.UTF_8)
        } else if (nome.startsWith(PREFIXO_FOTOS)) {
          val nomeArquivo = nome.removePrefix(PREFIXO_FOTOS)
          if (nomeArquivo.isNotBlank()) fotosDoZip[nomeArquivo] = zip.readBytes()
        }
        zip.closeEntry()
        entradaZip = zip.nextEntry
      }
    }

    if (json.isBlank()) error("Arquivo de backup inválido: dados.json não encontrado")

    val raiz = JSONObject(json)
    val lista = raiz.optJSONArray("atendimentos") ?: JSONArray()

    if (substituir) {
      banco.apagarTudo().forEach { Fotos.excluir(contexto, it) }
    }

    var importados = 0
    for (indice in 0 until lista.length()) {
      val objeto = lista.optJSONObject(indice) ?: continue
      val atendimento = Atendimento(
        placa = objeto.optString("placa"),
        marca = objeto.optString("marca"),
        modelo = objeto.optString("modelo"),
        ano = objeto.optString("ano"),
        chassi = objeto.optString("chassi"),
        dataServico = objeto.optLong("dataServico", System.currentTimeMillis()),
        tipoServico = TipoServico.de(objeto.optString("tipoServico")),
        defeito = objeto.optString("defeito"),
        solucao = objeto.optString("solucao"),
        ferramenta = objeto.optString("ferramenta"),
        valorCentavos = objeto.optLong("valorCentavos"),
      )
      val id = banco.salvar(atendimento)
      val fotos = objeto.optJSONArray("fotos") ?: JSONArray()
      for (indiceFoto in 0 until fotos.length()) {
        val nomeOriginal = fotos.optString(indiceFoto)
        val bytes = fotosDoZip[nomeOriginal] ?: continue
        val nomeNovo = Fotos.salvarDeBytes(contexto, bytes)
        banco.adicionarFoto(id, nomeNovo)
      }
      importados++
    }
    return importados
  }
}
