import { expect } from '@playwright/test';
import { MAILPIT_URL } from './config.ts';

interface Busca {
  messages: { ID: string }[] | null;
}

/** Espera o worker entregar o e-mail e devolve o link dele (o primeiro com ?token=). */
export async function linkDoEmail(destinatario: string, assunto: string): Promise<string> {
  let id: string | undefined;
  await expect
    .poll(
      async () => {
        const consulta = encodeURIComponent(`to:"${destinatario}" subject:"${assunto}"`);
        const resposta = await fetch(`${MAILPIT_URL}/api/v1/search?query=${consulta}`);
        id = ((await resposta.json()) as Busca).messages?.[0]?.ID;
        return id;
      },
      { message: `e-mail "${assunto}" para ${destinatario}`, timeout: 30_000 },
    )
    .toBeTruthy();
  const resposta = await fetch(`${MAILPIT_URL}/api/v1/message/${id}`);
  const { HTML } = (await resposta.json()) as { HTML: string };
  const link = /href="([^"]*[?&]token=[A-Za-z0-9_-]+)"/.exec(HTML)?.[1];
  if (!link) throw new Error(`O e-mail "${assunto}" não tem link com token.`);
  return link.replaceAll('&amp;', '&');
}
