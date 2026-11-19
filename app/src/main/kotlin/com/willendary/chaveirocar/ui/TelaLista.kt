package com.willendary.chaveirocar.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.modelo.TipoServico
import com.willendary.chaveirocar.ui.theme.coresEstendidas
import com.willendary.chaveirocar.util.formatarData
import com.willendary.chaveirocar.util.formatarValor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

enum class Periodo(val rotulo: String) {
  TODOS("Sempre"),
  DIAS_30("30 dias"),
  DIAS_90("90 dias"),
  ESTE_ANO("Este ano"),
  PERSONALIZADO("Escolher período"),
}

/**
 * Filtros da lista. Ficam no App para continuarem iguais ao abrir um registro e voltar.
 */
data class FiltroLista(
  val busca: String = "",
  val segmento: SegmentoAtendimento? = null,
  val tipo: TipoServico? = null,
  val periodo: Periodo = Periodo.TODOS,
  val desdePersonalizado: Long? = null,
  val atePersonalizado: Long? = null,
  val somenteComFotos: Boolean = false,
) {
  val ativo: Boolean
    get() = busca.isNotBlank() || segmento != null || tipo != null || periodo != Periodo.TODOS || somenteComFotos

  val filtrosExtrasAtivos: Int
    get() = listOf(
      segmento != null,
      tipo != null,
      periodo != Periodo.TODOS,
      somenteComFotos,
    ).count { it }

  fun desde(agora: Long = System.currentTimeMillis()): Long? = when (periodo) {
    Periodo.TODOS -> null
    Periodo.DIAS_30 -> agora - 30L * 24 * 60 * 60 * 1000
    Periodo.DIAS_90 -> agora - 90L * 24 * 60 * 60 * 1000
    Periodo.ESTE_ANO -> Calendar.getInstance().apply {
      set(Calendar.DAY_OF_YEAR, 1)
      zerarHorario(this)
    }.timeInMillis
    Periodo.PERSONALIZADO -> desdePersonalizado
  }

  fun ate(): Long? = if (periodo == Periodo.PERSONALIZADO) atePersonalizado else null

  fun descricao(): String = listOfNotNull(
    segmento?.rotulo,
    tipo?.rotulo,
    if (periodo != Periodo.TODOS) periodo.rotulo else null,
    if (somenteComFotos) "com fotos" else null,
  ).joinToString(" · ")

  companion object {
    val Saver: Saver<FiltroLista, Any> = listSaver(
      save = {
        listOf(
          it.busca,
          it.tipo?.name.orEmpty(),
          it.periodo.name,
          it.desdePersonalizado ?: 0L,
          it.atePersonalizado ?: 0L,
          if (it.somenteComFotos) 1 else 0,
          it.segmento?.name.orEmpty(),
        )
      },
      restore = { valores ->
        FiltroLista(
          busca = valores[0] as String,
          tipo = (valores[1] as String).takeIf { nome -> nome.isNotBlank() }?.let { TipoServico.de(it) },
          periodo = Periodo.valueOf(valores[2] as String),
          desdePersonalizado = (valores[3] as Long).takeIf { it > 0 },
          atePersonalizado = (valores[4] as Long).takeIf { it > 0 },
          somenteComFotos = valores[5] as Int == 1,
          segmento = (valores.getOrNull(6) as? String)?.takeIf { it.isNotBlank() }?.let { SegmentoAtendimento.de(it) },
        )
      },
    )
  }
}

private fun zerarHorario(calendario: Calendar) {
  calendario.set(Calendar.HOUR_OF_DAY, 0)
  calendario.set(Calendar.MINUTE, 0)
  calendario.set(Calendar.SECOND, 0)
  calendario.set(Calendar.MILLISECOND, 0)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaLista(
  banco: BancoDeAtendimentos,
  filtro: FiltroLista,
  aoMudarFiltro: (FiltroLista) -> Unit,
  onAbrir: (Long) -> Unit,
  onNovo: () -> Unit,
  versaoSincronia: Int = 0,
  sincronizando: Boolean = false,
  modalidadeAtiva: SegmentoAtendimento? = null,
  onTrocarArea: (() -> Unit)? = null,
) {
  var registros by remember { mutableStateOf<List<Atendimento>>(emptyList()) }
  var carregando by remember { mutableStateOf(true) }
  var erroCarga by remember { mutableStateOf(false) }
  var tentativa by remember { mutableStateOf(0) }
  var filtrosAbertos by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(filtro, tentativa, versaoSincronia, modalidadeAtiva) {
    carregando = true
    erroCarga = false
    try {
      registros = withContext(Dispatchers.IO) {
        banco.listar(
          consulta = filtro.busca,
          tipo = filtro.tipo,
          desde = filtro.desde(),
          ate = filtro.ate(),
          somenteComFotos = filtro.somenteComFotos,
          segmento = modalidadeAtiva ?: filtro.segmento,
        )
      }
    } catch (cancelado: kotlinx.coroutines.CancellationException) {
      throw cancelado
    } catch (_: Exception) {
      erroCarga = true
    } finally {
      carregando = false
    }
  }

  val subtituloTopo = when (modalidadeAtiva) {
    SegmentoAtendimento.RESIDENCIAL -> "Atendimentos residenciais"
    SegmentoAtendimento.AUTOMOTIVO -> "Atendimentos veiculares"
    null -> "Sua oficina organizada"
  }

  Scaffold(
    topBar = { CabecalhoTela("ChaveiroCar", subtituloTopo, sincronizando = sincronizando) },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        modifier = Modifier.testTag("acao_novo_servico"),
        onClick = onNovo,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        text = { Text("Novo serviço", fontWeight = FontWeight.Bold) },
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
      )
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    // Tudo rola junto: filtros não comprimem a lista em telas baixas ou com teclado aberto.
    LazyColumn(
      modifier = Modifier.conteudoResponsivo().padding(espacamento),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 104.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (modalidadeAtiva != null && onTrocarArea != null) {
        item {
          BarraModalidadeAtiva(
            modalidade = modalidadeAtiva,
            onTrocarArea = onTrocarArea,
            modificador = Modifier.padding(horizontal = 0.dp, vertical = 0.dp),
          )
        }
      }

      // Barra de busca pill limpa direto no topo (foco em produtividade imediata)
      item {
        OutlinedTextField(
          value = filtro.busca,
          onValueChange = { aoMudarFiltro(filtro.copy(busca = it)) },
          modifier = Modifier.fillMaxWidth(),
          placeholder = {
            Text(
              text = if (modalidadeAtiva == SegmentoAtendimento.RESIDENCIAL)
                "Buscar local, porta, fechadura ou cliente…"
              else "Buscar placa, modelo, código ou cliente…",
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          leadingIcon = {
            Icon(
              Icons.Default.Search,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          },
          trailingIcon = {
            if (filtro.busca.isNotEmpty()) {
              IconButton(onClick = { aoMudarFiltro(filtro.copy(busca = "")) }) {
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
      }

      if (modalidadeAtiva == null) {
        item {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
              selected = filtro.segmento == null,
              onClick = { aoMudarFiltro(filtro.copy(segmento = null)) },
              shape = RoundedCornerShape(12.dp),
              label = { Text("Todos") },
            )
            SegmentoAtendimento.entries.forEach { seg ->
              FilterChip(
                selected = filtro.segmento == seg,
                onClick = {
                  val novoSegmento = if (filtro.segmento == seg) null else seg
                  val novoTipo = if (novoSegmento != null && filtro.tipo != null && filtro.tipo !in TipoServico.doSegmento(novoSegmento)) {
                    null
                  } else {
                    filtro.tipo
                  }
                  aoMudarFiltro(filtro.copy(segmento = novoSegmento, tipo = novoTipo))
                },
                shape = RoundedCornerShape(12.dp),
                label = { Text(seg.rotulo) },
              )
            }
          }
        }
      }

      // Cabeçalho da lista + gatilho elegante de filtros
      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Column(Modifier.weight(1f)) {
            Text(
              text = if (filtro.ativo) "Resultados" else "Atendimentos",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.ExtraBold,
            )
            Text(
              text = when {
                carregando -> "Buscando serviços…"
                erroCarga -> "Não foi possível carregar"
                else -> "${registros.size} serviço(s)" + if (filtro.descricao().isNotBlank()) " · ${filtro.descricao()}" else ""
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          val qtdFiltros = filtro.filtrosExtrasAtivos
          Surface(
            onClick = { filtrosAbertos = !filtrosAbertos },
            shape = RoundedCornerShape(12.dp),
            color = if (filtrosAbertos || qtdFiltros > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            border = BorderStroke(
              1.dp,
              if (filtrosAbertos || qtdFiltros > 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
              else MaterialTheme.colorScheme.outlineVariant,
            ),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Icon(
                IconesApp.Filtro,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (filtrosAbertos || qtdFiltros > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = if (filtrosAbertos) "Ocultar filtros" else "Filtros",
                style = MaterialTheme.typography.labelLarge,
                color = if (filtrosAbertos || qtdFiltros > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              )
              if (qtdFiltros > 0 && !filtrosAbertos) {
                Box(
                  modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                  contentAlignment = Alignment.Center,
                ) {
                  Text(
                    qtdFiltros.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                  )
                }
              }
              Icon(
                if (filtrosAbertos) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (filtrosAbertos || qtdFiltros > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }

      if (filtrosAbertos) {
        item {
          val tiposDisponiveis = (modalidadeAtiva ?: filtro.segmento)?.let { TipoServico.doSegmento(it) } ?: TipoServico.entries
          CartaoSurface(raio = 18.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  "TIPO DE SERVIÇO",
                  style = MaterialTheme.typography.labelSmall,
                  letterSpacing = 0.8.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  FilterChip(
                    selected = filtro.tipo == null,
                    onClick = { aoMudarFiltro(filtro.copy(tipo = null)) },
                    shape = RoundedCornerShape(10.dp),
                    label = { Text("Todos") },
                  )
                  tiposDisponiveis.forEach { tipo ->
                    FilterChip(
                      selected = filtro.tipo == tipo,
                      onClick = { aoMudarFiltro(filtro.copy(tipo = if (filtro.tipo == tipo) null else tipo)) },
                      shape = RoundedCornerShape(10.dp),
                      label = { Text(tipo.rotulo) },
                    )
                  }
                }
              }

              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                  "PERÍODO",
                  style = MaterialTheme.typography.labelSmall,
                  letterSpacing = 0.8.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  Periodo.entries.forEach { periodo ->
                    FilterChip(
                      selected = filtro.periodo == periodo,
                      onClick = { aoMudarFiltro(filtro.copy(periodo = periodo)) },
                      shape = RoundedCornerShape(10.dp),
                      label = { Text(periodo.rotulo) },
                    )
                  }
                }
              }

              if (filtro.periodo == Periodo.PERSONALIZADO) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  EscolherData("De", filtro.desdePersonalizado, Modifier.weight(1f)) {
                    aoMudarFiltro(filtro.copy(desdePersonalizado = it))
                  }
                  EscolherData("Até", filtro.atePersonalizado, Modifier.weight(1f)) { escolhido ->
                    val fim = Calendar.getInstance().apply {
                      timeInMillis = escolhido
                      set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                    aoMudarFiltro(filtro.copy(atePersonalizado = fim))
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
              ) {
                FilterChip(
                  selected = filtro.somenteComFotos,
                  onClick = { aoMudarFiltro(filtro.copy(somenteComFotos = !filtro.somenteComFotos)) },
                  shape = RoundedCornerShape(10.dp),
                  label = { Text("Só com fotos") },
                )
                if (filtro.ativo) {
                  TextButton(onClick = { aoMudarFiltro(FiltroLista()) }) {
                    Text("Limpar filtros", fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
        }
      }

      // Carrossel Acesso Rápido redesenhado com mini Placa Mercosul
      if (!carregando && !erroCarga && !filtro.ativo && registros.size > 1) {
        item {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                "Acesso rápido",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                "Últimos ${registros.take(5).size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              items(registros.take(5), key = { it.id }) { registro ->
                CartaoAcessoRapido(registro = registro, onAbrir = { onAbrir(registro.id) })
              }
            }
          }
        }
      }

      when {
        carregando -> item {
          Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
          }
        }
        erroCarga -> item {
          EstadoVazio("Não foi possível carregar", "Tente novamente para consultar seus atendimentos.") {
            OutlinedButton(onClick = { tentativa++ }, shape = RoundedCornerShape(12.dp)) {
              Text("Tentar novamente")
            }
          }
        }
        registros.isEmpty() -> item {
          EstadoVazio(
            if (filtro.ativo) "Nenhum resultado" else "Seu próximo serviço começa aqui",
            if (filtro.ativo) "Tente outra busca ou remova os filtros."
            else "Registre o primeiro atendimento e construa sua memória técnica de bancada.",
          ) {
            if (filtro.ativo) {
              OutlinedButton(onClick = { aoMudarFiltro(FiltroLista()) }, shape = RoundedCornerShape(12.dp)) {
                Text("Limpar filtros")
              }
            } else {
              Button(onClick = onNovo, shape = RoundedCornerShape(12.dp)) {
                Text("Registrar primeiro serviço")
              }
            }
          }
        }
        else -> items(registros, key = { it.id }) { registro ->
          CartaoRegistro(registro = registro, onAbrir = { onAbrir(registro.id) })
        }
      }
    }
  }
}

@Composable
private fun CartaoAcessoRapido(registro: Atendimento, onAbrir: () -> Unit) {
  val estendidas = MaterialTheme.coresEstendidas
  val residencial = registro.segmento == SegmentoAtendimento.RESIDENCIAL
  val subtitulo = if (residencial) {
    listOf(registro.ambientePorta, registro.marcaFechadura, registro.modeloFechadura)
      .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Chaveiro residencial" }
  } else {
    listOf(registro.marca, registro.modelo).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Sem modelo" }
  }

  Card(
    onClick = onAbrir,
    modifier = Modifier.width(184.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (!residencial && registro.placa.isNotBlank()) {
        PlacaMercosulBadge(
          placa = registro.placa,
          compacto = true,
          modificador = Modifier.fillMaxWidth(),
        )
      } else {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Box(
            modifier = Modifier
              .size(28.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(if (residencial) estendidas.residencialFundo else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (residencial) Icons.Default.Home else IconesApp.Carro,
              contentDescription = null,
              tint = if (residencial) estendidas.residencial else MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp),
            )
          }
          Text(
            text = if (residencial) registro.tipoLocal.ifBlank { "Residencial" } else registro.modelo.ifBlank { "Sem placa" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }

      Text(
        text = subtitulo,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = formatarData(registro.dataServico),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (registro.pago) estendidas.sucesso else estendidas.alerta),
        )
      }
    }
  }
}

@Composable
private fun EscolherData(
  rotulo: String,
  valor: Long?,
  modificador: Modifier = Modifier,
  aoEscolher: (Long) -> Unit,
) {
  val contexto = LocalContext.current
  OutlinedButton(
    onClick = {
      val calendario = Calendar.getInstance().apply { timeInMillis = valor ?: System.currentTimeMillis() }
      DatePickerDialog(
        contexto,
        { _, ano, mes, dia ->
          val escolhido = Calendar.getInstance().apply {
            timeInMillis = valor ?: System.currentTimeMillis()
            set(ano, mes, dia)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
          }
          aoEscolher(escolhido.timeInMillis)
        },
        calendario.get(Calendar.YEAR),
        calendario.get(Calendar.MONTH),
        calendario.get(Calendar.DAY_OF_MONTH),
      ).show()
    },
    shape = RoundedCornerShape(12.dp),
    modifier = modificador,
  ) {
    Text(if (valor == null) rotulo else "$rotulo ${formatarData(valor)}", maxLines = 1, fontSize = 12.sp)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoRegistro(registro: Atendimento, onAbrir: () -> Unit) {
  val estendidas = MaterialTheme.coresEstendidas
  val residencial = registro.segmento == SegmentoAtendimento.RESIDENCIAL
  val temPlaca = !residencial && registro.placa.isNotBlank()

  val tituloPrincipal = if (residencial) {
    listOf(registro.tipoLocal, registro.ambientePorta)
      .filter { it.isNotBlank() }
      .joinToString(" · ")
      .ifBlank { "Serviço residencial" }
  } else {
    listOf(registro.marca, registro.modelo, registro.ano)
      .filter { it.isNotBlank() }
      .joinToString(" ")
      .ifBlank { "Veículo sem modelo" }
  }

  val subtituloExtra = if (residencial) {
    listOf(registro.marcaFechadura, registro.modeloFechadura, registro.tipoCilindro)
      .filter { it.isNotBlank() }
      .joinToString(" · ")
      .ifBlank { registro.enderecoLocal.ifBlank { "Chaveiro residencial" } }
  } else {
    listOfNotNull(
      registro.clienteNome.takeIf { it.isNotBlank() },
      if (!temPlaca) "Sem placa" else null,
      registro.codigoChave.takeIf { it.isNotBlank() }?.let { "Chave $it" },
    ).joinToString(" · ")
  }

  CartaoSurface(onClick = onAbrir, raio = 18.dp) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      // Cabeçalho do ticket: Placa Mercosul real (ou ícone Residencial) + Modelo + Chevron
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (temPlaca) {
          PlacaMercosulBadge(
            placa = registro.placa,
            compacto = true,
            modificador = Modifier.width(114.dp),
          )
        } else {
          Box(
            modifier = Modifier
              .size(46.dp)
              .clip(RoundedCornerShape(13.dp))
              .background(if (residencial) estendidas.residencialFundo else MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = if (residencial) Icons.Default.Home else IconesApp.Carro,
              contentDescription = null,
              tint = if (residencial) estendidas.residencial else MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp),
            )
          }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = tituloPrincipal,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (subtituloExtra.isNotBlank()) {
            Text(
              text = subtituloExtra,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Surface(
          shape = CircleShape,
          color = estendidas.superficieSutil,
        ) {
          Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(6.dp).size(18.dp),
          )
        }
      }

      // Tags limpas de serviço + status de pagamento
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Selo(
          texto = registro.segmento.rotulo,
          tom = if (residencial) TomSelo.RESIDENCIAL else TomSelo.PRIMARIO,
        )
        registro.tiposOrdenados.forEach { tipo ->
          Selo(texto = tipo.rotulo, tom = TomSelo.NEUTRO)
        }
        if (!registro.pago) {
          Selo(texto = "Pagamento pendente", tom = TomSelo.PENDENTE)
        }
      }

      // Prévia técnica em faixa sutil
      val resumo = when {
        TipoServico.ABERTURA in registro.tiposServico && registro.abertura.isNotBlank() -> registro.abertura
        registro.defeito.isNotBlank() -> registro.defeito
        else -> registro.solucao
      }
      if (resumo.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = estendidas.superficieSutil,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = resumo,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
          )
        }
      }

      // Rodapé limpo: data + fotos | valor em destaque
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = formatarData(registro.dataServico),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (registro.quantidadeFotos > 0) {
            Selo(texto = "${registro.quantidadeFotos} foto(s)", tom = TomSelo.NEUTRO)
          }
        }
        if (registro.valorCentavos > 0) {
          Text(
            text = formatarValor(registro.valorCentavos),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = if (registro.pago) estendidas.sucesso else estendidas.alerta,
          )
        }
      }
    }
  }
}
