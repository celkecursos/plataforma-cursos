import '@celke/kole-react/estilos.css';
import '@compartilhado/ui/estilos.css';
import '@compartilhado/ui/icones.ts';
import { ProvedorUi } from '@celke/kole-react';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { ProvedorSessao } from '@compartilhado/sessao/ProvedorSessao.tsx';
import { roteador } from './rotas.tsx';

createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <ProvedorUi>
      <ProvedorSessao>
        <RouterProvider router={roteador} />
      </ProvedorSessao>
    </ProvedorUi>
  </StrictMode>,
);
