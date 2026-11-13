package com.willendary.chaveirocar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.willendary.chaveirocar.nuvem.ApiChaveiroCar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class MembroEquipeUi(val id: String, val nome: String, val email: String, val papel: String)
private data class ConviteEquipeUi(val id: String, val email: String, val papel: String, val status: String)

@Composable
fun SecaoEquipeApi(
  empresaId: String,
  papelEmpresa: String,
  criarApi: () -> ApiChaveiroCar,
  guardarTokens: (ApiChaveiroCar) -> Unit,
) {
  val escopo = rememberCoroutineScope()
  val podeGerenciar = papelEmpresa == "proprietario" || papelEmpresa == "administrador"
  var emailConvite by remember { mutableStateOf("") }
  var papelConvite by remember { mutableStateOf("colaborador") }
  var membros by remember { mutableStateOf<List<MembroEquipeUi>>(emptyList()) }
  var convites by remember { mutableStateOf<List<ConviteEquipeUi>>(emptyList()) }
  var estado by remember { mutableStateOf("") }
  var carregando by remember { mutableStateOf(false) }

  fun atualizar() {
    if (carregando) return
    carregando = true
    escopo.launch {
      val resultado = runCatching {
        withContext(Dispatchers.IO) {
          val api = criarApi()
          val membrosJson = api.listarMembros(empresaId)
          val listaMembros = buildList {
            for (i in 0 until membrosJson.length()) {
              val item = membrosJson.optJSONObject(i) ?: continue
              add(MembroEquipeUi(
                item.optString("usuarioId"), item.optString("nome"),
                item.optString("email"), item.optString("papel"),
              ))
            }
          }
          val listaConvites = if (podeGerenciar) {
            val convitesJson = api.listarConvites(empresaId)
            buildList {
              for (i in 0 until convitesJson.length()) {
                val item = convitesJson.optJSONObject(i) ?: continue
                val situacao = when {
                  item.opt("aceitoEm") != null && item.opt("aceitoEm") != JSONObject.NULL -> "aceito"
                  item.opt("canceladoEm") != null && item.opt("canceladoEm") != JSONObject.NULL -> "cancelado"
                  item.optBoolean("expirado") -> "expirado"
                  else -> "pendente"
                }
                add(ConviteEquipeUi(item.optString("id"), item.optString("email"), item.optString("papel"), situacao))
              }
            }
          } else emptyList()
          guardarTokens(api)
          listaMembros to listaConvites
        }
      }
      carregando = false
      resultado.onSuccess { (listaMembros, listaConvites) ->
        membros = listaMembros
        convites = listaConvites
        estado = "Equipe atualizada."
      }.onFailure { erro -> estado = erro.message ?: "Não foi possível carregar a equipe." }
    }
  }

  fun executar(acao: suspend (ApiChaveiroCar) -> String) {
    if (carregando) return
    carregando = true
    escopo.launch {
      val api = criarApi()
      val resultado = runCatching { withContext(Dispatchers.IO) { acao(api) } }
      guardarTokens(api)
      carregando = false
      resultado.onSuccess { estado = it; atualizar() }
        .onFailure { estado = it.message ?: "Não foi possível concluir a ação." }
    }
  }

  LaunchedEffect(empresaId, papelEmpresa) { atualizar() }

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Equipe e acessos", style = MaterialTheme.typography.titleSmall)
      if (podeGerenciar) {
        OutlinedTextField(
          value = emailConvite,
          onValueChange = { emailConvite = it },
          label = { Text("E-mail do colaborador") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("administrador", "colaborador", "leitor").forEach { papel ->
            OutlinedButton(onClick = { papelConvite = papel }, enabled = !carregando) {
              Text(if (papelConvite == papel) "✓ $papel" else papel)
            }
          }
        }
        Button(
          onClick = {
            val destino = emailConvite.trim()
            if (destino.isNotEmpty()) executar { api ->
              val resultado = api.convidar(empresaId, destino, papelConvite)
              if (resultado.optBoolean("emailEnviado")) "Convite enviado para $destino."
              else "Convite criado, mas o e-mail não foi enviado."
            }
          },
          enabled = !carregando && emailConvite.isNotBlank(),
        ) { Text(if (carregando) "Aguarde..." else "Convidar") }
      }
      OutlinedButton(onClick = { atualizar() }, enabled = !carregando) {
        Text(if (carregando) "Atualizando..." else "Atualizar equipe")
      }
      membros.forEach { membro ->
        Text("${membro.nome} · ${membro.email} · ${membro.papel}", style = MaterialTheme.typography.bodySmall)
        if (podeGerenciar && membro.papel != "proprietario") {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("administrador", "colaborador", "leitor").forEach { papel ->
              OutlinedButton(onClick = {
                executar { api ->
                  api.alterarPapel(empresaId, membro.id, papel)
                  "Permissão de ${membro.email} alterada para $papel."
                }
              }, enabled = !carregando && membro.papel != papel) { Text(papel) }
            }
            OutlinedButton(onClick = {
              executar { api ->
                api.removerMembro(empresaId, membro.id)
                "Acesso de ${membro.email} removido."
              }
            }, enabled = !carregando) { Text("Remover") }
          }
        }
      }
      convites.filter { it.status == "pendente" }.forEach { convite ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Convite: ${convite.email} · ${convite.papel}", modifier = Modifier.weight(1f))
          if (podeGerenciar) OutlinedButton(onClick = {
            executar { api ->
              api.cancelarConvite(empresaId, convite.id)
              "Convite de ${convite.email} cancelado."
            }
          }, enabled = !carregando) { Text("Cancelar") }
        }
      }
      if (estado.isNotBlank()) Text(estado, style = MaterialTheme.typography.bodySmall)
    }
  }
}
