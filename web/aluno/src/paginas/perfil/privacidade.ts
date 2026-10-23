/** Regras puras do card de privacidade do perfil. */

export const PALAVRA_EXCLUSAO = 'EXCLUIR';

export function erroConfirmacaoExclusao(texto: string): string | undefined {
  return texto.trim() === PALAVRA_EXCLUSAO
    ? undefined
    : `Digite ${PALAVRA_EXCLUSAO} para confirmar.`;
}

/** Nome do arquivo da cópia dos dados, com a data local (meus-dados-2026-01-10.json). */
export function nomeArquivoDados(agora: Date): string {
  const dia = [agora.getFullYear(), agora.getMonth() + 1, agora.getDate()]
    .map((n) => String(n).padStart(2, '0'))
    .join('-');
  return `meus-dados-${dia}.json`;
}

/** Política de privacidade no site público (aluno.<domínio> → cursos.<domínio>/privacidade/). */
export function urlPoliticaPrivacidade(local: { protocol: string; host: string }): string {
  return `${local.protocol}//${local.host.replace(/^aluno\./, 'cursos.')}/privacidade/`;
}
