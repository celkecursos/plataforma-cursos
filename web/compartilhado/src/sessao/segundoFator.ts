/** Regras puras da segunda etapa do login (código do aplicativo e de recuperação). */

/** Código do aplicativo sem espaços (o aplicativo mostra "123 456"). */
export function limparCodigo(codigo: string): string {
  return codigo.replace(/\s/g, '');
}

export function erroCodigo(codigo: string): string | undefined {
  const limpo = limparCodigo(codigo);
  if (!limpo) return 'Informe o código de 6 dígitos do aplicativo.';
  if (!/^\d{6}$/.test(limpo)) return 'O código tem 6 dígitos.';
  return undefined;
}

/** Código de recuperação: 10 letras e números, com ou sem o hífen do meio. */
export function erroCodigoRecuperacao(codigo: string): string | undefined {
  const limpo = codigo.replace(/[\s-]/g, '');
  if (!limpo) return 'Informe um dos códigos de recuperação.';
  if (!/^[a-zA-Z0-9]{10}$/.test(limpo)) return 'O código de recuperação tem 10 letras e números.';
  return undefined;
}

/** Segredo em grupos de 4 para digitar no aplicativo sem errar. */
export function segredoEmGrupos(segredo: string): string {
  return segredo.replace(/(.{4})(?=.)/g, '$1 ');
}
