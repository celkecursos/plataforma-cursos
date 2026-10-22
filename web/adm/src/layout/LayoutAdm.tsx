import { Estrutura, type SecaoNav } from '@celke/kole-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { useSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { CAMINHOS } from '../caminhos.ts';
import { MARCA } from '../marca.ts';

export const NAV: SecaoNav[] = [
  { itens: [{ rotulo: 'Painel', icone: 'house', rota: CAMINHOS.inicio }] },
];

export function LayoutAdm() {
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
      usuario={{ nome, cargo: 'Administrador' }}
      menuUsuario={[{ id: 'sair', rotulo: 'Sair', icone: 'log-out', destrutivo: true }]}
      aoEscolherMenuUsuario={(id) => {
        if (id === 'sair') void sair().then(() => navigate(CAMINHOS.entrar, { replace: true }));
      }}
    >
      <Outlet />
    </Estrutura>
  );
}
