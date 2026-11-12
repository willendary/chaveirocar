package com.willendary.chaveirocar.modelo

/**
 * Ficha de referência técnica: o que serve em cada veículo/sistema.
 * Não é atendimento — é conhecimento consultável, preenchido pelo próprio chaveiro.
 */
data class FichaTecnica(
  val id: Long = 0,
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
)
