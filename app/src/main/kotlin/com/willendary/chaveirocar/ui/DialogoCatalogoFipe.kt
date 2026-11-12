package com.willendary.chaveirocar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.willendary.chaveirocar.catalogo.CatalogoFipe
import com.willendary.chaveirocar.catalogo.OpcaoCatalogo
import com.willendary.chaveirocar.util.normalizar

/** Seleção progressiva marca -> modelo -> ano usando o catálogo FIPE. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoCatalogoFipe(
  aoEscolher: (marca: String, modelo: String, ano: String) -> Unit,
  onFechar: () -> Unit,
) {
  val contexto = LocalContext.current
  var passo by remember { mutableStateOf(0) }
  var marca by remember { mutableStateOf<OpcaoCatalogo?>(null) }
  var modelo by remember { mutableStateOf<OpcaoCatalogo?>(null) }
  var opcoes by remember { mutableStateOf<List<OpcaoCatalogo>>(emptyList()) }
  var carregando by remember { mutableStateOf(true) }
  var erro by remember { mutableStateOf<String?>(null) }
  var aviso by remember { mutableStateOf<String?>(null) }
  var busca by remember { mutableStateOf("") }
  var tentativa by remember { mutableStateOf(0) }

  LaunchedEffect(passo, marca?.codigo, modelo?.codigo, tentativa) {
    carregando = true
    erro = null
    aviso = null
    runCatching {
      when (passo) {
        0 -> CatalogoFipe.marcas(contexto)
        1 -> CatalogoFipe.modelos(contexto, requireNotNull(marca).codigo)
        else -> CatalogoFipe.anos(contexto, requireNotNull(marca).codigo, requireNotNull(modelo).codigo)
      }
    }.onSuccess { resposta ->
      opcoes = resposta.opcoes
      aviso = resposta.aviso
    }.onFailure { falha ->
      opcoes = emptyList()
      erro = falha.message
    }
    carregando = false
  }

  LaunchedEffect(passo) { busca = "" }

  fun voltar() {
    when (passo) {
      2 -> {
        modelo = null
        passo = 1
      }
      1 -> {
        marca = null
        passo = 0
      }
      else -> onFechar()
    }
  }

  val filtradas = remember(busca, opcoes) {
    val termo = normalizar(busca)
    if (termo.isBlank()) opcoes
    else opcoes.filter { normalizar(it.nome).contains(termo) }
  }

  Dialog(
    onDismissRequest = onFechar,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Scaffold(
        topBar = {
          TopAppBar(
            title = {
              Column {
                Text("Catálogo de veículos", style = MaterialTheme.typography.titleMedium)
                Text(
                  text = when (passo) {
                    0 -> "Escolha a marca"
                    1 -> "Escolha o modelo"
                    else -> "Escolha o ano/versão"
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            },
            navigationIcon = { IconButton(onClick = { voltar() }) { Icon(Icons.Default.Close, "Fechar") } },
          )
        },
      ) { espacamento ->
        Column(modifier = Modifier.fillMaxSize().padding(espacamento)) {
          if (marca != null) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                listOfNotNull(marca?.nome, modelo?.nome).joinToString(" › "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }

          OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Filtrar nesta lista") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
          )

          aviso?.let { texto ->
            Text(
              text = texto,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
              color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
          }

          when {
            carregando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }

            erro != null -> Column(
              modifier = Modifier.fillMaxSize().padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Text(erro.orEmpty(), style = MaterialTheme.typography.bodyMedium)
              Button(onClick = { tentativa++ }) { Text("Tentar de novo") }
              TextButton(onClick = onFechar) { Text("Preencher manualmente") }
            }

            filtradas.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("Nada encontrado", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              items(filtradas, key = { it.codigo }) { opcao ->
                Card(
                  modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable {
                      when (passo) {
                        0 -> {
                          marca = opcao
                          passo = 1
                        }
                        1 -> {
                          modelo = opcao
                          passo = 2
                        }
                        else -> aoEscolher(
                          marca?.nome.orEmpty(),
                          modelo?.nome.orEmpty(),
                          CatalogoFipe.extrairAno(opcao.nome),
                        )
                      }
                    },
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    if (passo == 2) {
                      val ano = CatalogoFipe.extrairAno(opcao.nome)
                      if (ano.isNotBlank()) Selo(ano)
                    }
                    Text(opcao.nome, style = MaterialTheme.typography.bodyLarge)
                  }
                }
              }
              item {
                Text(
                  "Catálogo FIPE por marca/modelo/ano. Não identifica o veículo pela placa nem traz dados do proprietário.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(vertical = 12.dp),
                )
              }
            }
          }
        }
      }
    }
  }
}
