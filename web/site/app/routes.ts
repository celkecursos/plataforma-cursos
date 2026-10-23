import { index, route, type RouteConfig } from '@react-router/dev/routes';

export default [
  index('rotas/inicio.tsx'),
  route('cursos', 'rotas/cursos.tsx'),
  route('cursos/:slug', 'rotas/curso.tsx'),
  route('certificados/validar', 'rotas/validar.tsx'),
  route('termos', 'rotas/termo.tsx', { id: 'termos' }),
  route('privacidade', 'rotas/termo.tsx', { id: 'privacidade' }),
  route('sitemap.xml', 'rotas/sitemap.ts'),
  route('robots.txt', 'rotas/robots.ts'),
  route('saude', 'rotas/saude.ts'),
  route('*', 'rotas/nao-encontrada.tsx'),
] satisfies RouteConfig;
