import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { LayoutAluno } from './layout/LayoutAluno.tsx';
import { CadastroPagina } from './paginas/CadastroPagina.tsx';
import { ConfirmarEmailPagina } from './paginas/ConfirmarEmailPagina.tsx';
import { EntrarPagina } from './paginas/EntrarPagina.tsx';
import { MeusCursosPagina } from './paginas/MeusCursosPagina.tsx';
import { AulaPagina } from './paginas/aula/AulaPagina.tsx';
import { CertificadosPagina } from './paginas/CertificadosPagina.tsx';
import { NaoEncontradaPagina } from './paginas/NaoEncontradaPagina.tsx';
import { PerfilPagina } from './paginas/perfil/PerfilPagina.tsx';
import { TopicoPagina } from './paginas/forum/TopicoPagina.tsx';
import { TurmaPagina } from './paginas/turma/TurmaPagina.tsx';
import { RecuperarSenhaPagina } from './paginas/RecuperarSenhaPagina.tsx';
import { RedefinirSenhaPagina } from './paginas/RedefinirSenhaPagina.tsx';
import { ComprarPagina } from './paginas/pagamento/ComprarPagina.tsx';
import { RetornoPagamentoPagina } from './paginas/pagamento/RetornoPagamentoPagina.tsx';

export const roteador = createBrowserRouter([
  { path: CAMINHOS.entrar, element: <EntrarPagina /> },
  { path: CAMINHOS.cadastro, element: <CadastroPagina /> },
  { path: CAMINHOS.confirmarEmail, element: <ConfirmarEmailPagina /> },
  { path: CAMINHOS.recuperarSenha, element: <RecuperarSenhaPagina /> },
  { path: CAMINHOS.redefinirSenha, element: <RedefinirSenhaPagina /> },
  { path: CAMINHOS.retornoPagamento, element: <RetornoPagamentoPagina /> },
  {
    path: 'comprar/:ofertaId',
    element: (
      <RotaPrivada login={CAMINHOS.entrar}>
        <ComprarPagina />
      </RotaPrivada>
    ),
  },
  {
    element: (
      <RotaPrivada login={CAMINHOS.entrar}>
        <LayoutAluno />
      </RotaPrivada>
    ),
    children: [
      { index: true, element: <MeusCursosPagina /> },
      { path: 'turmas/:turmaId', element: <TurmaPagina /> },
      { path: 'turmas/:turmaId/aulas/:aulaId', element: <AulaPagina /> },
      { path: 'turmas/:turmaId/forum/:topicoId', element: <TopicoPagina /> },
      { path: CAMINHOS.certificados, element: <CertificadosPagina /> },
      { path: CAMINHOS.perfil, element: <PerfilPagina /> },
    ],
  },
  { path: '*', element: <NaoEncontradaPagina /> },
]);
