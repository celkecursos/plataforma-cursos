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
  /** Só na ativação do segundo fator: os códigos de recuperação, mostrados uma única vez. */
  codigosRecuperacao?: string[];
}

/**
 * Resposta do login de um administrador com a senha certa: falta o segundo fator (`CODIGO`) ou o
 * cadastro do aplicativo autenticador (`CADASTRO`). Ainda não há sessão.
 */
export interface DesafioSegundoFator {
  segundoFator: 'CODIGO' | 'CADASTRO';
  desafio: string;
  expiraEm: string;
}

/** Segredo do aplicativo autenticador e o endereço `otpauth://` do QR code. */
export interface CadastroSegundoFator {
  segredo: string;
  uri: string;
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
