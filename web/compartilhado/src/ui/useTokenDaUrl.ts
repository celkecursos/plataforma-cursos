import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';

/** Parâmetros da URL sem o `token` (os outros, como `origem`, ficam). */
export function semToken(parametros: URLSearchParams): URLSearchParams {
  const restantes = new URLSearchParams(parametros);
  restantes.delete('token');
  return restantes;
}

/**
 * Token de uso único que chega no link do e-mail (`?token=`). É lido uma vez e sai da barra de
 * endereço logo depois: não fica no histórico do navegador, em captura de tela nem no `Referer` de
 * um link aberto a partir da página.
 */
export function useTokenDaUrl(): string | null {
  const [parametros, setParametros] = useSearchParams();
  const [token] = useState(() => parametros.get('token'));

  useEffect(() => {
    if (parametros.has('token')) setParametros(semToken(parametros), { replace: true });
  }, [parametros, setParametros]);

  return token;
}
