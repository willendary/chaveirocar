package com.willendary.chaveirocar.util

import java.util.Locale

/**
 * Normaliza e valida placas brasileiras (antiga AAA1234 e Mercosul AAA1A23).
 * O OCR confunde letras e números parecidos; a correção é feita por posição,
 * sempre mostrando o resultado para o usuário confirmar.
 */
object Placa {
  private val ANTIGA = Regex("^[A-Z]{3}[0-9]{4}$")
  private val MERCOSUL = Regex("^[A-Z]{3}[0-9][A-Z][0-9]{2}$")

  private val PARA_LETRA = mapOf(
    '0' to 'O', '1' to 'I', '2' to 'Z', '3' to 'E', '4' to 'A',
    '5' to 'S', '6' to 'G', '7' to 'T', '8' to 'B', '9' to 'P',
  )
  private val PARA_DIGITO = mapOf(
    'O' to '0', 'Q' to '0', 'D' to '0', 'I' to '1', 'L' to '1', 'Z' to '2',
    'E' to '3', 'A' to '4', 'S' to '5', 'G' to '6', 'T' to '7', 'B' to '8', 'P' to '9',
  )

  fun normalizar(texto: String): String =
    texto.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }

  fun valida(texto: String): Boolean {
    val placa = normalizar(texto)
    return ANTIGA.matches(placa) || MERCOSUL.matches(placa)
  }

  /** Devolve a placa corrigida para o padrão mais provável, ou null. */
  fun reconstruir(token: String): String? {
    val base = normalizar(token)
    if (base.length != 7) return null
    if (valida(base)) return base
    return listOf("LLLDDDD", "LLLDLDD").firstNotNullOfOrNull { padrao -> gerar(base, padrao) }
  }

  /** Extrai candidatos do texto do OCR, sem repetir. */
  fun extrair(texto: String): List<String> {
    val achados = LinkedHashSet<String>()
    val tokens = texto.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
    for (token in tokens) {
      if (token.length == 7) reconstruir(token)?.let { achados.add(it) }
      for (inicio in 0..(token.length - 7).coerceAtLeast(0)) {
        if (token.length > 7) {
          reconstruir(token.substring(inicio, inicio + 7))?.let { achados.add(it) }
        }
      }
    }
    // "ABC 1234" vira "ABC1234" quando o OCR separa letras e números.
    for (indice in 0 until tokens.size - 1) {
      val juntos = tokens[indice] + tokens[indice + 1]
      if (juntos.length == 7) reconstruir(juntos)?.let { achados.add(it) }
    }
    return achados.take(5)
  }

  private fun gerar(base: String, padrao: String): String? {
    val opcoes = base.indices.map { indice ->
      val caractere = base[indice]
      if (padrao[indice] == 'L') {
        if (caractere.isLetter()) listOf(caractere) else listOfNotNull(PARA_LETRA[caractere])
      } else {
        if (caractere.isDigit()) listOf(caractere) else listOfNotNull(PARA_DIGITO[caractere])
      }
    }
    if (opcoes.any { it.isEmpty() }) return null
    var candidatos = listOf("")
    for (alternativas in opcoes) {
      candidatos = candidatos.flatMap { prefixo -> alternativas.map { prefixo + it } }
      if (candidatos.size > 64) candidatos = candidatos.take(64)
    }
    return candidatos.firstOrNull { valida(it) }
  }
}
