package com.willendary.chaveirocar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.dados.Fotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carrega uma foto do armazenamento do app em segundo plano.
 */
@Composable
fun ImagemLocal(
  caminho: String,
  modificador: Modifier = Modifier,
  ladoMaximo: Int = 600,
) {
  val contexto = LocalContext.current
  var bitmap by remember(caminho) { mutableStateOf<ImageBitmap?>(null) }
  LaunchedEffect(caminho, ladoMaximo) {
    bitmap = withContext(Dispatchers.IO) { Fotos.carregar(contexto, caminho, ladoMaximo)?.asImageBitmap() }
  }
  val atual = bitmap
  if (atual != null) {
    Image(
      bitmap = atual,
      contentDescription = null,
      modifier = modificador,
      contentScale = ContentScale.Crop,
    )
  } else {
    Box(
      modifier = modificador
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
    )
  }
}
