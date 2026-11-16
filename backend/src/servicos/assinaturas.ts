import type { Banco } from "../banco.js";
import { agora } from "../banco.js";
import { erros } from "../erros.js";
import type { SegmentoAtendimento, StatusAssinatura } from "../esquemas.js";
import { registrarAuditoria } from "../permissoes.js";
import { novoId } from "../util/id.js";

const DIA_MS = 24 * 60 * 60 * 1000;
const STATUS_VALIDOS = new Set<StatusAssinatura>([
  "ATIVA",
  "PENDENTE_PAGAMENTO",
  "FALHA_PAGAMENTO",
  "CANCELADA",
]);

export type AcaoSolicitacaoAssinatura = "CONTRATAR" | "ALTERAR" | "CANCELAR";
export type TipoEventoAssinatura =
  | "PAGAMENTO_CONFIRMADO"
  | "PAGAMENTO_PENDENTE"
  | "FALHA_PAGAMENTO"
  | "RENOVACAO_CONFIRMADA"
  | "ASSINATURA_CANCELADA"
  | "REEMBOLSO_PROCESSADO";

export interface MudancaAssinaturaAgendada {
  solicitacaoId: string;
  acao: AcaoSolicitacaoAssinatura;
  modulos: SegmentoAtendimento[];
  solicitadoEm: number;
}

export interface EstadoAssinatura {
  empresaId: string;
  planoCodigo: string;
  modulosContratados: SegmentoAtendimento[];
  modulosAtivos: SegmentoAtendimento[];
  status: StatusAssinatura;
  proximaRenovacaoEm: number | null;
  mudancaAgendada: MudancaAssinaturaAgendada | null;
  podeGerenciar: boolean;
  ambienteHomologacao: boolean;
  duplicado?: boolean;
}

export function normalizarModulos(valor: unknown): SegmentoAtendimento[] {
  if (!Array.isArray(valor)) return [];
  const ordem: SegmentoAtendimento[] = ["AUTOMOTIVO", "RESIDENCIAL"];
  const presentes = new Set<SegmentoAtendimento>();
  for (const item of valor) {
    if (item === "AUTOMOTIVO" || item === "RESIDENCIAL") {
      presentes.add(item);
    }
  }
  return ordem.filter((m) => presentes.has(m));
}

export function parseModulosArmazenados(bruto: unknown): SegmentoAtendimento[] {
  if (typeof bruto !== "string" || !bruto.trim()) return [];
  try {
    return normalizarModulos(JSON.parse(bruto));
  } catch {
    return [];
  }
}

export function normalizarStatusAssinatura(valor: unknown): StatusAssinatura {
  const texto = String(valor ?? "ATIVA");
  return STATUS_VALIDOS.has(texto as StatusAssinatura)
    ? (texto as StatusAssinatura)
    : "ATIVA";
}

export function calcularPlanoCodigo(modulos: SegmentoAtendimento[]): string {
  const temAuto = modulos.includes("AUTOMOTIVO");
  const temRes = modulos.includes("RESIDENCIAL");
  if (temAuto && temRes) return "COMPLETO";
  if (temRes) return "SOMENTE_RESIDENCIAL";
  if (temAuto) return "SOMENTE_VEICULAR";
  return "SEM_PLANO";
}

export function calcularModulosEfetivos(
  status: StatusAssinatura,
  modulosContratados: SegmentoAtendimento[],
): SegmentoAtendimento[] {
  if (status === "CANCELADA" || status === "FALHA_PAGAMENTO") {
    return [];
  }
  return modulosContratados;
}

function parseMudancaAgendada(bruto: unknown): MudancaAssinaturaAgendada | null {
  if (typeof bruto !== "string" || !bruto.trim()) return null;
  try {
    const obj = JSON.parse(bruto) as Partial<MudancaAssinaturaAgendada>;
    if (!obj || typeof obj.solicitacaoId !== "string" || typeof obj.acao !== "string") {
      return null;
    }
    const acao =
      obj.acao === "CONTRATAR" || obj.acao === "ALTERAR" || obj.acao === "CANCELAR"
        ? obj.acao
        : "ALTERAR";
    return {
      solicitacaoId: obj.solicitacaoId,
      acao,
      modulos: normalizarModulos(obj.modulos),
      solicitadoEm: Number(obj.solicitadoEm ?? 0),
    };
  } catch {
    return null;
  }
}

export async function obterAssinaturaEmpresa(
  banco: Banco,
  empresaId: string,
  opcoes: { podeGerenciar?: boolean; ambienteHomologacao?: boolean } = {},
): Promise<EstadoAssinatura> {
  const resultado = await banco.execute({
    sql: `SELECT id, plano_codigo, modulos_ativos, assinatura_status, proxima_renovacao_em, mudanca_agendada
          FROM empresas WHERE id = ? AND excluido_em IS NULL`,
    args: [empresaId],
  });
  const linha = resultado.rows[0];
  if (!linha) throw erros.naoEncontrado("Empresa não encontrada.");
  const status = normalizarStatusAssinatura(linha.assinatura_status);
  const modulosContratados = parseModulosArmazenados(linha.modulos_ativos);
  const modulosAtivos = calcularModulosEfetivos(status, modulosContratados);
  const planoCodigo =
    typeof linha.plano_codigo === "string" && linha.plano_codigo.trim()
      ? linha.plano_codigo
      : calcularPlanoCodigo(modulosContratados);

  return {
    empresaId,
    planoCodigo,
    modulosContratados,
    modulosAtivos,
    status,
    proximaRenovacaoEm:
      linha.proxima_renovacao_em === null || linha.proxima_renovacao_em === undefined
        ? null
        : Number(linha.proxima_renovacao_em),
    mudancaAgendada: parseMudancaAgendada(linha.mudanca_agendada),
    podeGerenciar: Boolean(opcoes.podeGerenciar),
    ambienteHomologacao: opcoes.ambienteHomologacao ?? true,
  };
}

export async function exigirModuloAtivo(
  banco: Banco,
  empresaId: string,
  modulo: SegmentoAtendimento,
): Promise<EstadoAssinatura> {
  const assinatura = await obterAssinaturaEmpresa(banco, empresaId);
  if (!assinatura.modulosAtivos.includes(modulo)) {
    throw erros.moduloNaoContratado(modulo);
  }
  return assinatura;
}

export async function solicitarMudancaAssinatura(
  banco: Banco,
  contexto: { empresaId: string; usuarioId: string; ip?: string; podeGerenciar: boolean; ambienteHomologacao?: boolean },
  entrada: { acao: AcaoSolicitacaoAssinatura; modulos?: SegmentoAtendimento[] },
): Promise<EstadoAssinatura> {
  const modulos = normalizarModulos(entrada.modulos ?? []);
  if (entrada.acao !== "CANCELAR" && modulos.length === 0) {
    throw erros.validacao("Selecione ao menos um módulo (Residencial ou Veicular).");
  }

  const solicitacao: MudancaAssinaturaAgendada = {
    solicitacaoId: novoId(),
    acao: entrada.acao,
    modulos: entrada.acao === "CANCELAR" ? [] : modulos,
    solicitadoEm: agora(),
  };

  await banco.execute({
    sql: "UPDATE empresas SET mudanca_agendada = ?, atualizado_em = ? WHERE id = ?",
    args: [JSON.stringify(solicitacao), solicitacao.solicitadoEm, contexto.empresaId],
  });

  await registrarAuditoria(banco, {
    empresaId: contexto.empresaId,
    usuarioId: contexto.usuarioId,
    acao: "assinatura.solicitada",
    entidade: "empresa",
    entidadeId: contexto.empresaId,
    detalhes: { ...solicitacao },
    ip: contexto.ip,
  });

  return obterAssinaturaEmpresa(banco, contexto.empresaId, {
    podeGerenciar: contexto.podeGerenciar,
    ambienteHomologacao: contexto.ambienteHomologacao,
  });
}

export async function processarEventoAssinatura(
  banco: Banco,
  contexto: { empresaId: string; usuarioId?: string; ip?: string; podeGerenciar: boolean; ambienteHomologacao?: boolean },
  entrada: {
    eventoId: string;
    tipo: TipoEventoAssinatura;
    modulos?: SegmentoAtendimento[];
    proximaRenovacaoEm?: number | null;
  },
): Promise<EstadoAssinatura> {
  const eventoId = entrada.eventoId.trim();
  if (!eventoId) {
    throw erros.validacao("Informe o identificador do evento de assinatura.");
  }

  const jaProcessado = await banco.execute({
    sql: "SELECT id FROM eventos_assinatura WHERE empresa_id = ? AND evento_id = ?",
    args: [contexto.empresaId, eventoId],
  });
  if (jaProcessado.rows[0]) {
    const atual = await obterAssinaturaEmpresa(banco, contexto.empresaId, {
      podeGerenciar: contexto.podeGerenciar,
      ambienteHomologacao: contexto.ambienteHomologacao,
    });
    return { ...atual, duplicado: true };
  }

  const atual = await obterAssinaturaEmpresa(banco, contexto.empresaId, {
    podeGerenciar: contexto.podeGerenciar,
    ambienteHomologacao: contexto.ambienteHomologacao,
  });
  const instante = agora();

  let novoStatus: StatusAssinatura = atual.status;
  let novosModulos: SegmentoAtendimento[] = atual.modulosContratados;
  let novaRenovacao: number | null = atual.proximaRenovacaoEm;
  let limparMudanca = false;

  switch (entrada.tipo) {
    case "PAGAMENTO_CONFIRMADO":
    case "RENOVACAO_CONFIRMADA": {
      const modulosEvento = normalizarModulos(entrada.modulos);
      if (modulosEvento.length > 0) {
        novosModulos = modulosEvento;
      } else if (atual.mudancaAgendada && atual.mudancaAgendada.modulos.length > 0) {
        novosModulos = atual.mudancaAgendada.modulos;
      }
      if (novosModulos.length === 0) {
        throw erros.validacao("Informe os módulos contratados para confirmar o pagamento.");
      }
      novoStatus = "ATIVA";
      novaRenovacao =
        entrada.proximaRenovacaoEm !== undefined
          ? entrada.proximaRenovacaoEm
          : instante + 30 * DIA_MS;
      limparMudanca = true;
      break;
    }

    case "PAGAMENTO_PENDENTE":
      novoStatus = "PENDENTE_PAGAMENTO";
      break;

    case "FALHA_PAGAMENTO":
      novoStatus = "FALHA_PAGAMENTO";
      break;

    case "ASSINATURA_CANCELADA":
    case "REEMBOLSO_PROCESSADO":
      novoStatus = "CANCELADA";
      novosModulos = [];
      novaRenovacao = null;
      limparMudanca = true;
      break;
  }

  const novoPlanoCodigo = calcularPlanoCodigo(novosModulos);
  const transacao = await banco.transaction("write");
  try {
    await transacao.execute({
      sql: `INSERT INTO eventos_assinatura
              (id, empresa_id, evento_id, tipo, modulos, status_resultante, detalhes, criado_em)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
      args: [
        novoId(),
        contexto.empresaId,
        eventoId,
        entrada.tipo,
        JSON.stringify(novosModulos),
        novoStatus,
        JSON.stringify({ planoCodigo: novoPlanoCodigo, proximaRenovacaoEm: novaRenovacao }),
        instante,
      ],
    });
    await transacao.execute({
      sql: `UPDATE empresas
            SET plano_codigo = ?,
                modulos_ativos = ?,
                assinatura_status = ?,
                proxima_renovacao_em = ?,
                mudanca_agendada = ?,
                atualizado_em = ?
            WHERE id = ?`,
      args: [
        novoPlanoCodigo,
        JSON.stringify(novosModulos),
        novoStatus,
        novaRenovacao,
        limparMudanca ? "" : (atual.mudancaAgendada ? JSON.stringify(atual.mudancaAgendada) : ""),
        instante,
        contexto.empresaId,
      ],
    });
    await transacao.commit();
  } catch (erro) {
    await transacao.rollback().catch(() => undefined);
    // Se houve corrida no UNIQUE(empresa_id, evento_id), retorna estado idempotente.
    const corrida = await banco.execute({
      sql: "SELECT id FROM eventos_assinatura WHERE empresa_id = ? AND evento_id = ?",
      args: [contexto.empresaId, eventoId],
    });
    if (corrida.rows[0]) {
      const estado = await obterAssinaturaEmpresa(banco, contexto.empresaId, {
        podeGerenciar: contexto.podeGerenciar,
        ambienteHomologacao: contexto.ambienteHomologacao,
      });
      return { ...estado, duplicado: true };
    }
    throw erro;
  }

  await registrarAuditoria(banco, {
    empresaId: contexto.empresaId,
    usuarioId: contexto.usuarioId ?? null,
    acao: "assinatura.evento_processado",
    entidade: "empresa",
    entidadeId: contexto.empresaId,
    detalhes: {
      eventoId,
      tipo: entrada.tipo,
      status: novoStatus,
      modulos: novosModulos,
      planoCodigo: novoPlanoCodigo,
    },
    ip: contexto.ip,
  });

  const atualizado = await obterAssinaturaEmpresa(banco, contexto.empresaId, {
    podeGerenciar: contexto.podeGerenciar,
    ambienteHomologacao: contexto.ambienteHomologacao,
  });
  return { ...atualizado, duplicado: false };
}
