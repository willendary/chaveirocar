package com.willendary.chaveirocar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.modelo.FichaTecnica
import com.willendary.chaveirocar.util.normalizar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SEM_VALOR = "\u0001"
private const val ROTULO_SEM_VALOR = "Não informado"

private fun valorDaFicha(valor: String): String = valor.ifBlank { SEM_VALOR }

private fun textoDoValor(valor: String): String = if (valor == SEM_VALOR) ROTULO_SEM_VALOR else valor

private fun combina(valor: String, filtro: String?): Boolean =
  filtro == null || valorDaFicha(valor) == filtro

private fun real(valor: String?): String = if (valor.isNullOrBlank() || valor == SEM_VALOR) "" else valor

/** Filtros da consulta ficam no App para sobreviver à abertura de uma ficha. */
data class EstadoConsulta(
  val busca: String = "",
  val equipamento: String? = null,
  val marca: String? = null,
  val modelo: String? = null,
  val ano: String? = null,
)

/**
 * Consulta técnica por etapas: equipamento -> marca -> modelo -> ano -> ficha.
 * A busca livre ignora as etapas e procura em chip, sistema e manual.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaConsultaTecnica(
  banco: BancoDeAtendimentos,
  estadoInicial: EstadoConsulta,
  aoMudarEstado: (EstadoConsulta) -> Unit,
  onAbrir: (Long) -> Unit,
  onNova: (FichaTecnica) -> Unit,
) {
  var fichas by remember { mutableStateOf<List<FichaTecnica>>(emptyList()) }
  var carregado by remember { mutableStateOf(false) }
  var estado by remember { mutableStateOf(estadoInicial) }
  val busca = estado.busca
  val equipamentoSel = estado.equipamento
  val marcaSel = estado.marca
  val modeloSel = estado.modelo
  val anoSel = estado.ano

  fun mudar(novo: EstadoConsulta) {
    estado = novo
    aoMudarEstado(novo)
  }

  LaunchedEffect(Unit) {
    fichas = withContext(Dispatchers.IO) { banco.listarFichas() }
    carregado = true
  }

  fun baseNova(): FichaTecnica = FichaTecnica(
    equipamento = real(equipamentoSel),
    marca = real(marcaSel),
    modelo = real(modeloSel),
    ano = real(anoSel),
  )

  fun retroceder() {
    mudar(
      when {
        estado.ano != null -> estado.copy(ano = null)
        estado.modelo != null -> estado.copy(modelo = null, ano = null)
        estado.marca != null -> estado.copy(marca = null, modelo = null, ano = null)
        else -> estado.copy(equipamento = null, marca = null, modelo = null, ano = null)
      },
    )
  }

  BackHandler(enabled = busca.isNotBlank()) { mudar(estado.copy(busca = "")) }
  BackHandler(enabled = busca.isBlank() && equipamentoSel != null) { retroceder() }

  val doEquipamento = remember(fichas, equipamentoSel) {
    fichas.filter { combina(it.equipamento, equipamentoSel) }
  }
  val daMarca = remember(doEquipamento, marcaSel) {
    doEquipamento.filter { combina(it.marca, marcaSel) }
  }
  val doModelo = remember(daMarca, modeloSel) {
    daMarca.filter { combina(it.modelo, modeloSel) }
  }
  val doAno = remember(doModelo, anoSel) {
    doModelo.filter { combina(it.ano, anoSel) }
  }

  fun opcoes(valores: List<String>): List<String> =
    valores.map { valorDaFicha(it) }.distinct()
      .sortedWith(compareBy({ it == SEM_VALOR }, { it }))

  val equipamentos = remember(fichas) { opcoes(fichas.map { it.equipamento }) }
  val marcas = remember(doEquipamento) { opcoes(doEquipamento.map { it.marca }) }
  val modelos = remember(daMarca) { opcoes(daMarca.map { it.modelo }) }
  val anos = remember(doModelo) { opcoes(doModelo.map { it.ano }) }

  val buscaNormalizada = normalizar(busca)
  val resultadosBusca = remember(fichas, buscaNormalizada) {
    if (buscaNormalizada.isBlank()) emptyList()
    else fichas.filter { ficha ->
      normalizar(
        listOf(
          ficha.equipamento, ficha.marca, ficha.modelo, ficha.ano, ficha.sistema,
          ficha.descricao, ficha.chip, ficha.chave, ficha.telecomando, ficha.frequencia,
          ficha.avisos, ficha.funcoes, ficha.manual, ficha.origem,
        ).joinToString(" "),
      ).contains(buscaNormalizada)
    }
  }

  val passo = when {
    equipamentoSel == null -> 0
    marcaSel == null -> 1
    modeloSel == null -> 2
    anoSel == null -> 3
    else -> 4
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MarcaChaveiro()
            Column {
              Text("Consulta técnica", style = MaterialTheme.typography.titleLarge)
              Text(
                "Chip, chave e sistema por veículo",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        actions = {
          IconButton(onClick = { onNova(baseNova()) }) {
            Icon(Icons.Default.Add, contentDescription = "Nova ficha técnica")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    Column(modifier = Modifier.conteudoResponsivo().padding(espacamento)) {
      OutlinedTextField(
        value = busca,
        onValueChange = { mudar(estado.copy(busca = it)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text("Buscar por veículo, chip, sistema…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
          unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
      )

      if (!carregado) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return@Column
      }

      if (fichas.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
          EstadoVazio(
            "Sua consulta técnica começa aqui",
            "Cadastre uma ficha com o chip, a chave e as observações deste veículo. Fica tudo no aparelho e funciona sem internet.",
            acao = {
              Button(onClick = { onNova(FichaTecnica()) }) { Text("Adicionar primeira ficha") }
            },
          )
        }
        return@Column
      }

      if (buscaNormalizada.isNotBlank()) {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(16.dp),
        ) {
          item { TituloSecao("${resultadosBusca.size} ficha(s) encontrada(s)") }
          if (resultadosBusca.isEmpty()) {
            item { Text("Nada encontrado. Tente outro termo.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
          }
          items(resultadosBusca, key = { it.id }) { ficha ->
            CartaoFicha(ficha = ficha, onAbrir = { onAbrir(ficha.id) })
          }
        }
        return@Column
      }

      if (equipamentoSel != null) {
        FlowRow(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          AssistChip(
            onClick = { mudar(estado.copy(equipamento = null, marca = null, modelo = null, ano = null)) },
            label = { Text(textoDoValor(equipamentoSel.orEmpty()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
          )
          marcaSel?.let { marca ->
            AssistChip(
              onClick = { mudar(estado.copy(marca = null, modelo = null, ano = null)) },
              label = { Text(textoDoValor(marca), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
          }
          modeloSel?.let { modelo ->
            AssistChip(
              onClick = { mudar(estado.copy(modelo = null, ano = null)) },
              label = { Text(textoDoValor(modelo), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
          }
          anoSel?.let { ano ->
            AssistChip(
              onClick = { mudar(estado.copy(ano = null)) },
              label = { Text(textoDoValor(ano)) },
            )
          }
        }
      }

      when (passo) {
        0 -> ListaDeOpcoes(
          titulo = "Escolha o equipamento",
          descricao = "A ferramenta usada no serviço.",
          opcoes = equipamentos,
          vazio = "Nenhum equipamento cadastrado.",
          onEscolher = { escolhido ->
            mudar(estado.copy(equipamento = escolhido, marca = null, modelo = null, ano = null))
          },
          onNova = { onNova(baseNova()) },
        )

        1 -> ListaDeOpcoes(
          titulo = "Escolha a montadora",
          descricao = "Marca do veículo.",
          opcoes = marcas,
          vazio = "Nenhuma marca para este equipamento.",
          onEscolher = { escolhido ->
            mudar(estado.copy(marca = escolhido, modelo = null, ano = null))
          },
          onNova = { onNova(baseNova()) },
        )

        2 -> ListaDeOpcoes(
          titulo = "Escolha o modelo",
          descricao = "Modelo do veículo.",
          opcoes = modelos,
          vazio = "Nenhum modelo para esta marca.",
          onEscolher = { escolhido ->
            mudar(estado.copy(modelo = escolhido, ano = null))
          },
          onNova = { onNova(baseNova()) },
        )

        3 -> ListaDeOpcoes(
          titulo = "Escolha o ano ou geração",
          descricao = "Ano informado na ficha. Quando não houver, use \"Não informado\".",
          opcoes = anos,
          vazio = "Nenhum ano para este modelo.",
          onEscolher = { escolhido -> mudar(estado.copy(ano = escolhido)) },
          onNova = { onNova(baseNova()) },
        )

        else -> LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(16.dp),
        ) {
          item {
            PainelDestaque(
              listOf(real(marcaSel), real(modeloSel), real(anoSel)).filter { it.isNotBlank() }.joinToString(" "),
              "Sistemas e referências cadastradas para este veículo.",
              "${doAno.size} FICHA(S)",
            )
          }
          items(doAno, key = { it.id }) { ficha ->
            CartaoFicha(ficha = ficha, onAbrir = { onAbrir(ficha.id) })
          }
          item {
            Button(
              onClick = { onNova(baseNova()) },
              modifier = Modifier.fillMaxWidth(),
            ) { Text("Adicionar outra ficha para este veículo") }
          }
        }
      }
    }
  }
}

@Composable
private fun ListaDeOpcoes(
  titulo: String,
  descricao: String,
  opcoes: List<String>,
  vazio: String,
  onEscolher: (String) -> Unit,
  onNova: () -> Unit,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
  ) {
    item {
      Text(titulo, style = MaterialTheme.typography.titleMedium)
      Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (opcoes.isEmpty()) {
      item {
        EstadoVazio(
          "Nada cadastrado",
          vazio,
          acao = { Button(onClick = onNova) { Text("Adicionar ficha") } },
        )
      }
    }
    items(opcoes) { opcao ->
      Card(
        onClick = { onEscolher(opcao) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            textoDoValor(opcao),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
          )
          Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
      }
    }
  }
}

@Composable
private fun CartaoFicha(ficha: FichaTecnica, onAbrir: () -> Unit) {
  Card(
    onClick = onAbrir,
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          listOf(ficha.marca, ficha.modelo, ficha.ano).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { ficha.sistema.ifBlank { "Ficha técnica" } },
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (!ficha.confirmado) Selo("Não confirmado", pendente = true)
      }
      if (ficha.sistema.isNotBlank()) {
        Text(ficha.sistema, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
      }
      if (ficha.descricao.isNotBlank()) {
        Text(
          ficha.descricao,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (ficha.chip.isNotBlank()) Selo("Chip ${ficha.chip}")
        if (ficha.equipamento.isNotBlank()) {
          Text(
            ficha.equipamento,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}
