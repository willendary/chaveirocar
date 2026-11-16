package com.willendary.chaveirocar.modelo

/**
 * Ficha de referência técnica: o que serve em cada veículo/sistema ou fechadura.
 * Pode vir do catálogo padrão embutido (id < 0) ou do cadastro da empresa (id > 0).
 */
data class FichaTecnica(
  val id: Long = 0,
  val segmento: SegmentoAtendimento = SegmentoAtendimento.AUTOMOTIVO,
  val equipamento: String = "",
  val marca: String = "",
  val modelo: String = "",
  val ano: String = "",
  val sistema: String = "",
  val descricao: String = "",
  val chip: String = "",
  val chave: String = "",
  val telecomando: String = "",
  val frequencia: String = "",
  val avisos: String = "",
  val funcoes: String = "",
  val manual: String = "",
  val foto: String = "",
  val confirmado: Boolean = false,
  val origem: String = "",
  val atualizadoEm: Long = 0,
) {
  val ehCatalogoOficial: Boolean
    get() = id < 0 || origem.equals("CATALOGO_OFICIAL", ignoreCase = true)

  val temManualOffline: Boolean
    get() = manual.isNotBlank()
}
