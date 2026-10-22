import { Botao } from '@celke/kole-react';
import { useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { LayoutConta } from '@compartilhado/ui/LayoutConta.tsx';
import { CAMINHOS } from '../caminhos.ts';

/** Sessão válida, mas sem o papel de administrador (ou rota inexistente). */
export function SemPermissao({
  titulo = 'Acesso restrito a administradores',
}: {
  titulo?: string;
}) {
  const { sair } = useSessao();
  const navigate = useNavigate();
  return (
    <LayoutConta marca="Plataforma de Cursos — Administração" titulo={titulo}>
      <Botao
        rotulo="Entrar com outra conta"
        icone="log-out"
        onClick={() => void sair().then(() => navigate(CAMINHOS.entrar, { replace: true }))}
      />
    </LayoutConta>
  );
}
