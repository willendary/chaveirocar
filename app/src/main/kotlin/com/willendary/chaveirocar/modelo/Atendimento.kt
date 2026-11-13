package com.willendary.chaveirocar.modelo

enum class SegmentoAtendimento(val rotulo: String) {
  AUTOMOTIVO("Automotivo"),
  RESIDENCIAL("Residencial");

  companion object {
    fun de(valor: String?): SegmentoAtendimento =
      if (valor == RESIDENCIAL.name) RESIDENCIAL else AUTOMOTIVO
  }
}

enum class TipoServico(
  val rotulo: String,
  val segmentos: Set<SegmentoAtendimento> = setOf(SegmentoAtendimento.AUTOMOTIVO),
) {
  CHAVE_CODIFICADA("Chave codificada", setOf(SegmentoAtendimento.AUTOMOTIVO)),
  TRANSPONDER("Transponder", setOf(SegmentoAtendimento.AUTOMOTIVO)),
  TELECOMANDO("Telecomando", setOf(SegmentoAtendimento.AUTOMOTIVO)),
  ABERTURA("Abertura", setOf(SegmentoAtendimento.AUTOMOTIVO)),
  IGNICAO("Ignição", setOf(SegmentoAtendimento.AUTOMOTIVO)),
  CONSERTO("Conserto", setOf(SegmentoAtendimento.AUTOMOTIVO)),
  PROGRAMACAO_CHAVE("Programação de chave", setOf(SegmentoAtendimento.AUTOMOTIVO)),
  ABERTURA_RESIDENCIAL("Abertura residencial", setOf(SegmentoAtendimento.RESIDENCIAL)),
  COPIA_CHAVE("Cópia de chave", setOf(SegmentoAtendimento.RESIDENCIAL)),
  TROCA_SEGREDO("Troca de segredo", setOf(SegmentoAtendimento.RESIDENCIAL)),
  INSTALACAO_FECHADURA("Instalação de fechadura", setOf(SegmentoAtendimento.RESIDENCIAL)),
  TROCA_CILINDRO("Cilindro / fechadura", setOf(SegmentoAtendimento.RESIDENCIAL)),
  FECHADURA_DIGITAL("Fechadura digital", setOf(SegmentoAtendimento.RESIDENCIAL)),
  REPARO_FECHADURA("Reparo de fechadura", setOf(SegmentoAtendimento.RESIDENCIAL)),
  OUTRO("Outro", setOf(SegmentoAtendimento.AUTOMOTIVO, SegmentoAtendimento.RESIDENCIAL));

  companion object {
    fun de(valor: String?): TipoServico = entries.firstOrNull { it.name == valor } ?: OUTRO

    fun doSegmento(segmento: SegmentoAtendimento): List<TipoServico> =
      entries.filter { segmento in it.segmentos }

    fun padraoDoSegmento(segmento: SegmentoAtendimento): TipoServico =
      if (segmento == SegmentoAtendimento.RESIDENCIAL) ABERTURA_RESIDENCIAL else CHAVE_CODIFICADA

    /** Desconsidera identificadores desconhecidos; usa o tipo anterior ao importar registros antigos. */
    fun deNomes(nomes: Iterable<String>, padrao: TipoServico): Set<TipoServico> =
      nomes.mapNotNull { nome -> entries.firstOrNull { it.name == nome.trim() } }.toSet()
        .ifEmpty { setOf(padrao) }
  }
}

enum class CategoriaFoto(val rotulo: String) {
  CARRO("Carro"),
  PROCEDIMENTO("Como se faz");

  fun rotuloPara(segmento: SegmentoAtendimento): String = when (this) {
    CARRO -> if (segmento == SegmentoAtendimento.RESIDENCIAL) "Fechadura / local" else "Carro"
    PROCEDIMENTO -> "Como se faz"
  }

  companion object {
    fun de(valor: String?): CategoriaFoto = entries.firstOrNull { it.name == valor } ?: CARRO
  }
}

data class SetaAnotacao(
  val x1: Float,
  val y1: Float,
  val x2: Float,
  val y2: Float,
  val corHex: String = "#EF4444",
  val espessura: Float = 6f,
)

data class Atendimento(
  val id: Long = 0,
  val segmento: SegmentoAtendimento = SegmentoAtendimento.AUTOMOTIVO,
  val placa: String = "",
  val marca: String = "",
  val modelo: String = "",
  val ano: String = "",
  val chassi: String = "",
  val tipoLocal: String = "",
  val ambientePorta: String = "",
  val marcaFechadura: String = "",
  val modeloFechadura: String = "",
  val tipoCilindro: String = "",
  val enderecoLocal: String = "",
  val dataServico: Long = System.currentTimeMillis(),
  val tipoServico: TipoServico = TipoServico.CHAVE_CODIFICADA,
  val defeito: String = "",
  val solucao: String = "",
  val abertura: String = "",
  val codigoChave: String = "",
  val codigoConfiguracao: String = "",
  val chipTransponder: String = "",
  val ferramenta: String = "",
  val valorCentavos: Long = 0,
  val clienteNome: String = "",
  val clienteTelefone: String = "",
  val pago: Boolean = true,
  val quantidadeFotos: Int = 0,
  val atualizadoEm: Long = 0,
  // Campo singular permanece para os registros e backups antigos.
  val tiposServico: Set<TipoServico> = setOf(tipoServico),
) {
  val tiposOrdenados: List<TipoServico>
    get() = TipoServico.entries.filter { it in tiposServico }.ifEmpty { listOf(tipoServico) }

  val tiposEmTexto: String
    get() = tiposOrdenados.joinToString(" · ") { it.rotulo }

  val descricaoResidencial: String
    get() = listOf(tipoLocal, ambientePorta, marcaFechadura, modeloFechadura)
      .filter { it.isNotBlank() }
      .joinToString(" · ")

  val descricaoVeiculo: String
    get() = listOf(marca, modelo, ano).filter { it.isNotBlank() }.joinToString(" · ")
}

data class Foto(
  val id: Long,
  val atendimentoId: Long,
  val caminho: String,
  val categoria: CategoriaFoto = CategoriaFoto.CARRO,
  val legenda: String = "",
  val ordem: Int = 0,
  val anotacoes: String = "[]",
)

data class ResumoModelo(
  val marca: String,
  val modelo: String,
  val ano: String,
  val quantidade: Int,
  val ultimaData: Long,
  val temProcedimento: Boolean,
)

data class ResumoResidencial(
  val tipoLocal: String,
  val marcaFechadura: String,
  val modeloFechadura: String,
  val quantidade: Int,
  val ultimaData: Long,
  val temProcedimento: Boolean,
)
