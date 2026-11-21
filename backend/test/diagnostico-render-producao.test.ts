import { it, expect } from "vitest";
it("smoke de versão de produção no Render, sem autenticação", async () => {
  const url = "https://chaveirocar-api.onrender.com/saude";
  try {
    const resposta = await fetch(url, { signal: AbortSignal.timeout(60_000), redirect: "error" });
    const corpo = (await resposta.text()).slice(0,1000);
    console.log("RENDER_PRODUCAO_VERIFICACAO", JSON.stringify({status:resposta.status,corpo}));
    expect(resposta.status).toBeGreaterThanOrEqual(200);
  } catch(e) {
    console.log("RENDER_PRODUCAO_VERIFICACAO_ERRO",String(e));
  }
}, 70_000);