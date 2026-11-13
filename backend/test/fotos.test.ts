import { randomUUID } from "node:crypto";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cadastrar, criarAmbiente, corpoMultipart, imagemJpeg, type Ambiente } from "./ajuda.js";

let ambiente: Ambiente;
beforeEach(async () => { ambiente = await criarAmbiente(); });
afterEach(async () => { await ambiente.fechar(); });

describe("fotos", () => {
  it("upload, listagem, download autenticado e soft delete", async () => {
    const conta = await cadastrar(ambiente.app);
    const id = conta.empresas[0]!.id;
    const a = (await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/atendimentos`, headers: conta.cabecalho,
      payload: { placa: "FOT1F23", modelo: "Gol" },
    })).json();

    const setasJson = JSON.stringify([{ x1: 0.1, y1: 0.2, x2: 0.8, y2: 0.7, cor: "#EF4444", espessura: 4 }]);
    const up = corpoMultipart(
      { categoria: "CARRO", legenda: "frente", anotacoes: setasJson },
      { nome: "foto.jpg", mime: "image/jpeg", bytes: imagemJpeg("xxx") },
    );
    const envio = await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/atendimentos/${a.id}/fotos`,
      headers: { ...conta.cabecalho, "content-type": up.contentType }, payload: up.payload,
    });
    expect(envio.statusCode).toBe(201);
    const foto = envio.json();
    expect(foto.mimeType).toBe("image/jpeg");
    expect(foto.anotacoes).toBe(setasJson);
    expect(foto.url).toContain(foto.id);

    const list = await ambiente.app.inject({ method: "GET", url: `/api/v1/empresas/${id}/atendimentos/${a.id}/fotos`, headers: conta.cabecalho });
    expect(list.json().itens).toHaveLength(1);

    const arq = await ambiente.app.inject({ method: "GET", url: foto.url, headers: conta.cabecalho });
    expect(arq.statusCode).toBe(200);
    expect(arq.headers["content-type"]).toContain("image/jpeg");

    const semAuth = await ambiente.app.inject({ method: "GET", url: foto.url });
    expect(semAuth.statusCode).toBe(401);

    const del = await ambiente.app.inject({ method: "DELETE", url: `/api/v1/empresas/${id}/fotos/${foto.id}?baseRevisao=1`, headers: conta.cabecalho });
    expect(del.statusCode).toBe(200);
    expect(del.json().excluidoEm).toBeTruthy();
    const list2 = await ambiente.app.inject({ method: "GET", url: `/api/v1/empresas/${id}/atendimentos/${a.id}/fotos`, headers: conta.cabecalho });
    expect(list2.json().itens).toHaveLength(0);
  });

  it("recusa arquivo que não é imagem", async () => {
    const conta = await cadastrar(ambiente.app);
    const id = conta.empresas[0]!.id;
    const a = (await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/atendimentos`, headers: conta.cabecalho, payload: { placa: "FOT2F23", modelo: "Gol" },
    })).json();
    const up = corpoMultipart({}, { nome: "x.jpg", mime: "image/jpeg", bytes: Buffer.from("não é imagem") });
    const r = await ambiente.app.inject({
      method: "POST", url: `/api/v1/empresas/${id}/atendimentos/${a.id}/fotos`,
      headers: { ...conta.cabecalho, "content-type": up.contentType }, payload: up.payload,
    });
    expect(r.statusCode).toBe(400);
  });

  it("repete upload com o mesmo UUID e checksum sem duplicar foto", async () => {
    const conta = await cadastrar(ambiente.app);
    const empresaId = conta.empresas[0]!.id;
    const atendimento = (await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/atendimentos`,
      headers: conta.cabecalho,
      payload: { placa: "IDEM1D23", modelo: "Onix" },
    })).json();
    const id = randomUUID();
    const bytes = imagemJpeg("mesma foto");

    async function enviar() {
      const multipart = corpoMultipart(
        { id, categoria: "CARRO", legenda: "frente", ordem: "0" },
        { nome: "foto.jpg", mime: "image/jpeg", bytes },
      );
      return ambiente.app.inject({
        method: "POST",
        url: `/api/v1/empresas/${empresaId}/atendimentos/${atendimento.id}/fotos`,
        headers: { ...conta.cabecalho, "content-type": multipart.contentType },
        payload: multipart.payload,
      });
    }

    const primeira = await enviar();
    const segunda = await enviar();
    expect(primeira.statusCode).toBe(201);
    expect(segunda.statusCode).toBe(201);
    expect(segunda.json().id).toBe(id);
    const lista = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresaId}/atendimentos/${atendimento.id}/fotos`,
      headers: conta.cabecalho,
    });
    expect(lista.json().itens).toHaveLength(1);
  });
});
