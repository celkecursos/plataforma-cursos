export const CAMINHOS = {
  entrar: '/entrar',
  inicio: '/',
  cursos: '/cursos',
  termos: '/termos',
  meiosPagamento: '/pagamentos',
  pedidos: '/pagamentos/pedidos',
  eventos: '/pagamentos/eventos',
  auditoria: '/auditoria',
  usuarios: '/usuarios',
} as const;

export const caminhoPedido = (pedidoId: number | string) => `/pagamentos/pedidos/${pedidoId}`;

export const caminhoCurso = (cursoId: number | string) => `/cursos/${cursoId}`;

export const caminhoTurma = (cursoId: number | string, turmaId: number | string) =>
  `/cursos/${cursoId}/turmas/${turmaId}`;

export const caminhoAula = (
  cursoId: number | string,
  turmaId: number | string,
  moduloId: number | string,
  aulaId: number | string,
) => `/cursos/${cursoId}/turmas/${turmaId}/modulos/${moduloId}/aulas/${aulaId}`;
