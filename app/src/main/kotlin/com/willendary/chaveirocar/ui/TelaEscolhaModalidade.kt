package com.willendary.chaveirocar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.modelo.SegmentoAtendimento
import com.willendary.chaveirocar.ui.theme.coresEstendidas
import com.willendary.chaveirocar.util.formatarValor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ResumoHubUi(
  val totalVeicular: Int = 0,
  val totalResidencial: Int = 0,
  val pendentesQtd: Int = 0,
  val pendentesCentavos: Long = 0L,
)

/**
 * Hub inicial após login/desbloqueio: resumo executivo + entrada direta
 * para a área Veicular ou Residencial.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TelaEscolhaModalidade(
  modulosAtivos: Set<SegmentoAtendimento>,
  statusAssinatura: String,
  sincronizando: Boolean = false,
  onSelecionarArea: (SegmentoAtendimento) -> Unit,
  onAbrirConsulta: (SegmentoAtendimento) -> Unit,
  onGerenciarPlano: () -> Unit,
  onAbrirAjustes: () -> Unit,
  banco: BancoDeAtendimentos? = null,
  versaoSincronia: Int = 0,
) {
  val estendidas = MaterialTheme.coresEstendidas
  val veicularAtivo = SegmentoAtendimento.AUTOMOTIVO in modulosAtivos
  val residencialAtivo = SegmentoAtendimento.RESIDENCIAL in modulosAtivos
  var resumo by remember { mutableStateOf(ResumoHubUi()) }

  LaunchedEffect(banco, versaoSincronia) {
    if (banco == null) return@LaunchedEffect
    resumo = withContext(Dispatchers.IO) {
      runCatching {
        val todos = banco.listar("")
        val veic = todos.count { it.segmento == SegmentoAtendimento.AUTOMOTIVO }
        val res = todos.count { it.segmento == SegmentoAtendimento.RESIDENCIAL }
        val pend = todos.filter { !it.pago }
        ResumoHubUi(
          totalVeicular = veic,
          totalResidencial = res,
          pendentesQtd = pend.size,
          pendentesCentavos = pend.sumOf { it.valorCentavos },
        )
      }.getOrDefault(ResumoHubUi())
    }
  }

  Scaffold(
    topBar = {
      Column {
        TopAppBar(
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              MarcaChaveiro(40.dp, elevacao = 1.dp)
              Column {
                Text("ChaveiroCar", style = MaterialTheme.typography.titleLarge)
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                  Box(
                    modifier = Modifier
                      .size(7.dp)
                      .clip(CircleShape)
                      .background(if (sincronizando) estendidas.alerta else estendidas.sucesso),
                  )
                  Text(
                    text = if (sincronizando) "Sincronizando nuvem..." else "Nuvem sincronizada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          },
          actions = {
            Surface(
              onClick = onAbrirAjustes,
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier.padding(end = 14.dp).size(40.dp),
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  Icons.Default.Settings,
                  contentDescription = "Ajustes e conta",
                  tint = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f))
      }
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    Column(
      modifier = Modifier
        .conteudoResponsivo()
        .fillMaxSize()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Cabeçalho editorial limpo (sem bloco escuro pesado)
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = "PAINEL OPERACIONAL",
          style = MaterialTheme.typography.labelSmall,
          letterSpacing = 1.2.sp,
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.ExtraBold,
        )
        Text(
          text = "Qual área você vai usar agora?",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          text = "Acesse atendimentos, fichário de procedimentos e manuais offline direto pela área.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Faixa executiva de KPIs ao vivo
      if (banco != null) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          KpiMiniCard(
            valor = resumo.totalVeicular.toString(),
            rotulo = "Veiculares",
            corDestaque = MaterialTheme.colorScheme.primary,
            fundoDestaque = MaterialTheme.colorScheme.primaryContainer,
            modificador = Modifier.weight(1f),
          )
          KpiMiniCard(
            valor = resumo.totalResidencial.toString(),
            rotulo = "Residenciais",
            corDestaque = estendidas.residencial,
            fundoDestaque = estendidas.residencialFundo,
            modificador = Modifier.weight(1f),
          )
          KpiMiniCard(
            valor = resumo.pendentesQtd.toString(),
            rotulo = "A receber",
            corDestaque = if (resumo.pendentesQtd > 0) estendidas.alerta else estendidas.sucesso,
            fundoDestaque = if (resumo.pendentesQtd > 0) estendidas.alertaFundo else estendidas.sucessoFundo,
            detalhe = if (resumo.pendentesCentavos > 0L) formatarValor(resumo.pendentesCentavos) else null,
            modificador = Modifier.weight(1f),
          )
        }
      }

      if (statusAssinatura != "ATIVA" || modulosAtivos.size < 2) {
        CartaoSurface(
          corFundo = MaterialTheme.colorScheme.surface,
          corBorda = estendidas.alerta.copy(alpha = 0.4f),
          raio = 16.dp,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text("Plano atual da empresa", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
              Text(
                text = descricaoStatusPlano(statusAssinatura, modulosAtivos),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            FilledTonalButton(
              onClick = onGerenciarPlano,
              shape = RoundedCornerShape(10.dp),
              contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) { Text("Ver plano") }
          }
        }
      }

      // Card Hero Veicular (assinatura Jade)
      CartaoModalidadeHub(
        titulo = "Veicular",
        subtitulo = "Automotivo · Transponders e codificadas",
        descricao = "Atendimentos por placa, leitura pela câmera, catálogo FIPE, códigos de chave/PIN e consulta técnica com manuais offline.",
        tags = listOf("Placas Mercosul", "Chips & PIN", "Catálogo FIPE", "Manuais"),
        icone = IconesApp.Carro,
        corAcento = MaterialTheme.colorScheme.primary,
        fundoAcento = MaterialTheme.colorScheme.primaryContainer,
        disponivel = veicularAtivo,
        tagCard = "card_modalidade_veicular",
        textoBotaoPrincipal = if (veicularAtivo) "Entrar em Veicular" else "Contratar / Ativar Veicular",
        textoBotaoConsulta = if (veicularAtivo) "Fichas técnicas e manuais" else null,
        onPrincipal = {
          if (veicularAtivo) onSelecionarArea(SegmentoAtendimento.AUTOMOTIVO) else onGerenciarPlano()
        },
        onConsulta = {
          if (veicularAtivo) onAbrirConsulta(SegmentoAtendimento.AUTOMOTIVO) else onGerenciarPlano()
        },
      )

      // Card Hero Residencial (assinatura Indigo)
      CartaoModalidadeHub(
        titulo = "Residencial",
        subtitulo = "Imóveis · Fechaduras e fechaduras digitais",
        descricao = "Aberturas residenciais, cilindros, travas tetra e multiponto, fechaduras digitais, controles de portão e histórico por local.",
        tags = listOf("Aberturas", "Tetra / Multiponto", "Digitais", "Portões"),
        icone = Icons.Default.Home,
        corAcento = estendidas.residencial,
        fundoAcento = estendidas.residencialFundo,
        disponivel = residencialAtivo,
        tagCard = "card_modalidade_residencial",
        textoBotaoPrincipal = if (residencialAtivo) "Entrar em Residencial" else "Contratar / Ativar Residencial",
        textoBotaoConsulta = if (residencialAtivo) "Fichas e manuais residenciais" else null,
        onPrincipal = {
          if (residencialAtivo) onSelecionarArea(SegmentoAtendimento.RESIDENCIAL) else onGerenciarPlano()
        },
        onConsulta = {
          if (residencialAtivo) onAbrirConsulta(SegmentoAtendimento.RESIDENCIAL) else onGerenciarPlano()
        },
      )

      OutlinedButton(
        onClick = onGerenciarPlano,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
      ) {
        Text("Gerenciar plano e módulos da empresa", fontWeight = FontWeight.SemiBold)
      }
      Spacer(Modifier.height(12.dp))
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CartaoModalidadeHub(
  titulo: String,
  subtitulo: String,
  descricao: String,
  tags: List<String>,
  icone: ImageVector,
  corAcento: Color,
  fundoAcento: Color,
  disponivel: Boolean,
  tagCard: String,
  textoBotaoPrincipal: String,
  textoBotaoConsulta: String?,
  onPrincipal: () -> Unit,
  onConsulta: () -> Unit,
) {
  Card(
    onClick = onPrincipal,
    modifier = Modifier.fillMaxWidth().testTag(tagCard),
    shape = RoundedCornerShape(22.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, corAcento.copy(alpha = 0.25f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Column {
      // Faixa superior estilizada com identidade cromática da modalidade
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            Brush.horizontalGradient(
              listOf(fundoAcento.copy(alpha = 0.85f), fundoAcento.copy(alpha = 0.22f)),
            ),
          )
          .padding(horizontal = 18.dp, vertical = 14.dp),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = corAcento,
            shadowElevation = 2.dp,
          ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
              Icon(icone, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
          }
          Column(Modifier.weight(1f)) {
            Text(
              text = titulo,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = subtitulo,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Selo(
            texto = if (disponivel) "Ativo" else "Inativo",
            tom = if (disponivel) TomSelo.SUCESSO else TomSelo.PENDENTE,
          )
        }
      }

      Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = descricao,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          tags.forEach { tag ->
            Selo(texto = tag, tom = TomSelo.NEUTRO)
          }
        }

        Button(
          onClick = onPrincipal,
          modifier = Modifier.fillMaxWidth().height(48.dp),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = corAcento,
            contentColor = Color.White,
          ),
        ) {
          Text(textoBotaoPrincipal, fontWeight = FontWeight.Bold, fontSize = 15.sp)
          Spacer(Modifier.width(6.dp))
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }

        if (textoBotaoConsulta != null) {
          Surface(
            onClick = onConsulta,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.coresEstendidas.superficieSutil,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = corAcento,
                modifier = Modifier.size(18.dp),
              )
              Text(
                text = textoBotaoConsulta,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
              )
              Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

private fun descricaoStatusPlano(status: String, modulos: Set<SegmentoAtendimento>): String {
  val lista = modulos.joinToString(" + ") { it.rotulo }.ifBlank { "Nenhum módulo ativo" }
  return when (status) {
    "PENDENTE_PAGAMENTO" -> "Pagamento pendente · $lista"
    "FALHA_PAGAMENTO" -> "Falha no pagamento · módulos suspensos"
    "CANCELADA" -> "Assinatura cancelada · seus dados continuam preservados"
    else -> "Ativo: $lista"
  }
}
