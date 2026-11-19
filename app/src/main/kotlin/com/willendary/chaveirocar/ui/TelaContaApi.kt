package com.willendary.chaveirocar.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.nuvem.ApiChaveiroCar
import com.willendary.chaveirocar.nuvem.CredencialGoogle
import com.willendary.chaveirocar.nuvem.IdentidadeGoogle
import com.willendary.chaveirocar.nuvem.MotorSinc
import com.willendary.chaveirocar.ui.theme.Jade
import com.willendary.chaveirocar.ui.theme.JadeVibrante
import com.willendary.chaveirocar.ui.theme.Navy
import com.willendary.chaveirocar.ui.theme.coresEstendidas
import com.willendary.chaveirocar.util.Erros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Tela de login obrigatório ao abrir o aplicativo sem sessão ativa. */
@Composable
fun TelaAutenticacaoObrigatoria(
  banco: BancoDeAtendimentos,
  aoAutenticar: () -> Unit,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
  ) {
    // Topo hero em gradiente Navy/Jade atrás do card de acesso
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(270.dp)
        .background(
          Brush.verticalGradient(
            listOf(Navy, Color(0xFF102A3A), Color(0xFF123F43)),
          ),
        ),
    )

    Column(
      modifier = Modifier
        .conteudoResponsivo(maximo = 520.dp)
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      // Hero Institucional
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp),
      ) {
        Box(
          modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp)),
          contentAlignment = Alignment.Center,
        ) {
          MarcaChaveiro(tamanho = 68.dp, elevacao = 4.dp)
        }
        Text(
          text = "CHAVEIROCAR",
          fontSize = 11.sp,
          fontWeight = FontWeight.ExtraBold,
          letterSpacing = 2.2.sp,
          color = JadeVibrante,
        )
        Text(
          text = "Sua bancada sincronizada",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
          textAlign = TextAlign.Center,
        )
        Text(
          text = "Atendimentos, placas, códigos e manuais offline para chaveiros automotivos e residenciais.",
          style = MaterialTheme.typography.bodySmall,
          textAlign = TextAlign.Center,
          color = Color(0xFFCBD5E1),
          modifier = Modifier.padding(horizontal = 12.dp),
        )
      }

      // Card de Acesso sobreposto
      SecaoContaApi(
        banco = banco,
        aoAutenticar = aoAutenticar,
      )
    }
  }
}

/** Conta ChaveiroCar (API): login, empresa, sync manual e resolução de conflitos. */
@Composable
fun SecaoContaApi(
  banco: BancoDeAtendimentos,
  aoAutenticar: () -> Unit = {},
  aoSair: () -> Unit = {},
  aoAtualizarAssinatura: () -> Unit = {},
) {
  val contexto = LocalContext.current
  val estendidas = MaterialTheme.coresEstendidas
  val preferencias = remember { Preferencias.obter(contexto) }
  val preferenciasApi = remember { Preferencias.obterApi(contexto) }
  val escopo = rememberCoroutineScope()
  val baseSalva = preferencias.getString(Preferencias.API_BASE_URL, null).orEmpty()
  val baseInicial = if (baseSalva.isBlank() || baseSalva.contains("10.0.2.2") || baseSalva.contains("localhost")) {
    ApiChaveiroCar.URL_PADRAO
  } else baseSalva

  var baseUrl by remember { mutableStateOf(baseInicial) }
  var email by remember { mutableStateOf(preferencias.getString(Preferencias.API_EMAIL, "") ?: "") }
  var emailVerificado by remember { mutableStateOf(false) }
  var nome by remember { mutableStateOf("") }
  var empresaNova by remember { mutableStateOf("") }
  var senha by remember { mutableStateOf("") }
  var tokenRedefinicao by remember { mutableStateOf("") }
  var tokenConvite by remember { mutableStateOf("") }
  var tokenConviteLogado by remember { mutableStateOf("") }
  var senhaVisivel by remember { mutableStateOf(false) }
  var modoCadastro by remember { mutableStateOf(false) }
  var modoEsqueciSenha by remember { mutableStateOf(false) }
  var modoRedefinicao by remember { mutableStateOf(false) }
  var modoConvite by remember { mutableStateOf(false) }
  var mostrarAceitarConvite by remember { mutableStateOf(false) }
  var logado by remember {
    mutableStateOf(!preferenciasApi.getString(Preferencias.API_ACESSO, "").isNullOrBlank())
  }
  var empresaId by remember { mutableStateOf(preferencias.getString(Preferencias.API_EMPRESA_ID, "") ?: "") }
  var empresas by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  var papeisPorEmpresa by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
  var conflitos by remember { mutableStateOf(banco.conflitos()) }
  var resolvendoConflito by remember { mutableStateOf(false) }
  var entrando by remember { mutableStateOf(false) }

  fun api(): ApiChaveiroCar {
    val api = ApiChaveiroCar(baseUrl.trim().ifBlank { ApiChaveiroCar.URL_PADRAO })
    api.tokenAcesso = preferenciasApi.getString(Preferencias.API_ACESSO, null)?.ifBlank { null }
    api.tokenRefresh = preferenciasApi.getString(Preferencias.API_REFRESH, null)?.ifBlank { null }
    return api
  }

  fun recarregar() {
    conflitos = banco.conflitos()
  }

  fun guardarTokens(api: ApiChaveiroCar) {
    preferenciasApi.edit()
      .putString(Preferencias.API_ACESSO, api.tokenAcesso ?: "")
      .putString(Preferencias.API_REFRESH, api.tokenRefresh ?: "")
      .apply()
  }

  fun extrairModulos(objeto: JSONObject): List<String> {
    val array = objeto.optJSONArray("modulosAtivos") ?: return listOf("AUTOMOTIVO", "RESIDENCIAL")
    return buildList {
      for (i in 0 until array.length()) {
        val mod = array.optString(i)
        if (mod.isNotBlank()) add(mod)
      }
    }
  }

  fun registrarEmpresas(lista: JSONArray?) {
    if (lista == null) return
    for (i in 0 until lista.length()) {
      val e = lista.optJSONObject(i) ?: continue
      val id = e.optString("id")
      if (id.isBlank()) continue
      val renovacao = if (e.isNull("proximaRenovacaoEm")) null else e.optLong("proximaRenovacaoEm").takeIf { it > 0L }
      Preferencias.guardarAssinaturaEmpresa(
        preferencias = preferencias,
        empresaId = id,
        planoCodigo = e.optString("planoCodigo", "COMPLETO"),
        status = e.optString("assinaturaStatus", "ATIVA"),
        modulosAtivos = extrairModulos(e),
        proximaRenovacaoEm = renovacao,
      )
    }
  }

  fun carregarEmpresas() {
    escopo.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          val cliente = api()
          val usuario = cliente.eu().optJSONObject("usuario")
          val lista = cliente.listarEmpresas()
          guardarTokens(cliente)
          val papeis = mutableMapOf<String, String>()
          val itens = buildList {
            for (i in 0 until lista.length()) {
              val e = lista.optJSONObject(i) ?: continue
              val id = e.optString("id")
              add(id to e.optString("nome"))
              papeis[id] = e.optString("papel")
            }
          }
          Pair(Triple(usuario?.optBoolean("emailVerificado") ?: false, itens, papeis), lista)
        }
      }.onSuccess { (dados, listaJson) ->
        val (verificado, lista, papeis) = dados
        registrarEmpresas(listaJson)
        emailVerificado = verificado
        empresas = lista
        papeisPorEmpresa = papeis
        if (lista.isNotEmpty() && lista.none { it.first == empresaId }) {
          empresaId = lista.first().first
          preferencias.edit().putString(Preferencias.API_EMPRESA_ID, empresaId).apply()
        }
        aoAtualizarAssinatura()
      }.onFailure { Erros.registrar(contexto, "Conta API: empresas", it) }
    }
  }

  fun entrar() {
    if (entrando) return
    entrando = true
    escopo.launch {
      val cliente = api()
      val resultado = runCatching {
        withContext(Dispatchers.IO) { cliente.entrar(email.trim(), senha) }
      }
      entrando = false
      resultado
        .onSuccess { resposta ->
          guardarTokens(cliente)
          logado = true
          emailVerificado = resposta.optJSONObject("usuario")?.optBoolean("emailVerificado") ?: false
          var empresasArray = resposta.optJSONArray("empresas")
          var idEmpresa = empresasArray?.optJSONObject(0)?.optString("id").orEmpty()
          if (idEmpresa.isBlank()) {
            runCatching {
              val lista = withContext(Dispatchers.IO) { cliente.listarEmpresas() }
              empresasArray = lista
              idEmpresa = lista.optJSONObject(0)?.optString("id").orEmpty()
            }
          }
          registrarEmpresas(empresasArray)
          if (idEmpresa.isNotBlank()) empresaId = idEmpresa
          preferencias.edit()
            .putString(Preferencias.API_BASE_URL, baseUrl.trim())
            .putString(Preferencias.API_EMAIL, email.trim())
            .apply { if (empresaId.isNotBlank()) putString(Preferencias.API_EMPRESA_ID, empresaId) }
            .apply()
          recarregar()
          aoAtualizarAssinatura()
          aoAutenticar()
          Toast.makeText(contexto, "Conectado à conta ChaveiroCar", Toast.LENGTH_SHORT).show()
        }
        .onFailure {
          Erros.registrar(contexto, "Conta API: login", it)
          Toast.makeText(contexto, "Não foi possível entrar. Confira seu e-mail e senha.", Toast.LENGTH_LONG).show()
        }
    }
  }

  fun concluirLoginGoogle(credencial: CredencialGoogle) {
    escopo.launch {
      val cliente = api()
      val resultado = runCatching {
        withContext(Dispatchers.IO) {
          cliente.entrarComGoogle(
            accessToken = credencial.token,
            empresa = empresaNova.trim().ifBlank { "Minha empresa" },
          )
        }
      }
      entrando = false
      resultado
        .onSuccess { resposta ->
          guardarTokens(cliente)
          val emailConta = resposta.optJSONObject("usuario")?.optString("email")
            ?.ifBlank { credencial.email.orEmpty() }
            ?: credencial.email.orEmpty()
          email = emailConta
          emailVerificado = true
          var empresasArray = resposta.optJSONArray("empresas")
          var idEmpresa = empresasArray?.optJSONObject(0)?.optString("id").orEmpty()
          if (idEmpresa.isBlank()) {
            runCatching {
              val lista = withContext(Dispatchers.IO) { cliente.listarEmpresas() }
              empresasArray = lista
              idEmpresa = lista.optJSONObject(0)?.optString("id").orEmpty()
            }
          }
          registrarEmpresas(empresasArray)
          if (idEmpresa.isNotBlank()) empresaId = idEmpresa
          preferencias.edit()
            .putString(Preferencias.API_BASE_URL, baseUrl.trim())
            .putString(Preferencias.API_EMAIL, emailConta)
            .apply { if (empresaId.isNotBlank()) putString(Preferencias.API_EMPRESA_ID, empresaId) }
            .apply()
          logado = true
          recarregar()
          aoAtualizarAssinatura()
          aoAutenticar()
          Toast.makeText(contexto, "Conectado com Google à conta ChaveiroCar", Toast.LENGTH_SHORT).show()
        }
        .onFailure {
          Erros.registrar(contexto, "Conta API: login Google", it)
          Toast.makeText(contexto, "Não foi possível autenticar na API com a conta Google.", Toast.LENGTH_LONG).show()
        }
    }
  }

  val autorizadorGoogle = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { retorno ->
    val dados = retorno.data
    if (retorno.resultCode != Activity.RESULT_OK || dados == null) {
      entrando = false
      return@rememberLauncherForActivityResult
    }
    escopo.launch {
      runCatching {
        withContext(Dispatchers.IO) { IdentidadeGoogle.concluirAutorizacao(contexto, dados) }
      }.onSuccess { autorizacao ->
        val credencial = autorizacao.credencial
        if (credencial != null) {
          concluirLoginGoogle(credencial)
        } else {
          entrando = false
          Toast.makeText(contexto, "Não foi possível obter a conta Google.", Toast.LENGTH_LONG).show()
        }
      }.onFailure { erro ->
        entrando = false
        Erros.registrar(contexto, "Conta API: consentimento Google", erro)
        Toast.makeText(contexto, "Falha ao conectar conta Google.", Toast.LENGTH_LONG).show()
      }
    }
  }

  fun entrarComGoogle() {
    if (entrando) return
    entrando = true
    escopo.launch {
      runCatching {
        withContext(Dispatchers.IO) { IdentidadeGoogle.autenticarConta(contexto) }
      }.onSuccess { autorizacao ->
        when {
          autorizacao.pendente != null ->
            autorizadorGoogle.launch(IntentSenderRequest.Builder(autorizacao.pendente).build())
          autorizacao.credencial != null ->
            concluirLoginGoogle(autorizacao.credencial)
          else -> {
            entrando = false
            Toast.makeText(contexto, "Não foi possível conectar com o Google.", Toast.LENGTH_LONG).show()
          }
        }
      }.onFailure { erro ->
        entrando = false
        Erros.registrar(contexto, "Conta API: iniciar Google", erro)
        Toast.makeText(
          contexto,
          "Google Play Services indisponível ou sem conta Google no aparelho.",
          Toast.LENGTH_LONG,
        ).show()
      }
    }
  }

  fun cadastrar() {
    if (entrando) return
    entrando = true
    escopo.launch {
      val cliente = api()
      val resultado = runCatching {
        withContext(Dispatchers.IO) {
          cliente.cadastrar(nome.trim(), email.trim(), senha, empresaNova.trim().ifBlank { "Minha empresa" })
        }
      }
      entrando = false
      resultado
        .onSuccess { resposta ->
          guardarTokens(cliente)
          logado = true
          emailVerificado = resposta.optJSONObject("usuario")?.optBoolean("emailVerificado") ?: false
          var empresasArray = resposta.optJSONArray("empresas")
          var idEmpresa = empresasArray?.optJSONObject(0)?.optString("id").orEmpty()
          if (idEmpresa.isBlank()) {
            runCatching {
              val lista = withContext(Dispatchers.IO) { cliente.listarEmpresas() }
              empresasArray = lista
              idEmpresa = lista.optJSONObject(0)?.optString("id").orEmpty()
            }
          }
          registrarEmpresas(empresasArray)
          if (idEmpresa.isNotBlank()) empresaId = idEmpresa
          preferencias.edit()
            .putString(Preferencias.API_BASE_URL, baseUrl.trim())
            .putString(Preferencias.API_EMAIL, email.trim())
            .putString(Preferencias.API_EMPRESA_ID, empresaId)
            .apply()
          recarregar()
          aoAtualizarAssinatura()
          aoAutenticar()
          val mensagem = if (resposta.optBoolean("emailEnviado")) {
            "Conta criada. Verifique seu e-mail."
          } else {
            "Conta criada, mas não foi possível enviar o e-mail de confirmação."
          }
          Toast.makeText(contexto, mensagem, Toast.LENGTH_LONG).show()
        }
        .onFailure {
          Erros.registrar(contexto, "Conta API: cadastro", it)
          Toast.makeText(contexto, "Não foi possível criar a conta. Confira os dados e tente novamente.", Toast.LENGTH_LONG).show()
        }
    }
  }

  fun solicitarRedefinicao() {
    if (entrando) return
    entrando = true
    escopo.launch {
      val resultado = runCatching {
        withContext(Dispatchers.IO) { api().solicitarRedefinicao(email.trim()) }
      }
      entrando = false
      resultado
        .onSuccess {
          modoEsqueciSenha = false
          modoRedefinicao = true
          Toast.makeText(contexto, "Se o e-mail estiver cadastrado, enviaremos um código.", Toast.LENGTH_LONG).show()
        }
        .onFailure {
          Erros.registrar(contexto, "Conta API: recuperação", it)
          Toast.makeText(contexto, "Não foi possível solicitar a recuperação.", Toast.LENGTH_LONG).show()
        }
    }
  }

  fun redefinirSenha() {
    if (entrando) return
    entrando = true
    escopo.launch {
      val resultado = runCatching {
        withContext(Dispatchers.IO) { api().redefinirSenha(tokenRedefinicao.trim(), senha) }
      }
      entrando = false
      resultado
        .onSuccess {
          modoRedefinicao = false
          modoCadastro = false
          senha = ""
          Toast.makeText(contexto, "Senha alterada. Entre com a nova senha.", Toast.LENGTH_LONG).show()
        }
        .onFailure {
          Erros.registrar(contexto, "Conta API: redefinir senha", it)
          Toast.makeText(contexto, "Código inválido ou expirado.", Toast.LENGTH_LONG).show()
        }
    }
  }

  fun reenviarVerificacao() {
    if (entrando) return
    entrando = true
    escopo.launch {
      val resultado = runCatching {
        withContext(Dispatchers.IO) { api().reenviarVerificacao() }
      }
      entrando = false
      resultado.onSuccess { resposta ->
        Toast.makeText(contexto, resposta.optString("mensagem"), Toast.LENGTH_LONG).show()
      }.onFailure {
        Erros.registrar(contexto, "Conta API: reenviar verificação", it)
        Toast.makeText(contexto, "Não foi possível reenviar a confirmação.", Toast.LENGTH_LONG).show()
      }
    }
  }

  fun aceitarConviteNovo() {
    if (entrando) return
    entrando = true
    escopo.launch {
      val cliente = api()
      val resultado = runCatching {
        withContext(Dispatchers.IO) {
          val aceito = cliente.aceitarConvite(tokenConvite.trim(), nome.trim(), senha)
          val lista = runCatching { cliente.listarEmpresas() }.getOrNull()
          aceito to lista
        }
      }
      entrando = false
      resultado.onSuccess { (resposta, empresasLista) ->
        guardarTokens(cliente)
        registrarEmpresas(empresasLista)
        email = resposta.optJSONObject("usuario")?.optString("email").orEmpty()
        emailVerificado = resposta.optJSONObject("usuario")?.optBoolean("emailVerificado") ?: true
        empresaId = resposta.optString("empresaId")
        preferencias.edit()
          .putString(Preferencias.API_BASE_URL, baseUrl.trim())
          .putString(Preferencias.API_EMAIL, email)
          .putString(Preferencias.API_EMPRESA_ID, empresaId)
          .apply()
        logado = true
        modoConvite = false
        aoAtualizarAssinatura()
        aoAutenticar()
        Toast.makeText(contexto, "Convite aceito. A empresa foi adicionada à sua conta.", Toast.LENGTH_LONG).show()
      }.onFailure {
        Erros.registrar(contexto, "Conta API: aceitar convite", it)
        Toast.makeText(contexto, "Convite inválido ou conta já existente. Entre primeiro se já tiver conta.", Toast.LENGTH_LONG).show()
      }
    }
  }

  fun aceitarConviteLogado() {
    if (entrando) return
    entrando = true
    escopo.launch {
      val cliente = api()
      val resultado = runCatching {
        withContext(Dispatchers.IO) { cliente.aceitarConvite(tokenConviteLogado.trim()) }
      }
      entrando = false
      resultado.onSuccess { resposta ->
        guardarTokens(cliente)
        empresaId = resposta.optString("empresaId")
        preferencias.edit().putString(Preferencias.API_EMPRESA_ID, empresaId).apply()
        mostrarAceitarConvite = false
        carregarEmpresas()
        Toast.makeText(contexto, "Convite aceito. A empresa foi adicionada à sua conta.", Toast.LENGTH_LONG).show()
      }.onFailure {
        Erros.registrar(contexto, "Conta API: aceitar convite", it)
        Toast.makeText(contexto, "Não foi possível aceitar. Confira o código e o e-mail da conta.", Toast.LENGTH_LONG).show()
      }
    }
  }

  LaunchedEffect(logado) {
    if (logado) carregarEmpresas()
  }

  CartaoSurface(raio = 22.dp) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      if (!logado) {
        // Seletor segmentado limpo (Login / Criar conta / Convite)
        if (!modoEsqueciSenha && !modoRedefinicao) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = estendidas.superficieSutil,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(modifier = Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              AbaAuth("Acesso", !modoCadastro && !modoConvite, Modifier.weight(1f)) {
                modoCadastro = false; modoConvite = false; modoEsqueciSenha = false; modoRedefinicao = false
              }
              AbaAuth("Criar conta", modoCadastro, Modifier.weight(1f)) {
                modoCadastro = true; modoConvite = false; modoEsqueciSenha = false; modoRedefinicao = false
              }
              AbaAuth("Convite", modoConvite, Modifier.weight(1f)) {
                modoConvite = true; modoCadastro = false; modoEsqueciSenha = false; modoRedefinicao = false
              }
            }
          }
        } else {
          Text(
            text = if (modoEsqueciSenha) "Recuperar senha" else "Definir nova senha",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
        }

        // Botão Google estilizado (texto exato preservado para testes)
        Surface(
          onClick = { entrarComGoogle() },
          enabled = !entrando,
          shape = RoundedCornerShape(14.dp),
          color = if (estendidas.escuro) MaterialTheme.colorScheme.surfaceVariant else Color.White,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
          ) {
            Box(
              modifier = Modifier
                .size(26.dp)
                .background(Color(0xFF4285F4), CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Text("G", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
              text = if (entrando) "Conectando..." else "Entrar com Google",
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
          Text(
            text = "ou com e-mail",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
          )
          HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }

        val coresCampo = OutlinedTextFieldDefaults.colors(
          unfocusedContainerColor = estendidas.superficieSutil,
          focusedContainerColor = MaterialTheme.colorScheme.surface,
          unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
          focusedBorderColor = MaterialTheme.colorScheme.primary,
        )

        if (modoCadastro || modoConvite) {
          OutlinedTextField(
            value = nome, onValueChange = { nome = it },
            label = { Text("Nome completo") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            shape = RoundedCornerShape(14.dp),
            colors = coresCampo,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (modoCadastro) {
          OutlinedTextField(
            value = empresaNova, onValueChange = { empresaNova = it },
            label = { Text("Nome da empresa / chaveiro") }, singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = coresCampo,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (!modoRedefinicao && !modoConvite) {
          OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("E-mail profissional") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(14.dp),
            colors = coresCampo,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (modoRedefinicao) {
          OutlinedTextField(
            value = tokenRedefinicao, onValueChange = { tokenRedefinicao = it },
            label = { Text("Código recebido no e-mail") },
            shape = RoundedCornerShape(14.dp),
            colors = coresCampo,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (modoConvite) {
          OutlinedTextField(
            value = tokenConvite, onValueChange = { tokenConvite = it },
            label = { Text("Código do convite") },
            shape = RoundedCornerShape(14.dp),
            colors = coresCampo,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (modoCadastro || modoConvite || modoRedefinicao || (!modoEsqueciSenha && !modoRedefinicao && !modoConvite)) {
          OutlinedTextField(
            value = senha, onValueChange = { senha = it },
            label = { Text(if (modoRedefinicao) "Nova senha" else "Senha") }, singleLine = true,
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
              TextButton(onClick = { senhaVisivel = !senhaVisivel }) {
                Text(
                  text = if (senhaVisivel) "Ocultar" else "Ver",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            },
            visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(14.dp),
            colors = coresCampo,
            modifier = Modifier.fillMaxWidth(),
          )
        }

        Button(
          onClick = {
            when {
              modoConvite -> aceitarConviteNovo()
              modoCadastro -> cadastrar()
              modoEsqueciSenha -> solicitarRedefinicao()
              modoRedefinicao -> redefinirSenha()
              else -> entrar()
            }
          },
          enabled = !entrando && (!modoConvite || tokenConvite.isNotBlank()),
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Jade, contentColor = Color.White),
          modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
          if (entrando) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
          } else {
            Text(
              text = when {
                modoConvite -> "Aceitar convite"
                modoCadastro -> "Criar conta"
                modoEsqueciSenha -> "Enviar instruções"
                modoRedefinicao -> "Alterar senha"
                else -> "Entrar"
              },
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
            )
          }
        }

        if (modoEsqueciSenha || modoRedefinicao) {
          Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (modoEsqueciSenha) {
              TextButton(onClick = { modoRedefinicao = true; modoEsqueciSenha = false }) {
                Text("Já tenho o código de redefinição", color = MaterialTheme.colorScheme.primary)
              }
            }
            TextButton(onClick = { modoEsqueciSenha = false; modoRedefinicao = false }) {
              Text("← Voltar ao login", fontWeight = FontWeight.SemiBold)
            }
          }
        } else if (!modoCadastro && !modoConvite) {
          TextButton(
            onClick = { modoEsqueciSenha = true },
            modifier = Modifier.align(Alignment.CenterHorizontally),
          ) {
            Text("Esqueci minha senha", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
          }
        }
      } else {
        // Estado logado (dentro de Ajustes)
        val inicial = email.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "C"
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Box(
            modifier = Modifier
              .size(46.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = inicial,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          }
          Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Conta conectada", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
              text = email.ifBlank { "Sessão ativa na nuvem" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Selo(
            texto = if (emailVerificado) "Verificada" else "Pendente",
            tom = if (emailVerificado) TomSelo.SUCESSO else TomSelo.PENDENTE,
          )
        }

        if (!emailVerificado) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text(
                "E-mail ainda não confirmado. Confirme para proteger o acesso à conta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              OutlinedButton(
                onClick = { reenviarVerificacao() },
                enabled = !entrando,
                shape = RoundedCornerShape(10.dp),
              ) {
                Text(if (entrando) "Enviando..." else "Reenviar confirmação")
              }
            }
          }
        }

        if (empresas.size > 1) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              "EMPRESA ATIVA",
              style = MaterialTheme.typography.labelSmall,
              letterSpacing = 0.8.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            empresas.forEach { (id, nomeEmpresa) ->
              val selecionada = id == empresaId
              Surface(
                onClick = {
                  empresaId = id
                  preferencias.edit().putString(Preferencias.API_EMPRESA_ID, id).apply()
                  aoAtualizarAssinatura()
                },
                shape = RoundedCornerShape(12.dp),
                color = if (selecionada) MaterialTheme.colorScheme.primaryContainer else estendidas.superficieSutil,
                border = BorderStroke(
                  1.dp,
                  if (selecionada) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                  Text(
                    text = nomeEmpresa,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selecionada) FontWeight.Bold else FontWeight.Medium,
                  )
                  if (selecionada) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                  }
                }
              }
            }
          }
        } else if (empresaId.isNotBlank()) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = estendidas.superficieSutil,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text("Empresa", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text(
                text = empresas.firstOrNull { it.first == empresaId }?.second ?: empresaId,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
              )
            }
          }
        }

        if (mostrarAceitarConvite) {
          OutlinedTextField(
            value = tokenConviteLogado,
            onValueChange = { tokenConviteLogado = it },
            label = { Text("Código do convite") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = { aceitarConviteLogado() },
              enabled = !entrando && tokenConviteLogado.isNotBlank(),
              shape = RoundedCornerShape(12.dp),
            ) { Text(if (entrando) "Aceitando..." else "Aceitar convite") }
            OutlinedButton(
              onClick = { mostrarAceitarConvite = false },
              shape = RoundedCornerShape(12.dp),
            ) { Text("Cancelar") }
          }
        } else {
          OutlinedButton(
            onClick = { mostrarAceitarConvite = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) { Text("Tenho um código de convite") }
        }

        if (conflitos.isNotEmpty()) {
          conflitos.forEach { conflito ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = estendidas.alertaFundo.copy(alpha = 0.55f),
              border = BorderStroke(1.dp, estendidas.alerta.copy(alpha = 0.4f)),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  "Conflito: ${conflito.placa.ifBlank { conflito.uuid }}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                )
                Text(
                  "Sua edição local divergiu da versão ${conflito.revisaoServidor} no servidor. Nada foi perdido.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Button(
                    onClick = {
                      if (resolvendoConflito) return@Button
                      resolvendoConflito = true
                      escopo.launch {
                        runCatching {
                          withContext(Dispatchers.IO) {
                            MotorSinc.manterLocal(banco, api(), empresaId, conflito.uuid, conflito.revisaoServidor, conflito.entidade)
                          }
                        }.onFailure { Erros.registrar(contexto, "Conta API: manter local", it) }
                        resolvendoConflito = false
                        recarregar()
                      }
                    },
                    enabled = !resolvendoConflito,
                    shape = RoundedCornerShape(10.dp),
                  ) { Text("Manter meu aparelho", fontSize = 12.sp) }
                  OutlinedButton(
                    onClick = {
                      if (resolvendoConflito) return@OutlinedButton
                      resolvendoConflito = true
                      escopo.launch {
                        runCatching {
                          withContext(Dispatchers.IO) {
                            MotorSinc.aceitarServidor(banco, api(), empresaId, conflito.uuid, conflito.entidade)
                          }
                        }.onFailure { Erros.registrar(contexto, "Conta API: aceitar servidor", it) }
                        resolvendoConflito = false
                        recarregar()
                      }
                    },
                    enabled = !resolvendoConflito,
                    shape = RoundedCornerShape(10.dp),
                  ) { Text("Aceitar servidor", fontSize = 12.sp) }
                }
              }
            }
          }
        }

        if (empresaId.isNotBlank() && papeisPorEmpresa.containsKey(empresaId)) {
          SecaoEquipeApi(
            empresaId = empresaId,
            papelEmpresa = papeisPorEmpresa[empresaId].orEmpty(),
            criarApi = { api() },
            guardarTokens = { guardarTokens(it) },
          )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        TextButton(
          onClick = {
            escopo.launch {
              withContext(Dispatchers.IO) { api().sair() }
              preferenciasApi.edit().remove(Preferencias.API_ACESSO).remove(Preferencias.API_REFRESH).apply()
              logado = false
              empresas = emptyList()
              papeisPorEmpresa = emptyMap()
              aoSair()
            }
            Toast.makeText(contexto, "Sessão encerrada neste aparelho.", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Sair da conta", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
      }
    }
  }
}

@Composable
private fun AbaAuth(
  rotulo: String,
  selecionado: Boolean,
  modificador: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = if (selecionado) MaterialTheme.colorScheme.surface else Color.Transparent,
    shadowElevation = if (selecionado) 1.dp else 0.dp,
    modifier = modificador,
  ) {
    Text(
      text = rotulo,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (selecionado) FontWeight.Bold else FontWeight.Medium,
      color = if (selecionado) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(vertical = 9.dp, horizontal = 6.dp),
    )
  }
}
