import yauzl from "yauzl";
import type { Banco } from "../banco.js";
import type { Armazenamento } from "../storage.js";
import { erros } from "../erros.js";
import { sha256, sha256Bytes } from "../util/id.js";
import { booleano, inteiro, texto } from "../util/texto.js";
import { uuidDeHash } from "../dominio/comum.js";
import { adicionarFoto } from "./fotos.js";
import { salvarAtendimento } from "./atendimentos.js";
import { salvarFicha } from "./fichas.js";
import type { DadosAtendimento } from "../esquemas.js";

const ORIGEM = "backup-android";
const LIMITE_TOTAL_ZIP = 300 * 1024 * 1024;
const LIMITE_ARQUIVO = 25 * 1024 * 1024;
const LIMITE_ENTRADAS = 50_000;

interface FotoBackup {
  arquivo?: unknown;
  categoria?: unknown;
  legenda?: unknown;
  ordem?: unknown;
}

interface AtendimentoBackup {
  id?: unknown;
  placa?: unknown;
  marca?: unknown;
  modelo?: unknown;
  ano?: unknown;
  chassi?: unknown;
  dataServico?: unknown;
  tipoServico?: unknown;
  tiposServico?: unknown;
  defeito?: unknown;
  solucao?: unknown;
  abertura?: unknown;
  codigoChave?: unknown;
  codigoConfiguracao?: unknown;
  chipTransponder?: unknown;
  ferramenta?: unknown;
  valorCentavos?: unknown;
  clienteNome?: unknown;
  clienteTelefone?: unknown;
  pago?: unknown;
  fotos?: unknown;
}

interface FichaBackup {
  id?: unknown;
  equipamento?: unknown;
  marca?: unknown;
  modelo?: unknown;
  ano?: unknown;
  sistema?: unknown;
  descricao?: unknown;
  chip?: unknown;
  chave?: unknown;
  telecomando?: unknown;
  frequencia?: unknown;
  avisos?: unknown;
  funcoes?: unknown;
  manual?: unknown;
  foto?: unknown;
  confirmado?: unknown;
  origem?: unknown;
}

interface RaizBackup {
  versao?: unknown;
  exportadoEm?: unknown;
  atendimentos?: unknown;
  fichas?: unknown;
}

export interface ResultadoImportacao {
  atendimentosImportados: number;
  atendimentosIgnorados: number;
  fichasImportadas: number;
  fichasIgnoradas: number;
  fotosImportadas: number;
  fotosIgnoradas: number;
  fotosAusentes: number;
  avisos: string[];
}

async function lerZip(bytes: Buffer): Promise<Map<string, Buffer>> {
  return new Promise((resolve, reject) => {
    yauzl.fromBuffer(bytes, { lazyEntries: true }, (falha, zip) => {
      if (falha || !zip) {
        reject(erros.validacao("Arquivo de backup inválido."));
        return;
      }
      const arquivos = new Map<string, Buffer>();
      let total = 0;
      let entradas = 0;

      zip.on("entry", (entrada: yauzl.Entry) => {
        entradas += 1;
        if (entradas > LIMITE_ENTRADAS) {
          reject(erros.validacao("O backup contém entradas demais."));
          zip.close();
          return;
        }
        if (/\/$/.test(entrada.fileName)) {
          zip.readEntry();
          return;
        }
        if (entrada.uncompressedSize > LIMITE_ARQUIVO) {
          reject(erros.validacao(`O arquivo ${entrada.fileName} é grande demais.`));
          zip.close();
          return;
        }
        total += entrada.uncompressedSize;
        if (total > LIMITE_TOTAL_ZIP) {
          reject(erros.validacao("O conteúdo descompactado do backup é grande demais."));
          zip.close();
          return;
        }
        zip.openReadStream(entrada, (erroLeitura, fluxo) => {
          if (erroLeitura || !fluxo) {
            reject(erros.validacao("Não foi possível ler o backup."));
            return;
          }
          const pedacos: Buffer[] = [];
          fluxo.on("data", (pedaco: Buffer) => pedacos.push(pedaco));
          fluxo.on("error", reject);
          fluxo.on("end", () => {
            arquivos.set(entrada.fileName, Buffer.concat(pedacos));
            zip.readEntry();
          });
        });
      });
      zip.on("end", () => resolve(arquivos));
      zip.on("error", () => reject(erros.validacao("Arquivo ZIP corrompido.")));
      zip.readEntry();
    });
  });
}

function lista<T>(valor: unknown): T[] {
  return Array.isArray(valor) ? (valor as T[]) : [];
}

function nomeArquivo(valor: unknown): string {
  const bruto = typeof valor === "string" ? valor : "";
  return bruto.split(/[\\/]/).pop() ?? "";
}

async function mapeamento(
  banco: Banco,
  empresaId: string,
  entidade: string,
  idLegado: string,
): Promise<string | null> {
  const resultado = await banco.execute({
    sql: `SELECT id_novo FROM migracoes_itens
          WHERE empresa_id = ? AND origem = ? AND entidade = ? AND id_legado = ?`,
    args: [empresaId, ORIGEM, entidade, idLegado],
  });
  const linha = resultado.rows[0];
  return linha ? String(linha.id_novo) : null;
}

async function salvarMapeamento(
  banco: Banco,
  empresaId: string,
  entidade: string,
  idLegado: string,
  idNovo: string,
): Promise<void> {
  await banco.execute({
    sql: `INSERT INTO migracoes_itens (empresa_id, origem, entidade, id_legado, id_novo, criado_em)
          VALUES (?, ?, ?, ?, ?, ?)
          ON CONFLICT (empresa_id, origem, entidade, id_legado) DO NOTHING`,
    args: [empresaId, ORIGEM, entidade, idLegado, idNovo, Date.now()],
  });
}

function chaveLegado(item: { id?: unknown }, objeto: unknown): string {
  const id = inteiro(item.id, 0);
  if (id > 0) return String(id);
  return `sem-id:${sha256(JSON.stringify(objeto))}`;
}

function idDeterministico(empresaId: string, entidade: string, chave: string): string {
  return uuidDeHash(sha256(`${empresaId}|${ORIGEM}|${entidade}|${chave}`));
}

export async function importarBackupAndroid(
  banco: Banco,
  storage: Armazenamento,
  contexto: { empresaId: string; usuarioId: string },
  bytes: Buffer,
  limiteFotos: number,
): Promise<ResultadoImportacao> {
  const arquivos = await lerZip(bytes);
  const dadosZip = arquivos.get("dados.json");
  if (!dadosZip) throw erros.validacao("Backup inválido: dados.json não encontrado.");

  let raiz: RaizBackup;
  try {
    raiz = JSON.parse(dadosZip.toString("utf8")) as RaizBackup;
  } catch {
    throw erros.validacao("Backup inválido: dados.json não pôde ser lido.");
  }

  const resultado: ResultadoImportacao = {
    atendimentosImportados: 0,
    atendimentosIgnorados: 0,
    fichasImportadas: 0,
    fichasIgnoradas: 0,
    fotosImportadas: 0,
    fotosIgnoradas: 0,
    fotosAusentes: 0,
    avisos: [],
  };

  for (const item of lista<AtendimentoBackup>(raiz.atendimentos)) {
    const chave = chaveLegado(item, item);
    const idNovo = idDeterministico(contexto.empresaId, "atendimento", chave);
    const existente = await mapeamento(banco, contexto.empresaId, "atendimento", chave);
    const jaExiste = existente
      ? await banco.execute({
          sql: "SELECT id FROM atendimentos WHERE id = ? AND empresa_id = ?",
          args: [existente, contexto.empresaId],
        })
      : { rows: [] as unknown[] };
    if (existente && jaExiste.rows.length > 0) {
      resultado.atendimentosIgnorados += 1;
      continue;
    }

    const tiposBrutos = lista<unknown>(item.tiposServico);
    const tipos = tiposBrutos.length > 0
      ? tiposBrutos.filter((tipo): tipo is string => typeof tipo === "string")
      : [texto(item.tipoServico, 40) || "OUTRO"];
    const atendimento = await salvarAtendimento(banco, contexto, {
      id: idNovo,
      placa: texto(item.placa, 10),
      marca: texto(item.marca, 120),
      modelo: texto(item.modelo, 120),
      ano: texto(item.ano, 30),
      chassi: texto(item.chassi, 60),
      dataServico: inteiro(item.dataServico, Date.now()),
      tiposServico: tipos as DadosAtendimento["tiposServico"],
      defeito: texto(item.defeito, 20_000),
      solucao: texto(item.solucao, 20_000),
      abertura: texto(item.abertura, 20_000),
      codigoChave: texto(item.codigoChave, 500),
      codigoConfiguracao: texto(item.codigoConfiguracao, 500),
      chipTransponder: texto(item.chipTransponder, 200),
      ferramenta: texto(item.ferramenta, 500),
      valorCentavos: Math.max(0, inteiro(item.valorCentavos, 0)),
      clienteNome: texto(item.clienteNome, 200),
      clienteTelefone: texto(item.clienteTelefone, 60),
      pago: booleano(item.pago, true),
    });
    await salvarMapeamento(banco, contexto.empresaId, "atendimento", chave, atendimento.id);
    resultado.atendimentosImportados += 1;

    for (const foto of lista<FotoBackup | string>(item.fotos)) {
      const nome = typeof foto === "string" ? nomeArquivo(foto) : nomeArquivo(foto.arquivo);
      if (!nome) continue;
      const chaveFoto = `${chave}:${nome}`;
      if (await mapeamento(banco, contexto.empresaId, "foto", chaveFoto)) {
        resultado.fotosIgnoradas += 1;
        continue;
      }
      const conteudo = arquivos.get(`fotos/${nome}`);
      if (!conteudo) {
        resultado.fotosAusentes += 1;
        resultado.avisos.push(`Foto ${nome} do atendimento ${chave} não está no ZIP.`);
        continue;
      }
      const meta = typeof foto === "string" ? {} : foto;
      const fotoSalva = await adicionarFoto(
        banco,
        storage,
        contexto,
        {
          id: idDeterministico(contexto.empresaId, "foto", chaveFoto),
          atendimentoId: atendimento.id,
          categoria: texto(meta.categoria, 20),
          legenda: texto(meta.legenda, 500),
          ordem: inteiro(meta.ordem, 0),
          bytes: conteudo,
        },
        limiteFotos,
      );
      await salvarMapeamento(banco, contexto.empresaId, "foto", chaveFoto, fotoSalva.id);
      resultado.fotosImportadas += 1;
    }
  }

  for (const item of lista<FichaBackup>(raiz.fichas)) {
    const chave = chaveLegado(item, item);
    const idNovo = idDeterministico(contexto.empresaId, "ficha", chave);
    const existente = await mapeamento(banco, contexto.empresaId, "ficha", chave);
    const jaExiste = existente
      ? await banco.execute({
          sql: "SELECT id FROM fichas_tecnicas WHERE id = ? AND empresa_id = ?",
          args: [existente, contexto.empresaId],
        })
      : { rows: [] as unknown[] };
    if (existente && jaExiste.rows.length > 0) {
      resultado.fichasIgnoradas += 1;
      continue;
    }

    const ficha = await salvarFicha(banco, contexto, {
      id: idNovo,
      equipamento: texto(item.equipamento, 120),
      marca: texto(item.marca, 120),
      modelo: texto(item.modelo, 120),
      ano: texto(item.ano, 30),
      sistema: texto(item.sistema, 200),
      descricao: texto(item.descricao, 20_000),
      chip: texto(item.chip, 200),
      chave: texto(item.chave, 500),
      telecomando: texto(item.telecomando, 500),
      frequencia: texto(item.frequencia, 100),
      avisos: texto(item.avisos, 20_000),
      funcoes: texto(item.funcoes, 20_000),
      manual: texto(item.manual, 100_000),
      confirmado: booleano(item.confirmado, false),
      origem: texto(item.origem, 500),
    });
    await salvarMapeamento(banco, contexto.empresaId, "ficha", chave, ficha.id);
    resultado.fichasImportadas += 1;

    const nome = nomeArquivo(item.foto);
    if (!nome) continue;
    const chaveFoto = `${chave}:${nome}`;
    if (await mapeamento(banco, contexto.empresaId, "foto", chaveFoto)) {
      resultado.fotosIgnoradas += 1;
      continue;
    }
    const conteudo = arquivos.get(`fichas/${nome}`);
    if (!conteudo) {
      resultado.fotosAusentes += 1;
      resultado.avisos.push(`Foto ${nome} da ficha ${chave} não está no ZIP.`);
      continue;
    }
    const fotoSalva = await adicionarFoto(
      banco,
      storage,
      contexto,
      {
        id: idDeterministico(contexto.empresaId, "foto", chaveFoto),
        fichaId: ficha.id,
        categoria: "PROCEDIMENTO",
        legenda: "Foto da ficha técnica",
        bytes: conteudo,
      },
      limiteFotos,
    );
    await salvarMapeamento(banco, contexto.empresaId, "foto", chaveFoto, fotoSalva.id);
    resultado.fotosImportadas += 1;
  }

  if (resultado.fotosAusentes > 0) {
    resultado.avisos.push(
      `${resultado.fotosAusentes} foto(s) referenciada(s) no dados.json não existem no ZIP.`,
    );
  }
  return resultado;
}

export function checksumDoBackup(bytes: Buffer): string {
  return sha256Bytes(bytes);
}
