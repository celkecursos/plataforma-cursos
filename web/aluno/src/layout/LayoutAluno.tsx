import { Estrutura, type SecaoNav } from '@celke/kole-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';
import { GuardaTermos } from './GuardaTermos.tsx';

const NAV: SecaoNav[] = [
  {
    itens: [
      { rotulo: 'Meus cursos', icone: 'graduation-cap', rota: CAMINHOS.inicio },
      { rotulo: 'Certificados', icone: 'award', rota: CAMINHOS.certificados },
      { rotulo: 'Meu perfil', icone: 'user', rota: CAMINHOS.perfil },
    ],
  },
];

/** Moldura da área logada do aluno: menu, usuário e, antes do conteúdo, o aceite dos termos vigentes. */
export function LayoutAluno() {
  const { estado, sair } = useSessao();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const nome = estado.status === 'autenticado' ? estado.usuario.nome : '';

  return (
    <Estrutura
      marca={MARCA}
      nav={NAV}
      rotaAtual={pathname}
      aoNavegar={navigate}
      renderLink={(p) => (
        <Link
          to={p.href}
          className={p.className}
          onClick={p.onClick}
          aria-current={p['aria-current']}
        >
          {p.children}
        </Link>
      )}
      usuario={{ nome, cargo: 'Aluno' }}
      menuUsuario={[
        { id: 'perfil', rotulo: 'Meu perfil', icone: 'user' },
        { id: 'sair', rotulo: 'Sair', icone: 'log-out', destrutivo: true },
      ]}
      aoEscolherMenuUsuario={(id) => {
        if (id === 'perfil') navigate(CAMINHOS.perfil);
        if (id === 'sair') void sair().then(() => navigate(CAMINHOS.entrar, { replace: true }));
      }}
    >
      <GuardaTermos>
        <Outlet />
      </GuardaTermos>
    </Estrutura>
  );
}
