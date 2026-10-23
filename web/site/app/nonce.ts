import { createContext, useContext } from 'react';

/** Nonce da Content-Security-Policy desta resposta (só existe no servidor; no navegador fica vazio). */
export const ContextoNonce = createContext<string | undefined>(undefined);

export function useNonce(): string | undefined {
  return useContext(ContextoNonce);
}
