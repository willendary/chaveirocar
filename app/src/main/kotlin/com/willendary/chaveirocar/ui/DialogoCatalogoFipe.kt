package com.willendary.chaveirocar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.willendary.chaveirocar.catalogo.CatalogoFipe
import com.willendary.chaveirocar.catalogo.OpcaoCatalogo
import com.willendary.chaveirocar.ui.theme.coresEstendidas
import com.willendary.chaveirocar.util.normalizar

/** Seleção progressiva marca -> modelo -> ano usando o catálogo FIPE. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoCatalogoFipe(
  aoEscolher: (marca: String, modelo: String, ano: String) -> Unit,
  onFechar: () -> Unit,
) {
  val contexto = LocalContext.current
  val estendidas = MaterialTheme.coresEstendidas
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
          Column {
            TopAppBar(
              title = {
                Column {
                  Text("Catálogo de veículos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
              colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
          }
        },
        containerColor = MaterialTheme.colorScheme.background,
      ) { espacamento ->
        Column(modifier = Modifier.fillMaxSize().padding(espacamento)) {
          // Barra visual de progresso das 3 etapas FIPE
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              IndicadorEtapas(
                etapas = listOf("Marca", "Modelo", "Ano"),
                passoAtual = passo,
                aoTocarEtapa = { alvo ->
                  if (alvo == 0) { marca = null; modelo = null; passo = 0 }
                  else if (alvo == 1) { modelo = null; passo = 1 }
                },
              )
              if (marca != null) {
                Text(
                  listOfNotNull(marca?.nome, modelo?.nome).joinToString(" › "),
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }

          OutlinedTextField(
            value = busca,
            onValueChange = { busca = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            placeholder = { Text("Filtrar nesta lista…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
              if (busca.isNotEmpty()) {
                IconButton(onClick = { busca = "" }) { Icon(Icons.Default.Close, contentDescription = "Limpar") }
              }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = MaterialTheme.colorScheme.surface,
              focusedContainerColor = MaterialTheme.colorScheme.surface,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
          )

          aviso?.let { texto ->
            Text(
              text = texto,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
              color = estendidas.alerta,
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
              Button(onClick = { tentativa++ }, shape = RoundedCornerShape(12.dp)) { Text("Tentar de novo") }
              TextButton(onClick = onFechar) { Text("Preencher manualmente") }
            }

            filtradas.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("Nada encontrado", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
              contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(filtradas, key = { it.codigo }) { opcao ->
                CartaoSurface(
                  raio = 14.dp,
                  onClick = {
                    when (passo) {
                      0 -> { marca = opcao; passo = 1 }
                      1 -> { modelo = opcao; passo = 2 }
                      else -> aoEscolher(
                        marca?.nome.orEmpty(),
                        modelo?.nome.orEmpty(),
                        CatalogoFipe.extrairAno(opcao.nome),
                      )
                    }
                  },
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    if (passo == 2) {
                      val ano = CatalogoFipe.extrairAno(opcao.nome)
                      if (ano.isNotBlank()) Selo(ano, tom = TomSelo.PRIMARIO)
                    }
                    Text(opcao.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Surface(shape = CircleShape, color = estendidas.superficieSutil) {
                      Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(4.dp).size(18.dp),
                      )
                    }
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
