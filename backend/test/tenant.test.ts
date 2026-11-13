import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cadastrar, criarAmbiente, type Ambiente, type Conta } from "./ajuda.js";

let ambiente: Ambiente;

beforeEach(async () => {
  ambiente = await criarAmbiente();
});

afterEach(async () => {
  await ambiente.fechar();
});

async function criarAtendimento(conta: Conta, placa: string, cliente = "") {
  const resposta = await ambiente.app.inject({
    method: "POST",
    url: `/api/v1/empresas/${conta.empresas[0]!.id}/atendimentos`,
    headers: conta.cabecalho,
    payload: { placa, modelo: "Palio", clienteNome: cliente, tiposServico: ["CHAVE_CODIFICADA"] },
  });
  expect(resposta.statusCode).toBe(201);
  return resposta.json();
}

describe("isolamento entre empresas", () => {
  it("não permite que outra empresa leia, liste ou escreva dados alheios", async () => {
    const ana = await cadastrar(ambiente.app, { email: "ana@exemplo.com", empresa: "Ana Chaves" });
    const bruno = await cadastrar(ambiente.app, { email: "bruno@exemplo.com", empresa: "Bruno Chaves" });
    const atendimento = await criarAtendimento(ana, "ABC1D23", "Cliente da Ana");

    const leitura = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${ana.empresas[0]!.id}/atendimentos/${atendimento.id}`,
      headers: bruno.cabecalho,
    });
    expect(leitura.statusCode).toBe(404);

    const listagem = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${ana.empresas[0]!.id}/atendimentos`,
      headers: bruno.cabecalho,
    });
    expect(listagem.statusCode).toBe(404);

    const escrita = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${ana.empresas[0]!.id}/atendimentos`,
      headers: bruno.cabecalho,
      payload: { placa: "XYZ9Z99" },
    });
    expect(escrita.statusCode).toBe(404);

    const empresa = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${ana.empresas[0]!.id}`,
      headers: bruno.cabecalho,
    });
    expect(empresa.statusCode).toBe(404);

    const propria = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${ana.empresas[0]!.id}/atendimentos/${atendimento.id}`,
      headers: ana.cabecalho,
    });
    expect(propria.statusCode).toBe(200);
    expect(propria.json().placa).toBe("ABC1D23");
  });

  it("não deixa a empresa escolhida no corpo sobrescrever o filtro de tenant", async () => {
    const ana = await cadastrar(ambiente.app, { email: "ana2@exemplo.com" });
    const bruno = await cadastrar(ambiente.app, { email: "bruno2@exemplo.com" });
    const resposta = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${bruno.empresas[0]!.id}/atendimentos`,
      headers: bruno.cabecalho,
      payload: {
        placa: "TEN1T23",
        modelo: "Palio",
        empresaId: ana.empresas[0]!.id,
      },
    });
    expect(resposta.statusCode).toBe(201);
    expect(resposta.json().empresaId).toBe(bruno.empresas[0]!.id);

    const listaAna = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${ana.empresas[0]!.id}/atendimentos`,
      headers: ana.cabecalho,
    });
    expect(listaAna.json().total).toBe(0);
  });
});
