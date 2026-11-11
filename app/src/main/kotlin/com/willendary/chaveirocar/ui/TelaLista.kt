package com.willendary.chaveirocar.ui

import android.app.DatePickerDialog
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
import com.willendary.chaveirocar.atualizacao.AtualizacaoDisponivel
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.BackupAutomatico
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.TipoServico
import com.willendary.chaveirocar.util.diasDesde
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
  val tipo: TipoServico? = null,
  val periodo: Periodo = Periodo.TODOS,
  val desdePersonalizado: Long? = null,
  val atePersonalizado: Long? = null,
  val somenteComFotos: Boolean = false,
) {
  val ativo: Boolean
    get() = busca.isNotBlank() || tipo != null || periodo != Periodo.TODOS || somenteComFotos

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaLista(
  banco: BancoDeAtendimentos,
  filtro: FiltroLista,
  aoMudarFiltro: (FiltroLista) -> Unit,
  onAbrir: (Long) -> Unit,
  onNovo: () -> Unit,
  onIrParaAjustes: () -> Unit,
  atualizacao: AtualizacaoDisponivel? = null,
  baixandoAtualizacao: Boolean = false,
  onAtualizarApp: () -> Unit = {},
) {
  val contexto = LocalContext.current
  var registros by remember { mutableStateOf<List<Atendimento>>(emptyList()) }
  var ultimoBackup by remember { mutableStateOf(0L) }

  LaunchedEffect(filtro) {
    registros = withContext(Dispatchers.IO) {
      banco.listar(
        consulta = filtro.busca,
        tipo = filtro.tipo,
        desde = filtro.desde(),
        ate = filtro.ate(),
        somenteComFotos = filtro.somenteComFotos,
      )
    }
  }

  LaunchedEffect(Unit) {
    val manual = Preferencias.obter(contexto).getLong(Preferencias.ULTIMO_BACKUP, 0)
    val automatico = BackupAutomatico.ultimoArquivo(contexto)?.lastModified() ?: 0L
    ultimoBackup = maxOf(manual, automatico)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("ChaveiroCar", style = MaterialTheme.typography.titleLarge)
            Text(
              text = if (registros.isEmpty()) "Seu histórico de serviços"
              else "${registros.size} registro(s)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background,
        ),
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = onNovo,
        text = { Text("Novo serviço", fontWeight = FontWeight.SemiBold) },
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
      )
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    Column(modifier = Modifier.fillMaxSize().padding(espacamento)) {
      OutlinedTextField(
        value = filtro.busca,
        onValueChange = { aoMudarFiltro(filtro.copy(busca = it)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = { Text("Placa, modelo, defeito, código...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
      )

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      ) {
        item {
          FilterChip(
            selected = filtro.tipo == null,
            onClick = { aoMudarFiltro(filtro.copy(tipo = null)) },
            label = { Text("Todos os tipos") },
          )
        }
        items(TipoServico.entries.toList()) { tipo ->
          FilterChip(
            selected = filtro.tipo == tipo,
            onClick = {
              aoMudarFiltro(filtro.copy(tipo = if (filtro.tipo == tipo) null else tipo))
            },
            label = { Text(tipo.rotulo) },
          )
        }
      }

      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
      ) {
        items(Periodo.entries.toList()) { periodo ->
          FilterChip(
            selected = filtro.periodo == periodo,
            onClick = {
              if (periodo == Periodo.PERSONALIZADO && filtro.desdePersonalizado == null) {
                aoMudarFiltro(filtro.copy(periodo = periodo, desdePersonalizado = null, atePersonalizado = null))
              } else {
                aoMudarFiltro(filtro.copy(periodo = periodo))
              }
            },
            label = { Text(periodo.rotulo) },
          )
        }
        item {
          FilterChip(
            selected = filtro.somenteComFotos,
            onClick = { aoMudarFiltro(filtro.copy(somenteComFotos = !filtro.somenteComFotos)) },
            label = { Text("Só com fotos") },
          )
        }
        if (filtro.ativo) {
          item {
            TextButton(onClick = { aoMudarFiltro(FiltroLista()) }) { Text("Limpar") }
          }
        }
      }

      if (filtro.periodo == Periodo.PERSONALIZADO) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          EscolherData(
            rotulo = "De",
            valor = filtro.desdePersonalizado,
            modificador = Modifier.weight(1f),
          ) { escolhido -> aoMudarFiltro(filtro.copy(desdePersonalizado = escolhido)) }
          EscolherData(
            rotulo = "Até",
            valor = filtro.atePersonalizado,
            modificador = Modifier.weight(1f),
          ) { escolhido ->
            val fimDoDia = (Calendar.getInstance().apply { timeInMillis = escolhido }).apply {
              set(Calendar.HOUR_OF_DAY, 23)
              set(Calendar.MINUTE, 59)
              set(Calendar.SECOND, 59)
            }.timeInMillis
            aoMudarFiltro(filtro.copy(atePersonalizado = fimDoDia))
          }
        }
      }

      if (atualizacao != null) {
        Card(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Versão ${atualizacao.versao} disponível", style = MaterialTheme.typography.titleSmall)
              Text(
                text = if (baixandoAtualizacao) "Baixando o APK..." else "Toque em atualizar para baixar.",
                style = MaterialTheme.typography.bodySmall,
              )
            }
            TextButton(onClick = onAtualizarApp, enabled = !baixandoAtualizacao) { Text("Atualizar") }
          }
        }
      }

      val diasDoBackup = diasDesde(ultimoBackup)
      if (registros.isNotEmpty() && diasDoBackup > 7) {
        Card(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = if (ultimoBackup == 0L) "Nenhum backup feito ainda. Se trocar de celular perde tudo."
              else "Último backup há $diasDoBackup dias.",
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onIrParaAjustes) { Text("Resolver") }
          }
        }
      }

      if (registros.isNotEmpty() || filtro.ativo) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "${registros.size} registro(s)" +
              if (filtro.descricao().isNotBlank()) " · ${filtro.descricao()}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
          )
        }
      }

      if (registros.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = if (filtro.ativo) "Nada encontrado" else "Nenhum carro ainda",
              style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
              text = if (filtro.ativo) "Tente outra placa, modelo ou limpe os filtros."
              else "Toque em \"Novo serviço\" para registrar o primeiro carro.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filtro.ativo) {
              Spacer(Modifier.height(12.dp))
              OutlinedButton(onClick = { aoMudarFiltro(FiltroLista()) }) { Text("Limpar filtros") }
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        ) {
          items(registros, key = { it.id }) { registro ->
            CartaoRegistro(registro = registro, onAbrir = { onAbrir(registro.id) })
          }
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

@Composable
private fun CartaoRegistro(registro: Atendimento, onAbrir: () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onAbrir),
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = registro.placa,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 22.sp,
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
          text = listOf(registro.marca, registro.modelo).filter { it.isNotBlank() }.joinToString(" "),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Spacer(Modifier.height(4.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Selo(registro.tipoServico.rotulo)
        Spacer(Modifier.width(8.dp))
        Text(
          text = formatarData(registro.dataServico),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (registro.quantidadeFotos > 0) {
          Spacer(Modifier.width(8.dp))
          Text(
            text = "${registro.quantidadeFotos} foto(s)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      val resumo = when {
        registro.tipoServico == TipoServico.ABERTURA && registro.abertura.isNotBlank() -> registro.abertura
        registro.defeito.isNotBlank() -> registro.defeito
        registro.solucao.isNotBlank() -> registro.solucao
        else -> ""
      }
      if (resumo.isNotBlank()) {
        Spacer(Modifier.height(8.dp))
        Text(
          text = resumo,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
