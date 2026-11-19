package com.willendary.chaveirocar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.catalogo.CatalogoFichasPadrao
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.modelo.FichaTecnica
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.ui.theme.coresEstendidas
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
 * Consulta técnica por etapas com barra visual de progresso:
 * equipamento -> marca -> modelo -> ano -> ficha.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaConsultaTecnica(
  banco: BancoDeAtendimentos,
  estadoInicial: EstadoConsulta,
  aoMudarEstado: (EstadoConsulta) -> Unit,
  onAbrir: (Long) -> Unit,
  onNova: (FichaTecnica) -> Unit,
  versaoSincronia: Int = 0,
  segmento: SegmentoAtendimento? = null,
  incluirCatalogoPadrao: Boolean = true,
  podeEditar: Boolean = true,
  onTrocarArea: (() -> Unit)? = null,
  onVoltar: (() -> Unit)? = null,
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

  LaunchedEffect(versaoSincronia, segmento, incluirCatalogoPadrao) {
    fichas = withContext(Dispatchers.IO) {
      val daEmpresa = banco.listarFichas(segmento = segmento)
      val doCatalogo = if (incluirCatalogoPadrao) CatalogoFichasPadrao.fichas(segmento = segmento) else emptyList()
      daEmpresa + doCatalogo
    }
    carregado = true
  }

  fun baseNova(): FichaTecnica = FichaTecnica(
    segmento = segmento ?: SegmentoAtendimento.AUTOMOTIVO,
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
      CabecalhoTela(
        titulo = "Consulta técnica",
        subtitulo = if (segmento == SegmentoAtendimento.RESIDENCIAL)
          "Fechaduras, cilindros e manuais offline"
        else "Chip, chave, sistema e manuais offline",
        aoVoltar = onVoltar,
        acoesExtra = if (podeEditar) {
          {
            FilledTonalIconButton(
              onClick = { onNova(baseNova()) },
              modifier = Modifier.padding(end = 10.dp),
            ) {
              Icon(Icons.Default.Add, contentDescription = "Nova ficha técnica")
            }
          }
        } else null,
      )
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    Column(modifier = Modifier.conteudoResponsivo().padding(espacamento)) {
      if (segmento != null && onTrocarArea != null) {
        BarraModalidadeAtiva(segmento, onTrocarArea)
      }

      OutlinedTextField(
        value = busca,
        onValueChange = { mudar(estado.copy(busca = it)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        placeholder = { Text("Buscar por veículo, chip, sistema ou manual…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
          if (busca.isNotEmpty()) {
            IconButton(onClick = { mudar(estado.copy(busca = "")) }) {
              Icon(Icons.Default.Close, contentDescription = "Limpar busca")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
          unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
          focusedBorderColor = MaterialTheme.colorScheme.primary,
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
              Button(onClick = { onNova(FichaTecnica()) }, shape = RoundedCornerShape(12.dp)) {
                Text("Adicionar primeira ficha")
              }
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
            item {
              EstadoVazio("Nada encontrado", "Tente buscar por outro termo, marca ou código de chip.")
            }
          }
          items(resultadosBusca, key = { it.id }) { ficha ->
            CartaoFicha(ficha = ficha, onAbrir = { onAbrir(ficha.id) })
          }
        }
        return@Column
      }

      // Barra visual de progresso das 4 etapas
      Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          IndicadorEtapas(
            etapas = listOf("Equip.", "Marca", "Modelo", "Ano"),
            passoAtual = passo.coerceAtMost(3),
            aoTocarEtapa = { alvo ->
              when (alvo) {
                0 -> mudar(estado.copy(equipamento = null, marca = null, modelo = null, ano = null))
                1 -> mudar(estado.copy(marca = null, modelo = null, ano = null))
                2 -> mudar(estado.copy(modelo = null, ano = null))
                3 -> mudar(estado.copy(ano = null))
              }
            },
          )
          if (equipamentoSel != null) {
            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              AssistChip(
                onClick = { mudar(estado.copy(equipamento = null, marca = null, modelo = null, ano = null)) },
                shape = RoundedCornerShape(10.dp),
                label = { Text(textoDoValor(equipamentoSel.orEmpty()), maxLines = 1, overflow = TextOverflow.Ellipsis) },
              )
              marcaSel?.let { marca ->
                AssistChip(
                  onClick = { mudar(estado.copy(marca = null, modelo = null, ano = null)) },
                  shape = RoundedCornerShape(10.dp),
                  label = { Text(textoDoValor(marca), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
              }
              modeloSel?.let { modelo ->
                AssistChip(
                  onClick = { mudar(estado.copy(modelo = null, ano = null)) },
                  shape = RoundedCornerShape(10.dp),
                  label = { Text(textoDoValor(modelo), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
              }
              anoSel?.let { ano ->
                AssistChip(
                  onClick = { mudar(estado.copy(ano = null)) },
                  shape = RoundedCornerShape(10.dp),
                  label = { Text(textoDoValor(ano)) },
                )
              }
            }
          }
        }
      }

      when (passo) {
        0 -> ListaDeOpcoes(
          titulo = "Escolha o equipamento",
          descricao = "A ferramenta ou linha usada no serviço.",
          opcoes = equipamentos,
          vazio = "Nenhum equipamento cadastrado.",
          onEscolher = { escolhido ->
            mudar(estado.copy(equipamento = escolhido, marca = null, modelo = null, ano = null))
          },
          onNova = { onNova(baseNova()) },
        )

        1 -> ListaDeOpcoes(
          titulo = if (segmento == SegmentoAtendimento.RESIDENCIAL) "Escolha a marca" else "Escolha a montadora",
          descricao = if (segmento == SegmentoAtendimento.RESIDENCIAL) "Fabricante da fechadura ou central." else "Marca do veículo.",
          opcoes = marcas,
          vazio = "Nenhuma marca para este equipamento.",
          onEscolher = { escolhido ->
            mudar(estado.copy(marca = escolhido, modelo = null, ano = null))
          },
          onNova = { onNova(baseNova()) },
        )

        2 -> ListaDeOpcoes(
          titulo = "Escolha o modelo",
          descricao = if (segmento == SegmentoAtendimento.RESIDENCIAL) "Modelo ou linha." else "Modelo do veículo.",
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
              "Sistemas e referências cadastradas para este item.",
              "${doAno.size} FICHA(S)",
            )
          }
          items(doAno, key = { it.id }) { ficha ->
            CartaoFicha(ficha = ficha, onAbrir = { onAbrir(ficha.id) })
          }
          if (podeEditar) {
            item {
              Button(
                onClick = { onNova(baseNova()) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
              ) { Text("Adicionar outra ficha", fontWeight = FontWeight.Bold) }
            }
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
  val estendidas = MaterialTheme.coresEstendidas
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
  ) {
    item {
      Column(modifier = Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (opcoes.isEmpty()) {
      item {
        EstadoVazio(
          "Nada cadastrado",
          vazio,
          acao = { Button(onClick = onNova, shape = RoundedCornerShape(12.dp)) { Text("Adicionar ficha") } },
        )
      }
    }
    items(opcoes) { opcao ->
      val texto = textoDoValor(opcao)
      val inicial = texto.trim().take(2).uppercase()
      CartaoSurface(onClick = { onEscolher(opcao) }, raio = 16.dp) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = inicial,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Text(
            text = texto,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
          )
          Surface(shape = CircleShape, color = estendidas.superficieSutil) {
            Icon(
              Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(5.dp).size(18.dp),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoFicha(ficha: FichaTecnica, onAbrir: () -> Unit) {
  CartaoSurface(onClick = onAbrir, raio = 18.dp) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = listOf(ficha.marca, ficha.modelo, ficha.ano).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { ficha.sistema.ifBlank { "Ficha técnica" } },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        if (!ficha.confirmado) Selo("Não confirmado", tom = TomSelo.PENDENTE)
      }
      if (ficha.sistema.isNotBlank()) {
        Text(
          text = ficha.sistema,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      if (ficha.descricao.isNotBlank()) {
        Text(
          text = ficha.descricao,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Selo(
          texto = if (ficha.ehCatalogoOficial) "Catálogo padrão" else "Da empresa",
          tom = if (ficha.ehCatalogoOficial) TomSelo.PRIMARIO else TomSelo.RESIDENCIAL,
        )
        if (ficha.temManualOffline) Selo("Manual offline", tom = TomSelo.SUCESSO)
        if (ficha.chip.isNotBlank()) Selo("Chip ${ficha.chip}", tom = TomSelo.NEUTRO)
        if (ficha.equipamento.isNotBlank()) Selo(ficha.equipamento, tom = TomSelo.NEUTRO)
      }
    }
  }
}
