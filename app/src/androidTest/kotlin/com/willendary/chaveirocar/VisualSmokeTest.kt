package com.willendary.chaveirocar

import android.graphics.Bitmap
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.willendary.chaveirocar.atualizacao.Atualizacoes
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.modelo.Atendimento
import com.willendary.chaveirocar.modelo.FichaTecnica
import com.willendary.chaveirocar.modelo.TipoServico
import com.willendary.chaveirocar.ui.*
import com.willendary.chaveirocar.ui.theme.ChaveiroCarTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

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
  private fun salvarCaptura(nome: String, imagem: Bitmap) {
    // MediaStore preserva as capturas mesmo apos a desinstalacao do APK de teste.
    val dados = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, "$nome.png")
      put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
      put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ChaveiroCarVisual/$perfil")
    }
    val uri = requireNotNull(contexto.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, dados)) {
      "Nao foi possivel criar captura: $nome"
    }
    contexto.contentResolver.openOutputStream(uri)?.use {
      check(imagem.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Falha ao salvar captura: $nome" }
    } ?: error("Nao foi possivel abrir a captura: $nome")
  }

  private fun foto(nome: String) {
    compose.waitForIdle()
    salvarCaptura(nome, compose.onRoot().captureToImage().asAndroidBitmap())
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
    compose.onNode(hasText("ABC1D23") and !hasSetTextAction()).performScrollTo().assertIsDisplayed()
    foto("02-busca")
    compose.onNode(hasText("ABC1D23") and !hasSetTextAction()).performClick()
    compose.runOnIdle { assertEquals(id, aberto) }
    compose.onNodeWithContentDescription("Limpar busca").performScrollTo().performClick()
    esperar("Acesso rápido")
    compose.onNodeWithText("Filtros").performScrollTo().performClick()
    compose.onNodeWithText("Abertura", useUnmergedTree = true).performScrollTo().performClick()
    compose.waitUntil(15000) {
      runCatching { compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Nenhum resultado")) }.isSuccess
    }
    compose.onNodeWithText("Nenhum resultado").assertIsDisplayed()
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
    var continuouSemDrive = false
    tela { TelaLoginNuvem { continuouSemDrive = it == null } }
    compose.onNodeWithText("Conectar Google Drive legado").performScrollTo().assertIsEnabled()
    compose.onNodeWithText("Continuar sem Drive").performScrollTo().assertIsEnabled()
    foto("10-acesso")
    compose.onNodeWithText("Continuar sem Drive").performScrollTo().performClick()
    compose.runOnIdle { assertTrue(continuouSemDrive) }
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
  @Test fun navegacaoPrincipal() {
    Preferencias.obter(contexto).edit()
      .putString(Preferencias.CONTA_NUVEM, "demonstracao@example.com")
      .putString(Preferencias.VERSAO_VISTA, Atualizacoes.versaoInstalada(contexto))
      .putLong(Preferencias.ULTIMO_BACKUP_AUTO, System.currentTimeMillis()).commit()
    tela { ChaveiroCarApp() }
    esperar("Acesso rápido"); foto("00-inicio-com-navegacao")
    compose.onNodeWithText("Fichário").performClick()
    // Em celulares compactos os cards do LazyColumn podem ficar fora da area visivel.
    // O cabecalho da aba confirma a navegacao sem depender da virtualizacao da lista.
    esperar("Conhecimento que fica.")
    compose.onNode(hasText("Ajustes") and hasClickAction()).performClick()
    esperar("Conta e backup")
    compose.onNode(hasText("Serviços") and hasClickAction()).performClick()
    esperar("Acesso rápido")
    compose.onNodeWithTag("acao_novo_servico").assertExists().performClick()
    esperar("Placa *")
    compose.onNodeWithContentDescription("Voltar").performClick()
    esperar("Acesso rápido")
  }

  @Test fun consultaTecnicaPorEtapas() {
    val fichaId = banco.salvarFicha(FichaTecnica(
      equipamento = "VVDI", marca = "Volkswagen", modelo = "Gol", ano = "2010",
      sistema = "G5", chip = "ID48", manual = "Manual de teste disponível offline.",
      confirmado = true,
    ))
    var aberta = 0L
    tela { TelaConsultaTecnica(banco, EstadoConsulta(), {}, { aberta = it }, {}) }
    esperar("Escolha o equipamento")
    compose.onNodeWithText("VVDI").performClick()
    esperar("Escolha a montadora")
    compose.onNodeWithText("Volkswagen").performClick()
    esperar("Escolha o modelo")
    compose.onNodeWithText("Gol").performClick()
    esperar("Escolha o ano ou geração")
    compose.onNodeWithText("2010").performClick()
    esperar("G5")
    foto("13-consulta-tecnica")
    compose.onNodeWithText("G5").performClick()
    compose.waitUntil(15000) { aberta == fichaId }
  }

  @Test fun novidades() {
    tela { DialogoNovidades("1.4.0", "Nova identidade visual, navegação mais clara e fichário renovado.", false, {}) }
    compose.onNodeWithText("Vamos lá").assertIsDisplayed()
    // Diálogo é uma janela separada, captura pela automação do Android.
    compose.waitForIdle()
    salvarCaptura("12-novidades", InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
  }
}
