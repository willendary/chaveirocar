package com.willendary.chaveirocar.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.dados.BancoDeAtendimentos
import com.willendary.chaveirocar.dados.Preferencias
import com.willendary.chaveirocar.nuvem.ApiChaveiroCar
import com.willendary.chaveirocar.nuvem.CredencialGoogle
import com.willendary.chaveirocar.nuvem.IdentidadeGoogle
import com.willendary.chaveirocar.nuvem.MotorSinc
import com.willendary.chaveirocar.util.Erros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tela de login obrigatório ao abrir o aplicativo sem sessão ativa. */
@Composable
fun TelaAutenticacaoObrigatoria(
  banco: BancoDeAtendimentos,
  aoAutenticar: () -> Unit,
) {
  Scaffold(
    topBar = {
      CabecalhoTela("ChaveiroCar", "Entre na sua conta para continuar")
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    containerColor = MaterialTheme.colorScheme.background,
  ) { espacamento ->
    Column(
      modifier = Modifier
        .conteudoResponsivo()
        .padding(espacamento)
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      PainelDestaque(
        "Acesso exclusivo.",
        "Conecte-se com sua conta Google ou e-mail para acessar e sincronizar seus atendimentos, fichas técnicas e fotos.",
        "CHAVEIROCAR",
      )
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
) {
  val contexto = LocalContext.current
  val preferencias = remember { Preferencias.obter(contexto) }
  val preferenciasApi = remember { Preferencias.obterApi(contexto) }
  val escopo = rememberCoroutineScope()
  val baseSalva = preferencias.getString(Preferencias.API_BASE_URL, null).orEmpty()
  val baseInicial = if (baseSalva.isBlank() || baseSalva.contains("10.0.2.2") || baseSalva.contains("localhost")) {
    ApiChaveiroCar.URL_PADRAO
  } else baseSalva

  var baseUrl by remember {
    mutableStateOf(baseInicial)
  }
  var email by remember {
    mutableStateOf(preferencias.getString(Preferencias.API_EMAIL, "") ?: "")
  }
  var emailVerificado by remember { mutableStateOf(false) }
  var nome by remember { mutableStateOf("") }
  var empresaNova by remember { mutableStateOf("") }
  var senha by remember { mutableStateOf("") }
  var tokenRedefinicao by remember { mutableStateOf("") }
  var tokenConvite by remember { mutableStateOf("") }
  var tokenConviteLogado by remember { mutableStateOf("") }
  var modoCadastro by remember { mutableStateOf(false) }
  var modoEsqueciSenha by remember { mutableStateOf(false) }
  var modoRedefinicao by remember { mutableStateOf(false) }
  var modoConvite by remember { mutableStateOf(false) }
  var mostrarAceitarConvite by remember { mutableStateOf(false) }
  var logado by remember {
    mutableStateOf(!preferenciasApi.getString(Preferencias.API_ACESSO, "").isNullOrBlank())
  }
  var empresaId by remember {
    mutableStateOf(preferencias.getString(Preferencias.API_EMPRESA_ID, "") ?: "")
  }
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
          Triple(usuario?.optBoolean("emailVerificado") ?: false, itens, papeis)
        }
      }.onSuccess { (verificado, lista, papeis) ->
        emailVerificado = verificado
        empresas = lista
        papeisPorEmpresa = papeis
        if (lista.isNotEmpty() && lista.none { it.first == empresaId }) {
          empresaId = lista.first().first
          preferencias.edit().putString(Preferencias.API_EMPRESA_ID, empresaId).apply()
        }
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
          preferencias.edit()
            .putString(Preferencias.API_BASE_URL, baseUrl.trim())
            .putString(Preferencias.API_EMAIL, email.trim())
            .apply()
          recarregar()
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
          val primeiraEmpresa = resposta.optJSONArray("empresas")?.optJSONObject(0)?.optString("id").orEmpty()
          if (primeiraEmpresa.isNotBlank()) {
            empresaId = primeiraEmpresa
          }
          preferencias.edit()
            .putString(Preferencias.API_BASE_URL, baseUrl.trim())
            .putString(Preferencias.API_EMAIL, emailConta)
            .apply {
              if (empresaId.isNotBlank()) putString(Preferencias.API_EMPRESA_ID, empresaId)
            }
            .apply()
          logado = true
          recarregar()
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
          empresaId = resposta.optJSONArray("empresas")?.optJSONObject(0)?.optString("id").orEmpty()
          preferencias.edit()
            .putString(Preferencias.API_BASE_URL, baseUrl.trim())
            .putString(Preferencias.API_EMAIL, email.trim())
            .putString(Preferencias.API_EMPRESA_ID, empresaId)
            .apply()
          recarregar()
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
          cliente.aceitarConvite(tokenConvite.trim(), nome.trim(), senha)
        }
      }
      entrando = false
      resultado.onSuccess { resposta ->
        guardarTokens(cliente)
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

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Sua conta e empresa", style = MaterialTheme.typography.titleSmall)
      Text(
        text = if (logado) "Conectado ${if (email.isBlank()) "" else "como $email"}. Seus registros e fotos são sincronizados automaticamente em todos os seus dispositivos."
        else "Entre com sua conta ChaveiroCar para sincronizar atendimentos, fichas e fotos entre celular, PC e iPhone.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (!logado) {
        Button(
          onClick = { entrarComGoogle() },
          enabled = !entrando,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (entrando) "Conectando..." else "Entrar com Google")
        }
        if (modoCadastro || modoConvite) {
          OutlinedTextField(
            value = nome, onValueChange = { nome = it },
            label = { Text("Seu nome") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (modoCadastro) {
          OutlinedTextField(
            value = empresaNova, onValueChange = { empresaNova = it },
            label = { Text("Nome da empresa") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (!modoRedefinicao && !modoConvite) {
          OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("E-mail") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (modoRedefinicao) {
          OutlinedTextField(
            value = tokenRedefinicao, onValueChange = { tokenRedefinicao = it },
            label = { Text("Código recebido no e-mail") },
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (modoConvite) {
          OutlinedTextField(
            value = tokenConvite, onValueChange = { tokenConvite = it },
            label = { Text("Código do convite") },
            modifier = Modifier.fillMaxWidth(),
          )
        }
        if (modoCadastro || modoConvite || modoRedefinicao || (!modoEsqueciSenha && !modoRedefinicao && !modoConvite)) {
          OutlinedTextField(
            value = senha, onValueChange = { senha = it },
            label = { Text(if (modoRedefinicao) "Nova senha" else "Senha") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
          )
        }
        when {
          modoConvite -> Button(onClick = { aceitarConviteNovo() }, enabled = !entrando && tokenConvite.isNotBlank()) {
            Text(if (entrando) "Aceitando..." else "Aceitar convite")
          }
          modoCadastro -> Button(onClick = { cadastrar() }, enabled = !entrando) {
            Text(if (entrando) "Criando conta..." else "Criar conta")
          }
          modoEsqueciSenha -> Button(onClick = { solicitarRedefinicao() }, enabled = !entrando) {
            Text(if (entrando) "Enviando..." else "Enviar instruções")
          }
          modoRedefinicao -> Button(onClick = { redefinirSenha() }, enabled = !entrando) {
            Text(if (entrando) "Alterando..." else "Alterar senha")
          }
          else -> Button(onClick = { entrar() }, enabled = !entrando) {
            Text(if (entrando) "Entrando..." else "Entrar")
          }
        }
        androidx.compose.material3.TextButton(onClick = {
          modoCadastro = false
          modoEsqueciSenha = false
          modoRedefinicao = false
          modoConvite = false
        }) { Text("Voltar ao login") }
        if (!modoCadastro && !modoEsqueciSenha && !modoRedefinicao && !modoConvite) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton(onClick = { modoCadastro = true }) {
              Text("Criar conta")
            }
          androidx.compose.material3.TextButton(onClick = { modoEsqueciSenha = true }) {
            Text("Esqueci a senha")
          }
          androidx.compose.material3.TextButton(onClick = { modoConvite = true }) {
            Text("Aceitar convite")
          }
          }
        }
        if (modoEsqueciSenha) {
          androidx.compose.material3.TextButton(onClick = { modoRedefinicao = true; modoEsqueciSenha = false }) {
            Text("Já tenho o código de redefinição")
          }
        }
      } else {
        if (!emailVerificado) {
          Text(
            "E-mail ainda não confirmado. Confirme para proteger o acesso à conta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
          OutlinedButton(onClick = { reenviarVerificacao() }, enabled = !entrando) {
            Text(if (entrando) "Enviando..." else "Reenviar confirmação")
          }
        }
        if (mostrarAceitarConvite) {
          OutlinedTextField(
            value = tokenConviteLogado,
            onValueChange = { tokenConviteLogado = it },
            label = { Text("Código do convite") },
            modifier = Modifier.fillMaxWidth(),
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { aceitarConviteLogado() }, enabled = !entrando && tokenConviteLogado.isNotBlank()) {
              Text(if (entrando) "Aceitando..." else "Aceitar convite")
            }
            OutlinedButton(onClick = { mostrarAceitarConvite = false }) { Text("Cancelar") }
          }
        } else {
          androidx.compose.material3.TextButton(onClick = { mostrarAceitarConvite = true }) {
            Text("Tenho um código de convite")
          }
        }
        if (empresas.size > 1) {
          empresas.forEach { (id, nome) ->
            OutlinedButton(
              onClick = {
                empresaId = id
                preferencias.edit().putString(Preferencias.API_EMPRESA_ID, id).apply()
              },
              modifier = Modifier.fillMaxWidth(),
            ) { Text(if (id == empresaId) "✓ $nome" else nome) }
          }
        } else if (empresaId.isNotBlank()) {
          Text("Empresa: ${empresas.firstOrNull { it.first == empresaId }?.second ?: empresaId}",
            style = MaterialTheme.typography.bodySmall)
        }
        if (conflitos.isNotEmpty()) {
          conflitos.forEach { conflito ->
            Card(modifier = Modifier.fillMaxWidth()) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Conflito: ${conflito.placa.ifBlank { conflito.uuid }}", style = MaterialTheme.typography.titleSmall)
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
                  ) { Text("Manter meu aparelho") }
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
                  ) { Text("Aceitar servidor") }
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
        androidx.compose.material3.TextButton(onClick = {
          escopo.launch {
            withContext(Dispatchers.IO) { api().sair() }
            preferenciasApi.edit().remove(Preferencias.API_ACESSO).remove(Preferencias.API_REFRESH).apply()
            logado = false
            empresas = emptyList()
            papeisPorEmpresa = emptyMap()
            aoSair()
          }
          Toast.makeText(contexto, "Sessão encerrada neste aparelho.", Toast.LENGTH_SHORT).show()
        }) { Text("Sair da conta") }
      }
    }
  }
}
