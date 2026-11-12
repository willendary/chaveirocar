import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cadastrar, criarAmbiente, corpoMultipart, imagemJpeg, zipArmazenado, type Ambiente } from "./ajuda.js";

let ambiente: Ambiente;
beforeEach(async () => { ambiente = await criarAmbiente(); });
afterEach(async () => { await ambiente.fechar(); });

async function backupZip(): Promise<Buffer> {
  const img = imagemJpeg("img1");
  const dados = {
    versao: 6, exportadoEm: Date.now(),
    atendimentos: [{ id: 7, atualizadoEm: 1, placa: "BKP1K23", marca: "VW", tiposServico: ["TRANSPONDER"], dataServico: Date.now(),
      fotos: [{ arquivo: "f1.jpg", categoria: "CARRO", legenda: "frente", ordem: 0 }] }],
    fichas: [{ id: 3, equipamento: "XTOL", marca: "VW", descricao: "x", foto: "" }],
  };
  return zipArmazenado([
    { nome: "dados.json", bytes: Buffer.from(JSON.stringify(dados), "utf8") },
    { nome: "fotos/f1.jpg", bytes: img },
  ]);
}

describe("equipe e convites", () => {
  it("convida, aceita, troca papel e remove", async () => {
    const dono = await cadastrar(ambiente.app, { email: "dono@exemplo.com" });
    const id = dono.empresas[0]!.id;
    const conv = await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/convites`, headers: dono.cabecalho,
      payload: { email: "func@exemplo.com", papel: "colaborador" },
    });
    expect(conv.statusCode).toBe(201);
    const token = conv.json().token;
    expect(token).toBeTruthy();

    const vis = await ambiente.app.inject({ method: "GET", url: `/api/v1/convites/${token}` });
    expect(vis.statusCode).toBe(200);
    expect(vis.json().empresaNome).toBeTruthy();

    const aceitou = await ambiente.app.inject({
      method: "POST", url: `/api/v1/convites/${token}/aceitar`, payload: { nome: "Func", senha: "Senha1234" },
    });
    expect(aceitou.statusCode).toBe(200);
    const funcId = aceitou.json().usuarioId;

    const membros = await ambiente.app.inject({ method: "GET", url: `/api/v1/empresas/${id}/membros`, headers: dono.cabecalho });
    expect(membros.json().itens).toHaveLength(2);

    const troca = await ambiente.app.inject({
      method: "PATCH", url: `/api/v1/empresas/${id}/membros/${funcId}`, headers: dono.cabecalho, payload: { papel: "administrador" },
    });
    expect(troca.statusCode).toBe(200);
    expect(troca.json().papel).toBe("administrador");

    const remove = await ambiente.app.inject({ method: "DELETE", url: `/api/v1/empresas/${id}/membros/${funcId}`, headers: dono.cabecalho });
    expect(remove.statusCode).toBe(204);
  });

  it("token reutilizado falha", async () => {
    const dono = await cadastrar(ambiente.app, { email: "dono2@exemplo.com" });
    const id = dono.empresas[0]!.id;
    const conv = await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/convites`, headers: dono.cabecalho,
      payload: { email: "x@exemplo.com", papel: "leitor" },
    });
    const token = conv.json().token;
    await ambiente.app.inject({ method: "POST", url: `/api/v1/convites/${token}/aceitar`, payload: { nome: "X", senha: "Senha1234" } });
    const segundo = await ambiente.app.inject({ method: "POST", url: `/api/v1/convites/${token}/aceitar`, payload: { nome: "X", senha: "Senha1234" } });
    expect(segundo.statusCode).toBe(400);
  });
});

describe("migração de backup Android", () => {
  it("importa atendimentos e fotos e é idempotente", async () => {
    const conta = await cadastrar(ambiente.app, { email: "mig@exemplo.com" });
    const id = conta.empresas[0]!.id;
    const zip = await backupZip();
    const up = corpoMultipart({}, { nome: "backup.zip", mime: "application/zip", bytes: zip });
    const r = await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/importacoes/backup`,
      headers: { ...conta.cabecalho, "content-type": up.contentType }, payload: up.payload,
    });
    expect(r.statusCode).toBe(200);
    expect(r.json().atendimentosImportados).toBe(1);
    expect(r.json().fichasImportadas).toBe(1);
    expect(r.json().fotosImportadas).toBe(1);

    const r2 = await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/importacoes/backup`,
      headers: { ...conta.cabecalho, "content-type": up.contentType }, payload: up.payload,
    });
    expect(r2.json().atendimentosIgnorados).toBe(1);
    expect(r2.json().atendimentosImportados).toBe(0);

    const lista = await ambiente.app.inject({ method: "GET", url: `/api/v1/empresas/${id}/atendimentos?busca=BKP1K23`, headers: conta.cabecalho });
    expect(lista.json().total).toBe(1);
  });
});
