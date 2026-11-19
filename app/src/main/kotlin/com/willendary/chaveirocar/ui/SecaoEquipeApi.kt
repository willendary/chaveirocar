package com.willendary.chaveirocar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willendary.chaveirocar.nuvem.ApiChaveiroCar
import com.willendary.chaveirocar.ui.theme.coresEstendidas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class MembroEquipeUi(val id: String, val nome: String, val email: String, val papel: String)
private data class ConviteEquipeUi(val id: String, val email: String, val papel: String, val status: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecaoEquipeApi(
  empresaId: String,
  papelEmpresa: String,
  criarApi: () -> ApiChaveiroCar,
  guardarTokens: (ApiChaveiroCar) -> Unit,
) {
  val escopo = rememberCoroutineScope()
  val contexto = LocalContext.current
  val estendidas = MaterialTheme.coresEstendidas
  val podeGerenciar = papelEmpresa == "proprietario" || papelEmpresa == "administrador"
  var emailConvite by remember { mutableStateOf("") }
  var papelConvite by remember { mutableStateOf("colaborador") }
  var codigoConvite by remember { mutableStateOf("") }
  var membros by remember { mutableStateOf<List<MembroEquipeUi>>(emptyList()) }
  var convites by remember { mutableStateOf<List<ConviteEquipeUi>>(emptyList()) }
  var estado by remember { mutableStateOf("") }
  var carregando by remember { mutableStateOf(false) }

  fun atualizar(mensagem: String = "Equipe atualizada.") {
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
        estado = mensagem
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
      resultado.onSuccess { atualizar(it) }
        .onFailure { estado = it.message ?: "Não foi possível concluir a ação." }
    }
  }

  LaunchedEffect(empresaId, papelEmpresa) { atualizar() }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = estendidas.superficieSutil,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text("Equipe e acessos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        TextButton(onClick = { atualizar() }, enabled = !carregando) {
          Text(if (carregando) "Atualizando..." else "Atualizar", fontSize = 12.sp)
        }
      }

      if (podeGerenciar) {
        OutlinedTextField(
          value = emailConvite,
          onValueChange = { emailConvite = it },
          label = { Text("E-mail do colaborador") },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("administrador", "colaborador", "leitor").forEach { papel ->
            FilterChip(
              selected = papelConvite == papel,
              onClick = { papelConvite = papel },
              enabled = !carregando,
              shape = RoundedCornerShape(10.dp),
              label = { Text(papel.replaceFirstChar { it.uppercase() }) },
            )
          }
        }
        Button(
          onClick = {
            val destino = emailConvite.trim()
            if (destino.isNotEmpty() && !carregando) {
              carregando = true
              escopo.launch {
                val api = criarApi()
                val resultado = runCatching {
                  withContext(Dispatchers.IO) { api.convidar(empresaId, destino, papelConvite) }
                }
                guardarTokens(api)
                carregando = false
                resultado.onSuccess { resposta ->
                  codigoConvite = resposta.optString("token")
                  val mensagem = when {
                    resposta.optBoolean("emailEnviado") -> "Convite enviado para $destino."
                    codigoConvite.isNotBlank() ->
                      "Convite criado. Envie o código abaixo para $destino."
                    else -> "Convite criado, mas o e-mail não foi enviado."
                  }
                  atualizar(mensagem)
                }.onFailure { erro ->
                  estado = erro.message ?: "Não foi possível criar o convite."
                }
              }
            }
          },
          enabled = !carregando && emailConvite.isNotBlank(),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth(),
        ) { Text(if (carregando) "Aguarde..." else "Convidar colaborador", fontWeight = FontWeight.Bold) }

        if (codigoConvite.isNotBlank()) {
          OutlinedTextField(
            value = codigoConvite,
            onValueChange = {},
            readOnly = true,
            label = { Text("Código do convite") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          )
          FilledTonalButton(
            onClick = { copiarTexto(contexto, "Código do convite", codigoConvite) },
            shape = RoundedCornerShape(10.dp),
          ) { Text("Copiar código") }
        }
      }

      membros.forEach { membro ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
              ) {
                Text(
                  text = membro.nome.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "M",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                )
              }
              Column(Modifier.weight(1f)) {
                Text(membro.nome.ifBlank { membro.email }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(membro.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Selo(membro.papel, tom = TomSelo.NEUTRO)
            }

            if (podeGerenciar && membro.papel != "proprietario") {
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("administrador", "colaborador", "leitor").forEach { papel ->
                  OutlinedButton(
                    onClick = {
                      executar { api ->
                        api.alterarPapel(empresaId, membro.id, papel)
                        "Permissão de ${membro.email} alterada para $papel."
                      }
                    },
                    enabled = !carregando && membro.papel != papel,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  ) { Text(papel, fontSize = 11.sp) }
                }
                TextButton(
                  onClick = {
                    executar { api ->
                      api.removerMembro(empresaId, membro.id)
                      "Acesso de ${membro.email} removido."
                    }
                  },
                  enabled = !carregando,
                ) { Text("Remover", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
              }
            }
          }
        }
      }

      convites.filter { it.status == "pendente" }.forEach { convite ->
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Column(Modifier.weight(1f)) {
              Text("Convite pendente", style = MaterialTheme.typography.labelSmall, color = estendidas.alerta)
              Text("${convite.email} · ${convite.papel}", style = MaterialTheme.typography.bodySmall)
            }
            if (podeGerenciar) {
              TextButton(
                onClick = {
                  executar { api ->
                    api.cancelarConvite(empresaId, convite.id)
                    "Convite de ${convite.email} cancelado."
                  }
                },
                enabled = !carregando,
              ) { Text("Cancelar", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
          }
        }
      }

      if (estado.isNotBlank()) {
        Text(estado, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
