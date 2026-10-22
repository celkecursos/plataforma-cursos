import { createBrowserRouter } from 'react-router';
import { RotaPrivada } from '@compartilhado/sessao/RotaPrivada.tsx';
import { CAMINHOS } from './caminhos.ts';
import { AuditoriaPagina } from './paginas/auditoria/AuditoriaPagina.tsx';
import { LayoutAdm } from './layout/LayoutAdm.tsx';
import { SemPermissao } from './layout/SemPermissao.tsx';
import { AulaPagina } from './paginas/aula/AulaPagina.tsx';
import { CursoPagina } from './paginas/curso/CursoPagina.tsx';
import { CursosPagina } from './paginas/cursos/CursosPagina.tsx';
import { EntrarPagina } from './paginas/EntrarPagina.tsx';
import { PainelPagina } from './paginas/PainelPagina.tsx';
import { EventosPagina } from './paginas/pagamentos/EventosPagina.tsx';
import { MeiosPagamentoPagina } from './paginas/pagamentos/MeiosPagamentoPagina.tsx';
import { PedidoPagina } from './paginas/pagamentos/PedidoPagina.tsx';
import { PedidosPagina } from './paginas/pagamentos/PedidosPagina.tsx';
import { TermosPagina } from './paginas/termos/TermosPagina.tsx';
import { TurmaPagina } from './paginas/turma/TurmaPagina.tsx';

export const roteador = createBrowserRouter([
  { path: CAMINHOS.entrar, element: <EntrarPagina /> },
  {
    element: (
      <RotaPrivada login={CAMINHOS.entrar} papel="ADMIN" semPermissao={<SemPermissao />}>
        <LayoutAdm />
      </RotaPrivada>
    ),
    children: [
      { index: true, element: <PainelPagina /> },
      { path: 'cursos', element: <CursosPagina /> },
      { path: 'cursos/:cursoId', element: <CursoPagina /> },
      { path: 'cursos/:cursoId/turmas/:turmaId', element: <TurmaPagina /> },
      {
        path: 'cursos/:cursoId/turmas/:turmaId/modulos/:moduloId/aulas/:aulaId',
        element: <AulaPagina />,
      },
      { path: 'termos', element: <TermosPagina /> },
      { path: 'pagamentos', element: <MeiosPagamentoPagina /> },
      { path: 'pagamentos/pedidos', element: <PedidosPagina /> },
      { path: 'pagamentos/pedidos/:pedidoId', element: <PedidoPagina /> },
      { path: 'pagamentos/eventos', element: <EventosPagina /> },
      { path: 'auditoria', element: <AuditoriaPagina /> },
    ],
  },
  { path: '*', element: <SemPermissao titulo="Página não encontrada" /> },
]);
