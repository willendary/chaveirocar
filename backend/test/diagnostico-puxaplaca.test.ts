import { expect, it } from "vitest";
import { extrairVeiculoDeHtml } from "../src/servicos/placasWeb.js";

/**
 * Verificação real e restrita do site público que entrega marca/modelo no HTML.
 * Sem cookies, captcha, proxy ou redirecionamento; não extrai dados pessoais.
 * Ativar exclusivamente no diagnóstico: remover antes da integração.
 */
it("diagnostica páginas gratuitas do Puxa Placa no runner", async () => {
  const placas = ["RIZ1B98", "CIX6290", "NAQ2405", "HLF2034", "FWA6B37"];
  try {const rob = await fetch("https://puxaplaca.com.br/robots.txt",{redirect:"error",signal:AbortSignal.timeout(6000)}); console.log("PuxaRobots",rob.status,(await rob.text()).slice(0,2000));} catch(e){console.log("PuxaRobots ERROR",String(e))}
  for(const placa of placas) {
    try {
      const url = "https://puxaplaca.com.br/placa/" + placa;
      const res = await fetch(url, {headers:{ Accept:"text/html", "User-Agent":"ChaveiroCar-ConsultaTecnica (+https://github.com/willendary/chaveirocar)" },redirect:"error",signal:AbortSignal.timeout(7000)});
      const type = res.headers.get("content-type") ?? "";
      const page = res.ok && type.includes("text/html") ? (await res.text()).slice(0,100000) : "";
      const parsed = extrairVeiculoDeHtml(page,placa);
      const found = /<t[dh][^>]*>\s*Modelo\s*:?\s*<\/t[dh]>/i.test(page) ||
        /Modelo\s*:<\/[^>]*>/i.test(page) || /SANDERO SIN16CVT/i.test(page);
      const stripped = page.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi," ").replace(/<[^>]+>/g," ").replace(/\s+/g," ");
      console.log("PuxaPlacaLive",placa,JSON.stringify({status:res.status,type:type.slice(0,35),len:page.length,found, parsed, snippet:stripped.slice(0,200)}));
      if(res.status===403||res.status===429)break;
      await new Promise(resolve=>setTimeout(resolve,4000));
    } catch(e) {
      console.log("PuxaPlacaLive",placa,"ERROR",String(e).slice(0,200));
      break;
    }
  }
  expect(placas.length).toBe(5);
},70_000);
