package com.willendary.chaveirocar.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Fotos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.CategoriaFoto
import com.willendary.chaveirocar.modelo.Foto
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.ui.theme.coresEstendidas
import com.willendary.chaveirocar.util.Compartilhamento
import com.willendary.chaveirocar.util.formatarData
import com.willendary.chaveirocar.util.formatarValor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaDetalhe(
  banco: BancoDeAtendimentos,
  id: Long,
  onEditar: (Long) -> Unit,
  onDuplicar: (Atendimento) -> Unit,
  onExcluido: () -> Unit,
  onVoltar: () -> Unit,
  onVerFicha: ((Atendimento) -> Unit)? = null,
) {
  val contexto = LocalContext.current
  val escopo = rememberCoroutineScope()
  val estendidas = MaterialTheme.coresEstendidas
  var atendimento by remember { mutableStateOf<Atendimento?>(null) }
  var fotos by remember { mutableStateOf<List<Foto>>(emptyList()) }
  var fotoAberta by remember { mutableStateOf<Foto?>(null) }
  var fotoEditandoSetas by remember { mutableStateOf<Foto?>(null) }
  var confirmandoExclusao by remember { mutableStateOf(false) }
  var gerandoPdf by remember { mutableStateOf(false) }

  LaunchedEffect(id) {
    withContext(Dispatchers.IO) {
      atendimento = banco.obter(id)
      fotos = banco.listarFotos(id)
    }
  }

  val registro = atendimento

  fun compartilhar() {
    if (registro == null) return
    Compartilhamento.compartilharTexto(
      contexto,
      Compartilhamento.textoDoAtendimento(registro),
    )
  }

  fun gerarPdf() {
    if (registro == null || gerandoPdf) return
    gerandoPdf = true
    escopo.launch {
      runCatching {
        val arquivo = withContext(Dispatchers.IO) {
          Compartilhamento.gerarPdf(
            contexto,
            registro,
            fotos.sortedBy { if (it.categoria == CategoriaFoto.CARRO) 0 else 1 },
          )
        }
        Compartilhamento.compartilharPdf(contexto, arquivo)
      }.onFailure {
        Toast.makeText(contexto, "Não foi possível gerar o PDF", Toast.LENGTH_SHORT).show()
      }
      gerandoPdf = false
    }
  }

  Scaffold(
    topBar = {
      Column {
        TopAppBar(
          title = { Text("Atendimento", style = MaterialTheme.typography.titleLarge) },
          navigationIcon = { BotaoVoltar(onVoltar) },
          actions = {
            IconButton(onClick = { compartilhar() }, enabled = atendimento != null) {
              Icon(Icons.Default.Share, "Compartilhar atendimento")
            }
            if (registro != null) {
              IconButton(onClick = { onEditar(registro.id) }) {
                Icon(Icons.Default.Edit, "Editar atendimento")
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
    if (registro == null) {
      Box(Modifier.fillMaxSize().padding(espacamento), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
      return@Scaffold
    }

    val residencial = registro.segmento == SegmentoAtendimento.RESIDENCIAL
    val temPlaca = !residencial && registro.placa.isNotBlank()
    val corArea = if (residencial) estendidas.residencial else MaterialTheme.colorScheme.primary
    val fundoArea = if (residencial) estendidas.residencialFundo else MaterialTheme.colorScheme.primaryContainer

    val tituloVeiculoOuLocal = if (residencial) {
      listOf(registro.tipoLocal, registro.ambientePorta).filter { it.isNotBlank() }.joinToString(" · ")
        .ifBlank { "Serviço residencial" }
    } else {
      listOf(registro.marca, registro.modelo, registro.ano).filter { it.isNotBlank() }.joinToString(" ")
        .ifBlank { "Veículo sem modelo" }
    }

    Column(
      modifier = Modifier
        .conteudoResponsivo()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // 1. Cartão Hero de Identificação + Financeiro
      CartaoSurface(raio = 22.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Selo(
              texto = registro.segmento.rotulo,
              tom = if (residencial) TomSelo.RESIDENCIAL else TomSelo.PRIMARIO,
            )
            Selo(
              texto = if (registro.pago) "Pago" else "Pagamento pendente",
              tom = if (registro.pago) TomSelo.SUCESSO else TomSelo.PENDENTE,
            )
          }

          if (temPlaca) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              PlacaMercosulBadge(
                placa = registro.placa,
                compacto = false,
                modificador = Modifier.width(156.dp),
              )
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                  text = tituloVeiculoOuLocal,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.ExtraBold,
                )
                Text(
                  text = formatarData(registro.dataServico),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(54.dp)
                  .clip(RoundedCornerShape(16.dp))
                  .background(fundoArea),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = if (residencial) Icons.Default.Home else IconesApp.Carro,
                  contentDescription = null,
                  tint = corArea,
                  modifier = Modifier.size(28.dp),
                )
              }
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                  text = tituloVeiculoOuLocal,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.ExtraBold,
                )
                Text(
                  text = "Serviço em ${formatarData(registro.dataServico)}",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            registro.tiposOrdenados.forEach { tipo ->
              Selo(texto = tipo.rotulo, tom = TomSelo.NEUTRO)
            }
          }

          // Metadados adicionais + Valor em destaque
          val detalhesExtras = buildList {
            if (!residencial && registro.chassi.isNotBlank()) add("Chassi: ${registro.chassi}")
            if (residencial) {
              val fech = listOf(registro.marcaFechadura, registro.modeloFechadura, registro.tipoCilindro)
                .filter { it.isNotBlank() }.joinToString(" · ")
              if (fech.isNotBlank()) add("Fechadura: $fech")
              if (registro.enderecoLocal.isNotBlank()) add("Endereço: ${registro.enderecoLocal}")
            }
            if (registro.atualizadoEm > registro.dataServico) {
              add("Atualizado em ${formatarData(registro.atualizadoEm)}")
            }
          }

          if (detalhesExtras.isNotEmpty() || registro.valorCentavos > 0) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                detalhesExtras.forEach { linha ->
                  Text(
                    text = linha,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              if (registro.valorCentavos > 0) {
                Column(horizontalAlignment = Alignment.End) {
                  Text(
                    text = "VALOR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                  )
                  Text(
                    text = formatarValor(registro.valorCentavos),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (registro.pago) estendidas.sucesso else estendidas.alerta,
                  )
                }
              }
            }
          }
        }
      }

      // 2. Grade de 4 Ações Rápidas de Bancada
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        BotaoAcaoIcone(
          icone = IconesApp.Mensagem,
          rotulo = "WhatsApp",
          corDestaque = estendidas.sucesso,
          fundoDestaque = estendidas.sucessoFundo,
          modificador = Modifier.weight(1f),
          onClick = { Compartilhamento.enviarReciboWhatsApp(contexto, registro) },
        )
        BotaoAcaoIcone(
          icone = IconesApp.Documento,
          rotulo = if (gerandoPdf) "Gerando…" else "PDF",
          corDestaque = MaterialTheme.colorScheme.primary,
          fundoDestaque = MaterialTheme.colorScheme.primaryContainer,
          habilitado = !gerandoPdf,
          modificador = Modifier.weight(1f),
          onClick = { gerarPdf() },
        )
        BotaoAcaoIcone(
          icone = Icons.Default.Edit,
          rotulo = "Editar",
          corDestaque = MaterialTheme.colorScheme.onSurface,
          fundoDestaque = MaterialTheme.colorScheme.surfaceVariant,
          modificador = Modifier.weight(1f),
          onClick = { onEditar(registro.id) },
        )
        if (onVerFicha != null) {
          BotaoAcaoIcone(
            icone = Icons.Default.Search,
            rotulo = "Manual",
            corDestaque = estendidas.residencial,
            fundoDestaque = estendidas.residencialFundo,
            modificador = Modifier.weight(1f),
            onClick = { onVerFicha(registro) },
          )
        } else {
          BotaoAcaoIcone(
            icone = IconesApp.Copiar,
            rotulo = "Recibo",
            corDestaque = estendidas.residencial,
            fundoDestaque = estendidas.residencialFundo,
            modificador = Modifier.weight(1f),
            onClick = { Compartilhamento.compartilharTexto(contexto, Compartilhamento.textoReciboCliente(registro)) },
          )
        }
      }

      // 3. Códigos de Bancada em Alto Contraste (1 toque para copiar)
      if (registro.codigoChave.isNotBlank() || registro.codigoConfiguracao.isNotBlank() || (!residencial && registro.chipTransponder.isNotBlank())) {
        TituloSecao(if (residencial) "Códigos e referências" else "Códigos do veículo")
        CartaoSurface(raio = 18.dp) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (registro.codigoChave.isNotBlank()) {
              CodigoCopiado(
                rotulo = "Código de chave",
                valor = registro.codigoChave,
                onCopiar = { copiarTexto(contexto, "Código de chave", registro.codigoChave) },
              )
            }
            if (registro.codigoConfiguracao.isNotBlank()) {
              CodigoCopiado(
                rotulo = if (residencial) "Senha / configuração" else "Código de configuração",
                valor = registro.codigoConfiguracao,
                onCopiar = { copiarTexto(contexto, "Código de configuração", registro.codigoConfiguracao) },
              )
            }
            if (!residencial && registro.chipTransponder.isNotBlank()) {
              CodigoCopiado(
                rotulo = "Chip / Transponder",
                valor = registro.chipTransponder,
                onCopiar = { copiarTexto(contexto, "Chip / Transponder", registro.chipTransponder) },
              )
            }
          }
        }
      }

      // 4. Técnica de Abertura em destaque
      if (registro.abertura.isNotBlank()) {
        TituloSecao("Técnica de abertura")
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = fundoArea,
          border = BorderStroke(1.dp, corArea.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
              modifier = Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(corArea),
            )
            Text(
              text = registro.abertura,
              modifier = Modifier.padding(16.dp),
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
        }
      }

      // 5. Diagnóstico e Solução
      if (registro.defeito.isNotBlank() || registro.solucao.isNotBlank() || registro.ferramenta.isNotBlank()) {
        TituloSecao("Diagnóstico e solução")
        CartaoSurface(raio = 18.dp) {
          Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (registro.defeito.isNotBlank()) {
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                  "DEFEITO ENCONTRADO",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  letterSpacing = 0.7.sp,
                )
                Text(registro.defeito, style = MaterialTheme.typography.bodyMedium)
              }
            }
            if (registro.defeito.isNotBlank() && registro.solucao.isNotBlank()) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            }
            if (registro.solucao.isNotBlank()) {
              Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                  "SOLUÇÃO APLICADA",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  letterSpacing = 0.7.sp,
                )
                Text(registro.solucao, style = MaterialTheme.typography.bodyMedium)
              }
            }
            if (registro.ferramenta.isNotBlank()) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Text(
                  "Ferramenta:",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Selo(texto = registro.ferramenta, tom = TomSelo.NEUTRO)
              }
            }
          }
        }
      }

      // 6. Cliente e Recibo
      if (registro.clienteNome.isNotBlank() || registro.clienteTelefone.isNotBlank()) {
        TituloSecao("Cliente")
        CartaoSurface(raio = 18.dp) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
            }
            Column(Modifier.weight(1f)) {
              if (registro.clienteNome.isNotBlank()) {
                Text(registro.clienteNome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
              }
              if (registro.clienteTelefone.isNotBlank()) {
                Text(
                  registro.clienteTelefone,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            OutlinedButton(
              onClick = { Compartilhamento.compartilharTexto(contexto, Compartilhamento.textoReciboCliente(registro)) },
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) { Text("Recibo", fontSize = 12.sp) }
          }
        }
      }

      // 7. Galerias de Fotos
      val fotosDoCarro = fotos.filter { it.categoria == CategoriaFoto.CARRO }
      val fotosDeProcedimento = fotos.filter { it.categoria == CategoriaFoto.PROCEDIMENTO }

      if (fotosDoCarro.isNotEmpty()) {
        TituloSecao(if (residencial) "Fotos do local e fechadura" else "Fotos do carro", detalhe = "${fotosDoCarro.size} foto(s)")
        GradeDeFotos(fotosDoCarro) { fotoAberta = it }
      }

      if (fotosDeProcedimento.isNotEmpty()) {
        TituloSecao("Como se faz", detalhe = "${fotosDeProcedimento.size} passo(s)")
        GradeDeFotos(fotosDeProcedimento, mostrarPasso = true) { fotoAberta = it }
      }

      // 8. Ações complementares
      Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
        if (onVerFicha != null) {
          OutlinedButton(
            onClick = { onVerFicha(registro) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
          ) {
            Text("Consultar ficha técnica e manual", fontWeight = FontWeight.SemiBold)
          }
        }
        Button(
          onClick = { onDuplicar(registro) },
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(14.dp),
        ) {
          Text(
            if (residencial) "Novo atendimento (mesmo local)" else "Novo atendimento (mesmo carro)",
            fontWeight = FontWeight.Bold,
          )
        }
        TextButton(
          onClick = { confirmandoExclusao = true },
          modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
          Icon(
            Icons.Default.Delete,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text("Excluir registro", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
        }
      }
      Spacer(Modifier.height(20.dp))
    }
  }

  // Lightbox com zoom por pinça
  val foto = fotoAberta
  if (foto != null) {
    Dialog(
      onDismissRequest = { fotoAberta = null },
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      var escala by remember { mutableStateOf(1f) }
      var offsetAtual by remember { mutableStateOf(Offset.Zero) }

      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color(0xFF070B14))
          .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
              escala = (escala * zoom).coerceIn(1f, 6f)
              offsetAtual = if (escala > 1f) {
                Offset(x = offsetAtual.x + pan.x, y = offsetAtual.y + pan.y)
              } else {
                Offset.Zero
              }
            }
          },
      ) {
        Box(
          modifier = Modifier.fillMaxSize().padding(16.dp),
          contentAlignment = Alignment.Center,
        ) {
          ImagemLocal(
            caminho = foto.caminho,
            ladoMaximo = 2400,
            modificador = Modifier
              .fillMaxWidth()
              .graphicsLayer(
                scaleX = escala,
                scaleY = escala,
                translationX = offsetAtual.x,
                translationY = offsetAtual.y,
              ),
          )
        }

        Surface(
          shape = RoundedCornerShape(16.dp),
          color = Color(0xD90F172A),
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .padding(14.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = if (escala > 1.05f) "Zoom ${(escala * 100).toInt()}% · arraste para mover"
              else "Use dois dedos para aproximar o detalhe",
              color = Color.White,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.weight(1f),
            )
            if (escala > 1.05f) {
              TextButton(onClick = { escala = 1f; offsetAtual = Offset.Zero }) {
                Text("Redefinir", color = Color(0xFF2DD4BF))
              }
            }
            TextButton(onClick = { fotoAberta = null }) {
              Text("Fechar", color = Color.White, fontWeight = FontWeight.Bold)
            }
          }
        }

        Box(
          modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(18.dp),
          contentAlignment = Alignment.Center,
        ) {
          Button(
            onClick = {
              fotoEditandoSetas = foto
              fotoAberta = null
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488)),
          ) {
            Text("↗ Anotar com setas / cortes", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }

  fotoEditandoSetas?.let { fotoEmEdicao ->
    EditorFotoDialog(
      caminho = fotoEmEdicao.caminho,
      anotacoesIniciais = fotoEmEdicao.anotacoes,
      onSalvar = { jsonAnotacoes ->
        fotoEditandoSetas = null
        escopo.launch {
          withContext(Dispatchers.IO) { banco.atualizarFoto(fotoEmEdicao.copy(anotacoes = jsonAnotacoes)) }
          fotos = withContext(Dispatchers.IO) { banco.listarFotos(id) }
        }
      },
      onCancelar = { fotoEditandoSetas = null },
    )
  }

  if (confirmandoExclusao && atendimento != null) {
    AlertDialog(
      onDismissRequest = { confirmandoExclusao = false },
      shape = RoundedCornerShape(22.dp),
      title = { Text("Excluir registro?", fontWeight = FontWeight.Bold) },
      text = { Text("O histórico e as fotos deste serviço serão apagados permanentemente.") },
      confirmButton = {
        Button(
          onClick = {
            confirmandoExclusao = false
            escopo.launch {
              val caminhos = withContext(Dispatchers.IO) { banco.excluir(id) }
              caminhos.forEach { Fotos.excluir(contexto, it) }
              onExcluido()
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          shape = RoundedCornerShape(12.dp),
        ) { Text("Excluir") }
      },
      dismissButton = {
        TextButton(onClick = { confirmandoExclusao = false }) { Text("Cancelar") }
      },
    )
  }
}

@Composable
private fun CodigoCopiado(rotulo: String, valor: String, onCopiar: () -> Unit) {
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
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = rotulo.uppercase(),
          style = MaterialTheme.typography.labelSmall,
          letterSpacing = 0.6.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = valor,
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.ExtraBold,
          fontSize = 21.sp,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      FilledTonalButton(
        onClick = onCopiar,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Icon(IconesApp.Copiar, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Copiar", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GradeDeFotos(
  fotos: List<Foto>,
  mostrarPasso: Boolean = false,
  aoAbrir: (Foto) -> Unit,
) {
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    fotos.forEachIndexed { indice, foto ->
      CartaoSurface(
        modificador = Modifier.width(152.dp),
        raio = 16.dp,
        onClick = { aoAbrir(foto) },
      ) {
        Column {
          Box {
            ImagemLocal(
              caminho = foto.caminho,
              ladoMaximo = 800,
              modificador = Modifier
                .fillMaxWidth()
                .height(138.dp),
            )
            if (mostrarPasso) {
              Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp),
              ) {
                Text(
                  text = "#${indice + 1}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                )
              }
            }
          }
          if (foto.legenda.isNotBlank()) {
            Text(
              text = foto.legenda,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
          }
        }
      }
    }
  }
}
