import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { LayoutAluno } from './layout/LayoutAluno.tsx';
import { CadastroPagina } from './paginas/CadastroPagina.tsx';
import { ConfirmarEmailPagina } from './paginas/ConfirmarEmailPagina.tsx';
import { EntrarPagina } from './paginas/EntrarPagina.tsx';
import { MeusCursosPagina } from './paginas/MeusCursosPagina.tsx';
import { NaoEncontradaPagina } from './paginas/NaoEncontradaPagina.tsx';
import { RecuperarSenhaPagina } from './paginas/RecuperarSenhaPagina.tsx';
import { RedefinirSenhaPagina } from './paginas/RedefinirSenhaPagina.tsx';

export const roteador = createBrowserRouter([
  { path: CAMINHOS.entrar, element: <EntrarPagina /> },
  { path: CAMINHOS.cadastro, element: <CadastroPagina /> },
  { path: CAMINHOS.confirmarEmail, element: <ConfirmarEmailPagina /> },
  { path: CAMINHOS.recuperarSenha, element: <RecuperarSenhaPagina /> },
  { path: CAMINHOS.redefinirSenha, element: <RedefinirSenhaPagina /> },
  {
    element: (
      <RotaPrivada login={CAMINHOS.entrar}>
        <LayoutAluno />
      </RotaPrivada>
    ),
    children: [{ index: true, element: <MeusCursosPagina /> }],
  },
  { path: '*', element: <NaoEncontradaPagina /> },
]);
