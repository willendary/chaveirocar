import type { Banco } from "../banco.js";
import { agora } from "../banco.js";
import { erros } from "../erros.js";
import { gerarHashSenha, senhaAceitavel } from "./senha.js";
import { emailValido, normalizarEmail, novoId } from "../util/id.js";
import { texto } from "../util/texto.js";

export interface Usuario {
  id: string;
  nome: string;
  email: string;
  emailVerificadoEm: number | null;
  criadoEm: number;
}

interface LinhaUsuario {
  id: string;
  nome: string;
  email: string;
  email_verificado_em: number | null;
  criado_em: number;
}

function paraUsuario(linha: LinhaUsuario): Usuario {
  return {
    id: linha.id,
    nome: linha.nome,
    email: linha.email,
    emailVerificadoEm:
      linha.email_verificado_em === null ? null : Number(linha.email_verificado_em),
    criadoEm: Number(linha.criado_em),
  };
}

export function validarEmail(email: string): string {
  const normalizado = normalizarEmail(email);
  if (!emailValido(normalizado)) throw erros.validacao("Informe um e-mail válido.");
  return normalizado;
}

export async function obterUsuarioPorEmail(banco: Banco, email: string): Promise<Usuario | null> {
  const resultado = await banco.execute({
    sql: `SELECT id, nome, email, email_verificado_em, criado_em FROM usuarios
          WHERE email = ? AND excluido_em IS NULL`,
    args: [normalizarEmail(email)],
  });
  const linha = resultado.rows[0] as unknown as LinhaUsuario | undefined;
  return linha ? paraUsuario(linha) : null;
}

export async function obterUsuario(banco: Banco, id: string): Promise<Usuario> {
  const resultado = await banco.execute({
    sql: "SELECT id, nome, email, email_verificado_em, criado_em FROM usuarios WHERE id = ? AND excluido_em IS NULL",
    args: [id],
  });
  const linha = resultado.rows[0] as unknown as LinhaUsuario | undefined;
  if (!linha) throw erros.naoEncontrado("Usuário não encontrado.");
  return paraUsuario(linha);
}

export async function criarUsuario(
  banco: Banco,
  entrada: { nome: string; email: string; senha: string },
): Promise<Usuario> {
  const nome = texto(entrada.nome, 120);
  if (!nome) throw erros.validacao("Informe seu nome.");
  const email = validarEmail(entrada.email);
  const problema = senhaAceitavel(entrada.senha);
  if (problema) throw erros.validacao(problema);
  if (await obterUsuarioPorEmail(banco, email)) {
    throw erros.conflito("Já existe uma conta com este e-mail.", { campo: "email" });
  }
  const id = novoId();
  const instante = agora();
  const hash = await gerarHashSenha(entrada.senha);
  await banco.execute({
    sql: `INSERT INTO usuarios (id, nome, email, senha_hash, criado_em, atualizado_em)
          VALUES (?, ?, ?, ?, ?, ?)`,
    args: [id, nome, email, hash, instante, instante],
  });
  return {
    id,
    nome,
    email,
    emailVerificadoEm: null,
    criadoEm: instante,
  };
}

export async function criarEmpresa(
  banco: Banco,
  usuarioId: string,
  nome: string,
): Promise<{ id: string; nome: string; papel: "proprietario" }> {
  const nomeEmpresa = texto(nome, 120) || "Minha empresa";
  const id = novoId();
  const instante = agora();
  const transacao = await banco.transaction("write");
  try {
    await transacao.execute({
      sql: "INSERT INTO empresas (id, nome, criado_em, atualizado_em) VALUES (?, ?, ?, ?)",
      args: [id, nomeEmpresa, instante, instante],
    });
    await transacao.execute({
      sql: `INSERT INTO membros (empresa_id, usuario_id, papel, criado_em, atualizado_em)
            VALUES (?, ?, 'proprietario', ?, ?)`,
      args: [id, usuarioId, instante, instante],
    });
    await transacao.commit();
  } catch (erro) {
    await transacao.rollback().catch(() => undefined);
    throw erro;
  }
  return { id, nome: nomeEmpresa, papel: "proprietario" };
}

export async function listarEmpresasDoUsuario(
  banco: Banco,
  usuarioId: string,
): Promise<Array<{ id: string; nome: string; papel: string }>> {
  const resultado = await banco.execute({
    sql: `SELECT e.id, e.nome, m.papel FROM membros m
          JOIN empresas e ON e.id = m.empresa_id
          WHERE m.usuario_id = ? AND m.excluido_em IS NULL AND e.excluido_em IS NULL
          ORDER BY e.nome`,
    args: [usuarioId],
  });
  return resultado.rows.map((linha) => ({
    id: String(linha.id),
    nome: String(linha.nome),
    papel: String(linha.papel),
  }));
}
