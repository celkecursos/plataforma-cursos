/** Formatação para exibição, em pt-BR e no fuso de quem está usando. */

const DATA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short' });
const DATA_HORA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

export function data(valor: string | null | undefined): string {
  return valor ? DATA.format(new Date(valor)) : '—';
}

export function dataHora(valor: string | null | undefined): string {
  return valor ? DATA_HORA.format(new Date(valor)) : '—';
}

export function bytes(valor: number | null | undefined): string {
  if (valor == null) return '—';
  const unidades = ['B', 'KB', 'MB', 'GB'];
  let n = valor;
  let i = 0;
  while (n >= 1024 && i < unidades.length - 1) {
    n /= 1024;
    i += 1;
  }
  return `${n.toLocaleString('pt-BR', { maximumFractionDigits: i === 0 ? 0 : 1 })} ${unidades[i]}`;
}

export function duracao(segundos: number | null | undefined): string {
  if (segundos == null) return '—';
  const m = Math.floor(segundos / 60);
  const s = Math.round(segundos % 60);
  return `${m}:${String(s).padStart(2, '0')}`;
}

/** `AAAA-MM-DD` (campo de data) → instante do início do dia no fuso local. */
export function inicioDoDia(dia: string): string {
  return new Date(`${dia}T00:00:00`).toISOString();
}

/** `AAAA-MM-DD` → instante do fim do dia no fuso local (a matrícula vale o dia inteiro). */
export function fimDoDia(dia: string): string {
  return new Date(`${dia}T23:59:59`).toISOString();
}

/** Instante → `AAAA-MM-DD` no fuso local (para preencher campo de data). */
export function diaLocal(valor: string | null | undefined): string {
  if (!valor) return '';
  const d = new Date(valor);
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mes}-${dia}`;
}
