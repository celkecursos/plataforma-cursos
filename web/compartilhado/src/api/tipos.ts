export type Papel = 'ALUNO' | 'ADMIN';

/** Dados públicos do usuário da sessão (`UsuarioResponse` do backend). */
export interface Usuario {
  id: number;
  nome: string;
  email: string;
  papeis: Papel[];
}

/** Resposta do login e do refresh (`SessaoResponse`). */
export interface Sessao {
  accessToken: string;
  tipo: 'Bearer';
  expiraEm: string;
  usuario: Usuario;
}

/** Página de listagem (`Pagina<T>` do backend). */
export interface Pagina<T> {
  itens: T[];
  pagina: number;
  tamanho: number;
  total: number;
  totalPaginas: number;
}

/** Corpo das respostas de mensagem simples (`MensagemResponse`). */
export interface Mensagem {
  mensagem: string;
}
