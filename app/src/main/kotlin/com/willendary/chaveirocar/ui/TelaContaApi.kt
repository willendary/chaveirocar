package com.willendary.chaveirocar.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.willendary.chaveirocar.nuvem.MotorSinc
import com.willendary.chaveirocar.util.Erros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Conta ChaveiroCar (API): login, empresa, sync manual e resolução de conflitos. */
@Composable
fun SecaoContaApi(banco: BancoDeAtendimentos) {
  val contexto = LocalContext.current
  val preferencias = remember { Preferencias.obter(contexto) }
  val preferenciasApi = remember { Preferencias.obterApi(contexto) }
  val escopo = rememberCoroutineScope()

  var baseUrl by remember {
    mutableStateOf(preferencias.getString(Preferencias.API_BASE_URL, "http://10.0.2.2:8787") ?: "")
  }
  var email by remember {
    mutableStateOf(preferencias.getString(Preferencias.API_EMAIL, "") ?: "")
  }
  var senha by remember { mutableStateOf("") }
  var logado by remember {
    mutableStateOf(!preferenciasApi.getString(Preferencias.API_ACESSO, "").isNullOrBlank())
  }
  var empresaId by remember {
    mutableStateOf(preferencias.getString(Preferencias.API_EMPRESA_ID, "") ?: "")
  }
  var empresas by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
  var pendentes by remember { mutableStateOf(banco.pendentesSync().size) }
  var conflitos by remember { mutableStateOf(banco.conflitos()) }
  var sincronizando by remember { mutableStateOf(false) }
  var entrando by remember { mutableStateOf(false) }

  fun api(): ApiChaveiroCar {
    val api = ApiChaveiroCar(baseUrl.trim().ifBlank { "http://10.0.2.2:8787" })
    api.tokenAcesso = preferenciasApi.getString(Preferencias.API_ACESSO, null)?.ifBlank { null }
    api.tokenRefresh = preferenciasApi.getString(Preferencias.API_REFRESH, null)?.ifBlank { null }
    return api
  }

  fun recarregar() {
    pendentes = banco.pendentesSync().size
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
          val lista = api().listarEmpresas()
          buildList {
            for (i in 0 until lista.length()) {
              val e = lista.optJSONObject(i) ?: continue
              add(e.optString("id") to e.optString("nome"))
            }
          }
        }
      }.onSuccess { lista ->
        empresas = lista
        if (lista.isNotEmpty() && empresaId.isBlank()) {
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
      val resultado = runCatching {
        withContext(Dispatchers.IO) { api().entrar(email.trim(), senha) }
      }
      entrando = false
      resultado
        .onSuccess {
          guardarTokens(api())
          logado = true
          preferencias.edit()
            .putString(Preferencias.API_BASE_URL, baseUrl.trim())
            .putString(Preferencias.API_EMAIL, email.trim())
            .apply()
          carregarEmpresas()
          recarregar()
          Toast.makeText(contexto, "Conectado à conta ChaveiroCar", Toast.LENGTH_SHORT).show()
        }
        .onFailure {
          Erros.registrar(contexto, "Conta API: login", it)
          Toast.makeText(contexto, "Não foi possível entrar. Confira e-mail, senha e endereço.", Toast.LENGTH_LONG).show()
        }
    }
  }

  fun sincronizar() {
    if (sincronizando) return
    val empresa = empresaId
    if (empresa.isBlank()) {
      Toast.makeText(contexto, "Escolha uma empresa primeiro.", Toast.LENGTH_SHORT).show()
      return
    }
    sincronizando = true
    escopo.launch {
      val resultado = runCatching {
        withContext(Dispatchers.IO) {
          val api = api()
          val cursor = preferencias.getLong(Preferencias.API_CURSOR, 0)
          val resumo = MotorSinc.sincronizar(banco, api, empresa, cursor, contexto)
          preferencias.edit().putLong(Preferencias.API_CURSOR, resumo.cursor).apply()
          guardarTokens(api)
          resumo
        }
      }
      sincronizando = false
      resultado
        .onSuccess { resumo ->
          recarregar()
          val texto = "Enviadas ${resumo.enviadas}, recebidas ${resumo.recebidas}" +
            (if (resumo.conflitos > 0) ", ${resumo.conflitos} conflito(s) para resolver" else "")
          Toast.makeText(contexto, "Sincronização: $texto", Toast.LENGTH_LONG).show()
        }
        .onFailure {
          Erros.registrar(contexto, "Conta API: sincronizar", it)
          Toast.makeText(contexto, "Sem conexão. O que falta será enviado depois.", Toast.LENGTH_LONG).show()
        }
    }
  }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Conta ChaveiroCar (API)", style = MaterialTheme.typography.titleSmall)
      Text(
        text = if (logado) "Conectado ${if (email.isBlank()) "" else "como $email"}. Edições locais sobem na sincronização; sem internet, ficam na fila."
        else "Entre com sua conta ChaveiroCar para sincronizar atendimentos entre celular, PC e iPhone. O backup no Google Drive continua como está.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      if (!logado) {
        OutlinedTextField(
          value = baseUrl, onValueChange = { baseUrl = it },
          label = { Text("Endereço da API") }, singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = email, onValueChange = { email = it },
          label = { Text("E-mail") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = senha, onValueChange = { senha = it },
          label = { Text("Senha") }, singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { entrar() }, enabled = !entrando) {
          Text(if (entrando) "Entrando..." else "Entrar")
        }
      } else {
        Text(
          text = "Fila local: $pendentes registro(s) aguardando envio. Conflitos: ${conflitos.size}.",
          style = MaterialTheme.typography.bodySmall,
        )
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(
            onClick = { sincronizar() },
            enabled = !sincronizando,
            modifier = Modifier.weight(1f),
          ) { Text(if (sincronizando) "Sincronizando..." else "Sincronizar agora") }
          OutlinedButton(
            onClick = { carregarEmpresas(); recarregar() },
            modifier = Modifier.weight(1f),
          ) { Text("Atualizar status") }
        }
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
                    if (sincronizando) return@Button
                    sincronizando = true
                    escopo.launch {
                      runCatching {
                        withContext(Dispatchers.IO) {
                          MotorSinc.manterLocal(banco, api(), empresaId, conflito.uuid, conflito.revisaoServidor, conflito.entidade)
                        }
                      }.onFailure { Erros.registrar(contexto, "Conta API: manter local", it) }
                      sincronizando = false
                      recarregar()
                    }
                  },
                  enabled = !sincronizando,
                ) { Text("Manter meu aparelho") }
                OutlinedButton(
                  onClick = {
                    if (sincronizando) return@OutlinedButton
                    sincronizando = true
                    escopo.launch {
                      runCatching {
                        withContext(Dispatchers.IO) {
                          MotorSinc.aceitarServidor(banco, api(), empresaId, conflito.uuid, conflito.entidade)
                        }
                      }.onFailure { Erros.registrar(contexto, "Conta API: aceitar servidor", it) }
                      sincronizando = false
                      recarregar()
                    }
                  },
                  enabled = !sincronizando,
                ) { Text("Aceitar servidor") }
              }
            }
          }
        }
        Text(
          "Atendimentos, fichas técnicas e fotos entram na fila. Fotos sobem após o atendimento; sem rede, ficam na fila e sobem depois.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.TextButton(onClick = {
          escopo.launch {
            withContext(Dispatchers.IO) { api().sair() }
            preferenciasApi.edit().remove(Preferencias.API_ACESSO).remove(Preferencias.API_REFRESH).apply()
            logado = false
            empresas = emptyList()
          }
          Toast.makeText(contexto, "Sessão encerrada neste aparelho.", Toast.LENGTH_SHORT).show()
        }) { Text("Sair da conta") }
      }
    }
  }
}
