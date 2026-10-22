import { ErroApi } from './erros.ts';
import type { Sessao } from './tipos.ts';

type Metodo = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface OpcoesRequisicao {
  corpo?: unknown;
  sinal?: AbortSignal;
  /** Não tenta renovar a sessão num 401 (login, refresh e logout). */
  semRenovar?: boolean;
}

/**
 * Cliente HTTP da API, na mesma origem do front (o Caddy, ou o proxy do Vite, leva `/api` à API).
 *
 * - O access token fica **só em memória** (nunca em localStorage): some ao fechar a aba.
 * - O refresh token é o cookie `HttpOnly` que o navegador manda sozinho para `/api/auth`.
 * - Num 401, renova a sessão uma vez e repete a requisição. Várias requisições que recebem 401 ao
 *   mesmo tempo esperam **a mesma** renovação: renovar em paralelo reutilizaria o refresh token, e o
 *   backend revogaria a sessão inteira por suspeita de roubo.
 */
export class ClienteApi {
  private token: string | null = null;
  private renovacao: Promise<Sessao | null> | null = null;
  private ouvintesSessao = new Set<(sessao: Sessao | null) => void>();

  /** Avisado a cada sessão nova (login ou renovação) e quando ela termina (`null`). */
  aoMudarSessao(ouvinte: (sessao: Sessao | null) => void): () => void {
    this.ouvintesSessao.add(ouvinte);
    return () => this.ouvintesSessao.delete(ouvinte);
  }

  definirSessao(sessao: Sessao | null): void {
    this.token = sessao?.accessToken ?? null;
    this.ouvintesSessao.forEach((ouvinte) => ouvinte(sessao));
  }

  get autenticado(): boolean {
    return this.token !== null;
  }

  /** Token atual, para quem pede à API sem passar por aqui (o player, nas playlists do vídeo). */
  get tokenAcesso(): string | null {
    return this.token;
  }

  get<T>(caminho: string, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('GET', caminho, opcoes);
  }

  post<T>(caminho: string, corpo?: unknown, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('POST', caminho, { ...opcoes, corpo });
  }

  put<T>(caminho: string, corpo?: unknown, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('PUT', caminho, { ...opcoes, corpo });
  }

  patch<T>(caminho: string, corpo?: unknown, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('PATCH', caminho, { ...opcoes, corpo });
  }

  delete<T>(caminho: string, opcoes?: OpcoesRequisicao): Promise<T> {
    return this.requisitar<T>('DELETE', caminho, opcoes);
  }

  /**
   * Renova a sessão pelo cookie de refresh (também usado ao abrir o app, para retomar a sessão).
   * Devolve a sessão nova, ou `null` se não há sessão válida.
   */
  renovar(): Promise<Sessao | null> {
    this.renovacao ??= this.post<Sessao>('/api/auth/refresh', undefined, { semRenovar: true })
      .then((sessao) => {
        this.definirSessao(sessao);
        return sessao;
      })
      .catch(() => {
        this.definirSessao(null);
        return null;
      })
      .finally(() => {
        this.renovacao = null;
      });
    return this.renovacao;
  }

  async requisitar<T>(metodo: Metodo, caminho: string, opcoes: OpcoesRequisicao = {}): Promise<T> {
    let resposta = await this.enviar(metodo, caminho, opcoes);
    if (resposta.status === 401 && !opcoes.semRenovar && this.token !== null) {
      const sessao = await this.renovar();
      if (sessao === null) throw await this.erro(resposta);
      resposta = await this.enviar(metodo, caminho, opcoes);
    }
    if (!resposta.ok) throw await this.erro(resposta);
    if (resposta.status === 204) return undefined as T;
    const tipo = resposta.headers.get('Content-Type') ?? '';
    return (tipo.includes('json') ? await resposta.json() : await resposta.text()) as T;
  }

  private async enviar(
    metodo: Metodo,
    caminho: string,
    opcoes: OpcoesRequisicao,
  ): Promise<Response> {
    const cabecalhos: Record<string, string> = { Accept: 'application/json' };
    if (opcoes.corpo !== undefined) cabecalhos['Content-Type'] = 'application/json';
    if (this.token) cabecalhos['Authorization'] = `Bearer ${this.token}`;
    try {
      return await fetch(caminho, {
        method: metodo,
        headers: cabecalhos,
        body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo),
        credentials: 'same-origin',
        signal: opcoes.sinal,
      });
    } catch (falha) {
      if (falha instanceof DOMException && falha.name === 'AbortError') throw falha;
      throw new ErroApi(0, null);
    }
  }

  private async erro(resposta: Response): Promise<ErroApi> {
    let corpo = null;
    try {
      corpo = await resposta.json();
    } catch {
      // corpo vazio ou não-JSON: fica a mensagem padrão do status
    }
    const tentarEm = Number(resposta.headers.get('Retry-After'));
    return new ErroApi(
      resposta.status,
      corpo,
      Number.isFinite(tentarEm) && tentarEm > 0 ? tentarEm : null,
    );
  }
}

/** Cliente único do app: uma instância só, para a renovação da sessão ser compartilhada. */
export const api = new ClienteApi();
