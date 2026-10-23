import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { api } from '../api/cliente.ts';
import type { DesafioSegundoFator, Sessao, Usuario } from '../api/tipos.ts';

/** Primeira etapa do login: a sessão aberta ou, para o administrador, o desafio do segundo fator. */
export type ResultadoEntrada =
  { tipo: 'sessao'; usuario: Usuario } | { tipo: 'desafio'; desafio: DesafioSegundoFator };

type Estado =
  { status: 'carregando' } | { status: 'anonimo' } | { status: 'autenticado'; usuario: Usuario };

interface ContextoSessao {
  estado: Estado;
  /**
   * Entra com e-mail e senha; erro da API (credenciais, e-mail não confirmado, limite) é lançado. O
   * administrador recebe o desafio do segundo fator, concluído pelo componente `SegundoFator`.
   */
  entrar: (email: string, senha: string) => Promise<ResultadoEntrada>;
  sair: () => Promise<void>;
}

const Contexto = createContext<ContextoSessao | null>(null);

/**
 * Sessão do app. Ao abrir, tenta retomar a sessão pelo cookie de refresh (sem pedir a senha de novo);
 * depois acompanha o cliente da API: renovação, login e expiração mudam o estado aqui.
 */
export function ProvedorSessao({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<Estado>({ status: 'carregando' });

  useEffect(() => {
    const parar = api.aoMudarSessao((sessao) =>
      setEstado(
        sessao ? { status: 'autenticado', usuario: sessao.usuario } : { status: 'anonimo' },
      ),
    );
    void api.renovar();
    return parar;
  }, []);

  const entrar = useCallback(async (email: string, senha: string): Promise<ResultadoEntrada> => {
    const resposta = await api.post<Sessao | DesafioSegundoFator>(
      '/api/auth/login',
      { email, senha },
      { semRenovar: true },
    );
    if ('desafio' in resposta) return { tipo: 'desafio', desafio: resposta };
    api.definirSessao(resposta);
    return { tipo: 'sessao', usuario: resposta.usuario };
  }, []);

  const sair = useCallback(async () => {
    try {
      await api.post('/api/auth/logout', undefined, { semRenovar: true });
    } finally {
      api.definirSessao(null);
    }
  }, []);

  const valor = useMemo(() => ({ estado, entrar, sair }), [estado, entrar, sair]);
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useSessao(): ContextoSessao {
  const contexto = useContext(Contexto);
  if (!contexto) throw new Error('useSessao fora do ProvedorSessao');
  return contexto;
}
