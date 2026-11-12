package com.willendary.chaveirocar.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.modelo.FichaTecnica
import com.willendary.chaveirocar.util.Erros
import com.willendary.chaveirocar.util.formatarData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaFichaTecnica(
  banco: BancoDeAtendimentos,
  id: Long,
  onEditar: () -> Unit,
  onExcluido: () -> Unit,
  onVoltar: () -> Unit,
) {
  val contexto = LocalContext.current
  val escopo = rememberCoroutineScope()
  var ficha by remember { mutableStateOf<FichaTecnica?>(null) }
  var carregado by remember { mutableStateOf(false) }
  var confirmarExclusao by remember { mutableStateOf(false) }

  LaunchedEffect(id) {
    ficha = withContext(Dispatchers.IO) { banco.obterFicha(id) }
    carregado = true
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Ficha técnica") },
        navigationIcon = { BotaoVoltar(onVoltar) },
        actions = {
          IconButton(onClick = onEditar) { Icon(Icons.Default.Edit, contentDescription = "Editar ficha") }
          IconButton(onClick = { confirmarExclusao = true }) {
            Icon(Icons.Default.Delete, contentDescription = "Excluir ficha")
          }
        },
      )
    },
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
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      val veiculo = listOf(atual.marca, atual.modelo, atual.ano).filter { it.isNotBlank() }.joinToString(" ")
      PainelDestaque(
        titulo = veiculo.ifBlank { atual.sistema.ifBlank { "Ficha técnica" } },
        descricao = atual.descricao.ifBlank { "Referência técnica cadastrada por você." },
        etiqueta = listOfNotNull(
          atual.equipamento.takeIf { it.isNotBlank() },
          atual.sistema.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifBlank { null },
      )

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (atual.confirmado) Selo("Confirmado") else Selo("Não confirmado", pendente = true)
        if (atual.atualizadoEm > 0) {
          Text(
            "Atualizado em ${formatarData(atual.atualizadoEm)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (atual.foto.isNotBlank()) {
        ImagemLocal(
          caminho = atual.foto,
          modificador = Modifier.fillMaxWidth().height(200.dp),
          ladoMaximo = 1000,
        )
      }

      LinhaCopiar("Chip / transponder", atual.chip, contexto)
      LinhaCopiar("Chave compatível", atual.chave, contexto)
      LinhaCopiar("Telecomando", atual.telecomando, contexto)
      LinhaCopiar("Frequência", atual.frequencia, contexto)

      if (atual.avisos.isNotBlank()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Informações importantes", style = MaterialTheme.typography.titleSmall)
            Text(atual.avisos, style = MaterialTheme.typography.bodyMedium)
          }
        }
      }

      if (atual.funcoes.isNotBlank()) {
        BlocoDeTexto("Funções documentadas", atual.funcoes)
      }

      if (atual.manual.isNotBlank()) {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Manual / anotações", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
              Selo("Disponível offline")
            }
            Text(atual.manual, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { copiarTexto(contexto, "Manual", atual.manual) }) { Text("Copiar manual") }
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
        modifier = Modifier.padding(vertical = 8.dp),
      )
      Spacer(Modifier.height(16.dp))
      Button(onClick = onEditar, modifier = Modifier.fillMaxWidth()) { Text("Editar ficha") }
      Spacer(Modifier.height(24.dp))
    }
  }

  if (confirmarExclusao) {
    AlertDialog(
      onDismissRequest = { confirmarExclusao = false },
      title = { Text("Excluir esta ficha?") },
      text = { Text("A ficha e a imagem de referência serão apagadas. Os atendimentos não mudam.") },
      confirmButton = {
        TextButton(onClick = {
          confirmarExclusao = false
          escopo.launch {
            withContext(Dispatchers.IO) {
              banco.excluirFicha(id)?.takeIf { it.isNotBlank() }?.let { Fotos.excluir(contexto, it) }
            }
            onExcluido()
          }
        }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } },
    )
  }
}

@Composable
private fun LinhaCopiar(rotulo: String, valor: String, contexto: android.content.Context) {
  if (valor.isBlank()) return
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(Modifier.weight(1f)) {
        Text(rotulo, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(valor, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
      }
      TextButton(onClick = { copiarTexto(contexto, rotulo, valor) }) { Text("Copiar") }
    }
  }
}

@Composable
private fun BlocoDeTexto(titulo: String, texto: String) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(titulo, style = MaterialTheme.typography.titleMedium)
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
) {
  val contexto = LocalContext.current
  val escopo = rememberCoroutineScope()
  var ficha by remember { mutableStateOf(base ?: FichaTecnica()) }
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
      TopAppBar(
        title = { Text(if (id > 0) "Editar ficha" else "Nova ficha técnica") },
        navigationIcon = { BotaoVoltar { descartar() } },
        actions = {
          Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.padding(end = 12.dp)) {
            Text(if (salvando) "Salvando…" else "Salvar")
          }
        },
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
        .imePadding()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      SecaoFormulario("01", "Identificação", "Equipamento, veículo e sistema desta referência.")
      CampoTexto(
        rotulo = "Equipamento",
        valor = ficha.equipamento,
        aoMudar = { ficha = ficha.copy(equipamento = it) },
        sugestoes = sugestoesEquipamento,
        dica = "VVDI, KD-X2, Autel...",
        modificador = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CampoTexto(
          rotulo = "Montadora",
          valor = ficha.marca,
          aoMudar = { ficha = ficha.copy(marca = it) },
          sugestoes = sugestoesMarca,
          dica = "Volkswagen",
          modificador = Modifier.weight(1f),
        )
        CampoTexto(
          rotulo = "Ano / geração",
          valor = ficha.ano,
          aoMudar = { ficha = ficha.copy(ano = it.filter { letra -> letra.isDigit() || letra == '/' }) },
          dica = "2010",
          modificador = Modifier.weight(1f),
        )
      }
      CampoTexto(
        rotulo = "Modelo",
        valor = ficha.modelo,
        aoMudar = { ficha = ficha.copy(modelo = it) },
        sugestoes = sugestoesModelo,
        dica = "Gol",
        modificador = Modifier.fillMaxWidth(),
      )
      CampoTexto(
        rotulo = "Sistema / módulo",
        valor = ficha.sistema,
        aoMudar = { ficha = ficha.copy(sistema = it) },
        sugestoes = sugestoesSistema,
        dica = "G5, Alarme G5, imobilizador...",
        modificador = Modifier.fillMaxWidth(),
      )
      CampoTexto(
        rotulo = "Descrição",
        valor = ficha.descricao,
        aoMudar = { ficha = ficha.copy(descricao = it) },
        dica = "Resumo do que serve nesta configuração",
        linhaUnica = false,
        minimoLinhas = 2,
        teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modificador = Modifier.fillMaxWidth(),
      )

      SecaoFormulario("02", "Referências técnicas", "O que procurar na hora do serviço.")
      CampoTexto(
        rotulo = "Chip / transponder",
        valor = ficha.chip,
        aoMudar = { ficha = ficha.copy(chip = it.uppercase()) },
        dica = "ID48, PCF7936...",
        estiloTexto = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modificador = Modifier.fillMaxWidth(),
      )
      CampoTexto(
        rotulo = "Chave compatível",
        valor = ficha.chave,
        aoMudar = { ficha = ficha.copy(chave = it) },
        dica = "Tipo, lâmina ou referência",
        modificador = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CampoTexto(
          rotulo = "Telecomando",
          valor = ficha.telecomando,
          aoMudar = { ficha = ficha.copy(telecomando = it) },
          dica = "Modelo/referência",
          modificador = Modifier.weight(1f),
        )
        CampoTexto(
          rotulo = "Frequência",
          valor = ficha.frequencia,
          aoMudar = { ficha = ficha.copy(frequencia = it) },
          dica = "433 MHz",
          modificador = Modifier.weight(1f),
        )
      }

      SecaoFormulario("03", "Observações e manual", "Avisos, funções e anotações para consultar offline.")
      CampoTexto(
        rotulo = "Funções documentadas",
        valor = ficha.funcoes,
        aoMudar = { ficha = ficha.copy(funcoes = it) },
        dica = "Ler chaves, programar telecomando, ler arquivo...",
        linhaUnica = false,
        minimoLinhas = 3,
        teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modificador = Modifier.fillMaxWidth(),
      )
      CampoTexto(
        rotulo = "Informações importantes / avisos",
        valor = ficha.avisos,
        aoMudar = { ficha = ficha.copy(avisos = it) },
        dica = "Pré-condições, riscos, cuidados...",
        linhaUnica = false,
        minimoLinhas = 3,
        teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modificador = Modifier.fillMaxWidth(),
      )
      CampoTexto(
        rotulo = "Manual / anotações (fica disponível offline)",
        valor = ficha.manual,
        aoMudar = { ficha = ficha.copy(manual = it) },
        dica = "Cole aqui o texto do manual ou suas anotações",
        linhaUnica = false,
        minimoLinhas = 5,
        teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modificador = Modifier.fillMaxWidth(),
      )
      CampoTexto(
        rotulo = "Origem / fonte",
        valor = ficha.origem,
        aoMudar = { ficha = ficha.copy(origem = it) },
        dica = "Manual do equipamento, curso, anotação própria...",
        modificador = Modifier.fillMaxWidth(),
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Column(Modifier.weight(1f)) {
          Text("Informação confirmada", style = MaterialTheme.typography.bodyLarge)
          Text(
            "Desligue quando ainda não testou ou a fonte não é segura.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Switch(
          checked = ficha.confirmado,
          onCheckedChange = { ficha = ficha.copy(confirmado = it) },
        )
      }

      SecaoFormulario("04", "Imagem de referência", "Opcional: foto do chip, chave ou telecomando.")
      val fotoAtual = fotoNova ?: ficha.foto
      if (fotoAtual.isNotBlank()) {
        ImagemLocal(
          caminho = fotoAtual,
          modificador = Modifier.fillMaxWidth().height(180.dp),
          ladoMaximo = 900,
        )
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { tirarFoto() }) { Text("Tirar foto") }
        OutlinedButton(onClick = { galeria.launch("image/*") }) { Text("Galeria") }
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

      Spacer(Modifier.height(8.dp))
      Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth()) {
        Text(if (salvando) "Salvando..." else "Salvar ficha")
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}
