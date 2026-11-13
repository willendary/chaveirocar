import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
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
    expect(conta.emailEnviado).toBe(false);
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

  it("autentica com Google (accessToken e idToken), cria conta/empresa e vincula usuário existente", async () => {
    const fetchOriginal = globalThis.fetch;
    try {
      globalThis.fetch = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = String(input);
        if (url.includes("userinfo")) {
          const auth = (init?.headers as Record<string, string> | undefined)?.Authorization ?? "";
          if (auth === "Bearer token-google-valido") {
            return new Response(
              JSON.stringify({
                sub: "google-sub-123",
                email: "googleuser@exemplo.com",
                email_verified: true,
                name: "Usuário Google",
              }),
              { status: 200, headers: { "content-type": "application/json" } },
            );
          }
          return new Response("{}", { status: 401 });
        }
        if (url.includes("tokeninfo")) {
          if (url.includes("idtoken-vinculo")) {
            return new Response(
              JSON.stringify({
                sub: "google-sub-999",
                email: "existente@exemplo.com",
                email_verified: "true",
                name: "Existente Google",
              }),
              { status: 200, headers: { "content-type": "application/json" } },
            );
          }
          return new Response("{}", { status: 401 });
        }
        return fetchOriginal(input, init);
      }) as typeof fetch;

      // 1) Novo usuário via accessToken
      const novo = await ambiente.app.inject({
        method: "POST",
        url: "/api/v1/auth/google-login",
        payload: { accessToken: "token-google-valido", empresa: "Chaveiro Google" },
      });
      expect(novo.statusCode).toBe(200);
      const corpoNovo = novo.json();
      expect(corpoNovo.usuario.email).toBe("googleuser@exemplo.com");
      expect(corpoNovo.usuario.emailVerificado).toBe(true);
      expect(corpoNovo.empresas).toHaveLength(1);
      expect(corpoNovo.empresas[0].nome).toBe("Chaveiro Google");
      expect(corpoNovo.tokens.acesso).toBeTruthy();

      // 2) Segundo login com o mesmo sub reutiliza a mesma conta e empresa
      const repetido = await ambiente.app.inject({
        method: "POST",
        url: "/api/v1/auth/google-login",
        payload: { accessToken: "token-google-valido" },
      });
      expect(repetido.statusCode).toBe(200);
      expect(repetido.json().usuario.id).toBe(corpoNovo.usuario.id);
      expect(repetido.json().empresas).toHaveLength(1);

      // 3) Vincula conta previamente criada por e-mail
      const preExistente = await cadastrar(ambiente.app, { email: "existente@exemplo.com" });
      const vinculado = await ambiente.app.inject({
        method: "POST",
        url: "/api/v1/auth/google-login",
        payload: { idToken: "idtoken-vinculo" },
      });
      expect(vinculado.statusCode).toBe(200);
      expect(vinculado.json().usuario.id).toBe(preExistente.usuario.id);
      expect(vinculado.json().usuario.emailVerificado).toBe(true);

      // 4) Token inválido retorna 401
      const invalido = await ambiente.app.inject({
        method: "POST",
        url: "/api/v1/auth/google-login",
        payload: { accessToken: "token-invalido-123" },
      });
      expect(invalido.statusCode).toBe(401);
    } finally {
      globalThis.fetch = fetchOriginal;
    }
  });

  it("bloqueia login e cadastro por senha quando GOOGLE_ONLY=true", async () => {
    const restrito = await criarAmbiente({ GOOGLE_ONLY: "true" });
    try {
      const cad = await restrito.app.inject({
        method: "POST",
        url: "/api/v1/auth/cadastro",
        payload: { nome: "Teste", email: "t@exemplo.com", senha: "Senha1234" },
      });
      expect(cad.statusCode).toBe(403);

      const login = await restrito.app.inject({
        method: "POST",
        url: "/api/v1/auth/login",
        payload: { email: "t@exemplo.com", senha: "Senha1234" },
      });
      expect(login.statusCode).toBe(403);
    } finally {
      await restrito.fechar();
    }
  });

  it("serve página OAuth Google no navegador para loopback local e recusa redirect externo", async () => {
    const ok = await ambiente.app.inject({
      method: "GET",
      url: "/api/v1/auth/google/navegador?redirect_uri=" + encodeURIComponent("http://127.0.0.1:54321/callback"),
    });
    expect(ok.statusCode).toBe(200);
    expect(ok.headers["content-type"]).toContain("text/html");
    expect(ok.headers["cross-origin-opener-policy"]).toBe("same-origin-allow-popups");
    expect(ok.headers["referrer-policy"]).toBe("no-referrer-when-downgrade");
    expect(ok.body).toContain("Entrar com Google");

    const okIos = await ambiente.app.inject({
      method: "GET",
      url: "/api/v1/auth/google/navegador?redirect_uri=" + encodeURIComponent("chaveirocar://callback"),
    });
    expect(okIos.statusCode).toBe(200);

    const recusado = await ambiente.app.inject({
      method: "GET",
      url: "/api/v1/auth/google/navegador?redirect_uri=" + encodeURIComponent("https://malicioso.exemplo.com/roubo"),
    });
    expect(recusado.statusCode).toBe(400);
  });
});
