import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import type { ArquivoApoio } from '../../tipos.ts';
import { ListaArquivos } from '../aula/ListaArquivos.tsx';

/** Arquivos de apoio do módulo; some quando não há nenhum. */
export function ArquivosModulo({ moduloId }: { moduloId: number }) {
  const arquivos = useCarregar(
    () => api.get<ArquivoApoio[]>(`/api/aluno/modulos/${moduloId}/arquivos`),
    [moduloId],
  );
  if (!arquivos.dados?.length) return null;
  return <ListaArquivos titulo="Arquivos do módulo" arquivos={arquivos.dados} />;
}
