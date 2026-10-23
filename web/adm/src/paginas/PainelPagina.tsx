import { BoasVindas, Botao } from '@celke/kole-react';
import { useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';

export function PainelPagina() {
  const { estado } = useSessao();
  const navigate = useNavigate();
  const nome = estado.status === 'autenticado' ? estado.usuario.nome : '';
  return (
    <BoasVindas nome={nome} cargo="Administrador">
      <Botao rotulo="Cursos" icone="graduation-cap" onClick={() => navigate(CAMINHOS.cursos)} />
      <Botao
        rotulo="Termos"
        icone="file-text"
        aparencia="contorno"
        onClick={() => navigate(CAMINHOS.termos)}
      />
    </BoasVindas>
  );
}
