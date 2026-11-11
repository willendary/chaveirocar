package com.willendary.chaveirocar.modelo

enum class TipoServico(val rotulo: String) {
  CHAVE_CODIFICADA("Chave codificada"),
  TRANSPONDER("Transponder"),
  TELECOMANDO("Telecomando"),
  ABERTURA("Abertura"),
  IGNICAO("Ignição"),
  OUTRO("Outro");

  companion object {
    fun de(valor: String?): TipoServico = entries.firstOrNull { it.name == valor } ?: OUTRO
  }
}

enum class CategoriaFoto(val rotulo: String) {
  CARRO("Carro"),
  PROCEDIMENTO("Como se faz");

  companion object {
    fun de(valor: String?): CategoriaFoto = entries.firstOrNull { it.name == valor } ?: CARRO
  }
}

data class Atendimento(
  val id: Long = 0,
  val placa: String = "",
  val marca: String = "",
  val modelo: String = "",
  val ano: String = "",
  val chassi: String = "",
  val dataServico: Long = System.currentTimeMillis(),
  val tipoServico: TipoServico = TipoServico.CHAVE_CODIFICADA,
  val defeito: String = "",
  val solucao: String = "",
  val abertura: String = "",
  val codigoChave: String = "",
  val codigoConfiguracao: String = "",
  val ferramenta: String = "",
  val valorCentavos: Long = 0,
  val quantidadeFotos: Int = 0,
)

data class Foto(
  val id: Long,
  val atendimentoId: Long,
  val caminho: String,
  val categoria: CategoriaFoto = CategoriaFoto.CARRO,
  val legenda: String = "",
  val ordem: Int = 0,
)

data class ResumoModelo(
  val marca: String,
  val modelo: String,
  val ano: String,
  val quantidade: Int,
  val ultimaData: Long,
  val temProcedimento: Boolean,
)
