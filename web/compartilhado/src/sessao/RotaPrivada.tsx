import { Carregando } from '@celke/kole-react';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import type { Papel } from '../api/tipos.ts';
import { useSessao } from './ProvedorSessao.tsx';

interface RotaPrivadaProps {
  /** Papel exigido; sem ele, basta estar autenticado. */
  papel?: Papel;
  /** Rota do login (a de origem vai em `?voltar=` para retornar depois de entrar). */
  login: string;
  /** O que mostrar a quem está autenticado sem o papel exigido. */
  semPermissao?: ReactNode;
  children: ReactNode;
}

/** Só renderiza o conteúdo com sessão (e o papel, quando pedido); sem sessão, vai para o login. */
export function RotaPrivada({ papel, login, semPermissao = null, children }: RotaPrivadaProps) {
  const { estado } = useSessao();
  const { pathname, search } = useLocation();

  if (estado.status === 'carregando') {
    return <Carregando mensagem="Carregando sua sessão..." />;
  }
  if (estado.status === 'anonimo') {
    const voltar = encodeURIComponent(pathname + search);
    return <Navigate to={`${login}?voltar=${voltar}`} replace />;
  }
  if (papel && !estado.usuario.papeis.includes(papel)) {
    return <>{semPermissao}</>;
  }
  return <>{children}</>;
}

/** Destino seguro depois do login: só caminhos internos do próprio app (evita redirecionamento aberto). */
export function destinoAposLogin(voltar: string | null, padrao = '/'): string {
  if (!voltar || !voltar.startsWith('/') || voltar.startsWith('//')) return padrao;
  return voltar;
}
