import { Alerta, CabecalhoPagina, Carregando, Vazio } from '@celke/kole-react';
import { api } from '@compartilhado/api/cliente.ts';
import { useCarregar } from '@compartilhado/api/useCarregar.ts';
import type { Certificado } from '../tipos.ts';
import { CertificadoEmitido } from './turma/CertificadoAba.tsx';

/** Todos os certificados do aluno, inclusive de matrículas já encerradas. */
export function CertificadosPagina() {
  const certificados = useCarregar(() => api.get<Certificado[]>('/api/aluno/certificados'), []);
  return (
    <div className="pc-pagina">
      <CabecalhoPagina titulo="Certificados" icone="award" />
      {certificados.erro && <Alerta severidade="err" mensagem={certificados.erro} />}
      {!certificados.dados && !certificados.erro && (
        <Carregando mensagem="Carregando os certificados..." />
      )}
      {certificados.dados?.length === 0 && (
        <Vazio
          titulo="Nenhum certificado ainda"
          descricao="Conclua as aulas de um curso para emitir o certificado na página dele."
          icone="award"
        />
      )}
      {certificados.dados?.map((c) => (
        <CertificadoEmitido key={c.id} certificado={c} />
      ))}
    </div>
  );
}
