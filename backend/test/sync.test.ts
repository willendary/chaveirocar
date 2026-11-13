import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cadastrar, criarAmbiente, type Ambiente } from "./ajuda.js";

let ambiente: Ambiente;
beforeEach(async () => { ambiente = await criarAmbiente(); });
afterEach(async () => { await ambiente.fechar(); });

describe("sync", () => {
  it("pull devolve mudanças e push é idempotente com conflito explícito", async () => {
    const conta = await cadastrar(ambiente.app, { email: "sync@exemplo.com" });
    const id = conta.empresas[0]!.id;
    const h = conta.cabecalho;

    const criado = await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/atendimentos`, headers: h,
      payload: { placa: "SYN1C23", modelo: "HB20" },
    });
    const a = criado.json();
    expect(criado.statusCode).toBe(201);

    const pull = await ambiente.app.inject({ method: "GET", url: `/api/v1/empresas/${id}/sync?cursor=0`, headers: h });
    expect(pull.statusCode).toBe(200);
    expect(pull.json().cursor).toBeGreaterThan(0);
    expect(pull.json().atendimentos).toHaveLength(1);

    // push de edição offline com base correta
    const { randomUUID } = await import("node:crypto");
    const opId = randomUUID();
    const lote = {
      operacoes: [{
        opId, entidade: "atendimento", tipo: "upsert", id: a.id, baseRevisao: 1,
        dados: { placa: "SYN1C23", modelo: "HB20", solucao: "via sync" },
      }],
    };
    const push = await ambiente.app.inject({ method: "POST", url: `/api/v1/empresas/${id}/sync`, headers: h, payload: lote });
    expect(push.statusCode).toBe(200);
    expect(push.json().resultados[0].status).toBe("aplicado");

    // mesmo opId de novo -> mesmo resultado, sem nova revisão
    const push2 = await ambiente.app.inject({ method: "POST", url: `/api/v1/empresas/${id}/sync`, headers: h, payload: lote });
    expect(push2.statusCode).toBe(200);
    expect(push2.json().resultados[0].status).toBe("aplicado");
    const depois = await ambiente.app.inject({ method: "GET", url: `/api/v1/empresas/${id}/atendimentos/${a.id}`, headers: h });
    expect(depois.json().revisao).toBe(2);

    // base divergente -> conflito, sem LWW silencioso
    const push3 = await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/sync`, headers: h,
      payload: { operacoes: [{ opId: randomUUID(), entidade: "atendimento", tipo: "upsert", id: a.id, baseRevisao: 1, dados: { placa: "SYN1C23", modelo: "HB20", solucao: "atrasada" } }] },
    });
    expect(push3.json().resultados[0].status).toBe("conflito");
    const intacto = await ambiente.app.inject({ method: "GET", url: `/api/v1/empresas/${id}/atendimentos/${a.id}`, headers: h });
    expect(intacto.json().solucao).toBe("via sync");
  });

  it("pull com cursor não devolve o já entregue", async () => {
    const conta = await cadastrar(ambiente.app, { email: "sync2@exemplo.com" });
    const id = conta.empresas[0]!.id;
    await ambiente.app.inject({ method: "POST", url: `/api/v1/empresas/${id}/atendimentos`, headers: conta.cabecalho, payload: { placa: "CURS123", modelo: "Civic" } });
    const p1 = await ambiente.app.inject({ method: "GET", url: `/api/v1/empresas/${id}/sync?cursor=0`, headers: conta.cabecalho });
    const p2 = await ambiente.app.inject({ method: "GET", url: `/api/v1/empresas/${id}/sync?cursor=${p1.json().cursor}`, headers: conta.cabecalho });
    expect(p2.json().atendimentos).toHaveLength(0);
    expect(p2.json().cursor).toBe(p1.json().cursor);
  });
});
