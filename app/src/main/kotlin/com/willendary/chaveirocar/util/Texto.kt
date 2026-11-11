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
