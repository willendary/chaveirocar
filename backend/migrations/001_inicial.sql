-- Esquema inicial do ChaveiroCar central (multiempresa).
-- Timestamps em milissegundos desde 1970 (INTEGER).

CREATE TABLE empresas (
  id TEXT PRIMARY KEY,
  nome TEXT NOT NULL,
  criado_em INTEGER NOT NULL,
  atualizado_em INTEGER NOT NULL,
  excluido_em INTEGER
);

CREATE TABLE usuarios (
  id TEXT PRIMARY KEY,
  nome TEXT NOT NULL,
  email TEXT NOT NULL UNIQUE,
  senha_hash TEXT NOT NULL,
  email_verificado_em INTEGER,
  criado_em INTEGER NOT NULL,
  atualizado_em INTEGER NOT NULL,
  excluido_em INTEGER
);

CREATE TABLE membros (
  empresa_id TEXT NOT NULL REFERENCES empresas(id),
  usuario_id TEXT NOT NULL REFERENCES usuarios(id),
  papel TEXT NOT NULL CHECK (papel IN ('proprietario', 'administrador', 'colaborador', 'leitor')),
  criado_em INTEGER NOT NULL,
  atualizado_em INTEGER NOT NULL,
  excluido_em INTEGER,
  PRIMARY KEY (empresa_id, usuario_id)
);

CREATE INDEX idx_membros_usuario ON membros(usuario_id);

CREATE TABLE convites (
  id TEXT PRIMARY KEY,
  empresa_id TEXT NOT NULL REFERENCES empresas(id),
  email TEXT NOT NULL,
  papel TEXT NOT NULL CHECK (papel IN ('administrador', 'colaborador', 'leitor')),
  token_hash TEXT NOT NULL UNIQUE,
  criado_por TEXT NOT NULL REFERENCES usuarios(id),
  criado_em INTEGER NOT NULL,
  expira_em INTEGER NOT NULL,
  aceito_em INTEGER,
  aceito_por TEXT REFERENCES usuarios(id),
  cancelado_em INTEGER,
  ultimo_envio_em INTEGER
);

CREATE INDEX idx_convites_empresa ON convites(empresa_id, criado_em DESC);
CREATE INDEX idx_convites_email ON convites(email);
CREATE UNIQUE INDEX idx_convites_pendente
  ON convites(empresa_id, email)
  WHERE aceito_em IS NULL AND cancelado_em IS NULL;

CREATE TABLE sessoes (
  id TEXT PRIMARY KEY,
  usuario_id TEXT NOT NULL REFERENCES usuarios(id),
  acesso_hash TEXT NOT NULL UNIQUE,
  acesso_expira_em INTEGER NOT NULL,
  refresh_hash TEXT NOT NULL UNIQUE,
  refresh_hash_anterior TEXT,
  criado_em INTEGER NOT NULL,
  expira_em INTEGER NOT NULL,
  ultimo_uso_em INTEGER,
  revogado_em INTEGER,
  ip TEXT NOT NULL DEFAULT '',
  user_agent TEXT NOT NULL DEFAULT ''
);

CREATE INDEX idx_sessoes_usuario ON sessoes(usuario_id, revogado_em);
CREATE INDEX idx_sessoes_refresh_anterior ON sessoes(refresh_hash_anterior);

CREATE TABLE tokens_conta (
  id TEXT PRIMARY KEY,
  usuario_id TEXT NOT NULL REFERENCES usuarios(id),
  tipo TEXT NOT NULL CHECK (tipo IN ('verificacao_email', 'redefinicao_senha')),
  token_hash TEXT NOT NULL UNIQUE,
  criado_em INTEGER NOT NULL,
  expira_em INTEGER NOT NULL,
  usado_em INTEGER
);

CREATE INDEX idx_tokens_conta_usuario ON tokens_conta(usuario_id, tipo, usado_em);

CREATE TABLE atendimentos (
  id TEXT PRIMARY KEY,
  empresa_id TEXT NOT NULL REFERENCES empresas(id),
  placa TEXT NOT NULL DEFAULT '',
  placa_busca TEXT NOT NULL DEFAULT '',
  marca TEXT NOT NULL DEFAULT '',
  modelo TEXT NOT NULL DEFAULT '',
  ano TEXT NOT NULL DEFAULT '',
  chassi TEXT NOT NULL DEFAULT '',
  data_servico INTEGER NOT NULL,
  tipo_servico TEXT NOT NULL DEFAULT 'OUTRO',
  tipos_servico TEXT NOT NULL DEFAULT '[]',
  defeito TEXT NOT NULL DEFAULT '',
  solucao TEXT NOT NULL DEFAULT '',
  abertura TEXT NOT NULL DEFAULT '',
  codigo_chave TEXT NOT NULL DEFAULT '',
  codigo_configuracao TEXT NOT NULL DEFAULT '',
  chip_transponder TEXT NOT NULL DEFAULT '',
  ferramenta TEXT NOT NULL DEFAULT '',
  valor_centavos INTEGER NOT NULL DEFAULT 0,
  cliente_nome TEXT NOT NULL DEFAULT '',
  cliente_telefone TEXT NOT NULL DEFAULT '',
  pago INTEGER NOT NULL DEFAULT 1,
  busca TEXT NOT NULL DEFAULT '',
  revisao INTEGER NOT NULL DEFAULT 1,
  criado_em INTEGER NOT NULL,
  atualizado_em INTEGER NOT NULL,
  excluido_em INTEGER,
  criado_por TEXT REFERENCES usuarios(id),
  atualizado_por TEXT REFERENCES usuarios(id)
);

CREATE INDEX idx_atendimentos_empresa_data ON atendimentos(empresa_id, data_servico DESC);
CREATE INDEX idx_atendimentos_empresa_placa ON atendimentos(empresa_id, placa_busca);
CREATE INDEX idx_atendimentos_empresa_atualizado ON atendimentos(empresa_id, atualizado_em);

CREATE TABLE fichas_tecnicas (
  id TEXT PRIMARY KEY,
  empresa_id TEXT NOT NULL REFERENCES empresas(id),
  equipamento TEXT NOT NULL DEFAULT '',
  marca TEXT NOT NULL DEFAULT '',
  modelo TEXT NOT NULL DEFAULT '',
  ano TEXT NOT NULL DEFAULT '',
  sistema TEXT NOT NULL DEFAULT '',
  descricao TEXT NOT NULL DEFAULT '',
  chip TEXT NOT NULL DEFAULT '',
  chave TEXT NOT NULL DEFAULT '',
  telecomando TEXT NOT NULL DEFAULT '',
  frequencia TEXT NOT NULL DEFAULT '',
  avisos TEXT NOT NULL DEFAULT '',
  funcoes TEXT NOT NULL DEFAULT '',
  manual TEXT NOT NULL DEFAULT '',
  confirmado INTEGER NOT NULL DEFAULT 0,
  origem TEXT NOT NULL DEFAULT '',
  busca TEXT NOT NULL DEFAULT '',
  revisao INTEGER NOT NULL DEFAULT 1,
  criado_em INTEGER NOT NULL,
  atualizado_em INTEGER NOT NULL,
  excluido_em INTEGER,
  criado_por TEXT REFERENCES usuarios(id),
  atualizado_por TEXT REFERENCES usuarios(id)
);

CREATE INDEX idx_fichas_empresa_veiculo ON fichas_tecnicas(empresa_id, marca, modelo);
CREATE INDEX idx_fichas_empresa_atualizado ON fichas_tecnicas(empresa_id, atualizado_em);

CREATE TABLE fotos (
  id TEXT PRIMARY KEY,
  empresa_id TEXT NOT NULL REFERENCES empresas(id),
  atendimento_id TEXT REFERENCES atendimentos(id),
  ficha_id TEXT REFERENCES fichas_tecnicas(id),
  categoria TEXT NOT NULL DEFAULT 'CARRO',
  legenda TEXT NOT NULL DEFAULT '',
  ordem INTEGER NOT NULL DEFAULT 0,
  storage_key TEXT NOT NULL,
  mime_type TEXT NOT NULL,
  tamanho INTEGER NOT NULL,
  checksum TEXT NOT NULL,
  revisao INTEGER NOT NULL DEFAULT 1,
  criado_em INTEGER NOT NULL,
  atualizado_em INTEGER NOT NULL,
  excluido_em INTEGER,
  criado_por TEXT REFERENCES usuarios(id),
  CHECK ((atendimento_id IS NOT NULL) <> (ficha_id IS NOT NULL))
);

CREATE INDEX idx_fotos_empresa_atendimento ON fotos(empresa_id, atendimento_id);
CREATE INDEX idx_fotos_empresa_ficha ON fotos(empresa_id, ficha_id);
CREATE UNIQUE INDEX idx_fotos_storage_key ON fotos(storage_key);

CREATE TABLE mudancas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  empresa_id TEXT NOT NULL REFERENCES empresas(id),
  entidade TEXT NOT NULL,
  entidade_id TEXT NOT NULL,
  revisao INTEGER NOT NULL,
  tipo TEXT NOT NULL CHECK (tipo IN ('upsert', 'exclusao')),
  criado_em INTEGER NOT NULL
);

CREATE INDEX idx_mudancas_empresa ON mudancas(empresa_id, id);

CREATE TABLE operacoes_sync (
  empresa_id TEXT NOT NULL REFERENCES empresas(id),
  op_id TEXT NOT NULL,
  resultado TEXT NOT NULL,
  criado_em INTEGER NOT NULL,
  PRIMARY KEY (empresa_id, op_id)
);

CREATE TABLE auditoria (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  empresa_id TEXT REFERENCES empresas(id),
  usuario_id TEXT REFERENCES usuarios(id),
  acao TEXT NOT NULL,
  entidade TEXT NOT NULL DEFAULT '',
  entidade_id TEXT NOT NULL DEFAULT '',
  detalhes TEXT NOT NULL DEFAULT '{}',
  ip TEXT NOT NULL DEFAULT '',
  criado_em INTEGER NOT NULL
);

CREATE INDEX idx_auditoria_empresa ON auditoria(empresa_id, id);
CREATE INDEX idx_auditoria_usuario ON auditoria(usuario_id, id);

CREATE TABLE migracoes_itens (
  empresa_id TEXT NOT NULL REFERENCES empresas(id),
  origem TEXT NOT NULL,
  entidade TEXT NOT NULL,
  id_legado TEXT NOT NULL,
  id_novo TEXT NOT NULL,
  criado_em INTEGER NOT NULL,
  PRIMARY KEY (empresa_id, origem, entidade, id_legado)
);
