package com.willendary.chaveirocar.dados

import android.content.Context
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.CategoriaFoto
import com.willendary.chaveirocar.modelo.FichaTecnica
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
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
  private const val PREFIXO_FICHAS = "fichas/"

  fun exportar(banco: BancoDeAtendimentos, contexto: Context, saida: OutputStream) {
    val zip = ZipOutputStream(BufferedOutputStream(saida))
    val raiz = JSONObject()
    raiz.put("versao", 6)
    raiz.put("exportadoEm", System.currentTimeMillis())
    val lista = JSONArray()

    for (atendimento in banco.listar("")) {
      val objeto = JSONObject().apply {
        put("id", atendimento.id)
        put("atualizadoEm", atendimento.atualizadoEm)
        put("segmento", atendimento.segmento.name)
        put("placa", atendimento.placa)
        put("marca", atendimento.marca)
        put("modelo", atendimento.modelo)
        put("ano", atendimento.ano)
        put("chassi", atendimento.chassi)
        put("tipoLocal", atendimento.tipoLocal)
        put("ambientePorta", atendimento.ambientePorta)
        put("marcaFechadura", atendimento.marcaFechadura)
        put("modeloFechadura", atendimento.modeloFechadura)
        put("tipoCilindro", atendimento.tipoCilindro)
        put("enderecoLocal", atendimento.enderecoLocal)
        put("dataServico", atendimento.dataServico)
        // Backup singular para interoperabilidade com versões anteriores.
        put("tipoServico", atendimento.tiposOrdenados.first().name)
        put("tiposServico", JSONArray(atendimento.tiposOrdenados.map { it.name }))
        put("defeito", atendimento.defeito)
        put("solucao", atendimento.solucao)
        put("abertura", atendimento.abertura)
        put("codigoChave", atendimento.codigoChave)
        put("codigoConfiguracao", atendimento.codigoConfiguracao)
        put("chipTransponder", atendimento.chipTransponder)
        put("ferramenta", atendimento.ferramenta)
        put("valorCentavos", atendimento.valorCentavos)
        put("clienteNome", atendimento.clienteNome)
        put("clienteTelefone", atendimento.clienteTelefone)
        put("pago", atendimento.pago)
      }
      val fotos = JSONArray()
      for (foto in banco.listarFotos(atendimento.id)) {
        val arquivo = Fotos.arquivo(contexto, foto.caminho)
        if (!arquivo.exists()) continue
        val nomeDentroDoZip = PREFIXO_FOTOS + foto.caminho
        zip.putNextEntry(ZipEntry(nomeDentroDoZip))
        arquivo.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
        fotos.put(
          JSONObject().apply {
            put("arquivo", foto.caminho)
            put("categoria", foto.categoria.name)
            put("legenda", foto.legenda)
            put("ordem", foto.ordem)
            put("anotacoes", foto.anotacoes)
          },
        )
      }
      objeto.put("fotos", fotos)
      lista.put(objeto)
    }

    raiz.put("atendimentos", lista)

    val fichas = JSONArray()
    for (ficha in banco.listarFichas()) {
      val objeto = JSONObject().apply {
        put("id", ficha.id)
        put("atualizadoEm", ficha.atualizadoEm)
        put("equipamento", ficha.equipamento)
        put("marca", ficha.marca)
        put("modelo", ficha.modelo)
        put("ano", ficha.ano)
        put("sistema", ficha.sistema)
        put("descricao", ficha.descricao)
        put("chip", ficha.chip)
        put("chave", ficha.chave)
        put("telecomando", ficha.telecomando)
        put("frequencia", ficha.frequencia)
        put("avisos", ficha.avisos)
        put("funcoes", ficha.funcoes)
        put("manual", ficha.manual)
        put("foto", ficha.foto)
        put("confirmado", ficha.confirmado)
        put("origem", ficha.origem)
      }
      val arquivo = Fotos.arquivo(contexto, ficha.foto)
      if (ficha.foto.isNotBlank() && arquivo.exists()) {
        zip.putNextEntry(ZipEntry(PREFIXO_FICHAS + ficha.foto))
        arquivo.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
      }
      fichas.put(objeto)
    }
    raiz.put("fichas", fichas)

    zip.putNextEntry(ZipEntry("dados.json"))
    zip.write(raiz.toString().toByteArray(Charsets.UTF_8))
    zip.closeEntry()
    zip.finish()
    zip.flush()
  }

  /**
   * @param substituir true mescla com a base atual vence o mais recente
   *                   (por id + atualizadoEm); false apenas acrescenta.
   *                   Backups antigos sem id caem no fluxo de acrescentar.
   * @return quantidade de registros aplicados.
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
        } else if (nome.startsWith(PREFIXO_FOTOS) || nome.startsWith(PREFIXO_FICHAS)) {
          val nomeArquivo = nome.substringAfter('/')
          if (nomeArquivo.isNotBlank()) fotosDoZip[nomeArquivo] = zip.readBytes()
        }
        zip.closeEntry()
        entradaZip = zip.nextEntry
      }
    }

    if (json.isBlank()) error("Arquivo de backup inválido: dados.json não encontrado")

    val raiz = JSONObject(json)
    val lista = raiz.optJSONArray("atendimentos") ?: JSONArray()

    var aplicados = 0
    for (indice in 0 until lista.length()) {
      val objeto = lista.optJSONObject(indice) ?: continue
      val idNuvem = objeto.optLong("id", 0)
      val atualizadoNuvem = objeto.optLong("atualizadoEm", 0)
      val local = if (substituir && idNuvem > 0) banco.obter(idNuvem) else null
      if (local != null && atualizadoNuvem < local.atualizadoEm) continue
      val tipoAntigo = TipoServico.de(objeto.optString("tipoServico"))
      val listaTipos = objeto.optJSONArray("tiposServico")
      val tipos = if (listaTipos == null) setOf(tipoAntigo) else
        TipoServico.deNomes((0 until listaTipos.length()).map { listaTipos.optString(it) }, tipoAntigo)
      val atendimento = Atendimento(
        segmento = SegmentoAtendimento.de(objeto.optString("segmento")),
        placa = objeto.optString("placa"),
        marca = objeto.optString("marca"),
        modelo = objeto.optString("modelo"),
        ano = objeto.optString("ano"),
        chassi = objeto.optString("chassi"),
        tipoLocal = objeto.optString("tipoLocal"),
        ambientePorta = objeto.optString("ambientePorta"),
        marcaFechadura = objeto.optString("marcaFechadura"),
        modeloFechadura = objeto.optString("modeloFechadura"),
        tipoCilindro = objeto.optString("tipoCilindro"),
        enderecoLocal = objeto.optString("enderecoLocal"),
        dataServico = objeto.optLong("dataServico", System.currentTimeMillis()),
        tipoServico = tipoAntigo,
        tiposServico = tipos,
        defeito = objeto.optString("defeito"),
        solucao = objeto.optString("solucao"),
        abertura = objeto.optString("abertura"),
        codigoChave = objeto.optString("codigoChave"),
        codigoConfiguracao = objeto.optString("codigoConfiguracao"),
        chipTransponder = objeto.optString("chipTransponder"),
        ferramenta = objeto.optString("ferramenta"),
        valorCentavos = objeto.optLong("valorCentavos"),
        clienteNome = objeto.optString("clienteNome"),
        clienteTelefone = objeto.optString("clienteTelefone"),
        pago = objeto.optBoolean("pago", true),
      )
      if (local != null) {
        banco.listarFotos(local.id).forEach { foto ->
          banco.excluirFoto(foto.id)?.let { Fotos.excluir(contexto, it) }
        }
      }
      val id = banco.salvar(if (local != null) atendimento.copy(id = local.id) else atendimento)
      val fotos = objeto.optJSONArray("fotos") ?: JSONArray()
      for (indiceFoto in 0 until fotos.length()) {
        val item = fotos.opt(indiceFoto)
        val arquivo: String
        val categoria: CategoriaFoto
        val legenda: String
        val anotacoes: String
        if (item is JSONObject) {
          arquivo = item.optString("arquivo")
          categoria = CategoriaFoto.de(item.optString("categoria"))
          legenda = item.optString("legenda")
          anotacoes = item.optString("anotacoes", "[]").ifBlank { "[]" }
        } else {
          // backups antigos (1.0) guardavam só o nome do arquivo
          arquivo = item?.toString().orEmpty()
          categoria = CategoriaFoto.CARRO
          legenda = ""
          anotacoes = "[]"
        }
        if (arquivo.isBlank()) continue
        val bytes = fotosDoZip[arquivo] ?: continue
        val nomeNovo = Fotos.salvarDeBytes(contexto, bytes)
        banco.adicionarFoto(id, nomeNovo, categoria, legenda, anotacoes)
      }
      aplicados++
    }

    val listaFichas = raiz.optJSONArray("fichas") ?: JSONArray()
    for (indice in 0 until listaFichas.length()) {
      val objeto = listaFichas.optJSONObject(indice) ?: continue
      val idNuvem = objeto.optLong("id", 0)
      val atualizadoNuvem = objeto.optLong("atualizadoEm", 0)
      val local = if (substituir && idNuvem > 0) banco.obterFicha(idNuvem) else null
      if (local != null && atualizadoNuvem < local.atualizadoEm) continue
      val fotoAntiga = objeto.optString("foto")
      val foto = fotosDoZip[fotoAntiga]
        ?.let { bytes -> Fotos.salvarDeBytes(contexto, bytes) }
        ?: local?.foto.orEmpty()
      val ficha = FichaTecnica(
        equipamento = objeto.optString("equipamento"),
        marca = objeto.optString("marca"),
        modelo = objeto.optString("modelo"),
        ano = objeto.optString("ano"),
        sistema = objeto.optString("sistema"),
        descricao = objeto.optString("descricao"),
        chip = objeto.optString("chip"),
        chave = objeto.optString("chave"),
        telecomando = objeto.optString("telecomando"),
        frequencia = objeto.optString("frequencia"),
        avisos = objeto.optString("avisos"),
        funcoes = objeto.optString("funcoes"),
        manual = objeto.optString("manual"),
        foto = foto,
        confirmado = objeto.optBoolean("confirmado", false),
        origem = objeto.optString("origem"),
      )
      if (local != null && local.foto.isNotBlank() && local.foto != foto) {
        Fotos.excluir(contexto, local.foto)
      }
      banco.salvarFicha(if (local != null) ficha.copy(id = local.id) else ficha)
      aplicados++
    }
    return aplicados
  }
}
