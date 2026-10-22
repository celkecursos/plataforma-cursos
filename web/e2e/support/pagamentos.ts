import { readFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { expect, type APIRequestContext } from '@playwright/test';
import type { ClienteApi } from './api.ts';
import { ALUNO_URL } from './config.ts';
import { stamp } from './dados.ts';

/** Fixtures do webhook 2.0.0 da Hotmart, as mesmas dos testes do backend. */
const FIXTURES = new URL(
  '../../../backend/src/test/resources/pagamentos/hotmart/',
  import.meta.url,
);

export interface HotmartConfigurada {
  id: number;
  hottok: string;
  /** Caminho do webhook (`/api/pagamentos/webhooks/<token>`), igual nos hosts do aluno e do ADM. */
  webhook: string;
}

/** Hotmart configurada só com o hottok e ativa (a ativa recebe os novos checkouts). */
export async function hotmartAtiva(adm: ClienteApi): Promise<HotmartConfigurada> {
  const hottok = `hottok-${stamp()}`;
  const gateway = await adm.post<{ id: number; webhookUrl: string }>(
    '/api/adm/pagamentos/gateways',
    {
      tipo: 'HOTMART',
      nome: `Hotmart ${stamp()}`,
      valores: { hottok },
    },
  );
  await adm.post(`/api/adm/pagamentos/gateways/${gateway.id}/ativacao`);
  const webhook = new URL(gateway.webhookUrl).pathname;
  return { id: gateway.id, hottok, webhook };
}

export async function criarOferta(adm: ClienteApi, turmaId: number, produto: string) {
  return adm.post<{ id: number }>(`/api/adm/turmas/${turmaId}/ofertas`, {
    produtoExterno: produto,
    ofertaExterna: 'e2e',
    titulo: 'À vista',
    precoCentavos: 19_700,
    moeda: 'BRL',
  });
}

interface Notificacao {
  evento: string;
  produto: string;
  transacao: string;
  xcod?: string;
  email?: string;
  ocorridoEm?: number;
}

/** Manda à API, pelo Caddy, a notificação da fixture com os dados do teste (como a Hotmart faria). */
export async function notificar(
  request: APIRequestContext,
  hotmart: HotmartConfigurada,
  { evento, produto, transacao, xcod, email, ocorridoEm }: Notificacao,
): Promise<void> {
  const corpo = JSON.parse(readFileSync(new URL(`${evento}.json`, FIXTURES), 'utf8')) as {
    id: string;
    creation_date: number;
    data: {
      product: { id: string | number };
      buyer: { email: string };
      purchase: { transaction: string; offer: { code: string }; origin: { xcod?: string } };
    };
  };
  corpo.id = randomUUID();
  corpo.creation_date = ocorridoEm ?? Date.now();
  corpo.data.product.id = produto;
  corpo.data.purchase.offer.code = 'e2e';
  corpo.data.purchase.transaction = transacao;
  if (xcod) corpo.data.purchase.origin.xcod = xcod;
  else delete corpo.data.purchase.origin.xcod;
  if (email) corpo.data.buyer.email = email;
  const resposta = await request.post(`${ALUNO_URL}${hotmart.webhook}`, {
    data: corpo,
    headers: { 'X-HOTMART-HOTTOK': hotmart.hottok },
  });
  expect(resposta.status(), await resposta.text()).toBe(200);
}
