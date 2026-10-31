import { useCallback, useEffect, useEffectEvent, useState } from 'react';
import { mensagemDeErro } from './erros.ts';

export interface Carga<T> {
  dados: T | undefined;
  erro: string | undefined;
  /** Buscando (a primeira vez ou de novo); os dados anteriores continuam em `dados`. */
  carregando: boolean;
  /** Busca de novo mantendo os dados atuais na tela. */
  recarregar: () => void;
  /** Troca os dados na tela sem ir à API (resposta de uma alteração). */
  definir: (dados: T) => void;
}

interface Estado<T> {
  /** Pedido (dependências + versão) a que os dados respondem. */
  pedido: string;
  dados?: T;
  erro?: string;
}

/**
 * Busca dados ao montar e quando as dependências mudam (comparadas pelo valor). Resposta de um
 * pedido antigo é descartada.
 */
export function useCarregar<T>(
  buscar: () => Promise<T>,
  dependencias: readonly unknown[],
): Carga<T> {
  const [versao, setVersao] = useState(0);
  const [estado, setEstado] = useState<Estado<T>>({ pedido: '' });
  const pedido = `${JSON.stringify(dependencias)}#${versao}`;
  const executar = useEffectEvent(buscar);

  useEffect(() => {
    let valido = true;
    executar().then(
      (dados) => valido && setEstado({ pedido, dados }),
      (falha: unknown) =>
        valido &&
        setEstado((atual) => ({ pedido, dados: atual.dados, erro: mensagemDeErro(falha) })),
    );
    return () => {
      valido = false;
    };
  }, [pedido]);

  const recarregar = useCallback(() => setVersao((v) => v + 1), []);
  const definir = useCallback((dados: T) => setEstado((atual) => ({ ...atual, dados })), []);
  return {
    dados: estado.dados,
    erro: estado.erro,
    carregando: estado.pedido !== pedido,
    recarregar,
    definir,
  };
}
