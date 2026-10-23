import { expect, type APIRequestContext } from '@playwright/test';
import { ADMIN_SEED, ADM_URL, ALUNO_URL, SENHA_SEED } from './config.ts';
import { stamp } from './dados.ts';
import { codigoTotp } from './totp.ts';

/** Cliente da API com o token de uma conta, para montar dados de teste sem passar pelas telas. */
export interface ClienteApi {
  get<T>(rota: string): Promise<T>;
  post<T>(rota: string, corpo?: unknown): Promise<T>;
  put<T>(rota: string, corpo: unknown): Promise<T>;
  patch<T>(rota: string, corpo: unknown): Promise<T>;
}

export async function clienteApi(
  request: APIRequestContext,
  email = ADMIN_SEED,
  senha = SENHA_SEED,
  base = ADM_URL,
): Promise<ClienteApi> {
  let login = await request.post(`${base}/api/auth/login`, { data: { email, senha } });
  if (!login.ok()) throw new Error(`Login de ${email} recusado: ${login.status()}`);
  const { desafio } = (await login.json()) as { desafio?: string };
  if (desafio) {
    // Admin do seed: segunda etapa com o código do aplicativo autenticador
    login = await request.post(`${base}/api/auth/segundo-fator`, {
      data: { desafio, codigo: codigoTotp() },
    });
    if (!login.ok()) throw new Error(`Segundo fator de ${email} recusado: ${login.status()}`);
  }
  const { accessToken } = (await login.json()) as { accessToken: string };
  const headers = { Authorization: `Bearer ${accessToken}` };

  const conferir = async <T>(
    metodo: string,
    rota: string,
    resposta: Awaited<ReturnType<APIRequestContext['get']>>,
  ) => {
    if (!resposta.ok())
      throw new Error(`${metodo} ${rota}: ${resposta.status()} ${await resposta.text()}`);
    const texto = await resposta.text();
    return (texto ? JSON.parse(texto) : undefined) as T;
  };
  return {
    get: async (rota) => conferir('GET', rota, await request.get(`${base}${rota}`, { headers })),
    post: async (rota, corpo) =>
      conferir('POST', rota, await request.post(`${base}${rota}`, { headers, data: corpo ?? {} })),
    put: async (rota, corpo) =>
      conferir('PUT', rota, await request.put(`${base}${rota}`, { headers, data: corpo })),
    patch: async (rota, corpo) =>
      conferir('PATCH', rota, await request.patch(`${base}${rota}`, { headers, data: corpo })),
  };
}

/** Cliente do aluno com os termos vigentes já aceitos (a área do aluno exige). */
export async function clienteAluno(request: APIRequestContext, email: string, senha: string) {
  const aluno = await clienteApi(request, email, senha, ALUNO_URL);
  for (const termo of await aluno.get<{ id: number }[]>('/api/termos/pendentes')) {
    await aluno.post(`/api/termos/${termo.id}/aceite`);
  }
  return aluno;
}

export interface CursoMontado {
  cursoId: number;
  titulo: string;
  turmaId: number;
  moduloId: number;
  aulaId: number;
}

/** Curso com turma ativa, um módulo e uma aula, criado pela API. */
export async function montarCurso(adm: ClienteApi): Promise<CursoMontado> {
  const titulo = `Curso ${stamp()}`;
  const curso = await adm.post<{ id: number }>('/api/adm/cursos', {
    titulo,
    resumo: 'Curso montado pelo teste.',
  });
  const turma = await adm.post<{ id: number }>(`/api/adm/cursos/${curso.id}/turmas`, {
    nome: 'Turma 1',
  });
  await adm.patch(`/api/adm/turmas/${turma.id}/status`, { status: 'ATIVA' });
  const modulo = await adm.post<{ id: number }>(`/api/adm/turmas/${turma.id}/modulos`, {
    titulo: 'Módulo 1',
  });
  const aula = await adm.post<{ id: number }>(`/api/adm/modulos/${modulo.id}/aulas`, {
    titulo: 'Aula 1',
  });
  return { cursoId: curso.id, titulo, turmaId: turma.id, moduloId: modulo.id, aulaId: aula.id };
}

/** Envia o vídeo da aula pelo mesmo caminho do ADM (partes direto ao armazenamento) e espera ficar pronto. */
export async function enviarVideo(
  request: APIRequestContext,
  adm: ClienteApi,
  aulaId: number,
  video: Buffer,
): Promise<void> {
  const rota = `/api/adm/aulas/${aulaId}/video`;
  const envio = await adm.post<{ tamanhoParte: number; partes: { numero: number; url: string }[] }>(
    `${rota}/envio`,
    { nomeArquivo: 'aula.mp4', contentType: 'video/mp4', tamanho: video.length },
  );
  const partes = [];
  for (const { numero, url } of envio.partes) {
    const inicio = (numero - 1) * envio.tamanhoParte;
    const resposta = await request.put(url, {
      data: video.subarray(inicio, inicio + envio.tamanhoParte),
    });
    if (!resposta.ok()) throw new Error(`Parte ${numero} recusada: ${resposta.status()}`);
    partes.push({ numero, etag: resposta.headers()['etag'] });
  }
  await adm.post(`${rota}/envio/conclusao`, { partes });
  await expect
    .poll(async () => (await adm.get<{ status: string }>(rota)).status, {
      message: 'vídeo convertido pelo worker',
      timeout: 120_000,
      intervals: [1_000],
    })
    .toBe('PRONTO');
}
