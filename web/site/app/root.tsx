import type { ReactNode } from 'react';
import {
  isRouteErrorResponse,
  Link,
  Links,
  Meta,
  Outlet,
  Scripts,
  ScrollRestoration,
  useRouteError,
  useRouteLoaderData,
  type LinksFunction,
} from 'react-router';
import tokens from '@celke/kole-react/tokens.css?url';
import estilos from './estilos/site.css?url';
import { Marca } from './componentes/Marca.tsx';
import { contexto } from './config.server.ts';
import { useNonce } from './nonce.ts';
import { NOME, type Contexto } from './seo.ts';

export const links: LinksFunction = () => [
  { rel: 'icon', href: '/favicon.ico', sizes: '32x32' },
  { rel: 'stylesheet', href: tokens },
  { rel: 'stylesheet', href: estilos },
];

export function loader() {
  return contexto();
}

/** Contexto do ambiente (endereços), disponível em todas as páginas. */
export function useContexto(): Contexto {
  const ctx = useRouteLoaderData<typeof loader>('root');
  return ctx ?? { siteUrl: '', alunoUrl: '', indexavel: false, ano: new Date().getFullYear() };
}

export function Layout({ children }: { children: ReactNode }) {
  const nonce = useNonce();
  const ctx = useContexto();
  return (
    <html lang="pt-BR">
      <head>
        <meta charSet="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <Meta />
        <Links />
      </head>
      <body>
        <a className="s-pular" href="#conteudo">
          Pular para o conteúdo
        </a>
        <header className="s-topo">
          <div className="s-largura s-topo-linha">
            <Marca />
            <nav aria-label="Principal" className="s-menu">
              <Link to="/cursos/">Cursos</Link>
              <Link to="/certificados/validar/">Validar certificado</Link>
              <a className="s-botao s-botao-pequeno" href={`${ctx.alunoUrl}/entrar`}>
                Entrar
              </a>
            </nav>
          </div>
        </header>
        <main id="conteudo">{children}</main>
        <footer className="s-rodape">
          <div className="s-largura s-rodape-grade">
            <div className="s-rodape-marca">
              <Marca />
              <p>
                Cursos online com aulas em vídeo, materiais de apoio, fórum com o instrutor e
                certificado verificável.
              </p>
            </div>
            <nav aria-label="Plataforma" className="s-rodape-coluna">
              <p className="s-rodape-titulo">Plataforma</p>
              <Link to="/cursos/">Cursos</Link>
              <a href={`${ctx.alunoUrl}/entrar`}>Área do aluno</a>
              <Link to="/certificados/validar/">Validar certificado</Link>
            </nav>
            <nav aria-label="Rodapé" className="s-rodape-coluna">
              <p className="s-rodape-titulo">Legal</p>
              <Link to="/termos/">Termos de uso</Link>
              <Link to="/privacidade/">Privacidade</Link>
            </nav>
          </div>
          <div className="s-largura s-rodape-base">
            © {ctx.ano} {NOME}. Todos os direitos reservados.
          </div>
        </footer>
        <ScrollRestoration nonce={nonce} />
        <Scripts nonce={nonce} />
      </body>
    </html>
  );
}

export default function App() {
  return <Outlet />;
}

/** Página não encontrada (404) ou erro inesperado, dentro do mesmo layout. */
export function ErrorBoundary() {
  const erro = useRouteError();
  const naoEncontrada = isRouteErrorResponse(erro) && erro.status === 404;
  return (
    <div className="s-largura s-secao s-nao-encontrada">
      <title>{`${naoEncontrada ? 'Página não encontrada' : 'Erro'} — ${NOME}`}</title>
      <meta name="robots" content="noindex" />
      <p className="s-codigo-erro" aria-hidden="true">
        {naoEncontrada ? '404' : '500'}
      </p>
      <h1>{naoEncontrada ? 'Página não encontrada' : 'Algo deu errado'}</h1>
      <p>
        {naoEncontrada
          ? 'O endereço não existe ou mudou.'
          : 'Não foi possível carregar esta página agora. Tente de novo em instantes.'}
      </p>
      <p className="s-acoes s-acoes-centro">
        <Link className="s-botao" to="/cursos/">
          Ver os cursos
        </Link>
      </p>
    </div>
  );
}
