-- Cache de dados técnicos de placas para poupar as cinco consultas gratuitas mensais.
-- Mantém apenas placa, marca, modelo e ano; não armazena chassi nem dados pessoais.
CREATE TABLE IF NOT EXISTS cache_consultas_placa (
  placa TEXT PRIMARY KEY,
  modelo TEXT NOT NULL,
  marca TEXT NOT NULL DEFAULT '',
  ano TEXT NOT NULL DEFAULT '',
  consultado_em INTEGER NOT NULL
);
