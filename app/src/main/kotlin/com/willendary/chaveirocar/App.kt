package com.willendary.chaveirocar

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.ui.TelaDetalhe
import com.willendary.chaveirocar.ui.TelaFormulario
import com.willendary.chaveirocar.ui.TelaLista

private const val TELA_LISTA = "lista"
private const val TELA_FORMULARIO = "formulario"
private const val TELA_DETALHE = "detalhe"

@Composable
fun ChaveiroCarApp() {
  val contexto = LocalContext.current
  val banco = remember { BancoDeAtendimentos(contexto) }
  var tela by rememberSaveable { mutableStateOf(TELA_LISTA) }
  var idSelecionado by rememberSaveable { mutableStateOf(0L) }
  var baseParaNovo by remember { mutableStateOf<Atendimento?>(null) }

  BackHandler(enabled = tela != TELA_LISTA) {
    tela = if (tela == TELA_FORMULARIO && idSelecionado > 0L) TELA_DETALHE else TELA_LISTA
  }

  when (tela) {
    TELA_LISTA ->
      TelaLista(
        banco = banco,
        onAbrir = { id ->
          idSelecionado = id
          tela = TELA_DETALHE
        },
        onNovo = {
          baseParaNovo = null
          idSelecionado = 0L
          tela = TELA_FORMULARIO
        },
      )

    TELA_FORMULARIO ->
      TelaFormulario(
        banco = banco,
        id = idSelecionado,
        base = if (idSelecionado == 0L) baseParaNovo else null,
        onSalvo = { id ->
          idSelecionado = id
          baseParaNovo = null
          tela = TELA_DETALHE
        },
        onVoltar = { tela = if (idSelecionado > 0L) TELA_DETALHE else TELA_LISTA },
      )

    TELA_DETALHE ->
      TelaDetalhe(
        banco = banco,
        id = idSelecionado,
        onEditar = { tela = TELA_FORMULARIO },
        onDuplicar = { registro ->
          baseParaNovo = registro.copy(
            id = 0,
            dataServico = System.currentTimeMillis(),
            defeito = "",
            solucao = "",
            ferramenta = "",
            valorCentavos = 0,
            quantidadeFotos = 0,
          )
          idSelecionado = 0L
          tela = TELA_FORMULARIO
        },
        onExcluido = {
          baseParaNovo = null
          idSelecionado = 0L
          tela = TELA_LISTA
        },
        onVoltar = { tela = TELA_LISTA },
      )
  }
}
