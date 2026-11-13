package com.willendary.chaveirocar.ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.CategoriaFoto
import com.willendary.chaveirocar.modelo.Foto
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.modelo.TipoServico
import com.willendary.chaveirocar.util.Erros
import com.willendary.chaveirocar.util.OcrPlaca
import com.willendary.chaveirocar.util.Placa
import com.willendary.chaveirocar.util.formatarData
import com.willendary.chaveirocar.util.formatarValorEditavel
import com.willendary.chaveirocar.util.paraCentavos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

private data class FotoNova(
  val nome: String,
  val categoria: CategoriaFoto,
  val legenda: String = "",
  val anotacoes: String = "[]",
)

private val TIPOS_LOCAL_PADRAO = listOf("Casa", "Apartamento", "Comércio", "Condomínio", "Portão", "Outro")
private val AMBIENTES_PORTA_PADRAO = listOf("Porta principal", "Porta social", "Portão", "Quarto", "Banheiro", "Escritório", "Gaveta / armário")
private val MARCAS_FECHADURA_PADRAO = listOf("Papaiz", "Pado", "Stam", "La Fonte", "Intelbras", "Yale", "Arouca", "Aliança", "3F", "Soprano")
private val CILINDROS_PADRAO = listOf("Yale / simples", "Tetra", "Multiponto", "Gorje", "Fechadura digital", "Tubular")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaFormulario(
  banco: BancoDeAtendimentos,
  id: Long,
  base: Atendimento? = null,
  onSalvo: (Long) -> Unit,
  onVoltar: () -> Unit,
) {
  val contexto = LocalContext.current
  val escopo = rememberCoroutineScope()

  var atendimento by remember { mutableStateOf(base ?: Atendimento()) }
  var fotosSalvas by remember { mutableStateOf<List<Foto>>(emptyList()) }
  var fotosNovas by remember { mutableStateOf<List<FotoNova>>(emptyList()) }
  var valorTexto by remember { mutableStateOf(formatarValorEditavel((base ?: Atendimento()).valorCentavos)) }
  var carregado by remember { mutableStateOf(id == 0L) }
  var historico by remember { mutableStateOf<List<Atendimento>>(emptyList()) }
  var arquivoCamera by remember { mutableStateOf<File?>(null) }
  var categoriaPendente by remember { mutableStateOf(CategoriaFoto.CARRO) }
  var salvando by remember { mutableStateOf(false) }
  var importandoFotos by remember { mutableStateOf(false) }
  var fotoEditando by remember { mutableStateOf<Foto?>(null) }
  var novaEditando by remember { mutableStateOf<FotoNova?>(null) }
  var fotoAnotandoSalva by remember { mutableStateOf<Foto?>(null) }
  var fotoAnotandoNova by remember { mutableStateOf<FotoNova?>(null) }
  var segmentoPendenteTroca by remember { mutableStateOf<SegmentoAtendimento?>(null) }
  var mostrarCatalogo by remember { mutableStateOf(false) }
  var arquivoPlaca by remember { mutableStateOf<File?>(null) }
  var lendoPlaca by remember { mutableStateOf(false) }
  var candidatosPlaca by remember { mutableStateOf<List<String>>(emptyList()) }
  var dialogoPlaca by remember { mutableStateOf(false) }

  var sugestoesMarca by remember { mutableStateOf<List<String>>(emptyList()) }
  var sugestoesModelo by remember { mutableStateOf<List<String>>(emptyList()) }
  var sugestoesDefeito by remember { mutableStateOf<List<String>>(emptyList()) }
  var sugestoesSolucaoBase by remember { mutableStateOf<List<String>>(emptyList()) }
  var sugestoesSolucaoDefeito by remember { mutableStateOf<List<String>>(emptyList()) }
  var sugestoesAbertura by remember { mutableStateOf<List<String>>(emptyList()) }
  var sugestoesTipoLocal by remember { mutableStateOf(TIPOS_LOCAL_PADRAO) }
  var sugestoesAmbientePorta by remember { mutableStateOf(AMBIENTES_PORTA_PADRAO) }
  var sugestoesMarcaFechadura by remember { mutableStateOf(MARCAS_FECHADURA_PADRAO) }
  var sugestoesModeloFechadura by remember { mutableStateOf<List<String>>(emptyList()) }
  var sugestoesTipoCilindro by remember { mutableStateOf(CILINDROS_PADRAO) }

  LaunchedEffect(id) {
    if (id > 0) {
      val registro = withContext(Dispatchers.IO) { banco.obter(id) }
      if (registro != null) {
        atendimento = registro
        valorTexto = formatarValorEditavel(registro.valorCentavos)
        fotosSalvas = withContext(Dispatchers.IO) { banco.listarFotos(id) }
      }
      carregado = true
    }
  }

  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) {
      sugestoesMarca = banco.valoresUsados("marca")
      sugestoesDefeito = banco.valoresUsados("defeito")
      sugestoesSolucaoBase = banco.valoresUsados("solucao")
      sugestoesTipoLocal = (TIPOS_LOCAL_PADRAO + banco.valoresUsados("tipo_local")).distinct()
      sugestoesAmbientePorta = (AMBIENTES_PORTA_PADRAO + banco.valoresUsados("ambiente_porta")).distinct()
      sugestoesMarcaFechadura = (MARCAS_FECHADURA_PADRAO + banco.valoresUsados("marca_fechadura")).distinct()
      sugestoesModeloFechadura = banco.valoresUsados("modelo_fechadura")
      sugestoesTipoCilindro = (CILINDROS_PADRAO + banco.valoresUsados("tipo_cilindro")).distinct()
    }
  }

  LaunchedEffect(atendimento.marca) {
    sugestoesModelo = withContext(Dispatchers.IO) { banco.modelosDaMarca(atendimento.marca) }
  }

  val placaParaHistorico = atendimento.placa.trim().uppercase()
  LaunchedEffect(placaParaHistorico) {
    historico = if (placaParaHistorico.length >= 7) {
      withContext(Dispatchers.IO) { banco.servicosDaPlaca(placaParaHistorico, id) }
    } else {
      emptyList()
    }
  }

  LaunchedEffect(atendimento.modelo) {
    sugestoesAbertura = if (atendimento.modelo.isBlank()) {
      emptyList()
    } else {
      withContext(Dispatchers.IO) {
        banco.servicosDoModelo(atendimento.modelo, id)
          .mapNotNull { registro -> registro.abertura.takeIf { it.isNotBlank() } }
          .distinct()
          .take(4)
      }
    }
  }

  LaunchedEffect(atendimento.defeito) {
    if (atendimento.defeito.length < 4) {
      sugestoesSolucaoDefeito = emptyList()
      return@LaunchedEffect
    }
    delay(400)
    sugestoesSolucaoDefeito = withContext(Dispatchers.IO) {
      banco.solucoesPorDefeito(atendimento.defeito)
    }
  }

  fun anexarFoto(nome: String, categoria: CategoriaFoto) {
    if (id > 0) {
      escopo.launch {
        withContext(Dispatchers.IO) { banco.adicionarFoto(id, nome, categoria) }
        fotosSalvas = withContext(Dispatchers.IO) { banco.listarFotos(id) }
      }
    } else {
      fotosNovas = fotosNovas + FotoNova(nome, categoria)
    }
  }

  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
    val arquivo = arquivoCamera
    val categoria = categoriaPendente
    arquivoCamera = null
    if (sucesso && arquivo != null && arquivo.exists() && arquivo.length() > 0) {
      escopo.launch {
        val nome = runCatching {
          withContext(Dispatchers.IO) { Fotos.salvarDeArquivo(contexto, arquivo) }
        }.onFailure { erro ->
          Erros.registrar(contexto, "Salvar foto da câmera", erro)
        }.getOrNull()
        arquivo.delete()
        if (nome != null) {
          anexarFoto(nome, categoria)
        } else {
          Toast.makeText(contexto, "Não foi possível salvar a foto. Tente de novo.", Toast.LENGTH_LONG).show()
        }
      }
    } else {
      arquivo?.delete()
      if (sucesso) {
        Toast.makeText(contexto, "Não foi possível salvar a foto. Tente de novo.", Toast.LENGTH_LONG).show()
      }
    }
  }

  // O Photo Picker permite escolher várias imagens na mesma abertura (inclusive 10+).
  // O contrato fornece um fallback para versões antigas do Android.
  val galeria = rememberLauncherForActivityResult(
    ActivityResultContracts.PickMultipleVisualMedia(50),
  ) { imagens ->
    if (imagens.isNotEmpty() && !importandoFotos) {
      val categoria = categoriaPendente
      escopo.launch {
        importandoFotos = true
        var importadas = 0
        var falhas = 0
        val novas = mutableListOf<FotoNova>()
        try {
          for (uri in imagens.distinct()) {
            val nome = runCatching {
              withContext(Dispatchers.IO) {
                contexto.contentResolver.openInputStream(uri)?.use { Fotos.salvarDeFluxo(contexto, it) }
                  ?: error("Imagem não disponível para importação")
              }
            }.onFailure { erro ->
              Erros.registrar(contexto, "Importar imagem da galeria", erro)
            }.getOrNull()

            if (nome == null) {
              falhas++
              continue
            }

            if (id > 0L) {
              val gravada = runCatching {
                withContext(Dispatchers.IO) { banco.adicionarFoto(id, nome, categoria) >= 0 }
              }.onFailure { erro ->
                Erros.registrar(contexto, "Vincular imagem ao atendimento", erro)
              }.getOrDefault(false)
              if (!gravada) {
                withContext(Dispatchers.IO) { Fotos.excluir(contexto, nome) }
                falhas++
                continue
              }
            } else {
              novas.add(FotoNova(nome, categoria))
            }
            importadas++
          }
        } finally {
          // Atualiza o estado uma vez por lote e preserva as fotos já existentes.
          if (id > 0L) {
            fotosSalvas = withContext(Dispatchers.IO) { banco.listarFotos(id) }
          } else {
            fotosNovas = fotosNovas + novas
          }
          importandoFotos = false
        }

        val mensagem = when {
          falhas == 0 -> "$importadas foto(s) adicionada(s)"
          importadas == 0 -> "Não foi possível importar as fotos selecionadas"
          else -> "$importadas foto(s) adicionada(s); $falhas não importada(s)"
        }
        Toast.makeText(contexto, mensagem, Toast.LENGTH_LONG).show()
      }
    }
  }

  fun tirarFoto(categoria: CategoriaFoto) {
    categoriaPendente = categoria
    val arquivo = File(contexto.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    arquivoCamera = arquivo
    runCatching {
      val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo)
      camera.launch(uri)
    }.onFailure { erro ->
      arquivoCamera = null
      arquivo.delete()
      Erros.registrar(contexto, "Abrir câmera", erro)
      Toast.makeText(contexto, "Não foi possível abrir a câmera", Toast.LENGTH_LONG).show()
    }
  }

  fun escolherDaGaleria(categoria: CategoriaFoto) {
    if (importandoFotos) return
    categoriaPendente = categoria
    galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
  }

  val cameraPlaca = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
    val arquivo = arquivoPlaca
    arquivoPlaca = null
    if (sucesso && arquivo != null && arquivo.exists() && arquivo.length() > 0) {
      lendoPlaca = true
      escopo.launch {
        val encontrados = runCatching {
          withContext(Dispatchers.IO) { OcrPlaca.reconhecer(contexto, arquivo) }
        }.onFailure { erro ->
          Erros.registrar(contexto, "Ler placa pela câmera", erro)
        }.getOrDefault(emptyList())
        arquivo.delete()
        lendoPlaca = false
        candidatosPlaca = encontrados
        if (encontrados.isEmpty()) {
          Toast.makeText(
            contexto,
            "Não encontrei uma placa. Tente outra foto ou digite manualmente.",
            Toast.LENGTH_LONG,
          ).show()
        } else {
          dialogoPlaca = true
        }
      }
    } else {
      arquivo?.delete()
      if (sucesso) {
        Toast.makeText(contexto, "Não foi possível usar essa foto. Tente de novo.", Toast.LENGTH_LONG).show()
      }
    }
  }

  fun lerPlaca() {
    if (lendoPlaca) return
    val arquivo = File(contexto.cacheDir, "placa_${System.currentTimeMillis()}.jpg")
    arquivoPlaca = arquivo
    runCatching {
      val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo)
      cameraPlaca.launch(uri)
    }.onFailure { erro ->
      arquivoPlaca = null
      arquivo.delete()
      Erros.registrar(contexto, "Abrir câmera para placa", erro)
      Toast.makeText(contexto, "Não foi possível abrir a câmera", Toast.LENGTH_LONG).show()
    }
  }

  fun removerFotoSalva(foto: Foto) {
    escopo.launch {
      val caminho = withContext(Dispatchers.IO) { banco.excluirFoto(foto.id) }
      if (caminho != null) withContext(Dispatchers.IO) { Fotos.excluir(contexto, caminho) }
      fotosSalvas = withContext(Dispatchers.IO) { banco.listarFotos(id) }
    }
  }

  fun removerFotoNova(foto: FotoNova) {
    fotosNovas = fotosNovas - foto
    Fotos.excluir(contexto, foto.nome)
  }

  fun moverFotoSalva(foto: Foto, passo: Int) {
    val daCategoria = fotosSalvas.filter { it.categoria == foto.categoria }
    val indice = daCategoria.indexOfFirst { it.id == foto.id }
    val alvo = daCategoria.getOrNull(indice + passo) ?: return
    escopo.launch {
      withContext(Dispatchers.IO) {
        banco.atualizarFoto(foto.copy(ordem = alvo.ordem))
        banco.atualizarFoto(alvo.copy(ordem = foto.ordem))
      }
      fotosSalvas = withContext(Dispatchers.IO) { banco.listarFotos(id) }
    }
  }

  fun moverFotoNova(foto: FotoNova, passo: Int) {
    val daCategoria = fotosNovas.filter { it.categoria == foto.categoria }
    val indice = daCategoria.indexOfFirst { it.nome == foto.nome }
    val alvo = daCategoria.getOrNull(indice + passo) ?: return
    val lista = fotosNovas.toMutableList()
    val posicaoAtual = lista.indexOfFirst { it.nome == foto.nome }
    val posicaoAlvo = lista.indexOfFirst { it.nome == alvo.nome }
    if (posicaoAtual < 0 || posicaoAlvo < 0) return
    lista[posicaoAtual] = alvo
    lista[posicaoAlvo] = foto
    fotosNovas = lista
  }

  fun salvar() {
    if (importandoFotos) {
      Toast.makeText(contexto, "Conclua a importação das fotos antes de salvar", Toast.LENGTH_SHORT).show()
      return
    }
    val placa = atendimento.placa.trim().uppercase()
    if (atendimento.segmento == SegmentoAtendimento.AUTOMOTIVO) {
      if (atendimento.modelo.trim().isBlank()) {
        Toast.makeText(contexto, "Informe o modelo do veículo", Toast.LENGTH_SHORT).show()
        return
      }
    } else {
      val temDadosResidenciais = listOf(
        atendimento.tipoLocal,
        atendimento.ambientePorta,
        atendimento.marcaFechadura,
        atendimento.modeloFechadura,
      ).any { it.trim().isNotBlank() }
      if (!temDadosResidenciais) {
        Toast.makeText(contexto, "Informe o tipo de local ou fechadura", Toast.LENGTH_SHORT).show()
        return
      }
    }
    if (salvando) return
    salvando = true
    escopo.launch {
      val idSalvo = withContext(Dispatchers.IO) {
        val idGravado = banco.salvar(atendimento.copy(placa = placa, modelo = atendimento.modelo.trim()))
        fotosNovas.forEach { banco.adicionarFoto(idGravado, it.nome, it.categoria, it.legenda, it.anotacoes) }
        idGravado
      }
      salvando = false
      onSalvo(idSalvo)
    }
  }

  fun aplicarTrocaDeSegmento(novo: SegmentoAtendimento) {
    val tipoPadrao = TipoServico.padraoDoSegmento(novo)
    val tiposFiltrados = atendimento.tiposServico.filter { it in TipoServico.doSegmento(novo) }.toSet()
    val novosTipos = tiposFiltrados.ifEmpty { setOf(tipoPadrao) }
    atendimento = atendimento.copy(
      segmento = novo,
      tipoServico = TipoServico.entries.first { it in novosTipos },
      tiposServico = novosTipos,
    )
  }

  fun solicitarTrocaSegmento(novo: SegmentoAtendimento) {
    if (novo == atendimento.segmento) return
    val preenchidoAtual = if (atendimento.segmento == SegmentoAtendimento.AUTOMOTIVO) {
      listOf(atendimento.placa, atendimento.marca, atendimento.modelo, atendimento.ano, atendimento.chassi).any { it.isNotBlank() }
    } else {
      listOf(atendimento.tipoLocal, atendimento.ambientePorta, atendimento.marcaFechadura, atendimento.modeloFechadura, atendimento.tipoCilindro, atendimento.enderecoLocal).any { it.isNotBlank() }
    }
    if (preenchidoAtual) {
      segmentoPendenteTroca = novo
    } else {
      aplicarTrocaDeSegmento(novo)
    }
  }

  fun sairDescartandoNovas() {
    if (importandoFotos) {
      Toast.makeText(contexto, "Conclua a importação das fotos antes de sair", Toast.LENGTH_SHORT).show()
      return
    }
    fotosNovas.forEach { Fotos.excluir(contexto, it.nome) }
    onVoltar()
  }

  BackHandler { sairDescartandoNovas() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (id > 0) "Editar serviço" else "Novo serviço") },
        navigationIcon = {
          BotaoVoltar { sairDescartandoNovas() }
        },
        actions = {
          Button(onClick = { salvar() }, enabled = !salvando && !importandoFotos, modifier = Modifier.padding(end = 12.dp)) { Text(if (salvando) "Salvando…" else "Salvar") }
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

    val residencial = atendimento.segmento == SegmentoAtendimento.RESIDENCIAL

    Column(
      modifier = Modifier
        .conteudoResponsivo().imePadding()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Tipo de atendimento", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SegmentoAtendimento.entries.forEach { seg ->
              FilterChip(
                selected = atendimento.segmento == seg,
                onClick = { solicitarTrocaSegmento(seg) },
                label = { Text(seg.rotulo) },
              )
            }
          }
        }
      }

      if (!residencial) {
        SecaoFormulario("01", "Veículo", "Informe o modelo do carro; a placa é opcional.")
        CampoTexto(
          rotulo = "Modelo *",
          valor = atendimento.modelo,
          aoMudar = { atendimento = atendimento.copy(modelo = it) },
          sugestoes = sugestoesModelo,
          dica = "Gol 1.6",
          modificador = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          CampoTexto(
            rotulo = "Marca",
            valor = atendimento.marca,
            aoMudar = { atendimento = atendimento.copy(marca = it) },
            sugestoes = sugestoesMarca,
            dica = "Volkswagen",
            modificador = Modifier.weight(1f),
          )
          OutlinedTextField(
            value = atendimento.ano,
            onValueChange = { atendimento = atendimento.copy(ano = it.filter { letra -> letra.isDigit() || letra == '/' }) },
            shape = MaterialTheme.shapes.medium,
            label = { Text("Ano") },
            placeholder = { Text("2018") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp),
          )
        }

        OutlinedButton(onClick = { mostrarCatalogo = true }, modifier = Modifier.fillMaxWidth()) {
          Text("Buscar marca, modelo e ano no catálogo")
        }

        CampoTexto(
          rotulo = "Placa (opcional)",
          valor = atendimento.placa,
          aoMudar = { texto ->
            atendimento = atendimento.copy(placa = texto.uppercase().filter { it.isLetterOrDigit() }.take(10))
          },
          dica = "ABC1D23",
          estiloTexto = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
          modificador = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { lerPlaca() }, enabled = !lendoPlaca) {
          Text(if (lendoPlaca) "Lendo placa…" else "Ler placa pela câmera")
        }

        if (historico.isNotEmpty()) {
          Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "Você já trabalhou neste carro: ${historico.size} serviço(s)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
              historico.take(3).forEach { anterior ->
                Spacer(Modifier.height(6.dp))
                Text(
                  text = "${formatarData(anterior.dataServico)} · ${anterior.tiposEmTexto}",
                  style = MaterialTheme.typography.bodySmall,
                )
                if (anterior.solucao.isNotBlank()) {
                  Text(text = anterior.solucao, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
              }
            }
          }
        }

        OutlinedTextField(
          value = atendimento.chassi,
          onValueChange = { atendimento = atendimento.copy(chassi = it.uppercase().take(17)) },
          shape = MaterialTheme.shapes.medium,
          label = { Text("Chassi (opcional)") },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
          modifier = Modifier.fillMaxWidth(),
        )
      } else {
        SecaoFormulario("01", "Local e fechadura", "Identifique o imóvel, o acesso e a fechadura atendida.")
        CampoTexto(
          rotulo = "Tipo de local *",
          valor = atendimento.tipoLocal,
          aoMudar = { atendimento = atendimento.copy(tipoLocal = it) },
          sugestoes = sugestoesTipoLocal,
          dica = "Casa, Apartamento, Comércio, Portão...",
          modificador = Modifier.fillMaxWidth(),
        )
        CampoTexto(
          rotulo = "Ambiente / porta",
          valor = atendimento.ambientePorta,
          aoMudar = { atendimento = atendimento.copy(ambientePorta = it) },
          sugestoes = sugestoesAmbientePorta,
          dica = "Porta principal, Portão, Quarto...",
          modificador = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          CampoTexto(
            rotulo = "Marca da fechadura",
            valor = atendimento.marcaFechadura,
            aoMudar = { atendimento = atendimento.copy(marcaFechadura = it) },
            sugestoes = sugestoesMarcaFechadura,
            dica = "Papaiz, Pado, Stam...",
            modificador = Modifier.weight(1f),
          )
          CampoTexto(
            rotulo = "Modelo da fechadura",
            valor = atendimento.modeloFechadura,
            aoMudar = { atendimento = atendimento.copy(modeloFechadura = it) },
            sugestoes = sugestoesModeloFechadura,
            dica = "Tetra 1000, Rolete...",
            modificador = Modifier.weight(1f),
          )
        }
        CampoTexto(
          rotulo = "Cilindro / tipo de chave",
          valor = atendimento.tipoCilindro,
          aoMudar = { atendimento = atendimento.copy(tipoCilindro = it) },
          sugestoes = sugestoesTipoCilindro,
          dica = "Yale / simples, Tetra, Multiponto, Gorje...",
          modificador = Modifier.fillMaxWidth(),
        )
        CampoTexto(
          rotulo = "Endereço / referência (opcional)",
          valor = atendimento.enderecoLocal,
          aoMudar = { atendimento = atendimento.copy(enderecoLocal = it) },
          dica = "Rua, condomínio ou ponto de referência",
          modificador = Modifier.fillMaxWidth(),
        )
      }

      OutlinedButton(onClick = {
        val calendario = Calendar.getInstance().apply { timeInMillis = atendimento.dataServico }
        DatePickerDialog(
          contexto,
          { _, ano, mes, dia ->
            val escolhido = Calendar.getInstance().apply {
              timeInMillis = atendimento.dataServico
              set(ano, mes, dia)
            }
            atendimento = atendimento.copy(dataServico = escolhido.timeInMillis)
          },
          calendario.get(Calendar.YEAR),
          calendario.get(Calendar.MONTH),
          calendario.get(Calendar.DAY_OF_MONTH),
        ).show()
      }, modifier = Modifier.fillMaxWidth()) {
        Text("Data do serviço: ${formatarData(atendimento.dataServico)}")
      }

      SecaoFormulario("02", "Atendimento", "Registre o problema e a solução aplicada.")
      Text("Serviços realizados", style = MaterialTheme.typography.titleSmall)
      Text("Marque todas as atividades realizadas neste atendimento.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TipoServico.doSegmento(atendimento.segmento).forEach { tipo ->
          FilterChip(
            selected = tipo in atendimento.tiposServico,
            onClick = {
              val novos = if (tipo in atendimento.tiposServico) {
                atendimento.tiposServico - tipo
              } else {
                atendimento.tiposServico + tipo
              }
              if (novos.isEmpty()) {
                Toast.makeText(contexto, "Selecione pelo menos um serviço", Toast.LENGTH_SHORT).show()
              } else {
                atendimento = atendimento.copy(
                  tipoServico = TipoServico.entries.first { it in novos },
                  tiposServico = novos,
                )
              }
            },
            label = { Text(tipo.rotulo) },
          )
        }
      }

      CampoTexto(
        rotulo = "Defeito encontrado",
        valor = atendimento.defeito,
        aoMudar = { atendimento = atendimento.copy(defeito = it) },
        sugestoes = sugestoesDefeito,
        dica = if (residencial) "Ex.: chave quebrada no cilindro, lingueta emperrada" else "Ex.: chave não reconhecida, imobilizador travado",
        linhaUnica = false,
        minimoLinhas = 3,
        teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modificador = Modifier.fillMaxWidth(),
      )

      CampoTexto(
        rotulo = "Solução aplicada",
        valor = atendimento.solucao,
        aoMudar = { atendimento = atendimento.copy(solucao = it) },
        sugestoes = (sugestoesSolucaoDefeito + sugestoesSolucaoBase).distinct(),
        dica = if (residencial) "Ex.: extração do fragmento, troca do segredo e 2 cópias" else "Ex.: reprogramei o transponder e sincronizei o telecomando",
        linhaUnica = false,
        minimoLinhas = 3,
        teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modificador = Modifier.fillMaxWidth(),
      )

      CampoTexto(
        rotulo = "Ferramenta / equipamento usado",
        valor = atendimento.ferramenta,
        aoMudar = { atendimento = atendimento.copy(ferramenta = it) },
        dica = if (residencial) "Micha, extrator, máquina copiadora..." else "VVDI, KD-X2, Autel...",
        modificador = Modifier.fillMaxWidth(),
      )

      SecaoFormulario("03", "Cliente e pagamento", "Informações de contato e cobrança.")
      CampoTexto(
        rotulo = "Valor cobrado (opcional)",
        valor = valorTexto,
        aoMudar = {
          valorTexto = it
          atendimento = atendimento.copy(valorCentavos = paraCentavos(it))
        },
        dica = "150,00",
        teclado = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modificador = Modifier.fillMaxWidth(),
      )

      CampoTexto(
        rotulo = "Cliente",
        valor = atendimento.clienteNome,
        aoMudar = { atendimento = atendimento.copy(clienteNome = it) },
        dica = if (residencial) "Nome do morador ou responsável" else "Nome do dono do carro",
        modificador = Modifier.fillMaxWidth(),
      )

      CampoTexto(
        rotulo = "Telefone do cliente (opcional)",
        valor = atendimento.clienteTelefone,
        aoMudar = { atendimento = atendimento.copy(clienteTelefone = it) },
        dica = "(11) 99999-0000",
        teclado = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modificador = Modifier.fillMaxWidth(),
      )

      Text("Pagamento", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = atendimento.pago,
          onClick = { atendimento = atendimento.copy(pago = true) },
          label = { Text("Pago") },
        )
        FilterChip(
          selected = !atendimento.pago,
          onClick = { atendimento = atendimento.copy(pago = false) },
          label = { Text("Pendente") },
        )
      }

      SecaoFormulario("04", "Memória técnica", "Guarde os detalhes para o próximo atendimento.")
      TituloSecao("Abertura")
      CampoTexto(
        rotulo = if (residencial) "Como se abre esta fechadura / observações" else "Como se abre este carro",
        valor = atendimento.abertura,
        aoMudar = { atendimento = atendimento.copy(abertura = it) },
        sugestoes = sugestoesAbertura,
        dica = if (residencial) "Ex.: micha multiponto sentido anti-horário" else "Ex.: puxar a maçaneta e usar cunha no canto superior",
        linhaUnica = false,
        minimoLinhas = 2,
        teclado = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modificador = Modifier.fillMaxWidth(),
      )

      TituloSecao("Códigos")
      CampoTexto(
        rotulo = "Código de chave",
        valor = atendimento.codigoChave,
        aoMudar = {
          atendimento = atendimento.copy(codigoChave = it.uppercase().filter { letra -> letra.isLetterOrDigit() || letra == '-' })
        },
        dica = "Corte / segredo / referência",
        estiloTexto = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modificador = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
          onClick = { copiarTexto(contexto, "Código de chave", atendimento.codigoChave) },
          enabled = atendimento.codigoChave.isNotBlank(),
        ) { Text("Copiar código de chave") }
      }
      CampoTexto(
        rotulo = if (residencial) "Senha / configuração digital" else "Código de configuração",
        valor = atendimento.codigoConfiguracao,
        aoMudar = { atendimento = atendimento.copy(codigoConfiguracao = it) },
        dica = if (residencial) "Código mestre / TAG / observação" else "Imobilizador / PIN",
        estiloTexto = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modificador = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
          onClick = { copiarTexto(contexto, "Código de configuração", atendimento.codigoConfiguracao) },
          enabled = atendimento.codigoConfiguracao.isNotBlank(),
        ) { Text("Copiar código de configuração") }
      }

      if (!residencial) {
        CampoTexto(
          rotulo = "Chip / Transponder",
          valor = atendimento.chipTransponder,
          aoMudar = { atendimento = atendimento.copy(chipTransponder = it) },
          dica = "Ex.: ID46, PCF7936, 4D-63",
          estiloTexto = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
          modificador = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(
            onClick = { copiarTexto(contexto, "Chip / Transponder", atendimento.chipTransponder) },
            enabled = atendimento.chipTransponder.isNotBlank(),
          ) { Text("Copiar chip / transponder") }
        }
      }

      SecaoFormulario("05", "Registro em fotos", "Documente o serviço e o passo a passo (toque em Setas para indicar componentes).")
      TituloSecao(if (residencial) "Fotos do local e fechadura" else "Fotos do carro")
      SecaoDeFotos(
        descricao = if (residencial) "Fotos da porta, fechadura, cilindro ou defeito." else "Fotos do veículo, do painel, do defeito.",
        categoria = CategoriaFoto.CARRO,
        fotosSalvas = fotosSalvas.filter { it.categoria == CategoriaFoto.CARRO },
        fotosNovas = fotosNovas.filter { it.categoria == CategoriaFoto.CARRO },
        emImportacao = importandoFotos,
        onTirarFoto = { tirarFoto(CategoriaFoto.CARRO) },
        onGaleria = { escolherDaGaleria(CategoriaFoto.CARRO) },
        onRemoverSalva = { removerFotoSalva(it) },
        onRemoverNova = { removerFotoNova(it) },
        onEditarLegendaSalva = { fotoEditando = it },
        onEditarLegendaNova = { novaEditando = it },
        onAnotarSalva = { fotoAnotandoSalva = it },
        onAnotarNova = { fotoAnotandoNova = it },
        onMoverSalva = { foto, passo -> moverFotoSalva(foto, passo) },
        onMoverNova = { foto, passo -> moverFotoNova(foto, passo) },
      )

      TituloSecao("Como se faz")
      SecaoDeFotos(
        descricao = "Passo a passo do serviço. Use as setas para ordenar ou anotar na imagem.",
        categoria = CategoriaFoto.PROCEDIMENTO,
        fotosSalvas = fotosSalvas.filter { it.categoria == CategoriaFoto.PROCEDIMENTO },
        fotosNovas = fotosNovas.filter { it.categoria == CategoriaFoto.PROCEDIMENTO },
        emImportacao = importandoFotos,
        onTirarFoto = { tirarFoto(CategoriaFoto.PROCEDIMENTO) },
        onGaleria = { escolherDaGaleria(CategoriaFoto.PROCEDIMENTO) },
        onRemoverSalva = { removerFotoSalva(it) },
        onRemoverNova = { removerFotoNova(it) },
        onEditarLegendaSalva = { fotoEditando = it },
        onEditarLegendaNova = { novaEditando = it },
        onAnotarSalva = { fotoAnotandoSalva = it },
        onAnotarNova = { fotoAnotandoNova = it },
        onMoverSalva = { foto, passo -> moverFotoSalva(foto, passo) },
        onMoverNova = { foto, passo -> moverFotoNova(foto, passo) },
      )

      Spacer(Modifier.height(8.dp))
      Button(onClick = { salvar() }, enabled = !salvando && !importandoFotos, modifier = Modifier.fillMaxWidth()) {
        Text(if (salvando) "Salvando..." else "Salvar serviço")
      }
      Spacer(Modifier.height(24.dp))
    }
  }

  segmentoPendenteTroca?.let { novoSegmento ->
    AlertDialog(
      onDismissRequest = { segmentoPendenteTroca = null },
      title = { Text("Mudar para ${novoSegmento.rotulo}?") },
      text = {
        Text("Os tipos de serviço selecionados serão ajustados para o segmento ${novoSegmento.rotulo.lowercase()}.")
      },
      confirmButton = {
        TextButton(onClick = {
          segmentoPendenteTroca = null
          aplicarTrocaDeSegmento(novoSegmento)
        }) { Text("Mudar") }
      },
      dismissButton = {
        TextButton(onClick = { segmentoPendenteTroca = null }) { Text("Cancelar") }
      },
    )
  }

  fotoEditando?.let { foto ->
    DialogoLegenda(
      titulo = "Legenda da foto",
      legendaAtual = foto.legenda,
      onSalvar = { legenda ->
        fotoEditando = null
        escopo.launch {
          withContext(Dispatchers.IO) { banco.atualizarFoto(foto.copy(legenda = legenda)) }
          fotosSalvas = withContext(Dispatchers.IO) { banco.listarFotos(id) }
        }
      },
      onCancelar = { fotoEditando = null },
    )
  }

  novaEditando?.let { foto ->
    DialogoLegenda(
      titulo = "Legenda da foto",
      legendaAtual = foto.legenda,
      onSalvar = { legenda ->
        fotosNovas = fotosNovas.map { if (it.nome == foto.nome) it.copy(legenda = legenda) else it }
        novaEditando = null
      },
      onCancelar = { novaEditando = null },
    )
  }

  fotoAnotandoSalva?.let { foto ->
    EditorFotoDialog(
      caminho = foto.caminho,
      anotacoesIniciais = foto.anotacoes,
      onSalvar = { jsonAnotacoes ->
        fotoAnotandoSalva = null
        escopo.launch {
          withContext(Dispatchers.IO) { banco.atualizarFoto(foto.copy(anotacoes = jsonAnotacoes)) }
          fotosSalvas = withContext(Dispatchers.IO) { banco.listarFotos(id) }
        }
      },
      onCancelar = { fotoAnotandoSalva = null },
    )
  }

  fotoAnotandoNova?.let { foto ->
    EditorFotoDialog(
      caminho = foto.nome,
      anotacoesIniciais = foto.anotacoes,
      onSalvar = { jsonAnotacoes ->
        fotosNovas = fotosNovas.map { if (it.nome == foto.nome) it.copy(anotacoes = jsonAnotacoes) else it }
        fotoAnotandoNova = null
      },
      onCancelar = { fotoAnotandoNova = null },
    )
  }

  if (mostrarCatalogo) {
    DialogoCatalogoFipe(
      aoEscolher = { marca, modelo, ano ->
        atendimento = atendimento.copy(
          marca = marca,
          modelo = modelo,
          ano = ano.filter { it.isDigit() },
        )
        mostrarCatalogo = false
      },
      onFechar = { mostrarCatalogo = false },
    )
  }

  if (dialogoPlaca) {
    var texto by remember(candidatosPlaca) {
      mutableStateOf(candidatosPlaca.firstOrNull().orEmpty())
    }
    AlertDialog(
      onDismissRequest = { dialogoPlaca = false },
      title = { Text("Placa encontrada") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Confira o texto reconhecido antes de usar:", style = MaterialTheme.typography.bodyMedium)
          OutlinedTextField(
            value = texto,
            onValueChange = { digitado -> texto = Placa.normalizar(digitado).take(7) },
            label = { Text("Placa") },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
          )
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            candidatosPlaca.forEach { candidato ->
              SuggestionChip(onClick = { texto = candidato }, label = { Text(candidato) })
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = {
          val placa = Placa.normalizar(texto)
          if (!Placa.valida(placa)) {
            Toast.makeText(contexto, "Placa incompleta. Confira as 7 posições.", Toast.LENGTH_SHORT).show()
          } else {
            atendimento = atendimento.copy(placa = placa)
            dialogoPlaca = false
          }
        }) { Text("Usar esta placa") }
      },
      dismissButton = {
        TextButton(onClick = { dialogoPlaca = false }) { Text("Cancelar") }
      },
    )
  }
}

@Composable
private fun SecaoDeFotos(
  descricao: String,
  categoria: CategoriaFoto,
  fotosSalvas: List<Foto>,
  fotosNovas: List<FotoNova>,
  emImportacao: Boolean,
  onTirarFoto: () -> Unit,
  onGaleria: () -> Unit,
  onRemoverSalva: (Foto) -> Unit,
  onRemoverNova: (FotoNova) -> Unit,
  onEditarLegendaSalva: (Foto) -> Unit,
  onEditarLegendaNova: (FotoNova) -> Unit,
  onAnotarSalva: (Foto) -> Unit,
  onAnotarNova: (FotoNova) -> Unit,
  onMoverSalva: (Foto, Int) -> Unit,
  onMoverNova: (FotoNova, Int) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (fotosSalvas.isEmpty() && fotosNovas.isEmpty()) {
      Text(
        text = if (categoria == CategoriaFoto.CARRO) "Nenhuma foto adicionada ainda."
        else "Nenhuma foto de procedimento ainda.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    } else {
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
      ) {
        items(fotosSalvas, key = { "s${it.id}" }) { foto ->
          CartaoFotoLegenda(
            caminho = foto.caminho,
            legenda = foto.legenda,
            indice = fotosSalvas.indexOf(foto) + 1,
            onEditarLegenda = { onEditarLegendaSalva(foto) },
            onAnotar = { onAnotarSalva(foto) },
            onEsquerda = { onMoverSalva(foto, -1) },
            onDireita = { onMoverSalva(foto, 1) },
            onRemover = { onRemoverSalva(foto) },
          )
        }
        items(fotosNovas, key = { "n${it.nome}" }) { foto ->
          CartaoFotoLegenda(
            caminho = foto.nome,
            legenda = foto.legenda,
            indice = fotosNovas.indexOf(foto) + 1,
            onEditarLegenda = { onEditarLegendaNova(foto) },
            onAnotar = { onAnotarNova(foto) },
            onEsquerda = { onMoverNova(foto, -1) },
            onDireita = { onMoverNova(foto, 1) },
            onRemover = { onRemoverNova(foto) },
          )
        }
      }
    }
    if (emImportacao) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text("Importando fotos...", style = MaterialTheme.typography.bodySmall)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      OutlinedButton(onClick = onTirarFoto, enabled = !emImportacao) { Text("Tirar foto") }
      OutlinedButton(onClick = onGaleria, enabled = !emImportacao) { Text("Galeria (várias fotos)") }
    }
  }
}

@Composable
private fun CartaoFotoLegenda(
  caminho: String,
  legenda: String,
  indice: Int,
  onEditarLegenda: () -> Unit,
  onAnotar: () -> Unit,
  onEsquerda: () -> Unit,
  onDireita: () -> Unit,
  onRemover: () -> Unit,
) {
  Column(
    modifier = Modifier.width(156.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(110.dp)
        .clip(RoundedCornerShape(12.dp))
        .clickable(onClick = onEditarLegenda),
    ) {
      ImagemLocal(caminho = caminho, modificador = Modifier.fillMaxSize(), ladoMaximo = 400)
      Text(
        text = indice.toString(),
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
          .padding(6.dp)
          .clip(RoundedCornerShape(6.dp))
          .background(MaterialTheme.colorScheme.secondaryContainer)
          .padding(horizontal = 6.dp, vertical = 2.dp),
      )
    }
    Spacer(Modifier.height(4.dp))
    Text(
      text = legenda.ifBlank { "Sem legenda" },
      style = MaterialTheme.typography.bodySmall,
      color = if (legenda.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
      else MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.height(32.dp).clickable(onClick = onEditarLegenda),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
      TextButton(onClick = onAnotar, contentPadding = PaddingValues(4.dp)) { Text("↗ Setas", fontSize = 12.sp) }
      TextButton(onClick = onEsquerda, contentPadding = PaddingValues(4.dp)) { Text("←", fontSize = 14.sp) }
      TextButton(onClick = onDireita, contentPadding = PaddingValues(4.dp)) { Text("→", fontSize = 14.sp) }
      TextButton(onClick = onRemover, contentPadding = PaddingValues(4.dp)) {
        Text("✕", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
      }
    }
  }
}

@Composable
private fun DialogoLegenda(
  titulo: String,
  legendaAtual: String,
  onSalvar: (String) -> Unit,
  onCancelar: () -> Unit,
) {
  var texto by remember(legendaAtual) { mutableStateOf(legendaAtual) }
  AlertDialog(
    onDismissRequest = onCancelar,
    title = { Text(titulo) },
    text = {
      OutlinedTextField(
        value = texto,
        onValueChange = { texto = it },
        label = { Text("Legenda (opcional)") },
        placeholder = { Text("Ex.: 1. tirar a forração") },
        singleLine = false,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
      )
    },
    confirmButton = { TextButton(onClick = { onSalvar(texto.trim()) }) { Text("Salvar") } },
    dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar") } },
  )
}
