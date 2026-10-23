/** Regras de formulário iguais às do backend, para o erro aparecer antes de ir à API. */

export const SENHA_MINIMA_ALUNO = 10;
export const SENHA_MAXIMA = 128;

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function erroEmail(valor: string): string | undefined {
  const email = valor.trim();
  if (!email) return 'Informe o e-mail.';
  if (email.length > 254 || !EMAIL.test(email)) return 'Informe um e-mail válido.';
  return undefined;
}

export function erroSenhaNova(valor: string, minimo = SENHA_MINIMA_ALUNO): string | undefined {
  if (!valor) return 'Informe a senha.';
  if (valor.length < minimo) return `A senha deve ter pelo menos ${minimo} caracteres.`;
  if (valor.length > SENHA_MAXIMA) return `A senha deve ter no máximo ${SENHA_MAXIMA} caracteres.`;
  return undefined;
}

export function erroConfirmacao(senha: string, confirmacao: string): string | undefined {
  if (!confirmacao) return 'Repita a senha.';
  if (senha !== confirmacao) return 'As senhas não conferem.';
  return undefined;
}

export function erroObrigatorio(
  valor: string,
  mensagem: string,
  maximo?: number,
): string | undefined {
  const texto = valor.trim();
  if (!texto) return mensagem;
  if (maximo && texto.length > maximo) return `Use no máximo ${maximo} caracteres.`;
  return undefined;
}

/** Só as mensagens definidas; vazio = formulário válido. */
export function semErros(erros: Record<string, string | undefined>): boolean {
  return Object.values(erros).every((erro) => !erro);
}
