package com.willendary.chaveirocar.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Digital como atalho para abrir o app. O PIN continua sendo a saída
 * quando o sensor não reconhece ou o aparelho não tem biometria.
 */
object Biometria {

  fun disponivel(contexto: Context): Boolean =
    BiometricManager.from(contexto)
      .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
      BiometricManager.BIOMETRIC_SUCCESS

  fun autenticar(
    atividade: FragmentActivity,
    titulo: String,
    aoSucesso: () -> Unit,
    aoErro: (String) -> Unit = {},
  ) {
    val prompt = BiometricPrompt(
      atividade,
      ContextCompat.getMainExecutor(atividade),
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          aoSucesso()
        }

        override fun onAuthenticationError(codigo: Int, mensagem: CharSequence) {
          aoErro(mensagem.toString())
        }
      },
    )
    val informacoes = BiometricPrompt.PromptInfo.Builder()
      .setTitle(titulo)
      .setNegativeButtonText("Usar PIN")
      .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
      .build()
    prompt.authenticate(informacoes)
  }
}
