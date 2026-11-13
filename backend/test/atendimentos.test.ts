import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cadastrar, criarAmbiente, type Ambiente } from "./ajuda.js";

let ambiente: Ambiente;

beforeEach(async () => {
  ambiente = await criarAmbiente();
});
afterEach(async () => {
  await ambiente.fechar();
});

async function criar(conta: Awaited<ReturnType<typeof cadastrar>>) {
  const r = await ambiente.app.inject({
    method: "POST",
    url: `/api/v1/empresas/${conta.empresas[0]!.id}/atendimentos`,
    headers: conta.cabecalho,
    payload: { placa: "ABC1D23", marca: "Fiat", modelo: "Uno", tiposServico: ["CHAVE_CODIFICADA", "TRANSPONDER"] },
  });
  expect(r.statusCode).toBe(201);
  return r.json();
}

describe("atendimentos", () => {
  it("cria, lê, atualiza e exclui com revisão", async () => {
    const conta = await cadastrar(ambiente.app);
    const id = conta.empresas[0]!.id;
    const base = `/api/v1/empresas/${id}/atendimentos`;
    const a = await criar(conta);
    expect(a.revisao).toBe(1);
    expect(a.segmento).toBe("AUTOMOTIVO");
    expect(a.tiposServico).toEqual(["CHAVE_CODIFICADA", "TRANSPONDER"]);

    const lista = await ambiente.app.inject({ method: "GET", url: `${base}?busca=abc1d23`, headers: conta.cabecalho });
    expect(lista.statusCode).toBe(200);
    expect(lista.json().total).toBe(1);

    const upd = await ambiente.app.inject({
      method: "PUT", url: `${base}/${a.id}`, headers: conta.cabecalho,
      payload: { placa: "ABC1D23", modelo: "Uno", baseRevisao: 1, solucao: "Programada" },
    });
    expect(upd.statusCode).toBe(200);
    expect(upd.json().revisao).toBe(2);

    const conflito = await ambiente.app.inject({
      method: "PUT", url: `${base}/${a.id}`, headers: conta.cabecalho,
      payload: { placa: "ABC1D23", modelo: "Uno", baseRevisao: 1, solucao: "outra" },
    });
    expect(conflito.statusCode).toBe(409);
    expect(conflito.json().erro.codigo).toBe("CONFLITO");

    const del = await ambiente.app.inject({
      method: "DELETE", url: `${base}/${a.id}?baseRevisao=2`, headers: conta.cabecalho,
    });
    expect(del.statusCode).toBe(200);
    expect(del.json().excluidoEm).toBeTruthy();

    const sumiu = await ambiente.app.inject({ method: "GET", url: `${base}/${a.id}`, headers: conta.cabecalho });
    expect(sumiu.statusCode).toBe(404);
  });

  it("permite placa vazia e exige modelo no segmento AUTOMOTIVO", async () => {
    const conta = await cadastrar(ambiente.app);
    const id = conta.empresas[0]!.id;
    const base = `/api/v1/empresas/${id}/atendimentos`;

    const semModelo = await ambiente.app.inject({
      method: "POST",
      url: base,
      headers: conta.cabecalho,
      payload: { placa: "ABC1D23", marca: "Fiat" },
    });
    expect(semModelo.statusCode).toBe(400);
    expect(semModelo.json().erro.mensagem).toContain("modelo");

    const semPlaca = await ambiente.app.inject({
      method: "POST",
      url: base,
      headers: conta.cabecalho,
      payload: { marca: "Chevrolet", modelo: "Onix", tiposServico: ["ABERTURA"] },
    });
    expect(semPlaca.statusCode).toBe(201);
    expect(semPlaca.json().placa).toBe("");
    expect(semPlaca.json().modelo).toBe("Onix");
  });

  it("cria e filtra atendimentos do segmento RESIDENCIAL sem exigir dados automotivos", async () => {
    const conta = await cadastrar(ambiente.app);
    const id = conta.empresas[0]!.id;
    const base = `/api/v1/empresas/${id}/atendimentos`;

    await criar(conta);

    const residencial = await ambiente.app.inject({
      method: "POST",
      url: base,
      headers: conta.cabecalho,
      payload: {
        segmento: "RESIDENCIAL",
        tipoLocal: "Apartamento",
        ambientePorta: "Porta social",
        marcaFechadura: "Papaiz",
        modeloFechadura: "Tetra",
        tipoCilindro: "Multiponto",
        enderecoLocal: "Rua A, 100",
        tiposServico: ["ABERTURA_RESIDENCIAL", "TROCA_SEGREDO"],
        valorCentavos: 18000,
      },
    });
    expect(residencial.statusCode).toBe(201);
    const corpo = residencial.json();
    expect(corpo.segmento).toBe("RESIDENCIAL");
    expect(corpo.placa).toBe("");
    expect(corpo.modelo).toBe("");
    expect(corpo.tipoLocal).toBe("Apartamento");
    expect(corpo.ambientePorta).toBe("Porta social");
    expect(corpo.marcaFechadura).toBe("Papaiz");
    expect(corpo.tiposServico).toEqual(["ABERTURA_RESIDENCIAL", "TROCA_SEGREDO"]);

    const filtroRes = await ambiente.app.inject({
      method: "GET",
      url: `${base}?segmento=RESIDENCIAL`,
      headers: conta.cabecalho,
    });
    expect(filtroRes.statusCode).toBe(200);
    expect(filtroRes.json().total).toBe(1);
    expect(filtroRes.json().itens[0].id).toBe(corpo.id);

    const filtroAuto = await ambiente.app.inject({
      method: "GET",
      url: `${base}?segmento=AUTOMOTIVO`,
      headers: conta.cabecalho,
    });
    expect(filtroAuto.statusCode).toBe(200);
    expect(filtroAuto.json().total).toBe(1);
  });

  it("exige baseRevisao para editar", async () => {
    const conta = await cadastrar(ambiente.app);
    const a = await criar(conta);
    const r = await ambiente.app.inject({
      method: "PUT", url: `/api/v1/empresas/${conta.empresas[0]!.id}/atendimentos/${a.id}`,
      headers: conta.cabecalho, payload: { placa: "ABC1D23", modelo: "Uno" },
    });
    expect(r.statusCode).toBe(409);
  });

  it("leitor não escreve", async () => {
    const conta = await cadastrar(ambiente.app);
    const id = conta.empresas[0]!.id;
    // cria convite leitor e aceita via conta nova
    const conv = await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/convites`, headers: conta.cabecalho,
      payload: { email: "leitor@exemplo.com", papel: "leitor" },
    });
    expect(conv.statusCode).toBe(201);
    const token = conv.json().token;
    const aceitou = await ambiente.app.inject({
      method: "POST", url: `/api/v1/convites/${token}/aceitar`, payload: { nome: "Leitor", senha: "Senha1234" },
    });
    expect(aceitou.statusCode).toBe(200);
    const leitor = aceitou.json();
    const r = await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/atendimentos`,
      headers: { authorization: `Bearer ${leitor.tokens.acesso}` }, payload: { placa: "XXX1X99" },
    });
    expect(r.statusCode).toBe(403);
  });
});
