import { ErroApi, erros } from "../erros.js";

export async function consultarPlaca(placa: string) {
  const chave = process.env.FALCON_DATA_HUB_API_KEY;
  const indisponivel = new ErroApi(503, "INDISPONIVEL", "Consulta por placa indisponível.");
  if (!chave) throw indisponivel;
  const resposta = await fetch(`https://beta.falcon-server.com.br/data-hub/private/v1/vehicles/${placa}/search`, {
    headers: { Authorization: `Bearer ${chave}` },
    signal: AbortSignal.timeout(6000),
    redirect: "error",
  }).catch(() => { throw indisponivel; });
  if (resposta.status === 404) throw erros.naoEncontrado("Placa não localizada.");
  if (resposta.status === 402 || resposta.status === 429) {
    throw erros.limite("Limite da consulta por placa atingido. Informe o modelo manualmente.");
  }
  if (resposta.status === 400) {
    const erro = await resposta.json().catch(() => null) as { message?: string } | null;
    if (erro?.message === "Vehicle not found.") throw erros.naoEncontrado("Placa não localizada.");
  }
  if (!resposta.ok) throw indisponivel;
  const dados = await resposta.json().catch(() => { throw indisponivel; }) as { data?: Record<string, unknown> } | null;
  const veiculo = dados?.data;
  if (typeof veiculo?.modelo !== "string" || !veiculo.modelo.trim()) throw erros.naoEncontrado("Modelo não localizado.");
  return {
    modelo: veiculo.modelo.trim(),
    marca: typeof veiculo.marca === "string" ? veiculo.marca.trim() : "",
    ano: String(veiculo.ano_modelo || veiculo.ano || ""),
  };
}
