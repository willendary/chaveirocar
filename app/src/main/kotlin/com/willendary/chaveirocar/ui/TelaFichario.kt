package com.willendary.chaveirocar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.CategoriaFoto
import com.willendary.chaveirocar.modelo.Foto
import com.willendary.chaveirocar.modelo.ResumoModelo
import com.willendary.chaveirocar.modelo.ResumoResidencial
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.ui.theme.Ambar
import com.willendary.chaveirocar.ui.theme.coresEstendidas
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
  versaoSincronia: Int = 0,
  segmentoFixo: SegmentoAtendimento? = null,
  onTrocarArea: (() -> Unit)? = null,
) {
  val contexto = LocalContext.current
  var busca by remember { mutableStateOf("") }
  var segmentoEstado by remember { mutableStateOf(segmentoFixo ?: SegmentoAtendimento.AUTOMOTIVO) }
  val segmento = segmentoFixo ?: segmentoEstado
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

  LaunchedEffect(versaoSincronia) {
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
    topBar = { CabecalhoTela("Fichário", "Sua biblioteca de soluções") },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    Column(modifier = Modifier.conteudoResponsivo().padding(espacamento)) {
      if (segmentoFixo != null && onTrocarArea != null) {
        BarraModalidadeAtiva(segmentoFixo, onTrocarArea)
      }

      OutlinedTextField(
        value = busca,
        onValueChange = { busca = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        placeholder = {
          Text(if (segmento == SegmentoAtendimento.RESIDENCIAL) "Buscar local, porta ou fechadura…" else "Buscar marca ou modelo…")
        },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
          if (busca.isNotEmpty()) {
            IconButton(onClick = { busca = "" }) {
              Icon(Icons.Default.Close, contentDescription = "Limpar")
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

      if (segmentoFixo == null) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FilterChip(
            selected = segmento == SegmentoAtendimento.AUTOMOTIVO,
            onClick = { segmentoEstado = SegmentoAtendimento.AUTOMOTIVO },
            shape = RoundedCornerShape(12.dp),
            label = { Text("Veículos (${modelos.size})") },
          )
          FilterChip(
            selected = segmento == SegmentoAtendimento.RESIDENCIAL,
            onClick = { segmentoEstado = SegmentoAtendimento.RESIDENCIAL },
            shape = RoundedCornerShape(12.dp),
            label = { Text("Residencial (${residenciais.size})") },
          )
        }
      }

      when {
        !carregado -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }

        segmento == SegmentoAtendimento.RESIDENCIAL && residenciaisVisiveis.isEmpty() -> Box(
          Modifier.fillMaxSize().padding(20.dp),
          contentAlignment = Alignment.Center,
        ) {
          EstadoVazio(
            if (busca.isBlank()) "Fichário residencial vazio" else "Nenhuma fechadura ou local encontrado",
            if (busca.isBlank()) "Os serviços residenciais aparecem organizados aqui por tipo de local e fechadura."
            else "Tente buscar por outra marca de fechadura ou ambiente.",
          )
        }

        segmento == SegmentoAtendimento.RESIDENCIAL -> LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        ) {
          item {
            PainelDestaque(
              "Memória residencial.",
              "Aberturas, cilindros e procedimentos organizados por local e fechadura.",
              "${residenciais.size} ITEM(NS) RESIDENCIAIS",
            )
          }
          item { TituloSecao("Locais e fechaduras", detalhe = "${residenciaisVisiveis.size}") }
          items(residenciaisVisiveis) { item ->
            CartaoResidencial(item = item, onAbrir = { onAbrirResidencial(item) })
          }
        }

        visiveis.isEmpty() -> Box(
          Modifier.fillMaxSize().padding(20.dp),
          contentAlignment = Alignment.Center,
        ) {
          EstadoVazio(
            if (busca.isBlank()) "Sua biblioteca começa aqui" else "Nenhum modelo encontrado",
            if (busca.isBlank()) "Os veículos e procedimentos aparecem aqui depois do primeiro serviço."
            else "Tente outra marca ou modelo.",
          )
        }

        else -> LazyColumn(
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        ) {
          item {
            PainelDestaque(
              "Conhecimento que fica.",
              "Aberturas, códigos e procedimentos organizados por veículo.",
              "${modelos.size} MODELO(S) NA BIBLIOTECA",
            )
          }
          item {
            TituloSecao(
              if (favoritos.isEmpty()) "Seus veículos" else "Seus veículos · favoritos primeiro",
              detalhe = "${visiveis.size}",
            )
          }
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
  val estendidas = MaterialTheme.coresEstendidas
  val titulo = item.tipoLocal.ifBlank { "Serviço residencial" }
  val fechadura = listOf(item.marcaFechadura, item.modeloFechadura)
    .filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Fechadura não informada" }

  CartaoSurface(onClick = onAbrir, raio = 18.dp) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Box(
        modifier = Modifier
          .size(46.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(estendidas.residencialFundo),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Default.Home, contentDescription = null, tint = estendidas.residencial, modifier = Modifier.size(22.dp))
      }
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = titulo.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          letterSpacing = 0.6.sp,
          color = estendidas.residencial,
        )
        Text(fechadura, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
          text = "${item.quantidade} serviço(s) · último em ${formatarData(item.ultimaData)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.padding(top = 2.dp),
        ) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(if (item.temProcedimento) estendidas.sucesso else MaterialTheme.colorScheme.outline),
          )
          Text(
            text = if (item.temProcedimento) "Procedimentos disponíveis" else "Sem procedimentos salvos",
            style = MaterialTheme.typography.labelSmall,
            color = if (item.temProcedimento) estendidas.sucesso else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun CartaoModelo(
  modelo: ResumoModelo,
  onAbrir: () -> Unit,
  fixado: Boolean = false,
  onFixar: () -> Unit = {},
) {
  val estendidas = MaterialTheme.coresEstendidas
  val iniciais = modelo.marca.trim().take(2).uppercase().ifBlank { "VC" }

  CartaoSurface(onClick = onAbrir, raio = 18.dp) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Box(
        modifier = Modifier
          .size(46.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = iniciais,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.primary,
        )
      }

      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = modelo.marca.ifBlank { "Veículo" }.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.primary,
          )
          if (modelo.ano.isNotBlank()) {
            Selo(texto = modelo.ano, tom = TomSelo.NEUTRO)
          }
        }
        Text(
          text = modelo.modelo.ifBlank { "Modelo não informado" },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${modelo.quantidade} serviço(s) · último em ${formatarData(modelo.ultimaData)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(if (modelo.temProcedimento) estendidas.sucesso else MaterialTheme.colorScheme.outline),
          )
          Text(
            text = if (modelo.temProcedimento) "Procedimentos disponíveis" else "Adicione no próximo serviço",
            style = MaterialTheme.typography.labelSmall,
            color = if (modelo.temProcedimento) estendidas.sucesso else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      IconButton(onClick = onFixar) {
        Icon(
          Icons.Default.Star,
          contentDescription = if (fixado) "Desafixar modelo" else "Fixar modelo",
          tint = if (fixado) Ambar else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
        )
      }
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
  onVerFicha: (() -> Unit)? = null,
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
  val tituloVeiculo = listOf(marca, modelo, ano).filter { it.isNotBlank() }.joinToString(" ")

  Scaffold(
    topBar = {
      Column {
        TopAppBar(
          title = {
            Column {
              Text(tituloVeiculo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text("${servicos.size} serviço(s) registrados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          },
          navigationIcon = { BotaoVoltar(onVoltar) },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
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
        CartaoSurface(raio = 16.dp) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sem procedimento registrado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
              "Na próxima vez, preencha a técnica de abertura ou os códigos no atendimento.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (onVerFicha != null) {
        Button(
          onClick = onVerFicha,
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(14.dp),
        ) {
          Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Consultar ficha técnica e manual", fontWeight = FontWeight.Bold)
        }
      }

      if (!abertura.isNullOrBlank()) {
        TituloSecao("Técnica de abertura")
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = abertura,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp),
          )
        }
      }

      if (!codigoChave.isNullOrBlank() || !codigoConfiguracao.isNullOrBlank()) {
        TituloSecao("Códigos salvos")
        CartaoSurface(raio = 18.dp) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!codigoChave.isNullOrBlank()) {
              LinhaCodigoFichario("Código de chave", codigoChave) {
                copiarTexto(contexto, "Código de chave", codigoChave)
              }
            }
            if (!codigoConfiguracao.isNullOrBlank()) {
              LinhaCodigoFichario("Código de configuração", codigoConfiguracao) {
                copiarTexto(contexto, "Código de configuração", codigoConfiguracao)
              }
            }
          }
        }
      }

      if (procedimentos.isNotEmpty()) {
        TituloSecao("Como se faz", detalhe = "${procedimentos.size} foto(s)")
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          procedimentos.forEach { foto ->
            CartaoSurface(modificador = Modifier.width(148.dp), raio = 16.dp, onClick = { fotoAberta = foto.caminho }) {
              Column {
                ImagemLocal(caminho = foto.caminho, ladoMaximo = 800, modificador = Modifier.fillMaxWidth().height(132.dp))
                if (foto.legenda.isNotBlank()) {
                  Text(
                    foto.legenda,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                  )
                }
              }
            }
          }
        }
      }

      TituloSecao("Serviços deste carro", detalhe = "${servicos.size}")
      servicos.forEach { servico ->
        CartaoServicoFichario(servico = servico, onAbrir = { onAbrirServico(servico.id) })
      }
      Spacer(Modifier.height(20.dp))
    }
  }

  DialogoFotoSimples(fotoAberta) { fotoAberta = null }
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
  val estendidas = MaterialTheme.coresEstendidas
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
      Column {
        TopAppBar(
          title = {
            Column {
              Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text("${servicos.size} serviço(s) registrados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          },
          navigationIcon = { BotaoVoltar(onVoltar) },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
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
        CartaoSurface(raio = 16.dp) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sem procedimento registrado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
              "Na próxima vez, preencha a técnica de abertura, códigos ou fotos de passo a passo.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }

      if (!abertura.isNullOrBlank()) {
        TituloSecao("Técnica de abertura / observações")
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = estendidas.residencialFundo,
          border = BorderStroke(1.dp, estendidas.residencial.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = abertura,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(16.dp),
          )
        }
      }

      if (!codigoChave.isNullOrBlank() || !codigoConfiguracao.isNullOrBlank()) {
        TituloSecao("Códigos e senhas")
        CartaoSurface(raio = 18.dp) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!codigoChave.isNullOrBlank()) {
              LinhaCodigoFichario("Código de chave", codigoChave) {
                copiarTexto(contexto, "Código de chave", codigoChave)
              }
            }
            if (!codigoConfiguracao.isNullOrBlank()) {
              LinhaCodigoFichario("Senha / configuração", codigoConfiguracao) {
                copiarTexto(contexto, "Senha / configuração", codigoConfiguracao)
              }
            }
          }
        }
      }

      if (procedimentos.isNotEmpty()) {
        TituloSecao("Como se faz", detalhe = "${procedimentos.size} foto(s)")
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          procedimentos.forEach { foto ->
            CartaoSurface(modificador = Modifier.width(148.dp), raio = 16.dp, onClick = { fotoAberta = foto.caminho }) {
              Column {
                ImagemLocal(caminho = foto.caminho, ladoMaximo = 800, modificador = Modifier.fillMaxWidth().height(132.dp))
                if (foto.legenda.isNotBlank()) {
                  Text(
                    foto.legenda,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                  )
                }
              }
            }
          }
        }
      }

      TituloSecao("Serviços realizados", detalhe = "${servicos.size}")
      servicos.forEach { servico ->
        CartaoServicoFichario(servico = servico, onAbrir = { onAbrirServico(servico.id) })
      }
      Spacer(Modifier.height(20.dp))
    }
  }

  DialogoFotoSimples(fotoAberta) { fotoAberta = null }
}

@Composable
private fun LinhaCodigoFichario(rotulo: String, valor: String, onCopiar: () -> Unit) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = MaterialTheme.coresEstendidas.superficieSutil,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Column(Modifier.weight(1f)) {
        Text(rotulo.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
      }
      FilledTonalButton(onClick = onCopiar, shape = RoundedCornerShape(10.dp)) {
        Text("Copiar", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoServicoFichario(servico: Atendimento, onAbrir: () -> Unit) {
  CartaoSurface(onClick = onAbrir, raio = 16.dp) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        servico.tiposOrdenados.forEach { Selo(it.rotulo, tom = TomSelo.PRIMARIO) }
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(formatarData(servico.dataServico), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (servico.quantidadeFotos > 0) {
          Selo("${servico.quantidadeFotos} foto(s)", tom = TomSelo.NEUTRO)
        }
      }
      val resumo = servico.solucao.ifBlank { servico.defeito }.ifBlank { servico.abertura }
      if (resumo.isNotBlank()) {
        Text(resumo, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun DialogoFotoSimples(foto: String?, onFechar: () -> Unit) {
  if (foto == null) return
  Dialog(onDismissRequest = onFechar) {
    CartaoSurface(raio = 20.dp) {
      Column {
        ImagemLocal(
          caminho = foto,
          ladoMaximo = 2000,
          modificador = Modifier.fillMaxWidth().heightIn(max = 520.dp).clickable { onFechar() },
        )
        TextButton(onClick = onFechar, modifier = Modifier.fillMaxWidth()) {
          Text("Fechar", fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
