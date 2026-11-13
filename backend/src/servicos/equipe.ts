import type { Banco } from "../banco.js";
import { agora } from "../banco.js";
import type { Config } from "../config.js";
import type { ServicoEmail } from "../email.js";
import { linkConvite } from "../email.js";
import { erros } from "../erros.js";
import type { Papel } from "../esquemas.js";
import { registrarAuditoria } from "../permissoes.js";
import { criarUsuario, obterUsuario, obterUsuarioPorEmail, validarEmail } from "../auth/contas.js";
import { criarSessao, type Tokens } from "../auth/sessoes.js";
import { normalizarEmail, novoId, novoSegredo, sha256 } from "../util/id.js";

const DIA = 24 * 60 * 60 * 1000;
const VALIDADE_CONVITE = 7 * DIA;

export interface Convite {
  id: string;
  email: string;
  papel: Papel;
  criadoEm: number;
  expiraEm: number;
  aceitoEm: number | null;
  canceladoEm: number | null;
  expirado: boolean;
}

interface LinhaConvite {
  id: string;
  email: string;
  papel: string;
  criado_em: number;
  expira_em: number;
  aceito_em: number | null;
  cancelado_em: number | null;
}

function paraConvite(linha: LinhaConvite): Convite {
  return {
    id: linha.id,
    email: linha.email,
    papel: linha.papel as Papel,
    criadoEm: Number(linha.criado_em),
    expiraEm: Number(linha.expira_em),
    aceitoEm: linha.aceito_em === null ? null : Number(linha.aceito_em),
    canceladoEm: linha.cancelado_em === null ? null : Number(linha.cancelado_em),
    expirado: Number(linha.expira_em) < agora() && linha.aceito_em === null,
  };
}

export interface SolicitarConvite {
  email: string;
  papel: Exclude<Papel, "proprietario">;
}

export async function criarConvite(
  banco: Banco,
  config: Config,
  email: ServicoEmail,
  contexto: { empresaId: string; usuarioId: string },
  entrada: SolicitarConvite,
  registrar: (mensagem: string) => void,
): Promise<{ convite: Convite; token?: string; emailEnviado: boolean }> {
  const emailConvidado = validarEmail(entrada.email);
  if (!["administrador", "colaborador", "leitor"].includes(entrada.papel)) {
    throw erros.validacao("Papel inválido para convite.");
  }

  const jaMembro = await banco.execute({
    sql: `SELECT m.usuario_id FROM membros m
          JOIN usuarios u ON u.id = m.usuario_id
          WHERE m.empresa_id = ? AND u.email = ? AND m.excluido_em IS NULL`,
    args: [contexto.empresaId, emailConvidado],
  });
  if (jaMembro.rows[0]) {
    throw erros.conflito("Esta pessoa já faz parte da equipe.", { campo: "email" });
  }

  const abertos = await banco.execute({
    sql: `SELECT COUNT(*) AS total FROM convites
          WHERE empresa_id = ? AND aceito_em IS NULL AND cancelado_em IS NULL AND expira_em > ?`,
    args: [contexto.empresaId, agora()],
  });
  if (Number(abertos.rows[0]?.total ?? 0) >= config.limites.convitesAbertos) {
    throw erros.limite("Há muitos convites abertos. Cancele algum antes de criar outro.");
  }

  const pendente = await banco.execute({
    sql: `SELECT id FROM convites
          WHERE empresa_id = ? AND email = ? AND aceito_em IS NULL AND cancelado_em IS NULL`,
    args: [contexto.empresaId, emailConvidado],
  });
  const token = novoSegredo(32);
  const instante = agora();
  const expiraEm = instante + VALIDADE_CONVITE;
  let id: string;
  if (pendente.rows[0]) {
    id = String(pendente.rows[0].id);
    await banco.execute({
      sql: `UPDATE convites SET token_hash = ?, papel = ?, expira_em = ?, ultimo_envio_em = ?
            WHERE id = ?`,
      args: [sha256(token), entrada.papel, expiraEm, instante, id],
    });
  } else {
    id = novoId();
    await banco.execute({
      sql: `INSERT INTO convites
          (id, empresa_id, email, papel, token_hash, criado_por, criado_em, expira_em, ultimo_envio_em)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      args: [
        id,
        contexto.empresaId,
        emailConvidado,
        entrada.papel,
        sha256(token),
        contexto.usuarioId,
        instante,
        expiraEm,
        instante,
      ],
    });
  }

  const convite: Convite = {
    id,
    email: emailConvidado,
    papel: entrada.papel,
    criadoEm: instante,
    expiraEm,
    aceitoEm: null,
    canceladoEm: null,
    expirado: false,
  };

  let emailEnviado = false;
  try {
    await email.enviar({
      para: emailConvidado,
      assunto: "Convite para o ChaveiroCar",
      texto: [
        "Você foi convidado para participar do ChaveiroCar.",
        "",
        `Abra este link para aceitar: ${linkConvite(config, token)}`,
        "",
        "Se você não esperava este convite, ignore esta mensagem.",
      ].join("\n"),
    });
    emailEnviado = true;
  } catch (erro) {
    registrar(`Falha ao enviar convite para ${emailConvidado}: ${(erro as Error).message}`);
  }

  await registrarAuditoria(banco, {
    empresaId: contexto.empresaId,
    usuarioId: contexto.usuarioId,
    acao: "convite.criado",
    entidade: "convite",
    entidadeId: id,
    detalhes: { email: emailConvidado, papel: entrada.papel },
  });

  return {
    convite,
    token: config.exporTokenDev ? token : undefined,
    emailEnviado,
  };
}

export async function listarConvites(banco: Banco, empresaId: string): Promise<Convite[]> {
  const resultado = await banco.execute({
    sql: `SELECT id, email, papel, criado_em, expira_em, aceito_em, cancelado_em
          FROM convites WHERE empresa_id = ?
          ORDER BY criado_em DESC`,
    args: [empresaId],
  });
  return resultado.rows.map((linha) => paraConvite(linha as unknown as LinhaConvite));
}

export async function cancelarConvite(
  banco: Banco,
  contexto: { empresaId: string; usuarioId: string },
  id: string,
): Promise<Convite> {
  const resultado = await banco.execute({
    sql: `SELECT id, email, papel, criado_em, expira_em, aceito_em, cancelado_em
          FROM convites WHERE id = ? AND empresa_id = ?`,
    args: [id, contexto.empresaId],
  });
  const linha = resultado.rows[0] as unknown as LinhaConvite | undefined;
  if (!linha) throw erros.naoEncontrado("Convite não encontrado.");
  if (linha.aceito_em !== null) throw erros.conflito("Este convite já foi aceito.");
  if (linha.cancelado_em !== null) return paraConvite(linha);
  await banco.execute({
    sql: "UPDATE convites SET cancelado_em = ? WHERE id = ? AND empresa_id = ?",
    args: [agora(), id, contexto.empresaId],
  });
  await registrarAuditoria(banco, {
    empresaId: contexto.empresaId,
    usuarioId: contexto.usuarioId,
    acao: "convite.cancelado",
    entidade: "convite",
    entidadeId: id,
    detalhes: { email: linha.email },
  });
  return paraConvite({ ...linha, cancelado_em: agora() });
}

async function localizarConvitePorToken(
  banco: Banco,
  token: string,
): Promise<LinhaConvite & { empresa_id: string; empresa_nome: string }> {
  const resultado = await banco.execute({
    sql: `SELECT c.id, c.empresa_id, c.email, c.papel, c.criado_em, c.expira_em,
                 c.aceito_em, c.cancelado_em, e.nome AS empresa_nome
          FROM convites c JOIN empresas e ON e.id = c.empresa_id
          WHERE c.token_hash = ?`,
    args: [sha256(token)],
  });
  const linha = resultado.rows[0];
  if (!linha) throw erros.tokenInvalido("Convite não encontrado ou já utilizado.");
  const convite = linha as unknown as LinhaConvite & { empresa_id: string; empresa_nome: string };
  if (convite.cancelado_em !== null) throw erros.tokenInvalido("Este convite foi cancelado.");
  if (convite.aceito_em !== null) throw erros.tokenInvalido("Este convite já foi aceito.");
  if (Number(convite.expira_em) < agora()) throw erros.tokenInvalido("Este convite expirou.");
  return convite;
}

export async function visualizarConvite(
  banco: Banco,
  token: string,
): Promise<{ empresaNome: string; email: string; papel: Papel; expiraEm: number }> {
  const convite = await localizarConvitePorToken(banco, token);
  return {
    empresaNome: convite.empresa_nome,
    email: convite.email,
    papel: convite.papel as Papel,
    expiraEm: Number(convite.expira_em),
  };
}

export async function aceitarConvite(
  banco: Banco,
  token: string,
  entrada: { usuarioId?: string; nome?: string; senha?: string },
  ip: string,
  userAgent: string,
): Promise<{ usuarioId: string; empresaId: string; tokens: Tokens }> {
  const convite = await localizarConvitePorToken(banco, token);
  let usuarioId = entrada.usuarioId;

  if (usuarioId) {
    const usuario = await obterUsuario(banco, usuarioId);
    if (normalizarEmail(usuario.email) !== convite.email) {
      throw erros.semPermissao("Este convite foi enviado para outro e-mail.");
    }
  } else {
    const existente = await obterUsuarioPorEmail(banco, convite.email);
    if (existente) {
      throw erros.conflito(
        "Já existe uma conta com este e-mail. Entre na sua conta para aceitar o convite.",
        { codigo: "CONTA_EXISTENTE" },
      );
    }
    if (!entrada.nome || !entrada.senha) {
      throw erros.validacao("Informe seu nome e uma senha para criar a conta.");
    }
    const usuario = await criarUsuario(banco, {
      nome: entrada.nome,
      email: convite.email,
      senha: entrada.senha,
    });
    usuarioId = usuario.id;
  }

  const instante = agora();
  const transacao = await banco.transaction("write");
  try {
    await transacao.execute({
      sql: `INSERT INTO membros (empresa_id, usuario_id, papel, criado_em, atualizado_em)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (empresa_id, usuario_id) DO UPDATE SET
              papel = excluded.papel, excluido_em = NULL, atualizado_em = excluded.atualizado_em`,
      args: [convite.empresa_id, usuarioId, convite.papel, instante, instante],
    });
    await transacao.execute({
      sql: "UPDATE convites SET aceito_em = ?, aceito_por = ? WHERE id = ?",
      args: [instante, usuarioId, convite.id],
    });
    await transacao.execute({
      sql: `UPDATE usuarios SET email_verificado_em = COALESCE(email_verificado_em, ?), atualizado_em = ?
            WHERE id = ?`,
      args: [instante, instante, usuarioId],
    });
    await transacao.commit();
  } catch (erro) {
    await transacao.rollback().catch(() => undefined);
    throw erro;
  }

  await registrarAuditoria(banco, {
    empresaId: convite.empresa_id,
    usuarioId,
    acao: "convite.aceito",
    entidade: "convite",
    entidadeId: convite.id,
    detalhes: { papel: convite.papel },
    ip,
  });

  const tokens = await criarSessao(banco, usuarioId, ip, userAgent);
  return { usuarioId, empresaId: convite.empresa_id, tokens };
}

export interface MembroEquipe {
  usuarioId: string;
  nome: string;
  email: string;
  papel: Papel;
  desde: number;
}

export async function listarMembros(banco: Banco, empresaId: string): Promise<MembroEquipe[]> {
  const resultado = await banco.execute({
    sql: `SELECT m.usuario_id, u.nome, u.email, m.papel, m.criado_em
          FROM membros m JOIN usuarios u ON u.id = m.usuario_id
          WHERE m.empresa_id = ? AND m.excluido_em IS NULL AND u.excluido_em IS NULL
          ORDER BY CASE m.papel
            WHEN 'proprietario' THEN 0 WHEN 'administrador' THEN 1
            WHEN 'colaborador' THEN 2 ELSE 3 END, u.nome`,
    args: [empresaId],
  });
  return resultado.rows.map((linha) => ({
    usuarioId: String(linha.usuario_id),
    nome: String(linha.nome),
    email: String(linha.email),
    papel: String(linha.papel) as Papel,
    desde: Number(linha.criado_em),
  }));
}

export async function alterarPapel(
  banco: Banco,
  contexto: { empresaId: string; usuarioId: string },
  alvoId: string,
  novoPapel: Papel,
): Promise<MembroEquipe> {
  const membros = await listarMembros(banco, contexto.empresaId);
  const alvo = membros.find((membro) => membro.usuarioId === alvoId);
  if (!alvo) throw erros.naoEncontrado("Membro não encontrado.");
  const ator = membros.find((membro) => membro.usuarioId === contexto.usuarioId);
  if (!ator) throw erros.semPermissao();
  if (alvo.papel === "proprietario" && novoPapel !== "proprietario") {
    const total = membros.filter((membro) => membro.papel === "proprietario").length;
    if (total <= 1) throw erros.semPermissao("A empresa precisa de pelo menos um proprietário.");
  }
  if (ator.papel === "administrador") {
    if (alvo.papel === "proprietario" || alvo.papel === "administrador") {
      throw erros.semPermissao("Administrador não pode alterar proprietários ou outros administradores.");
    }
    if (novoPapel === "proprietario" || novoPapel === "administrador") {
      throw erros.semPermissao("Administrador só pode definir colaborador ou leitor.");
    }
  }
  if (ator.papel !== "proprietario" && ator.papel !== "administrador") {
    throw erros.semPermissao();
  }
  await banco.execute({
    sql: "UPDATE membros SET papel = ?, atualizado_em = ? WHERE empresa_id = ? AND usuario_id = ?",
    args: [novoPapel, agora(), contexto.empresaId, alvoId],
  });
  await registrarAuditoria(banco, {
    empresaId: contexto.empresaId,
    usuarioId: contexto.usuarioId,
    acao: "membro.papel_alterado",
    entidade: "membro",
    entidadeId: alvoId,
    detalhes: { de: alvo.papel, para: novoPapel },
  });
  return { ...alvo, papel: novoPapel };
}

export async function removerMembro(
  banco: Banco,
  contexto: { empresaId: string; usuarioId: string },
  alvoId: string,
): Promise<void> {
  const membros = await listarMembros(banco, contexto.empresaId);
  const alvo = membros.find((membro) => membro.usuarioId === alvoId);
  if (!alvo) throw erros.naoEncontrado("Membro não encontrado.");
  const ator = membros.find((membro) => membro.usuarioId === contexto.usuarioId);
  if (!ator) throw erros.semPermissao();
  if (ator.papel !== "proprietario" && ator.papel !== "administrador") {
    throw erros.semPermissao();
  }
  if (ator.papel === "administrador" && (alvo.papel === "proprietario" || alvo.papel === "administrador")) {
    throw erros.semPermissao("Administrador não pode remover proprietários ou outros administradores.");
  }
  if (alvo.papel === "proprietario") {
    const total = membros.filter((membro) => membro.papel === "proprietario").length;
    if (total <= 1) throw erros.semPermissao("A empresa precisa de pelo menos um proprietário.");
  }
  await banco.execute({
    sql: "UPDATE membros SET excluido_em = ?, atualizado_em = ? WHERE empresa_id = ? AND usuario_id = ?",
    args: [agora(), agora(), contexto.empresaId, alvoId],
  });
  await registrarAuditoria(banco, {
    empresaId: contexto.empresaId,
    usuarioId: contexto.usuarioId,
    acao: "membro.removido",
    entidade: "membro",
    entidadeId: alvoId,
    detalhes: { email: alvo.email, papel: alvo.papel },
  });
}
