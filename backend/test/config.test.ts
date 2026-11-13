import { describe, expect, it } from "vitest";
import { criarConfig } from "../src/config.js";

const producao = {
  AMBIENTE: "producao",
  TURSO_URL: "libsql://chaveirocar.aws-sa-east-1.turso.io",
  TURSO_TOKEN: "token-de-teste",
};

describe("configuração de produção", () => {
  it("exige SMTP para não expor links de conta nos logs", () => {
    expect(() => criarConfig(producao)).toThrow("SMTP_URL é obrigatório em produção");
  });

  it("aceita SMTP configurado", () => {
    expect(criarConfig({ ...producao, SMTP_URL: "smtp://localhost" }).ambiente).toBe("producao");
  });
});
