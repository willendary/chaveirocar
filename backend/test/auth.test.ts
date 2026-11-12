import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cadastrar, criarAmbiente, type Ambiente } from "./ajuda.js";

let ambiente: Ambiente;

beforeEach(async () => {
  ambiente = await criarAmbiente();
});

afterEach(async () => {
  await ambiente.fechar();
});

describe("autenticação", () => {
  it("cadastra, confirma e-mail e retorna a empresa criada", async () => {
    const conta = await cadastrar(ambiente.app, { email: "ana@exemplo.com" });
    expect(conta.tokens.acesso).toBeTruthy();
    expect(conta.tokens.refresh).toBeTruthy();
    expect(conta.empresas).toHaveLength(1);
    expect(conta.empresas[0]!.papel).toBe("proprietario");
    expect(conta.usuario.emailVerificado).toBe(false);
    expect(conta.verificacaoToken).toBeTruthy();

    const verificacao = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/verificar-email",
      payload: { token: conta.verificacaoToken },
    });
    expect(verificacao.statusCode).toBe(204);

    const eu = await ambiente.app.inject({
      method: "GET",
      url: "/api/v1/eu",
      headers: conta.cabecalho,
    });
    expect(eu.statusCode).toBe(200);
    expect(eu.json().usuario.emailVerificado).toBe(true);
  });

  it("recusa e-mail duplicado e senha fraca", async () => {
    await cadastrar(ambiente.app, { email: "bia@exemplo.com" });
    const duplicado = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/cadastro",
      payload: { nome: "Bia", email: "bia@exemplo.com", senha: "Senha1234" },
    });
    expect(duplicado.statusCode).toBe(409);

    const fraca = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/cadastro",
      payload: { nome: "Bia", email: "outra@exemplo.com", senha: "12345678" },
    });
    expect(fraca.statusCode).toBe(400);
  });

  it("login recusa senha errada e aceita a correta", async () => {
    await cadastrar(ambiente.app, { email: "caio@exemplo.com", senha: "Senha1234" });
    const errado = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/login",
      payload: { email: "caio@exemplo.com", senha: "Errada1234" },
    });
    expect(errado.statusCode).toBe(401);

    const certo = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/login",
      payload: { email: "caio@exemplo.com", senha: "Senha1234" },
    });
    expect(certo.statusCode).toBe(200);
    expect(certo.json().tokens.acesso).toBeTruthy();
  });

  it("rotaciona o refresh e revoga a família ao detectar reuso", async () => {
    const conta = await cadastrar(ambiente.app, { email: "dora@exemplo.com" });
    const primeira = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/renovar",
      payload: { refresh: conta.tokens.refresh },
    });
    expect(primeira.statusCode).toBe(200);
    const novos = primeira.json() as { refresh: string };

    const reuso = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/renovar",
      payload: { refresh: conta.tokens.refresh },
    });
    expect(reuso.statusCode).toBe(401);

    const depoisDoReuso = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/renovar",
      payload: { refresh: novos.refresh },
    });
    expect(depoisDoReuso.statusCode).toBe(401);
  });

  it("logout invalida o token de acesso", async () => {
    const conta = await cadastrar(ambiente.app, { email: "eva@exemplo.com" });
    const sair = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/sair",
      headers: conta.cabecalho,
      payload: { refresh: conta.tokens.refresh },
    });
    expect(sair.statusCode).toBe(204);

    const eu = await ambiente.app.inject({
      method: "GET",
      url: "/api/v1/eu",
      headers: conta.cabecalho,
    });
    expect(eu.statusCode).toBe(401);
  });

  it("redefine a senha e revoga sessões antigas", async () => {
    const conta = await cadastrar(ambiente.app, { email: "fabio@exemplo.com", senha: "Senha1234" });
    const pedido = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/esqueci-senha",
      payload: { email: "fabio@exemplo.com" },
    });
    expect(pedido.statusCode).toBe(200);
    const token = pedido.json().redefinicaoToken as string;
    expect(token).toBeTruthy();

    const redefinicao = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/redefinir-senha",
      payload: { token, senha: "NovaSenha123" },
    });
    expect(redefinicao.statusCode).toBe(204);

    const sessaoAntiga = await ambiente.app.inject({
      method: "GET",
      url: "/api/v1/eu",
      headers: conta.cabecalho,
    });
    expect(sessaoAntiga.statusCode).toBe(401);

    const loginAntigo = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/login",
      payload: { email: "fabio@exemplo.com", senha: "Senha1234" },
    });
    expect(loginAntigo.statusCode).toBe(401);

    const loginNovo = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/login",
      payload: { email: "fabio@exemplo.com", senha: "NovaSenha123" },
    });
    expect(loginNovo.statusCode).toBe(200);
  });

  it("não revela se o e-mail existe na recuperação de senha", async () => {
    const resposta = await ambiente.app.inject({
      method: "POST",
      url: "/api/v1/auth/esqueci-senha",
      payload: { email: "nao-existe@exemplo.com" },
    });
    expect(resposta.statusCode).toBe(200);
    expect(resposta.json().redefinicaoToken).toBeUndefined();
  });

  it("responde 401 sem token e 401 com token inválido", async () => {
    const semToken = await ambiente.app.inject({ method: "GET", url: "/api/v1/eu" });
    expect(semToken.statusCode).toBe(401);
    const invalido = await ambiente.app.inject({
      method: "GET",
      url: "/api/v1/eu",
      headers: { authorization: "Bearer nada" },
    });
    expect(invalido.statusCode).toBe(401);
  });
});
