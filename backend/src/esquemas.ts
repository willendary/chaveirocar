import { Type, type Static } from "@sinclair/typebox";

export const ID = Type.String({
  pattern: "^[0-9a-fA-F-]{36}$",
  description: "UUID estável gerado pelo cliente ou pelo servidor.",
});

export const PAPEIS = ["proprietario", "administrador", "colaborador", "leitor"] as const;
export type Papel = (typeof PAPEIS)[number];

export const PapelSchema = Type.Union(PAPEIS.map((papel) => Type.Literal(papel)));

export const ErroSchema = Type.Object({
  erro: Type.Object({
    codigo: Type.String(),
    mensagem: Type.String(),
    detalhes: Type.Optional(Type.Unknown()),
  }),
  requisicaoId: Type.String(),
});

export const TokensSchema = Type.Object({
  acesso: Type.String(),
  expiraEm: Type.Integer(),
  refresh: Type.String(),
  refreshExpiraEm: Type.Integer(),
  tipo: Type.Literal("Bearer"),
});

export const UsuarioSchema = Type.Object({
  id: ID,
  nome: Type.String(),
  email: Type.String(),
  emailVerificado: Type.Boolean(),
});

export const EmpresaSchema = Type.Object({
  id: ID,
  nome: Type.String(),
  papel: PapelSchema,
});

export const MembroSchema = Type.Object({
  usuarioId: ID,
  nome: Type.String(),
  email: Type.String(),
  papel: PapelSchema,
  desde: Type.Integer(),
});

export const ConviteSchema = Type.Object({
  id: ID,
  email: Type.String(),
  papel: PapelSchema,
  criadoEm: Type.Integer(),
  expiraEm: Type.Integer(),
  aceitoEm: Type.Union([Type.Integer(), Type.Null()]),
  canceladoEm: Type.Union([Type.Integer(), Type.Null()]),
  expirado: Type.Boolean(),
});

export const TIPOS_SERVICO = [
  "CHAVE_CODIFICADA",
  "TRANSPONDER",
  "TELECOMANDO",
  "ABERTURA",
  "IGNICAO",
  "CONSERTO",
  "PROGRAMACAO_CHAVE",
  "OUTRO",
] as const;
export type TipoServico = (typeof TIPOS_SERVICO)[number];

export const TiposServicoSchema = Type.Array(
  Type.Union(TIPOS_SERVICO.map((valor) => Type.Literal(valor))),
  { minItems: 1, maxItems: 8 },
);

export const DadosAtendimentoSchema = Type.Object({
  placa: Type.String({ minLength: 1, maxLength: 10 }),
  marca: Type.Optional(Type.String({ maxLength: 120 })),
  modelo: Type.Optional(Type.String({ maxLength: 120 })),
  ano: Type.Optional(Type.String({ maxLength: 30 })),
  chassi: Type.Optional(Type.String({ maxLength: 60 })),
  dataServico: Type.Optional(Type.Integer({ minimum: 0 })),
  tiposServico: Type.Optional(TiposServicoSchema),
  defeito: Type.Optional(Type.String({ maxLength: 20_000 })),
  solucao: Type.Optional(Type.String({ maxLength: 20_000 })),
  abertura: Type.Optional(Type.String({ maxLength: 20_000 })),
  codigoChave: Type.Optional(Type.String({ maxLength: 500 })),
  codigoConfiguracao: Type.Optional(Type.String({ maxLength: 500 })),
  chipTransponder: Type.Optional(Type.String({ maxLength: 200 })),
  ferramenta: Type.Optional(Type.String({ maxLength: 500 })),
  valorCentavos: Type.Optional(Type.Integer({ minimum: 0 })),
  clienteNome: Type.Optional(Type.String({ maxLength: 200 })),
  clienteTelefone: Type.Optional(Type.String({ maxLength: 60 })),
  pago: Type.Optional(Type.Boolean()),
});

export const AtendimentoEntradaSchema = Type.Intersect([
  DadosAtendimentoSchema,
  Type.Object({
    id: Type.Optional(ID),
    baseRevisao: Type.Optional(Type.Integer({ minimum: 0 })),
  }),
]);

export const AtendimentoRespostaSchema = Type.Object({
  id: ID,
  empresaId: ID,
  placa: Type.String(),
  marca: Type.String(),
  modelo: Type.String(),
  ano: Type.String(),
  chassi: Type.String(),
  dataServico: Type.Integer(),
  tipoServico: Type.String(),
  tiposServico: Type.Array(Type.String()),
  defeito: Type.String(),
  solucao: Type.String(),
  abertura: Type.String(),
  codigoChave: Type.String(),
  codigoConfiguracao: Type.String(),
  chipTransponder: Type.String(),
  ferramenta: Type.String(),
  valorCentavos: Type.Integer(),
  clienteNome: Type.String(),
  clienteTelefone: Type.String(),
  pago: Type.Boolean(),
  revisao: Type.Integer(),
  criadoEm: Type.Integer(),
  atualizadoEm: Type.Integer(),
  excluidoEm: Type.Union([Type.Integer(), Type.Null()]),
  criadoPor: Type.Union([ID, Type.Null()]),
  atualizadoPor: Type.Union([ID, Type.Null()]),
  quantidadeFotos: Type.Integer(),
});

export const ListaAtendimentosSchema = Type.Object({
  itens: Type.Array(AtendimentoRespostaSchema),
  total: Type.Integer(),
  pagina: Type.Integer(),
  tamanho: Type.Integer(),
});

export const DadosFichaSchema = Type.Object({
  equipamento: Type.Optional(Type.String({ maxLength: 120 })),
  marca: Type.Optional(Type.String({ maxLength: 120 })),
  modelo: Type.Optional(Type.String({ maxLength: 120 })),
  ano: Type.Optional(Type.String({ maxLength: 30 })),
  sistema: Type.Optional(Type.String({ maxLength: 200 })),
  descricao: Type.Optional(Type.String({ maxLength: 20_000 })),
  chip: Type.Optional(Type.String({ maxLength: 200 })),
  chave: Type.Optional(Type.String({ maxLength: 500 })),
  telecomando: Type.Optional(Type.String({ maxLength: 500 })),
  frequencia: Type.Optional(Type.String({ maxLength: 100 })),
  avisos: Type.Optional(Type.String({ maxLength: 20_000 })),
  funcoes: Type.Optional(Type.String({ maxLength: 20_000 })),
  manual: Type.Optional(Type.String({ maxLength: 100_000 })),
  confirmado: Type.Optional(Type.Boolean()),
  origem: Type.Optional(Type.String({ maxLength: 500 })),
});

export const FichaEntradaSchema = Type.Intersect([
  DadosFichaSchema,
  Type.Object({
    id: Type.Optional(ID),
    baseRevisao: Type.Optional(Type.Integer({ minimum: 0 })),
  }),
]);

export const FichaRespostaSchema = Type.Object({
  id: ID,
  empresaId: ID,
  equipamento: Type.String(),
  marca: Type.String(),
  modelo: Type.String(),
  ano: Type.String(),
  sistema: Type.String(),
  descricao: Type.String(),
  chip: Type.String(),
  chave: Type.String(),
  telecomando: Type.String(),
  frequencia: Type.String(),
  avisos: Type.String(),
  funcoes: Type.String(),
  manual: Type.String(),
  confirmado: Type.Boolean(),
  origem: Type.String(),
  fotoId: Type.Union([ID, Type.Null()]),
  revisao: Type.Integer(),
  criadoEm: Type.Integer(),
  atualizadoEm: Type.Integer(),
  excluidoEm: Type.Union([Type.Integer(), Type.Null()]),
  criadoPor: Type.Union([ID, Type.Null()]),
  atualizadoPor: Type.Union([ID, Type.Null()]),
});

export const ListaFichasSchema = Type.Object({
  itens: Type.Array(FichaRespostaSchema),
  total: Type.Integer(),
  pagina: Type.Integer(),
  tamanho: Type.Integer(),
});

export const FotoRespostaSchema = Type.Object({
  id: ID,
  empresaId: ID,
  atendimentoId: Type.Union([ID, Type.Null()]),
  fichaId: Type.Union([ID, Type.Null()]),
  categoria: Type.Union([Type.Literal("CARRO"), Type.Literal("PROCEDIMENTO")]),
  legenda: Type.String(),
  ordem: Type.Integer(),
  mimeType: Type.String(),
  tamanho: Type.Integer(),
  checksum: Type.String(),
  url: Type.String(),
  revisao: Type.Integer(),
  criadoEm: Type.Integer(),
  atualizadoEm: Type.Integer(),
  excluidoEm: Type.Union([Type.Integer(), Type.Null()]),
});

export const ListaFotosSchema = Type.Object({ itens: Type.Array(FotoRespostaSchema) });

export const OperacaoSyncSchema = Type.Object({
  opId: ID,
  entidade: Type.Union([
    Type.Literal("atendimento"),
    Type.Literal("ficha"),
    Type.Literal("foto"),
  ]),
  tipo: Type.Union([Type.Literal("upsert"), Type.Literal("exclusao")]),
  id: ID,
  baseRevisao: Type.Optional(Type.Integer({ minimum: 0 })),
  dados: Type.Optional(Type.Unknown()),
});

export const LoteSyncSchema = Type.Object({
  operacoes: Type.Array(OperacaoSyncSchema, { minItems: 1, maxItems: 100 }),
});

export const ResultadoOperacaoSchema = Type.Object({
  opId: Type.String(),
  status: Type.Union([
    Type.Literal("aplicado"),
    Type.Literal("conflito"),
    Type.Literal("erro"),
  ]),
  revisao: Type.Optional(Type.Integer()),
  registro: Type.Optional(Type.Unknown()),
  erro: Type.Optional(
    Type.Object({ codigo: Type.String(), mensagem: Type.String() }),
  ),
});

export const RespostaSyncSchema = Type.Object({
  resultados: Type.Array(ResultadoOperacaoSchema),
  cursor: Type.Integer(),
});

export const PullSyncSchema = Type.Object({
  cursor: Type.Integer(),
  temMais: Type.Boolean(),
  atendimentos: Type.Array(Type.Unknown()),
  fichas: Type.Array(Type.Unknown()),
  fotos: Type.Array(Type.Unknown()),
});

export const ResultadoImportacaoSchema = Type.Object({
  atendimentosImportados: Type.Integer(),
  atendimentosIgnorados: Type.Integer(),
  fichasImportadas: Type.Integer(),
  fichasIgnoradas: Type.Integer(),
  fotosImportadas: Type.Integer(),
  fotosIgnoradas: Type.Integer(),
  fotosAusentes: Type.Integer(),
  avisos: Type.Array(Type.String()),
});

export const AuditoriaSchema = Type.Object({
  id: Type.Integer(),
  acao: Type.String(),
  entidade: Type.String(),
  entidadeId: Type.String(),
  usuarioId: Type.Union([ID, Type.Null()]),
  detalhes: Type.Unknown(),
  criadoEm: Type.Integer(),
});

export type DadosAtendimento = Static<typeof DadosAtendimentoSchema>;
export type DadosFicha = Static<typeof DadosFichaSchema>;
export type OperacaoSync = Static<typeof OperacaoSyncSchema>;
