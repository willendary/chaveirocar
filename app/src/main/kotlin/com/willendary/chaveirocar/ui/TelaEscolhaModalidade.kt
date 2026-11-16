package com.willendary.chaveirocar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.modelo.SegmentoAtendimento

/**
 * Entrada inicial após login/desbloqueio: escolha direta entre Veicular e Residencial
 * antes de criar qualquer serviço.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelaEscolhaModalidade(
  modulosAtivos: Set<SegmentoAtendimento>,
  statusAssinatura: String,
  sincronizando: Boolean = false,
  onSelecionarArea: (SegmentoAtendimento) -> Unit,
  onAbrirConsulta: (SegmentoAtendimento) -> Unit,
  onGerenciarPlano: () -> Unit,
  onAbrirAjustes: () -> Unit,
) {
  val veicularAtivo = SegmentoAtendimento.AUTOMOTIVO in modulosAtivos
  val residencialAtivo = SegmentoAtendimento.RESIDENCIAL in modulosAtivos

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            MarcaChaveiro()
            Column {
              Text("ChaveiroCar", style = MaterialTheme.typography.titleLarge)
              Text(
                text = if (sincronizando) "Sincronizando dados..." else "Escolha a área de trabalho",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        actions = {
          IconButton(onClick = onAbrirAjustes) {
            Icon(Icons.Default.Settings, contentDescription = "Ajustes e conta")
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
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
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      PainelDestaque(
        titulo = "Qual área você vai usar agora?",
        descricao = "Acesse serviços, histórico, fichário e manuais técnicos direto pela modalidade, sem precisar iniciar um atendimento.",
        etiqueta = "ENTRADA POR MODALIDADE",
      )

      if (statusAssinatura != "ATIVA" || modulosAtivos.size < 2) {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
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
            TextButton(onClick = onGerenciarPlano) { Text("Ver plano") }
          }
        }
      }

      CartaoModalidade(
        titulo = "Veicular",
        descricao = "Chaves codificadas, chips/transponders, telecomandos, abertura de carros, fichário de modelos e consulta técnica com manuais.",
        icone = { Icon(Icons.Default.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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

      CartaoModalidade(
        titulo = "Residencial",
        descricao = "Aberturas residenciais, cilindros, travas tetra e multiponto, fechaduras digitais, controles de portão e histórico por local.",
        icone = { Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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

      OutlinedButton(onClick = onGerenciarPlano, modifier = Modifier.fillMaxWidth()) {
        Text("Gerenciar plano e módulos da empresa")
      }
      Spacer(Modifier.height(16.dp))
    }
  }
}

@Composable
private fun CartaoModalidade(
  titulo: String,
  descricao: String,
  icone: @Composable () -> Unit,
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
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        icone()
        Text(
          text = titulo,
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.weight(1f),
        )
        Selo(
          texto = if (disponivel) "Disponível no plano" else "Não contratado",
          pendente = !disponivel,
        )
      }
      Text(
        text = descricao,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(onClick = onPrincipal, modifier = Modifier.fillMaxWidth()) {
        Text(textoBotaoPrincipal)
      }
      if (textoBotaoConsulta != null) {
        OutlinedButton(onClick = onConsulta, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Default.Search, contentDescription = null)
          Spacer(Modifier.padding(horizontal = 4.dp))
          Text(textoBotaoConsulta)
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
