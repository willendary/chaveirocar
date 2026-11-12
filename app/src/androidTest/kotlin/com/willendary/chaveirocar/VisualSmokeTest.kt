package com.willendary.chaveirocar

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.TipoServico
import com.willendary.chaveirocar.ui.*
import com.willendary.chaveirocar.ui.theme.ChaveiroCarTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Exercita as telas reais com dados fictícios, sem depender de login ou rede. */
class VisualSmokeTest {
  @get:Rule val compose = createComposeRule()
  private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
  private lateinit var banco: BancoDeAtendimentos
  private var id: Long = 0
  private val perfil get() = InstrumentationRegistry.getArguments().getString("perfil") ?: "celular"

  @Before fun preparar() {
    contexto.deleteDatabase("chaveirocar.db")
    Preferencias.obter(contexto).edit().clear().commit()
    banco = BancoDeAtendimentos(contexto)
    id = banco.salvar(Atendimento(placa = "ABC1D23", marca = "Volkswagen", modelo = "Polo", ano = "2022",
      defeito = "Chave não reconhecida pelo veículo", solucao = "Transponder programado e testado.",
      abertura = "Procedimento documentado na ficha técnica.", codigoChave = "HU66", ferramenta = "Autel",
      clienteNome = "Cliente de demonstração", valorCentavos = 25000, pago = false))
    banco.salvar(Atendimento(placa = "XYZ9A87", marca = "Chevrolet", modelo = "Onix", ano = "2023",
      tipoServico = TipoServico.TELECOMANDO, solucao = "Telecomando sincronizado.", valorCentavos = 18000))
  }
  @After fun fechar() { banco.close() }

  private fun tela(escuro: Boolean = false, conteudo: @Composable () -> Unit) {
    compose.setContent { ChaveiroCarTheme(escuro) {
      Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { conteudo() }
    } }
  }
  private fun esperar(texto: String) {
    compose.waitUntil(15000) { compose.onAllNodesWithText(texto).fetchSemanticsNodes().isNotEmpty() }
    compose.waitForIdle()
  }
  private fun foto(nome: String) {
    compose.waitForIdle()
    val pasta = File(contexto.getExternalFilesDir(null), "screenshots/$perfil").apply { mkdirs() }
    File(pasta, "$nome.png").outputStream().use {
      compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
    }
  }

  @Test fun listaBuscaFiltrosEAbertura() {
    var aberto = 0L
    tela {
      var filtro by remember { mutableStateOf(FiltroLista()) }
      TelaLista(banco, filtro, { filtro = it }, { aberto = it }, {})
    }
    esperar("Acesso rápido")
    foto("01-servicos")
    compose.onNode(hasSetTextAction()).performTextInput("ABC1D23")
    esperar("1 serviço(s)")
    compose.onNodeWithText("ABC1D23").performScrollTo().assertIsDisplayed()
    foto("02-busca")
    compose.onNodeWithText("ABC1D23").performClick()
    compose.runOnIdle { assertEquals(id, aberto) }
    compose.onNodeWithContentDescription("Limpar busca").performScrollTo().performClick()
    esperar("Acesso rápido")
    compose.onNodeWithText("Filtros").performScrollTo().performClick()
    compose.onNodeWithText("Abertura", useUnmergedTree = true).performScrollTo().performClick()
    esperar("Nenhum resultado")
    compose.onNodeWithText("Nenhum resultado").performScrollTo().assertIsDisplayed()
    foto("03-vazio-filtrado")
  }

  @Test fun listaEscura() {
    tela(true) { TelaLista(banco, FiltroLista(), {}, {}, {}) }
    esperar("Acesso rápido"); foto("04-servicos-escuro")
  }
  @Test fun ficharioFavorito() {
    tela { TelaFichario(banco) {} }
    esperar("Polo")
    compose.onAllNodesWithContentDescription("Fixar modelo")[0].performScrollTo().performClick()
    compose.onNodeWithContentDescription("Desafixar modelo").assertExists()
    foto("05-fichario")
  }
  @Test fun detalheECopiarCodigo() {
    tela { TelaDetalhe(banco, id, {}, {}, {}, {}) }
    esperar("ABC1D23"); foto("06-atendimento")
    compose.onNodeWithText("Copiar").performScrollTo().performClick()
    compose.onNodeWithText("HU66").assertIsDisplayed()
    foto("07-codigos")
  }
  @Test fun formularioSalvaEdicao() {
    var salvo = 0L
    tela { TelaFormulario(banco, id, onSalvo = { salvo = it }, onVoltar = {}) }
    esperar("Placa *"); foto("08-formulario")
    compose.onNodeWithText("Modelo").performScrollTo().performTextReplacement("Polo revisado")
    compose.onNodeWithText("Salvar").performClick()
    compose.waitUntil(15000) { salvo > 0 }
    assertEquals("Polo revisado", banco.obter(id)?.modelo)
    assertEquals("HU66", banco.obter(id)?.codigoChave)
  }
  @Test fun ajustes() {
    tela { TelaAjustes(banco, null, false, {}, {}, {}, {}) }
    esperar("Segurança"); foto("09-ajustes")
    compose.onNodeWithText("Exigir PIN ao abrir").performScrollTo().assertIsDisplayed()
  }
  @Test fun acesso() {
    tela { TelaLoginNuvem {} }
    compose.onNodeWithText("Continuar com Google").performScrollTo().assertIsEnabled()
    foto("10-acesso")
  }
  @Test fun bloqueio() {
    tela { TelaBloqueio(false) {} }
    compose.onNodeWithText("Entrar").assertIsNotEnabled()
    compose.onNodeWithText("1").performScrollTo().performClick()
    compose.onNodeWithText("2").performClick()
    compose.onNodeWithText("3").performClick()
    compose.onNodeWithText("4").performClick()
    compose.onNodeWithText("Entrar").assertIsEnabled()
    foto("11-bloqueio")
  }
  @Test fun novidades() {
    tela { DialogoNovidades("1.4.0", "Nova identidade visual, navegação mais clara e fichário renovado.", false, {}) }
    compose.onNodeWithText("Vamos lá").assertIsDisplayed()
    // Diálogo é uma janela separada, captura pela automação do Android.
    compose.waitForIdle()
    val pasta = File(contexto.getExternalFilesDir(null), "screenshots/$perfil").apply { mkdirs() }
    File(pasta, "12-novidades.png").outputStream().use {
      InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
    }
  }
}
