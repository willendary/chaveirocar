import { ErroApi, erros } from "../erros.js";

export async function consultarPlaca(placa: string) {
  const chave = process.env.PLACADATA_API_KEY;
  const indisponivel = new ErroApi(503, "INDISPONIVEL", "Consulta por placa indisponível.");
  if (!chave) throw indisponivel;
  const resposta = await fetch(`https://api.placadata.com/veiculo/${placa}?campos=placa`, {
    headers: { "x-api-key": chave },
    signal: AbortSignal.timeout(6000),
    redirect: "error",
  }).catch(() => { throw indisponivel; });
  if (resposta.status === 404) throw erros.naoEncontrado("Placa não localizada.");
  if (!resposta.ok) throw indisponivel;
  const dados = await resposta.json().catch(() => { throw indisponivel; }) as { fipe?: Record<string, unknown> } | null;
  const veiculo = dados?.fipe;
  if (typeof veiculo?.modelo !== "string" || !veiculo.modelo.trim()) throw erros.naoEncontrado("Modelo não localizado.");
  return {
    modelo: veiculo.modelo.trim(),
    marca: typeof veiculo.marca === "string" ? veiculo.marca.trim() : "",
    ano: String(veiculo.anoModelo || veiculo.anoFabricacao || ""),
  };
}
