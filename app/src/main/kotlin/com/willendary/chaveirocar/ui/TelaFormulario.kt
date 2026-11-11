package com.willendary.chaveirocar.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.Foto
import com.willendary.chaveirocar.modelo.TipoServico
import com.willendary.chaveirocar.util.formatarData
import com.willendary.chaveirocar.util.formatarValorEditavel
import com.willendary.chaveirocar.util.paraCentavos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

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
  var fotosNovas by remember { mutableStateOf<List<String>>(emptyList()) }
  var valorTexto by remember { mutableStateOf(formatarValorEditavel((base ?: Atendimento()).valorCentavos)) }
  var carregado by remember { mutableStateOf(id == 0L) }
  var historico by remember { mutableStateOf<List<Atendimento>>(emptyList()) }
  var arquivoCamera by remember { mutableStateOf<File?>(null) }
  var salvando by remember { mutableStateOf(false) }

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

  val placaParaHistorico = atendimento.placa.trim().uppercase()
  LaunchedEffect(placaParaHistorico) {
    historico = if (placaParaHistorico.length >= 7) {
      withContext(Dispatchers.IO) { banco.servicosDaPlaca(placaParaHistorico, id) }
    } else {
      emptyList()
    }
  }

  fun anexarFoto(nome: String) {
    if (id > 0) {
      escopo.launch {
        withContext(Dispatchers.IO) { banco.adicionarFoto(id, nome) }
        fotosSalvas = withContext(Dispatchers.IO) { banco.listarFotos(id) }
      }
    } else {
      fotosNovas = fotosNovas + nome
    }
  }

  val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { sucesso ->
    val arquivo = arquivoCamera
    arquivoCamera = null
    if (sucesso && arquivo != null && arquivo.exists() && arquivo.length() > 0) {
      escopo.launch {
        val nome = withContext(Dispatchers.IO) { Fotos.salvarDeArquivo(contexto, arquivo) }
        arquivo.delete()
        anexarFoto(nome)
      }
    } else {
      arquivo?.delete()
    }
  }

  val galeria = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    if (uri != null) {
      escopo.launch {
        val nome = withContext(Dispatchers.IO) {
          contexto.contentResolver.openInputStream(uri)?.use { Fotos.salvarDeFluxo(contexto, it) }
        }
        if (nome != null) anexarFoto(nome)
      }
    }
  }

  fun tirarFoto() {
    val arquivo = File(contexto.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    arquivoCamera = arquivo
    val uri = FileProvider.getUriForFile(contexto, "${contexto.packageName}.arquivos", arquivo)
    camera.launch(uri)
  }

  fun removerFotoSalva(foto: Foto) {
    escopo.launch {
      val caminho = withContext(Dispatchers.IO) { banco.excluirFoto(foto.id) }
      if (caminho != null) withContext(Dispatchers.IO) { Fotos.excluir(contexto, caminho) }
      fotosSalvas = withContext(Dispatchers.IO) { banco.listarFotos(id) }
    }
  }

  fun removerFotoNova(nome: String) {
    fotosNovas = fotosNovas - nome
    Fotos.excluir(contexto, nome)
  }

  fun salvar() {
    val placa = atendimento.placa.trim().uppercase()
    if (placa.isBlank()) {
      Toast.makeText(contexto, "Informe a placa do carro", Toast.LENGTH_SHORT).show()
      return
    }
    if (salvando) return
    salvando = true
    escopo.launch {
      val idSalvo = withContext(Dispatchers.IO) {
        val idGravado = banco.salvar(atendimento.copy(placa = placa))
        fotosNovas.forEach { banco.adicionarFoto(idGravado, it) }
        idGravado
      }
      salvando = false
      onSalvo(idSalvo)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(if (id > 0) "Editar serviço" else "Novo serviço") },
        navigationIcon = {
          TextButton(onClick = {
            fotosNovas.forEach { Fotos.excluir(contexto, it) }
            onVoltar()
          }) { Text("Voltar") }
        },
        actions = {
          TextButton(onClick = { salvar() }, enabled = !salvando) { Text("Salvar", fontWeight = FontWeight.Bold) }
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
        .fillMaxSize()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedTextField(
        value = atendimento.placa,
        onValueChange = { texto ->
          atendimento = atendimento.copy(placa = texto.uppercase().filter { it.isLetterOrDigit() }.take(10))
        },
        label = { Text("Placa *") },
        placeholder = { Text("ABC1D23") },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
      )

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
                text = "${formatarData(anterior.dataServico)} · ${anterior.tipoServico.rotulo}",
                style = MaterialTheme.typography.bodySmall,
              )
              if (anterior.solucao.isNotBlank()) {
                Text(text = anterior.solucao, style = MaterialTheme.typography.bodySmall, maxLines = 2)
              }
            }
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = atendimento.marca,
          onValueChange = { atendimento = atendimento.copy(marca = it) },
          label = { Text("Marca") },
          placeholder = { Text("Volkswagen") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = atendimento.ano,
          onValueChange = { atendimento = atendimento.copy(ano = it.filter { letra -> letra.isDigit() || letra == '/' }) },
          label = { Text("Ano") },
          placeholder = { Text("2018") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.width(110.dp),
        )
      }

      OutlinedTextField(
        value = atendimento.modelo,
        onValueChange = { atendimento = atendimento.copy(modelo = it) },
        label = { Text("Modelo") },
        placeholder = { Text("Gol 1.6") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = atendimento.chassi,
        onValueChange = { atendimento = atendimento.copy(chassi = it.uppercase().take(17)) },
        label = { Text("Chassi (opcional)") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth(),
      )

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

      Text("Tipo de serviço", style = MaterialTheme.typography.titleSmall)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TipoServico.entries.forEach { tipo ->
          FilterChip(
            selected = atendimento.tipoServico == tipo,
            onClick = { atendimento = atendimento.copy(tipoServico = tipo) },
            label = { Text(tipo.rotulo) },
          )
        }
      }

      OutlinedTextField(
        value = atendimento.defeito,
        onValueChange = { atendimento = atendimento.copy(defeito = it) },
        label = { Text("Defeito encontrado") },
        placeholder = { Text("Ex.: chave não reconhecida, imobilizador travado") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      )

      OutlinedTextField(
        value = atendimento.solucao,
        onValueChange = { atendimento = atendimento.copy(solucao = it) },
        label = { Text("Solução aplicada") },
        placeholder = { Text("Ex.: reprogramei o transponder e sincronizei o telecomando") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
      )

      OutlinedTextField(
        value = atendimento.ferramenta,
        onValueChange = { atendimento = atendimento.copy(ferramenta = it) },
        label = { Text("Ferramenta / equipamento usado") },
        placeholder = { Text("VVDI, KD-X2, Autel...") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = valorTexto,
        onValueChange = {
          valorTexto = it
          atendimento = atendimento.copy(valorCentavos = paraCentavos(it))
        },
        label = { Text("Valor cobrado (opcional)") },
        prefix = { Text("R$ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
      )

      Text("Fotos", style = MaterialTheme.typography.titleSmall)
      if (fotosSalvas.isEmpty() && fotosNovas.isEmpty()) {
        Text(
          "Nenhuma foto. Foto ajuda a lembrar do carro e do serviço.",
          style = MaterialTheme.typography.bodySmall,
        )
      } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
          items(fotosSalvas, key = { "s${it.id}" }) { foto ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              ImagemLocal(
                caminho = foto.caminho,
                modificador = Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)),
              )
              TextButton(onClick = { removerFotoSalva(foto) }) { Text("Remover", fontSize = 11.sp) }
            }
          }
          items(fotosNovas, key = { "n$it" }) { nome ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              ImagemLocal(
                caminho = nome,
                modificador = Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)),
              )
              TextButton(onClick = { removerFotoNova(nome) }) { Text("Remover", fontSize = 11.sp) }
            }
          }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { tirarFoto() }) { Text("Tirar foto") }
        OutlinedButton(onClick = { galeria.launch("image/*") }) { Text("Escolher da galeria") }
      }

      Spacer(Modifier.height(8.dp))
      Button(onClick = { salvar() }, enabled = !salvando, modifier = Modifier.fillMaxWidth()) {
        Text(if (salvando) "Salvando..." else "Salvar serviço")
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}
