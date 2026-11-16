-- Planos por modalidade, eventos idempotentes de assinatura e segmento em fichas técnicas.

ALTER TABLE fichas_tecnicas ADD COLUMN segmento TEXT NOT NULL DEFAULT 'AUTOMOTIVO';

CREATE INDEX IF NOT EXISTS idx_fichas_empresa_segmento
  ON fichas_tecnicas(empresa_id, segmento, marca, modelo);

ALTER TABLE empresas ADD COLUMN plano_codigo TEXT NOT NULL DEFAULT 'COMPLETO';

ALTER TABLE empresas ADD COLUMN modulos_ativos TEXT NOT NULL DEFAULT '["AUTOMOTIVO","RESIDENCIAL"]';

ALTER TABLE empresas ADD COLUMN assinatura_status TEXT NOT NULL DEFAULT 'ATIVA';

ALTER TABLE empresas ADD COLUMN proxima_renovacao_em INTEGER;

ALTER TABLE empresas ADD COLUMN mudanca_agendada TEXT NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS eventos_assinatura (
  id TEXT PRIMARY KEY,
  empresa_id TEXT NOT NULL REFERENCES empresas(id),
  evento_id TEXT NOT NULL,
  tipo TEXT NOT NULL,
  modulos TEXT NOT NULL DEFAULT '[]',
  status_resultante TEXT NOT NULL DEFAULT '',
  detalhes TEXT NOT NULL DEFAULT '{}',
  criado_em INTEGER NOT NULL,
  UNIQUE(empresa_id, evento_id)
);

CREATE INDEX IF NOT EXISTS idx_eventos_assinatura_empresa
  ON eventos_assinatura(empresa_id, criado_em DESC);
