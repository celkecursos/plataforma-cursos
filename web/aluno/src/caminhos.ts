/** Caminhos da área do aluno; os de conta são os mesmos dos links que a API manda por e-mail. */
export const CAMINHOS = {
  entrar: '/entrar',
  cadastro: '/cadastro',
  confirmarEmail: '/confirmar-email',
  recuperarSenha: '/recuperar-senha',
  redefinirSenha: '/redefinir-senha',
  inicio: '/',
  certificados: '/certificados',
  perfil: '/perfil',
  retornoPagamento: '/pagamento/retorno',
} as const;

/** Destino do botão "Comprar" do site. */
export const caminhoComprar = (ofertaId: number | string) => `/comprar/${ofertaId}`;

export const caminhoTurma = (turmaId: number | string) => `/turmas/${turmaId}`;

export const caminhoAula = (turmaId: number | string, aulaId: number | string) =>
  `/turmas/${turmaId}/aulas/${aulaId}`;

export const caminhoTopico = (turmaId: number | string, topicoId: number | string) =>
  `/turmas/${turmaId}/forum/${topicoId}`;
