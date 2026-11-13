package com.willendary.chaveirocar.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.modelo.TipoServico
import com.willendary.chaveirocar.util.formatarData
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
) {
  var registros by remember { mutableStateOf<List<Atendimento>>(emptyList()) }
  var carregando by remember { mutableStateOf(true) }
  var erroCarga by remember { mutableStateOf(false) }
  var tentativa by remember { mutableStateOf(0) }
  var filtrosAbertos by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(filtro, tentativa) {
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
          segmento = filtro.segmento,
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

  Scaffold(
    topBar = { CabecalhoTela("ChaveiroCar", "Seu parceiro de bancada") },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        modifier = Modifier.testTag("acao_novo_servico"),
        onClick = onNovo, containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        text = { Text("Novo serviço") }, icon = { Icon(Icons.Default.Add, contentDescription = null) },
      )
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    // Tudo rola junto: filtros não comprimem a lista em telas baixas ou com teclado aberto.
    LazyColumn(
      modifier = Modifier.conteudoResponsivo().padding(espacamento),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 100.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        PainelDestaque("Cada serviço,\nsempre à mão.", "Seu histórico, códigos e soluções em um só lugar.", "SUA OFICINA ORGANIZADA")
      }
      item {
        OutlinedTextField(
          value = filtro.busca, onValueChange = { aoMudarFiltro(filtro.copy(busca = it)) },
          modifier = Modifier.fillMaxWidth(),
          placeholder = { Text("Buscar placa, modelo, local, fechadura…") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          trailingIcon = {
            if (filtro.busca.isNotEmpty()) IconButton(onClick = { aoMudarFiltro(filtro.copy(busca = "")) }) {
              Icon(Icons.Default.Close, contentDescription = "Limpar busca")
            }
          },
          singleLine = true, shape = MaterialTheme.shapes.medium,
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
          ),
        )
      }
      item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = filtro.segmento == null,
            onClick = { aoMudarFiltro(filtro.copy(segmento = null)) },
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
              label = { Text(seg.rotulo) },
            )
          }
        }
      }
      item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(if (filtro.ativo) "Resultados" else "Atendimentos", style = MaterialTheme.typography.titleLarge)
            Text(if (carregando) "Buscando serviços…" else if (erroCarga) "Não foi possível carregar"
              else "${registros.size} serviço(s)" + if (filtro.descricao().isNotBlank()) " · ${filtro.descricao()}" else "",
              style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          TextButton(onClick = { filtrosAbertos = !filtrosAbertos }) {
            Text(if (filtrosAbertos) "Ocultar filtros" else "Filtros")
            Icon(if (filtrosAbertos) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null)
          }
        }
      }
      if (filtrosAbertos) item {
        val tiposDisponiveis = filtro.segmento?.let { TipoServico.doSegmento(it) } ?: TipoServico.entries
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tipo de serviço", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              FilterChip(selected = filtro.tipo == null, onClick = { aoMudarFiltro(filtro.copy(tipo = null)) }, label = { Text("Todos") })
              tiposDisponiveis.forEach { tipo ->
                FilterChip(selected = filtro.tipo == tipo,
                  onClick = { aoMudarFiltro(filtro.copy(tipo = if (filtro.tipo == tipo) null else tipo)) },
                  label = { Text(tipo.rotulo) })
              }
            }
            Text("Período", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Periodo.entries.forEach { periodo ->
                FilterChip(selected = filtro.periodo == periodo, onClick = { aoMudarFiltro(filtro.copy(periodo = periodo)) }, label = { Text(periodo.rotulo) })
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(selected = filtro.somenteComFotos,
                onClick = { aoMudarFiltro(filtro.copy(somenteComFotos = !filtro.somenteComFotos)) }, label = { Text("Só com fotos") })
              if (filtro.ativo) TextButton(onClick = { aoMudarFiltro(FiltroLista()) }) { Text("Limpar filtros") }
            }
          }
        }
      }
      if (!carregando && !erroCarga && !filtro.ativo && registros.size > 1) item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Acesso rápido", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(registros.take(5), key = { it.id }) { registro ->
              val tituloRapido = if (registro.segmento == SegmentoAtendimento.RESIDENCIAL) {
                registro.tipoLocal.ifBlank { registro.ambientePorta.ifBlank { "Residencial" } }
              } else {
                registro.placa.ifBlank { registro.modelo.ifBlank { "Sem placa" } }
              }
              val subtituloRapido = if (registro.segmento == SegmentoAtendimento.RESIDENCIAL) {
                listOf(registro.ambientePorta, registro.marcaFechadura, registro.modeloFechadura)
                  .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Chaveiro residencial" }
              } else {
                listOf(registro.marca, registro.modelo).filter { it.isNotBlank() }.joinToString(" ")
              }
              Card(onClick = { onAbrir(registro.id) }, modifier = Modifier.width(188.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(tituloRapido, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(subtituloRapido,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(formatarData(registro.dataServico), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }
      }
      when {
        carregando -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        erroCarga -> item { EstadoVazio("Não foi possível carregar", "Tente novamente para consultar seus atendimentos.") {
          OutlinedButton(onClick = { tentativa++ }) { Text("Tentar novamente") }
        } }
        registros.isEmpty() -> item {
          EstadoVazio(if (filtro.ativo) "Nenhum resultado" else "Seu próximo serviço começa aqui",
            if (filtro.ativo) "Tente outra busca ou remova os filtros." else "Registre o primeiro serviço e construa sua memória de oficina.") {
            if (filtro.ativo) OutlinedButton(onClick = { aoMudarFiltro(FiltroLista()) }) { Text("Limpar filtros") }
          }
        }
        else -> items(registros, key = { it.id }) { registro ->
          CartaoRegistro(registro, onAbrir = { onAbrir(registro.id) })
        }
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
    modifier = modificador,
  ) {
    Text(if (valor == null) rotulo else "$rotulo ${formatarData(valor)}", maxLines = 1, fontSize = 12.sp)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoRegistro(registro: Atendimento, onAbrir: () -> Unit) {
  val residencial = registro.segmento == SegmentoAtendimento.RESIDENCIAL
  val titulo = if (residencial) {
    listOf(registro.tipoLocal, registro.ambientePorta)
      .filter { it.isNotBlank() }
      .joinToString(" · ")
      .ifBlank { "Serviço residencial" }
  } else {
    registro.placa.ifBlank {
      listOf(registro.marca, registro.modelo).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Sem placa" }
    }
  }
  val subtitulo = if (residencial) {
    listOf(registro.marcaFechadura, registro.modeloFechadura, registro.tipoCilindro)
      .filter { it.isNotBlank() }
      .joinToString(" · ")
      .ifBlank { registro.enderecoLocal.ifBlank { "Chaveiro residencial" } }
  } else if (registro.placa.isNotBlank()) {
    listOf(registro.marca, registro.modelo, registro.ano)
      .filter { it.isNotBlank() }
      .joinToString(" ")
      .ifBlank { "Veículo sem modelo" }
  } else {
    listOf(registro.ano, "Sem placa")
      .filter { it.isNotBlank() }
      .joinToString(" · ")
  }

  Card(
    onClick = onAbrir, modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(titulo, fontFamily = if (residencial || registro.placa.isBlank()) FontFamily.Default else FontFamily.Monospace,
            fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
          Text(subtitulo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      }
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Selo(registro.segmento.rotulo)
        registro.tiposOrdenados.forEach { Selo(it.rotulo) }
        if (!registro.pago) Selo("Pagamento pendente", pendente = true)
      }
      val resumo = when {
        TipoServico.ABERTURA in registro.tiposServico && registro.abertura.isNotBlank() -> registro.abertura
        registro.defeito.isNotBlank() -> registro.defeito
        else -> registro.solucao
      }
      if (resumo.isNotBlank()) Text(resumo, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(formatarData(registro.dataServico), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (registro.quantidadeFotos > 0) Text("${registro.quantidadeFotos} foto(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (registro.valorCentavos > 0) Text(com.willendary.chaveirocar.util.formatarValor(registro.valorCentavos),
          style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
      }
    }
  }
}
