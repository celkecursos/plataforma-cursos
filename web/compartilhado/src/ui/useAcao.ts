import { useToast } from '@celke/kole-react';
import { useCallback, useState } from 'react';
import { mensagemDeErro } from '@compartilhado/api/erros.ts';

/**
 * Executa uma alteração na API mostrando o resultado em aviso (toast). Devolve o retorno, ou
 * `undefined` se falhou (o erro já foi mostrado).
 */
export function useAcao() {
  const toast = useToast();
  const [executando, setExecutando] = useState(false);

  const executar = useCallback(
    async <T>(acao: () => Promise<T>, sucesso?: string): Promise<T | undefined> => {
      setExecutando(true);
      try {
        const resultado = await acao();
        if (sucesso) toast.ok(sucesso);
        return resultado;
      } catch (erro) {
        toast.err('Não foi possível concluir', mensagemDeErro(erro));
        return undefined;
      } finally {
        setExecutando(false);
      }
    },
    [toast],
  );

  return { executar, executando };
}
