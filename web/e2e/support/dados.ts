/** Dados únicos por execução: a stack não é limpa entre um teste e outro. */
let sequencia = 0;

export function stamp(): string {
  sequencia += 1;
  return `pw${Date.now().toString(36)}${sequencia}`;
}

export function emailNovo(): string {
  return `${stamp()}@teste.celke.uno`;
}

export const SENHA_NOVA = 'senha-forte-do-teste-e2e';
