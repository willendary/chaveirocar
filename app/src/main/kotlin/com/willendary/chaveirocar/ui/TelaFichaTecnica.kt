package com.willendary.chaveirocar.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.willendary.chaveirocar.catalogo.CatalogoFichasPadrao
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.modelo.FichaTecnica
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.ui.theme.coresEstendidas
import com.willendary.chaveirocar.util.Erros
import com.willendary.chaveirocar.util.formatarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaFichaTecnica(
  banco: BancoDeAtendimentos,
  id: Long,
  onEditar: () -> Unit,
  onExcluido: () -> Unit,
  onVoltar: () -> Unit,
  onEditarOficial: ((FichaTecnica) -> Unit)? = null,
  onCriarServico: ((FichaTecnica) -> Unit)? = null,
  podeEditar: Boolean = true,
) {
  val contexto = LocalContext.current
  val escopo = rememberCoroutineScope()
  var ficha by remember { mutableStateOf<FichaTecnica?>(null) }
  var carregado by remember { mutableStateOf(false) }
  var confirmarExclusao by remember { mutableStateOf(false) }

  LaunchedEffect(id) {
    ficha = if (id < 0L) CatalogoFichasPadrao.obterPorId(id)
    else withContext(Dispatchers.IO) { banco.obterFicha(id) }
    carregado = true
  }

  Scaffold(
    topBar = {
      Column {
        TopAppBar(
          title = { Text("Ficha técnica", style = MaterialTheme.typography.titleLarge) },
          navigationIcon = { BotaoVoltar(onVoltar) },
          actions = {
            val atualBarra = ficha
            if (podeEditar && atualBarra != null) {
              IconButton(onClick = {
                if (atualBarra.ehCatalogoOficial && id < 0L && onEditarOficial != null) {
                  onEditarOficial(atualBarra)
                } else {
                  onEditar()
                }
              }) { Icon(Icons.Default.Edit, contentDescription = "Editar ficha") }
              if (id > 0L) {
                IconButton(onClick = { confirmarExclusao = true }) {
                  Icon(Icons.Default.Delete, contentDescription = "Excluir ficha")
                }
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    val atual = ficha
    if (!carregado || atual == null) {
      Box(Modifier.fillMaxSize().padding(espacamento), contentAlignment = Alignment.Center) {
        if (carregado) Text("Ficha não encontrada") else CircularProgressIndicator()
      }
      return@Scaffold
    }

    Column(
      modifier = Modifier
        .conteudoResponsivo()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      val veiculo = listOf(atual.marca, atual.modelo, atual.ano).filter { it.isNotBlank() }.joinToString(" ")
      PainelDestaque(
        titulo = veiculo.ifBlank { atual.sistema.ifBlank { "Ficha técnica" } },
        descricao = atual.descricao.ifBlank {
          if (atual.ehCatalogoOficial) "Referência do catálogo padrão." else "Referência técnica da empresa."
        },
        etiqueta = listOfNotNull(
          atual.segmento.rotulo,
          atual.equipamento.takeIf { it.isNotBlank() },
          atual.sistema.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { null },
      )

      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Selo(
          texto = if (atual.ehCatalogoOficial) "Catálogo padrão" else "Anotação da empresa",
          tom = if (atual.ehCatalogoOficial) TomSelo.PRIMARIO else TomSelo.RESIDENCIAL,
        )
        Selo(
          texto = if (atual.temManualOffline) "Manual offline" else "Sem manual",
          tom = if (atual.temManualOffline) TomSelo.SUCESSO else TomSelo.NEUTRO,
        )
        Selo(
          texto = if (atual.confirmado) "Confirmado" else "Não confirmado",
          tom = if (atual.confirmado) TomSelo.SUCESSO else TomSelo.PENDENTE,
        )
      }
      if (atual.atualizadoEm > 0) {
        Text(
          "Atualizado em ${formatarData(atual.atualizadoEm)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (atual.foto.isNotBlank()) {
        ImagemLocal(
          caminho = atual.foto,
          modificador = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)),
          ladoMaximo = 1000,
        )
      }

      // Grade técnica 2x2 de referências rápidas (1 toque copia!)
      val especificacoes = listOf(
        "Chip / transponder" to atual.chip,
        "Chave compatível" to atual.chave,
        "Telecomando" to atual.telecomando,
        "Frequência" to atual.frequencia,
      ).filter { it.second.isNotBlank() }

      if (especificacoes.isNotEmpty()) {
        TituloSecao("Especificações de bancada", detalhe = "Toque para copiar")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          especificacoes.chunked(2).forEach { par ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              par.forEach { (rotulo, valor) ->
                TileEspecificacao(
                  rotulo = rotulo,
                  valor = valor,
                  modificador = Modifier.weight(1f),
                  onCopiar = { copiarTexto(contexto, rotulo, valor) },
                )
              }
              if (par.size == 1) Spacer(Modifier.weight(1f))
            }
          }
        }
      }

      if (atual.avisos.isNotBlank()) {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.errorContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
              Text(
                "Informações importantes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
            Text(
              atual.avisos,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
            )
          }
        }
      }

      if (atual.funcoes.isNotBlank()) {
        BlocoDeTexto("Funções documentadas", atual.funcoes)
      }

      if (atual.manual.isNotBlank()) {
        CartaoSurface(raio = 18.dp) {
          Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Manual / anotações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
              Selo("Disponível offline", tom = TomSelo.SUCESSO)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Text(atual.manual, style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(
              onClick = { copiarTexto(contexto, "Manual", atual.manual) },
              shape = RoundedCornerShape(10.dp),
            ) {
              Icon(IconesApp.Copiar, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(Modifier.width(6.dp))
              Text("Copiar manual", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      if (atual.origem.isNotBlank()) {
        BlocoDeTexto("Origem da informação", atual.origem)
      }

      Text(
        "Ficha de consulta: não altera atendimentos antigos nem executa funções na central do veículo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (onCriarServico != null && podeEditar) {
        OutlinedButton(
          onClick = { onCriarServico(atual) },
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(14.dp),
        ) {
          Text("Novo serviço com esta ficha", fontWeight = FontWeight.SemiBold)
        }
      }
      if (podeEditar) {
        Button(
          onClick = {
            if (atual.ehCatalogoOficial && id < 0L && onEditarOficial != null) {
              onEditarOficial(atual)
            } else {
              onEditar()
            }
          },
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(14.dp),
        ) {
          Text(
            if (id < 0L) "Complementar ficha para a empresa" else "Editar ficha",
            fontWeight = FontWeight.Bold,
          )
        }
      }
      Spacer(Modifier.height(20.dp))
    }
  }

  if (confirmarExclusao) {
    AlertDialog(
      onDismissRequest = { confirmarExclusao = false },
      shape = RoundedCornerShape(22.dp),
      title = { Text("Excluir esta ficha?", fontWeight = FontWeight.Bold) },
      text = { Text("A ficha e a imagem de referência serão apagadas. Os atendimentos não mudam.") },
      confirmButton = {
        Button(
          onClick = {
            confirmarExclusao = false
            escopo.launch {
              withContext(Dispatchers.IO) {
                banco.excluirFicha(id)?.takeIf { it.isNotBlank() }?.let { Fotos.excluir(contexto, it) }
              }
              onExcluido()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          shape = RoundedCornerShape(12.dp),
        ) { Text("Excluir") }
      },
      dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } },
    )
  }
}

@Composable
private fun TileEspecificacao(
  rotulo: String,
  valor: String,
  modificador: Modifier = Modifier,
  onCopiar: () -> Unit,
) {
  CartaoSurface(onClick = onCopiar, modificador = modificador, raio = 16.dp) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          text = rotulo.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Icon(
          IconesApp.Copiar,
          contentDescription = "Copiar",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(14.dp),
        )
      }
      Text(
        text = valor,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.ExtraBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun BlocoDeTexto(titulo: String, texto: String) {
  CartaoSurface(raio = 18.dp) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
      Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaFichaFormulario(
  banco: BancoDeAtendimentos,
  id: Long,
  base: FichaTecnica? = null,
  onSalvo: (Long) -> Unit,
  onVoltar: () -> Unit,
  segmentoFixo: SegmentoAtendimento? = null,
) {
  val contexto = LocalContext.current
  val escopo = rememberCoroutineScope()
  var ficha by remember { mutableStateOf(base ?: FichaTecnica(segmento = segmentoFixo ?: SegmentoAtendimento.AUTOMOTIVO)) }
  var carregado by remember { mutableStateOf(id == 0L) }
  var salvando by remember { mutableStateOf(false) }
  var fotoNova by remember { mutableStateOf<String?>(null) }
  var arquivoCamera by remember { mutableStateOf<File?>(null) }
  var sugestoesEquipamento by remember { mutableStateOf<List<String>>(emptyList()) }
  var sugestoesMarca by remember { mutableStateOf<List<String>>(emptyList()) }
  var sugestoesModelo by remember { mutableStateOf<List<String>>(emptyList()) }
  var sugestoesSistema by remember { mutableStateOf<List<String>>(emptyList()) }

  LaunchedEffect(id) {
    if (id > 0) {
      val registro = withContext(Dispatchers.IO) { banco.obterFicha(id) }
      if (registro != null) ficha = registro
      carregado = true
    }
  }

  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      val fichas = banco.listarFichas()
      sugestoesEquipamento = (fichas.map { it.equipamento } + banco.valoresUsados("ferramenta"))
        .filter { it.isNotBlank() }.distinct().take(20)
      sugestoesMarca = (fichas.map { it.marca } + banco.valoresUsados("marca"))
        .filter { it.isNotBlank() }.distinct().take(30)
      sugestoesModelo = (fichas.map { it.modelo } + banco.valoresUsados("modelo"))
        .filter { it.isNotBlank() }.distinct().take(30)
      sugestoesSistema = fichas.map { it.sistema }.filter { it.isNotBlank() }.distinct().take(20)
    }
  }

  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
    val arquivo = arquivoCamera
    arquivoCamera = null
    if (sucesso && arquivo != null && arquivo.exists() && arquivo.length() > 0) {
      escopo.launch {
        val nome = runCatching {
          withContext(Dispatchers.IO) { Fotos.salvarDeArquivo(contexto, arquivo) }
        }.onFailure { erro ->
          Erros.registrar(contexto, "Salvar imagem da ficha", erro)
        }.getOrNull()
        arquivo.delete()
        if (nome != null) {
          fotoNova?.let { Fotos.excluir(contexto, it) }
          fotoNova = nome
        } else {
          Toast.makeText(contexto, "Não foi possível salvar a imagem.", Toast.LENGTH_LONG).show()
        }
      }
    } else {
      arquivo?.delete()
    }
  }

  val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) {
      escopo.launch {
        val nome = runCatching {
          withContext(Dispatchers.IO) {
            contexto.contentResolver.openInputStream(uri)?.use { Fotos.salvarDeFluxo(contexto, it) }
          }
        }.onFailure { erro ->
          Erros.registrar(contexto, "Importar imagem da ficha", erro)
        }.getOrNull()
        if (nome != null) {
          fotoNova?.let { Fotos.excluir(contexto, it) }
          fotoNova = nome
        } else {
          Toast.makeText(contexto, "Não foi possível abrir essa imagem.", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  fun tirarFoto() {
    val arquivo = File(contexto.cacheDir, "ficha_${System.currentTimeMillis()}.jpg")
    arquivoCamera = arquivo
    runCatching {
      val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo)
      camera.launch(uri)
    }.onFailure { erro ->
      arquivoCamera = null
      arquivo.delete()
      Erros.registrar(contexto, "Abrir câmera da ficha", erro)
      Toast.makeText(contexto, "Não foi possível abrir a câmera", Toast.LENGTH_LONG).show()
    }
  }

  fun descartar() {
    fotoNova?.let { Fotos.excluir(contexto, it) }
    onVoltar()
  }

  BackHandler { descartar() }

  fun salvar() {
    if (ficha.marca.isBlank() && ficha.modelo.isBlank() && ficha.sistema.isBlank()) {
      Toast.makeText(contexto, "Informe ao menos o veículo ou o sistema", Toast.LENGTH_SHORT).show()
      return
    }
    if (salvando) return
    salvando = true
    val fotoFinal = fotoNova ?: ficha.foto
    val fotoAntiga = ficha.foto
    escopo.launch {
      val novoId = withContext(Dispatchers.IO) { banco.salvarFicha(ficha.copy(foto = fotoFinal)) }
      if (fotoFinal != fotoAntiga && fotoAntiga.isNotBlank()) {
        withContext(Dispatchers.IO) { Fotos.excluir(contexto, fotoAntiga) }
      }
      salvando = false
      onSalvo(novoId)
    }
  }

  Scaffold(
    topBar = {
      Column {
        TopAppBar(
          title = { Text(if (id > 0) "Editar ficha" else "Nova ficha técnica", style = MaterialTheme.typography.titleLarge) },
          navigationIcon = { BotaoVoltar { descartar() } },
          actions = {
            Button(
              onClick = { salvar() },
              enabled = !salvando,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.padding(end = 12.dp),
            ) { Text(if (salvando) "Salvando…" else "Salvar", fontWeight = FontWeight.Bold) }
          },
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
        .imePadding()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      if (id == 0L && base?.ehCatalogoOficial == true) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = "Complementando o Catálogo Padrão: suas alterações serão salvas como anotação da empresa, preservando o catálogo oficial.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(14.dp),
          )
        }
      }

      // Card 01 — Identificação
      CartaoSurface(raio = 20.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SecaoFormulario("01", "Identificação", "Equipamento, veículo ou fechadura e sistema.")
          if (segmentoFixo == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              SegmentoAtendimento.entries.forEach { seg ->
                FilterChip(
                  selected = ficha.segmento == seg,
                  onClick = { ficha = ficha.copy(segmento = seg) },
                  shape = RoundedCornerShape(12.dp),
                  label = { Text(seg.rotulo) },
                )
              }
            }
          } else {
            Selo("Modalidade: ${ficha.segmento.rotulo}")
          }
          CampoTexto("Equipamento", ficha.equipamento, { ficha = ficha.copy(equipamento = it) }, sugestoes = sugestoesEquipamento, dica = "VVDI, KD-X2, Autel...", modificador = Modifier.fillMaxWidth())
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoTexto("Montadora", ficha.marca, { ficha = ficha.copy(marca = it) }, sugestoes = sugestoesMarca, dica = "Volkswagen", modificador = Modifier.weight(1f))
            CampoTexto("Ano / geração", ficha.ano, { ficha = ficha.copy(ano = it.filter { letra -> letra.isDigit() || letra == '/' }) }, dica = "2010", modificador = Modifier.weight(1f))
          }
          CampoTexto("Modelo", ficha.modelo, { ficha = ficha.copy(modelo = it) }, sugestoes = sugestoesModelo, dica = "Gol", modificador = Modifier.fillMaxWidth())
          CampoTexto("Sistema / módulo", ficha.sistema, { ficha = ficha.copy(sistema = it) }, sugestoes = sugestoesSistema, dica = "G5, Alarme G5, imobilizador...", modificador = Modifier.fillMaxWidth())
          CampoTexto("Descrição", ficha.descricao, { ficha = ficha.copy(descricao = it) }, dica = "Resumo do que serve nesta configuração", linhaUnica = false, minimoLinhas = 2, teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modificador = Modifier.fillMaxWidth())
        }
      }

      // Card 02 — Referências técnicas
      CartaoSurface(raio = 20.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SecaoFormulario("02", "Referências técnicas", "O que procurar na hora do serviço.")
          CampoTexto("Chip / transponder", ficha.chip, { ficha = ficha.copy(chip = it.uppercase()) }, dica = "ID48, PCF7936...", estiloTexto = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), modificador = Modifier.fillMaxWidth())
          CampoTexto("Chave compatível", ficha.chave, { ficha = ficha.copy(chave = it) }, dica = "Tipo, lâmina ou referência", modificador = Modifier.fillMaxWidth())
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CampoTexto("Telecomando", ficha.telecomando, { ficha = ficha.copy(telecomando = it) }, dica = "Modelo/referência", modificador = Modifier.weight(1f))
            CampoTexto("Frequência", ficha.frequencia, { ficha = ficha.copy(frequencia = it) }, dica = "433 MHz", modificador = Modifier.weight(1f))
          }
        }
      }

      // Card 03 — Observações e manual
      CartaoSurface(raio = 20.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SecaoFormulario("03", "Observações e manual", "Avisos, funções e anotações offline.")
          CampoTexto("Funções documentadas", ficha.funcoes, { ficha = ficha.copy(funcoes = it) }, dica = "Ler chaves, programar telecomando...", linhaUnica = false, minimoLinhas = 3, teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modificador = Modifier.fillMaxWidth())
          CampoTexto("Informações importantes / avisos", ficha.avisos, { ficha = ficha.copy(avisos = it) }, dica = "Pré-condições, riscos, cuidados...", linhaUnica = false, minimoLinhas = 3, teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modificador = Modifier.fillMaxWidth())
          CampoTexto("Manual / anotações (fica disponível offline)", ficha.manual, { ficha = ficha.copy(manual = it) }, dica = "Cole aqui o texto do manual ou suas anotações", linhaUnica = false, minimoLinhas = 5, teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences), modificador = Modifier.fillMaxWidth())
          CampoTexto("Origem / fonte", ficha.origem, { ficha = ficha.copy(origem = it) }, dica = "Manual do equipamento, curso, anotação própria...", modificador = Modifier.fillMaxWidth())
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.coresEstendidas.superficieSutil,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Column(Modifier.weight(1f)) {
                Text("Informação confirmada", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                  "Desligue quando ainda não testou na bancada.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(checked = ficha.confirmado, onCheckedChange = { ficha = ficha.copy(confirmado = it) })
            }
          }
        }
      }

      // Card 04 — Imagem de referência
      CartaoSurface(raio = 20.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          SecaoFormulario("04", "Imagem de referência", "Opcional: foto do chip, chave ou telecomando.")
          val fotoAtual = fotoNova ?: ficha.foto
          if (fotoAtual.isNotBlank()) {
            ImagemLocal(
              caminho = fotoAtual,
              modificador = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)),
              ladoMaximo = 900,
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { tirarFoto() }, shape = RoundedCornerShape(12.dp)) { Text("Tirar foto") }
            OutlinedButton(onClick = { galeria.launch("image/*") }, shape = RoundedCornerShape(12.dp)) { Text("Galeria") }
            if (fotoAtual.isNotBlank()) {
              TextButton(onClick = {
                if (fotoNova != null) {
                  Fotos.excluir(contexto, fotoNova!!)
                  fotoNova = null
                } else {
                  ficha = ficha.copy(foto = "")
                }
              }) { Text("Remover", color = MaterialTheme.colorScheme.error) }
            }
          }
        }
      }

      Button(
        onClick = { salvar() },
        enabled = !salvando,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
      ) {
        Text(if (salvando) "Salvando..." else "Salvar ficha", fontWeight = FontWeight.Bold, fontSize = 15.sp)
      }
      Spacer(Modifier.height(20.dp))
    }
  }
}
