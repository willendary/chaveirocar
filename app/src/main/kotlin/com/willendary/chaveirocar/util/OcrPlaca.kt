package com.willendary.chaveirocar.util

import android.content.Context
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Leitura local da placa com ML Kit (modelo embutido no APK, funciona sem internet).
 * A imagem é processada e apagada; só o texto vai para o formulário.
 */
object OcrPlaca {

  fun reconhecer(contexto: Context, arquivo: File): List<String> {
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo)
    val imagem = InputImage.fromFilePath(contexto, uri)
    val reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return try {
      val resultado = Tasks.await(reconhecedor.process(imagem), 20, TimeUnit.SECONDS)
      Placa.extrair(resultado.text)
    } finally {
      reconhecedor.close()
    }
  }
}
