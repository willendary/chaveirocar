package com.willendary.chaveirocar.util

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

/**
 * Deixa o texto pronto para busca: sem acento, minúsculo, só letras, números e espaço simples.
 */
fun normalizar(valor: String?): String {
  if (valor.isNullOrBlank()) return ""
  val semAcento = Normalizer.normalize(valor, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
  return semAcento.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
}

fun formatarData(millis: Long): String = formatoData.format(Date(millis))

fun formatarValor(centavos: Long): String {
  if (centavos <= 0) return ""
  return "R$ " + agruparMilhares(centavos / 100) + "," + (centavos % 100).toString().padStart(2, '0')
}

fun formatarValorEditavel(centavos: Long): String {
  if (centavos <= 0) return ""
  return (centavos / 100).toString() + "," + (centavos % 100).toString().padStart(2, '0')
}

/**
 * Aceita "150", "150,50" e "1.234,56". Vírgula é separador decimal; ponto é separador de milhar.
 */
fun paraCentavos(texto: String): Long {
  val limpo = texto.filter { it.isDigit() || it == ',' }
  if (limpo.isEmpty()) return 0
  val decimal = limpo.replace(',', '.')
  return (decimal.toDoubleOrNull() ?: 0.0).times(100).roundToLong()
}

fun agruparMilhares(valor: Long): String {
  val digitos = valor.toString()
  val resultado = StringBuilder()
  for ((indice, caractere) in digitos.withIndex()) {
    if (indice > 0 && (digitos.length - indice) % 3 == 0) resultado.append('.')
    resultado.append(caractere)
  }
  return resultado.toString()
}

fun diasDesde(millis: Long): Long {
  if (millis <= 0) return Long.MAX_VALUE
  return (System.currentTimeMillis() - millis) / (24 * 60 * 60 * 1000)
}

/** Formata placa para o padrão Mercosul (ABC1D23) ou antigo (ABC-1234). */
fun formatarPlacaMascara(entrada: String): String {
  val limpo = entrada.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }.take(7)
  return if (limpo.length == 7 && limpo.take(3).all { it.isLetter() } && limpo.drop(3).all { it.isDigit() }) {
    "${limpo.take(3)}-${limpo.drop(3)}"
  } else {
    limpo
  }
}

/** Formata telefone brasileiro com DDD: (XX) XXXXX-XXXX ou (XX) XXXX-XXXX. */
fun formatarTelefoneMascara(entrada: String): String {
  val digitos = entrada.filter { it.isDigit() }.take(11)
  return when {
    digitos.isEmpty() -> ""
    digitos.length <= 2 -> "($digitos"
    digitos.length <= 6 -> "(${digitos.substring(0, 2)}) ${digitos.substring(2)}"
    digitos.length <= 10 -> "(${digitos.substring(0, 2)}) ${digitos.substring(2, 6)}-${digitos.substring(6)}"
    else -> "(${digitos.substring(0, 2)}) ${digitos.substring(2, 7)}-${digitos.substring(7)}"
  }
}

/** Formata centavos como moeda R$ X.XXX,XX em tempo real. */
fun formatarMoedaDigitacao(entrada: String): Pair<String, Long> {
  val digitos = entrada.filter { it.isDigit() }
  if (digitos.isBlank()) return "" to 0L
  val centavos = digitos.toLongOrNull() ?: 0L
  val texto = formatarValor(centavos)
  return texto to centavos
}
