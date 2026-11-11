package com.willendary.chaveirocar.util

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN do app não é guardado em texto: vira hash com sal e PBKDF2.
 * 4 a 6 dígitos não salvam de ataque físico sofisticado, mas impedem
 * quem pega o celular na bancada de abrir o histórico.
 */
object Seguranca {

  private const val ITERACOES = 120_000
  private const val TAMANHO_CHAVE = 256

  fun novoSal(): String {
    val sal = ByteArray(16)
    SecureRandom().nextBytes(sal)
    return Base64.encodeToString(sal, Base64.NO_WRAP)
  }

  fun gerarHash(pin: String, sal: String): String {
    val derivada = derivar(pin, Base64.decode(sal, Base64.NO_WRAP))
    return Base64.encodeToString(derivada, Base64.NO_WRAP)
  }

  fun conferir(pin: String, sal: String, hashEsperado: String): Boolean {
    val derivada = derivar(pin, Base64.decode(sal, Base64.NO_WRAP))
    return Base64.encodeToString(derivada, Base64.NO_WRAP) == hashEsperado
  }

  private fun derivar(pin: String, sal: ByteArray): ByteArray {
    val especificacao = PBEKeySpec(pin.toCharArray(), sal, ITERACOES, TAMANHO_CHAVE)
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(especificacao).encoded
  }
}
