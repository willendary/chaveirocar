ALTER TABLE usuarios ADD COLUMN provider TEXT NOT NULL DEFAULT 'local';

ALTER TABLE usuarios ADD COLUMN provider_id TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_usuarios_provider ON usuarios(provider, provider_id);

ALTER TABLE atendimentos ADD COLUMN segmento TEXT NOT NULL DEFAULT 'AUTOMOTIVO';

ALTER TABLE atendimentos ADD COLUMN tipo_local TEXT NOT NULL DEFAULT '';

ALTER TABLE atendimentos ADD COLUMN ambiente_porta TEXT NOT NULL DEFAULT '';

ALTER TABLE atendimentos ADD COLUMN marca_fechadura TEXT NOT NULL DEFAULT '';

ALTER TABLE atendimentos ADD COLUMN modelo_fechadura TEXT NOT NULL DEFAULT '';

ALTER TABLE atendimentos ADD COLUMN tipo_cilindro TEXT NOT NULL DEFAULT '';

ALTER TABLE atendimentos ADD COLUMN endereco_local TEXT NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_atendimentos_empresa_segmento ON atendimentos(empresa_id, segmento, data_servico DESC);

ALTER TABLE fotos ADD COLUMN anotacoes TEXT NOT NULL DEFAULT '[]';
