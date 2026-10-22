import { BoasVindas } from '@celke/kole-react';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';

export function PainelPagina() {
  const { estado } = useSessao();
  const nome = estado.status === 'autenticado' ? estado.usuario.nome : '';
  return <BoasVindas nome={nome} />;
}
