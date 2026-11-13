package com.willendary.chaveirocar.ui

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Build
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.FilterChip
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.CategoriaFoto
import com.willendary.chaveirocar.modelo.Foto
import com.willendary.chaveirocar.modelo.ResumoModelo
import com.willendary.chaveirocar.modelo.ResumoResidencial
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.util.formatarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fichário: o chaveiro pensa por carro ou por fechadura/local residencial, não por serviço avulso.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaFichario(
  banco: BancoDeAtendimentos,
  onAbrirModelo: (ResumoModelo) -> Unit,
  onAbrirResidencial: (ResumoResidencial) -> Unit = {},
) {
  val contexto = LocalContext.current
  var busca by remember { mutableStateOf("") }
  var segmento by remember { mutableStateOf(SegmentoAtendimento.AUTOMOTIVO) }
  var modelos by remember { mutableStateOf<List<ResumoModelo>>(emptyList()) }
  var residenciais by remember { mutableStateOf<List<ResumoResidencial>>(emptyList()) }
  var carregado by remember { mutableStateOf(false) }
  val preferencias = remember { Preferencias.obter(contexto) }
  var favoritos by remember {
    mutableStateOf(preferencias.getStringSet(Preferencias.MODELOS_FAVORITOS, emptySet()) ?: emptySet())
  }

  fun chave(modelo: ResumoModelo): String =
    "${modelo.marca.trim().lowercase()}|${modelo.modelo.trim().lowercase()}|${modelo.ano.trim()}"

  fun alternarFavorito(modelo: ResumoModelo) {
    val c = chave(modelo)
    val novo = if (c in favoritos) favoritos - c else favoritos + c
    favoritos = novo
    preferencias.edit().putStringSet(Preferencias.MODELOS_FAVORITOS, novo).apply()
  }

  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      modelos = banco.resumoPorModelo()
      residenciais = banco.resumoResidencial()
    }
    carregado = true
  }

  val visiveis = remember(busca, modelos, favoritos) {
    val termo = busca.trim()
    val filtrados = if (termo.isBlank()) modelos
    else modelos.filter { modelo ->
      "${modelo.marca} ${modelo.modelo} ${modelo.ano}".contains(termo, ignoreCase = true)
    }
    if (termo.isNotBlank() || favoritos.isEmpty()) filtrados
    else filtrados.sortedByDescending { chave(it) in favoritos }
  }

  val residenciaisVisiveis = remember(busca, residenciais) {
    val termo = busca.trim()
    if (termo.isBlank()) residenciais
    else residenciais.filter { item ->
      "${item.tipoLocal} ${item.marcaFechadura} ${item.modeloFechadura}"
        .contains(termo, ignoreCase = true)
    }
  }

  Scaffold(
    topBar = {
      CabecalhoTela("Fichário", "Sua biblioteca de soluções")
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    Column(modifier = Modifier.conteudoResponsivo().padding(espacamento)) {
      OutlinedTextField(
        value = busca,
        onValueChange = { busca = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        placeholder = {
          Text(if (segmento == SegmentoAtendimento.RESIDENCIAL) "Buscar local, porta ou fechadura" else "Buscar marca ou modelo")
        },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
          unfocusedContainerColor = MaterialTheme.colorScheme.surface,
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = segmento == SegmentoAtendimento.AUTOMOTIVO,
          onClick = { segmento = SegmentoAtendimento.AUTOMOTIVO },
          label = { Text("Veículos (${modelos.size})") },
        )
        FilterChip(
          selected = segmento == SegmentoAtendimento.RESIDENCIAL,
          onClick = { segmento = SegmentoAtendimento.RESIDENCIAL },
          label = { Text("Residencial (${residenciais.size})") },
        )
      }

      when {
        !carregado -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }

        segmento == SegmentoAtendimento.RESIDENCIAL && residenciaisVisiveis.isEmpty() -> Box(
          Modifier.fillMaxSize().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          EstadoVazio(
            if (busca.isBlank()) "Fichário residencial vazio" else "Nenhuma fechadura ou local encontrado",
            if (busca.isBlank()) "Os serviços de chaveiro residencial aparecem organizados aqui por tipo de local e fechadura."
            else "Tente buscar por outra marca de fechadura ou ambiente.",
          )
        }

        segmento == SegmentoAtendimento.RESIDENCIAL -> LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
          item {
            PainelDestaque(
              "Memória residencial.",
              "Aberturas, cilindros e procedimentos organizados por local e fechadura.",
              "${residenciais.size} ITEM(NS) RESIDENCIAIS",
            )
          }
          item { TituloSecao("Locais e fechaduras") }
          items(residenciaisVisiveis) { item ->
            CartaoResidencial(item = item, onAbrir = { onAbrirResidencial(item) })
          }
        }

        visiveis.isEmpty() -> Box(
          Modifier.fillMaxSize().padding(32.dp),
          contentAlignment = Alignment.Center,
        ) {
          EstadoVazio(if (busca.isBlank()) "Sua biblioteca começa aqui" else "Nenhum modelo encontrado",
            if (busca.isBlank()) "Os veículos e procedimentos aparecem aqui depois do primeiro serviço."
            else "Tente outra marca ou modelo.")
        }

        else -> LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
          item {
            PainelDestaque("Conhecimento que fica.", "Aberturas, códigos e procedimentos organizados por veículo.", "${modelos.size} MODELO(S) NA BIBLIOTECA")
          }
          item { TituloSecao(if (favoritos.isEmpty()) "Seus veículos" else "Seus veículos · favoritos primeiro") }
          items(visiveis) { modelo ->
            CartaoModelo(
              modelo = modelo,
              onAbrir = { onAbrirModelo(modelo) },
              fixado = chave(modelo) in favoritos,
              onFixar = { alternarFavorito(modelo) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CartaoResidencial(item: ResumoResidencial, onAbrir: () -> Unit) {
  val titulo = item.tipoLocal.ifBlank { "Serviço residencial" }
  val fechadura = listOf(item.marcaFechadura, item.modeloFechadura).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Fechadura não informada" }
  Card(
    onClick = onAbrir,
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(titulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
      Text(fechadura, style = MaterialTheme.typography.titleLarge)
      Text(
        "${item.quantidade} serviço(s) · último em ${formatarData(item.ultimaData)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        if (item.temProcedimento) "Procedimentos disponíveis" else "Adicione técnicas de abertura ou fotos no próximo serviço",
        style = MaterialTheme.typography.bodySmall,
        color = if (item.temProcedimento) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun CartaoModelo(modelo: ResumoModelo, onAbrir: () -> Unit, fixado: Boolean = false, onFixar: () -> Unit = {}) {
  Card(onClick = onAbrir, modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
          Text(modelo.marca.ifBlank { "Veículo" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
          Text(modelo.modelo.ifBlank { "Modelo não informado" }, style = MaterialTheme.typography.titleLarge)
        }
        IconButton(onClick = onFixar) {
          Icon(Icons.Default.Star, if (fixado) "Desafixar modelo" else "Fixar modelo",
            tint = if (fixado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
      }
      if (modelo.ano.isNotBlank()) Selo(modelo.ano)
      Text("${modelo.quantidade} serviço(s) · último em ${formatarData(modelo.ultimaData)}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(if (modelo.temProcedimento) "Procedimentos disponíveis" else "Adicione um procedimento no próximo serviço",
        style = MaterialTheme.typography.bodySmall,
        color = if (modelo.temProcedimento) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaFicharioModelo(
  banco: BancoDeAtendimentos,
  marca: String,
  modelo: String,
  ano: String,
  onAbrirServico: (Long) -> Unit,
  onVoltar: () -> Unit,
) {
  val contexto = LocalContext.current
  var servicos by remember { mutableStateOf<List<Atendimento>>(emptyList()) }
  var procedimentos by remember { mutableStateOf<List<Foto>>(emptyList()) }
  var carregado by remember { mutableStateOf(false) }
  var fotoAberta by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(marca, modelo, ano) {
    withContext(Dispatchers.IO) {
      val encontrados = banco.servicosDoVeiculo(marca, modelo, ano)
      servicos = encontrados
      procedimentos = encontrados.flatMap { servico ->
        banco.listarFotos(servico.id).filter { it.categoria == CategoriaFoto.PROCEDIMENTO }
      }
    }
    carregado = true
  }

  val abertura = servicos.firstOrNull { it.abertura.isNotBlank() }?.abertura
  val codigoChave = servicos.firstOrNull { it.codigoChave.isNotBlank() }?.codigoChave
  val codigoConfiguracao = servicos.firstOrNull { it.codigoConfiguracao.isNotBlank() }?.codigoConfiguracao

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              listOf(marca, modelo, ano).filter { it.isNotBlank() }.joinToString(" "),
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${servicos.size} serviço(s)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = { BotaoVoltar(onVoltar) },
      )
    },
  ) { espacamento ->
    if (!carregado) {
      Box(Modifier.fillMaxSize().padding(espacamento), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      return@Scaffold
    }

    Column(
      modifier = Modifier
        .conteudoResponsivo()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (abertura.isNullOrBlank() && codigoChave.isNullOrBlank() && codigoConfiguracao.isNullOrBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp)) {
            Text("Sem procedimento registrado", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "Na próxima vez, preencha a abertura ou os códigos no serviço.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (!abertura.isNullOrBlank()) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
          ),
        ) {
          Column(Modifier.padding(14.dp)) {
            Text("Abertura", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(abertura, style = MaterialTheme.typography.bodyLarge)
          }
        }
      }

      if (!codigoChave.isNullOrBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Código de chave", style = MaterialTheme.typography.labelMedium)
              Text(
                codigoChave,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
              )
            }
            TextButton(onClick = { copiarTexto(contexto, "Código de chave", codigoChave) }) {
              Text("Copiar")
            }
          }
        }
      }

      if (!codigoConfiguracao.isNullOrBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Código de configuração", style = MaterialTheme.typography.labelMedium)
              Text(
                codigoConfiguracao,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
              )
            }
            TextButton(
              onClick = { copiarTexto(contexto, "Código de configuração", codigoConfiguracao) },
            ) { Text("Copiar") }
          }
        }
      }

      if (procedimentos.isNotEmpty()) {
        TituloSecao("Como se faz")
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          procedimentos.forEach { foto ->
            Column(modifier = Modifier.width(140.dp)) {
              ImagemLocal(
                caminho = foto.caminho,
                ladoMaximo = 800,
                modificador = Modifier
                  .width(140.dp)
                  .height(140.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { fotoAberta = foto.caminho },
              )
              if (foto.legenda.isNotBlank()) {
                Text(
                  text = foto.legenda,
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      TituloSecao("Serviços deste carro")
      servicos.forEach { servico ->
        Card(
          modifier = Modifier.fillMaxWidth().clickable { onAbrirServico(servico.id) },
        ) {
          Column(Modifier.padding(14.dp)) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              servico.tiposOrdenados.forEach { Selo(it.rotulo) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = formatarData(servico.dataServico),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (servico.quantidadeFotos > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                  text = "${servico.quantidadeFotos} foto(s)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            val resumo = servico.solucao.ifBlank { servico.defeito }.ifBlank { servico.abertura }
            if (resumo.isNotBlank()) {
              Spacer(Modifier.height(4.dp))
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
      Spacer(Modifier.height(24.dp))
    }
  }

  val foto = fotoAberta
  if (foto != null) {
    Dialog(onDismissRequest = { fotoAberta = null }) {
      Card {
        Column {
          ImagemLocal(
            caminho = foto,
            ladoMaximo = 2000,
            modificador = Modifier
              .fillMaxWidth()
              .heightIn(max = 520.dp)
              .clickable { fotoAberta = null },
          )
          TextButton(onClick = { fotoAberta = null }, modifier = Modifier.fillMaxWidth()) {
            Text("Fechar")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaFicharioResidencial(
  banco: BancoDeAtendimentos,
  tipoLocal: String,
  marcaFechadura: String,
  modeloFechadura: String,
  onAbrirServico: (Long) -> Unit,
  onVoltar: () -> Unit,
) {
  val contexto = LocalContext.current
  var servicos by remember { mutableStateOf<List<Atendimento>>(emptyList()) }
  var procedimentos by remember { mutableStateOf<List<Foto>>(emptyList()) }
  var carregado by remember { mutableStateOf(false) }
  var fotoAberta by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(tipoLocal, marcaFechadura, modeloFechadura) {
    withContext(Dispatchers.IO) {
      val encontrados = banco.servicosResidenciais(tipoLocal, marcaFechadura, modeloFechadura)
      servicos = encontrados
      procedimentos = encontrados.flatMap { servico ->
        banco.listarFotos(servico.id).filter { it.categoria == CategoriaFoto.PROCEDIMENTO }
      }
    }
    carregado = true
  }

  val abertura = servicos.firstOrNull { it.abertura.isNotBlank() }?.abertura
  val codigoChave = servicos.firstOrNull { it.codigoChave.isNotBlank() }?.codigoChave
  val codigoConfiguracao = servicos.firstOrNull { it.codigoConfiguracao.isNotBlank() }?.codigoConfiguracao

  val titulo = listOf(tipoLocal, marcaFechadura, modeloFechadura)
    .filter { it.isNotBlank() }
    .joinToString(" · ")
    .ifBlank { "Serviços residenciais" }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text(
              titulo,
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${servicos.size} serviço(s)",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        navigationIcon = { BotaoVoltar(onVoltar) },
      )
    },
  ) { espacamento ->
    if (!carregado) {
      Box(Modifier.fillMaxSize().padding(espacamento), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      return@Scaffold
    }

    Column(
      modifier = Modifier
        .conteudoResponsivo()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (abertura.isNullOrBlank() && codigoChave.isNullOrBlank() && codigoConfiguracao.isNullOrBlank() && procedimentos.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp)) {
            Text("Sem procedimento registrado", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "Na próxima vez, preencha a técnica de abertura, códigos ou fotos de passo a passo.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (!abertura.isNullOrBlank()) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
          Column(Modifier.padding(14.dp)) {
            Text("Técnica de abertura / observações", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(abertura, style = MaterialTheme.typography.bodyLarge)
          }
        }
      }

      if (!codigoChave.isNullOrBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Código de chave", style = MaterialTheme.typography.labelMedium)
              Text(codigoChave, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            TextButton(onClick = { copiarTexto(contexto, "Código de chave", codigoChave) }) {
              Text("Copiar")
            }
          }
        }
      }

      if (!codigoConfiguracao.isNullOrBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
          Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Senha / configuração", style = MaterialTheme.typography.labelMedium)
              Text(codigoConfiguracao, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            TextButton(onClick = { copiarTexto(contexto, "Senha / configuração", codigoConfiguracao) }) {
              Text("Copiar")
            }
          }
        }
      }

      if (procedimentos.isNotEmpty()) {
        TituloSecao("Como se faz")
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          procedimentos.forEach { foto ->
            Column(modifier = Modifier.width(140.dp)) {
              ImagemLocal(
                caminho = foto.caminho,
                ladoMaximo = 800,
                modificador = Modifier
                  .width(140.dp)
                  .height(140.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .clickable { fotoAberta = foto.caminho },
              )
              if (foto.legenda.isNotBlank()) {
                Text(
                  text = foto.legenda,
                  style = MaterialTheme.typography.bodySmall,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }

      TituloSecao("Serviços realizados")
      servicos.forEach { servico ->
        Card(
          modifier = Modifier.fillMaxWidth().clickable { onAbrirServico(servico.id) },
        ) {
          Column(Modifier.padding(14.dp)) {
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              servico.tiposOrdenados.forEach { Selo(it.rotulo) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = formatarData(servico.dataServico),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              if (servico.quantidadeFotos > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                  text = "${servico.quantidadeFotos} foto(s)",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            val resumo = servico.solucao.ifBlank { servico.defeito }.ifBlank { servico.abertura }
            if (resumo.isNotBlank()) {
              Spacer(Modifier.height(4.dp))
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
      Spacer(Modifier.height(24.dp))
    }
  }

  val foto = fotoAberta
  if (foto != null) {
    Dialog(onDismissRequest = { fotoAberta = null }) {
      Card {
        Column {
          ImagemLocal(
            caminho = foto,
            ladoMaximo = 2000,
            modificador = Modifier
              .fillMaxWidth()
              .heightIn(max = 520.dp)
              .clickable { fotoAberta = null },
          )
          TextButton(onClick = { fotoAberta = null }, modifier = Modifier.fillMaxWidth()) {
            Text("Fechar")
          }
        }
      }
    }
  }
}
