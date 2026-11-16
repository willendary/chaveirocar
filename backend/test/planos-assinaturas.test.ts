import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { cadastrar, criarAmbiente, imagemJpeg, corpoMultipart, type Ambiente } from "./ajuda.js";

let ambiente: Ambiente;

beforeEach(async () => {
  ambiente = await criarAmbiente();
});

afterEach(async () => {
  await ambiente.fechar();
});

describe("planos por modalidade, assinaturas e fichas por segmento", () => {
  it("preserva acesso completo por padrão e separa fichas por segmento", async () => {
    const conta = await cadastrar(ambiente.app, { email: "padrao@exemplo.com" });
    const empresa = conta.empresas[0]!;
    expect(empresa).toMatchObject({
      planoCodigo: "COMPLETO",
      modulosAtivos: ["AUTOMOTIVO", "RESIDENCIAL"],
      assinaturaStatus: "ATIVA",
    });

    const fichaAuto = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresa.id}/fichas`,
      headers: conta.cabecalho,
      payload: {
        segmento: "AUTOMOTIVO",
        equipamento: "OBDMap",
        marca: "Volkswagen",
        modelo: "Gol",
        ano: "2012",
        chip: "ID48",
      },
    });
    expect(fichaAuto.statusCode).toBe(201);
    expect(fichaAuto.json().segmento).toBe("AUTOMOTIVO");

    const fichaRes = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresa.id}/fichas`,
      headers: conta.cabecalho,
      payload: {
        segmento: "RESIDENCIAL",
        equipamento: "Manual Fabricante",
        marca: "Intelbras",
        modelo: "FR 201",
        sistema: "Fechadura Digital",
      },
    });
    expect(fichaRes.statusCode).toBe(201);
    expect(fichaRes.json().segmento).toBe("RESIDENCIAL");

    const somenteAuto = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresa.id}/fichas?segmento=AUTOMOTIVO`,
      headers: conta.cabecalho,
    });
    expect(somenteAuto.statusCode).toBe(200);
    expect(somenteAuto.json().total).toBe(1);
    expect(somenteAuto.json().itens[0].modelo).toBe("Gol");

    const somenteRes = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresa.id}/fichas?segmento=RESIDENCIAL`,
      headers: conta.cabecalho,
    });
    expect(somenteRes.statusCode).toBe(200);
    expect(somenteRes.json().total).toBe(1);
    expect(somenteRes.json().itens[0].modelo).toBe("FR 201");
  });

  it("aplica acesso somente Residencial e somente Veicular, bloqueia rotas/sync e preserva dados", async () => {
    const conta = await cadastrar(ambiente.app, { email: "modulos@exemplo.com" });
    const empresaId = conta.empresas[0]!.id;
    const h = conta.cabecalho;

    // Cria dados antes de restringir o plano para validar preservação.
    const atAutoResp = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/atendimentos`,
      headers: h,
      payload: { segmento: "AUTOMOTIVO", placa: "VEI1A23", modelo: "Onix" },
    });
    expect(atAutoResp.statusCode).toBe(201);
    const atAuto = atAutoResp.json();

    const fotoAutoMp = corpoMultipart(
      { categoria: "CARRO", legenda: "Painel" },
      { nome: "painel.jpg", mime: "image/jpeg", bytes: imagemJpeg("auto") },
    );
    const fotoAutoResp = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/atendimentos/${atAuto.id}/fotos`,
      headers: { ...h, "content-type": fotoAutoMp.contentType },
      payload: fotoAutoMp.payload,
    });
    expect(fotoAutoResp.statusCode).toBe(201);
    const fotoAuto = fotoAutoResp.json();

    const fichaAutoResp = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/fichas`,
      headers: h,
      payload: { segmento: "AUTOMOTIVO", marca: "Chevrolet", modelo: "Onix", chip: "ID46" },
    });
    expect(fichaAutoResp.statusCode).toBe(201);

    // Contrata somente Residencial (solicitar não libera antes da confirmação).
    const cancelarPrimeiro = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/assinatura/eventos`,
      headers: h,
      payload: { eventoId: "evt-cancel-1", tipo: "ASSINATURA_CANCELADA" },
    });
    expect(cancelarPrimeiro.statusCode).toBe(200);
    expect(cancelarPrimeiro.json().modulosAtivos).toEqual([]);

    const solicitarRes = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/assinatura/solicitar`,
      headers: h,
      payload: { acao: "CONTRATAR", modulos: ["RESIDENCIAL"] },
    });
    expect(solicitarRes.statusCode).toBe(200);
    // Solicitação apenas agenda; não libera antes da confirmação.
    expect(solicitarRes.json().modulosAtivos).toEqual([]);
    expect(solicitarRes.json().mudancaAgendada.modulos).toEqual(["RESIDENCIAL"]);

    const confirmarRes = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/assinatura/eventos`,
      headers: h,
      payload: { eventoId: "evt-pag-res-1", tipo: "PAGAMENTO_CONFIRMADO" },
    });
    expect(confirmarRes.statusCode).toBe(200);
    expect(confirmarRes.json().planoCodigo).toBe("SOMENTE_RESIDENCIAL");
    expect(confirmarRes.json().modulosAtivos).toEqual(["RESIDENCIAL"]);
    expect(confirmarRes.json().status).toBe("ATIVA");

    // Operações residenciais funcionam.
    const atResResp = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/atendimentos`,
      headers: h,
      payload: { segmento: "RESIDENCIAL", tipoLocal: "Casa", marcaFechadura: "Papaiz" },
    });
    expect(atResResp.statusCode).toBe(201);

    const fichaResResp = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/fichas`,
      headers: h,
      payload: { segmento: "RESIDENCIAL", marca: "Stam", modelo: "Tetra", sistema: "Tetra" },
    });
    expect(fichaResResp.statusCode).toBe(201);

    // Operações veiculares são bloqueadas pela API (sem contornar por chamada direta).
    const bloqueioCriarAuto = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/atendimentos`,
      headers: h,
      payload: { segmento: "AUTOMOTIVO", modelo: "Polo" },
    });
    expect(bloqueioCriarAuto.statusCode).toBe(403);
    expect(bloqueioCriarAuto.json().erro.codigo).toBe("MODULO_NAO_CONTRATADO");

    const bloqueioListarAuto = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresaId}/atendimentos?segmento=AUTOMOTIVO`,
      headers: h,
    });
    expect(bloqueioListarAuto.statusCode).toBe(403);
    expect(bloqueioListarAuto.json().erro.codigo).toBe("MODULO_NAO_CONTRATADO");

    const bloqueioLerAuto = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresaId}/atendimentos/${atAuto.id}`,
      headers: h,
    });
    expect(bloqueioLerAuto.statusCode).toBe(403);

    const bloqueioFichaAuto = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/fichas`,
      headers: h,
      payload: { segmento: "AUTOMOTIVO", marca: "VW", modelo: "Gol" },
    });
    expect(bloqueioFichaAuto.statusCode).toBe(403);

    const bloqueioFotoAuto = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresaId}/fotos/${fotoAuto.id}/arquivo`,
      headers: h,
    });
    expect(bloqueioFotoAuto.statusCode).toBe(403);

    // Listagem geral traz só os módulos ativos; sync pull filtra e push rejeita sem apagar.
    const listaGeral = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresaId}/atendimentos`,
      headers: h,
    });
    expect(listaGeral.statusCode).toBe(200);
    expect(listaGeral.json().total).toBe(1);
    expect(listaGeral.json().itens[0].segmento).toBe("RESIDENCIAL");

    const pullRes = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresaId}/sync?cursor=0`,
      headers: h,
    });
    expect(pullRes.statusCode).toBe(200);
    expect(pullRes.json().atendimentos).toHaveLength(1);
    expect(pullRes.json().atendimentos[0].segmento).toBe("RESIDENCIAL");
    expect(pullRes.json().fichas).toHaveLength(1);
    expect(pullRes.json().fichas[0].segmento).toBe("RESIDENCIAL");

    const { randomUUID } = await import("node:crypto");
    const pushAutoBloqueado = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/sync`,
      headers: h,
      payload: {
        operacoes: [{
          opId: randomUUID(),
          entidade: "atendimento",
          tipo: "upsert",
          id: randomUUID(),
          baseRevisao: 0,
          dados: { segmento: "AUTOMOTIVO", modelo: "Palio" },
        }],
      },
    });
    expect(pushAutoBloqueado.statusCode).toBe(200);
    expect(pushAutoBloqueado.json().resultados[0].status).toBe("erro");
    expect(pushAutoBloqueado.json().resultados[0].erro.codigo).toBe("MODULO_NAO_CONTRATADO");

    // Troca para somente Veicular e valida que os dados antigos automotivos continuam intactos.
    const confirmarVeicular = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/assinatura/eventos`,
      headers: h,
      payload: {
        eventoId: "evt-pag-vei-1",
        tipo: "PAGAMENTO_CONFIRMADO",
        modulos: ["AUTOMOTIVO"],
      },
    });
    expect(confirmarVeicular.statusCode).toBe(200);
    expect(confirmarVeicular.json().planoCodigo).toBe("SOMENTE_VEICULAR");
    expect(confirmarVeicular.json().modulosAtivos).toEqual(["AUTOMOTIVO"]);

    const lerAutoDepois = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresaId}/atendimentos/${atAuto.id}`,
      headers: h,
    });
    expect(lerAutoDepois.statusCode).toBe(200);
    expect(lerAutoDepois.json().placa).toBe("VEI1A23");

    const lerResBloqueado = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresaId}/atendimentos/${atResResp.json().id}`,
      headers: h,
    });
    expect(lerResBloqueado.statusCode).toBe(403);
  });

  it("trata idempotência, falha de pagamento, renovação e permissões de membro", async () => {
    const conta = await cadastrar(ambiente.app, { email: "ciclo@exemplo.com" });
    const empresaId = conta.empresas[0]!.id;
    const h = conta.cabecalho;

    // Convida um colaborador e um leitor.
    const convColab = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/convites`,
      headers: h,
      payload: { email: "colab-plano@exemplo.com", papel: "colaborador" },
    });
    const aceiteColab = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/convites/${convColab.json().token}/aceitar`,
      payload: { nome: "Colaborador", senha: "Senha1234" },
    });
    const hColab = { authorization: `Bearer ${aceiteColab.json().tokens.acesso}` };

    const convLeitor = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/convites`,
      headers: h,
      payload: { email: "leitor-plano@exemplo.com", papel: "leitor" },
    });
    const aceiteLeitor = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/convites/${convLeitor.json().token}/aceitar`,
      payload: { nome: "Leitor", senha: "Senha1234" },
    });
    const hLeitor = { authorization: `Bearer ${aceiteLeitor.json().tokens.acesso}` };

    // Colaborador pode ver assinatura, mas não pode contratar/alterar plano.
    const verColab = await ambiente.app.inject({
      method: "GET",
      url: `/api/v1/empresas/${empresaId}/assinatura`,
      headers: hColab,
    });
    expect(verColab.statusCode).toBe(200);
    expect(verColab.json().podeGerenciar).toBe(false);

    const tentarGerenciar = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/assinatura/solicitar`,
      headers: hColab,
      payload: { acao: "CONTRATAR", modulos: ["RESIDENCIAL"] },
    });
    expect(tentarGerenciar.statusCode).toBe(403);

    // Leitor continua bloqueado para escrita mesmo com módulo contratado.
    const leitorEscrita = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/atendimentos`,
      headers: hLeitor,
      payload: { segmento: "AUTOMOTIVO", modelo: "Ka" },
    });
    expect(leitorEscrita.statusCode).toBe(403);
    expect(leitorEscrita.json().erro.codigo).toBe("SEM_PERMISSAO");

    // Evento repetido é idempotente.
    const ev1 = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/assinatura/eventos`,
      headers: h,
      payload: {
        eventoId: "evt-idemp-1",
        tipo: "PAGAMENTO_CONFIRMADO",
        modulos: ["AUTOMOTIVO"],
      },
    });
    expect(ev1.statusCode).toBe(200);
    expect(ev1.json().duplicado).toBe(false);
    expect(ev1.json().modulosAtivos).toEqual(["AUTOMOTIVO"]);

    const ev1Repetido = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/assinatura/eventos`,
      headers: h,
      payload: {
        eventoId: "evt-idemp-1",
        tipo: "PAGAMENTO_CONFIRMADO",
        modulos: ["AUTOMOTIVO", "RESIDENCIAL"],
      },
    });
    expect(ev1Repetido.statusCode).toBe(200);
    expect(ev1Repetido.json().duplicado).toBe(true);
    expect(ev1Repetido.json().modulosAtivos).toEqual(["AUTOMOTIVO"]);

    // Falha de pagamento suspende acesso; renovação restaura automaticamente.
    const falha = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/assinatura/eventos`,
      headers: h,
      payload: { eventoId: "evt-falha-1", tipo: "FALHA_PAGAMENTO" },
    });
    expect(falha.statusCode).toBe(200);
    expect(falha.json().status).toBe("FALHA_PAGAMENTO");
    expect(falha.json().modulosAtivos).toEqual([]);

    const colabBloqueado = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/atendimentos`,
      headers: hColab,
      payload: { segmento: "AUTOMOTIVO", modelo: "Gol" },
    });
    expect(colabBloqueado.statusCode).toBe(403);
    expect(colabBloqueado.json().erro.codigo).toBe("MODULO_NAO_CONTRATADO");

    const renovacao = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/assinatura/eventos`,
      headers: h,
      payload: { eventoId: "evt-renov-1", tipo: "RENOVACAO_CONFIRMADA" },
    });
    expect(renovacao.statusCode).toBe(200);
    expect(renovacao.json().status).toBe("ATIVA");
    expect(renovacao.json().modulosAtivos).toEqual(["AUTOMOTIVO"]);

    const colabLiberado = await ambiente.app.inject({
      method: "POST",
      url: `/api/v1/empresas/${empresaId}/atendimentos`,
      headers: hColab,
      payload: { segmento: "AUTOMOTIVO", modelo: "Gol" },
    });
    expect(colabLiberado.statusCode).toBe(201);
  });
});
