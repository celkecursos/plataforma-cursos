import { describe, expect, it } from 'vitest';
import { bytes, diaLocal, duracao, fimDoDia, inicioDoDia } from '@compartilhado/ui/formato.ts';
import { tipoDoVideo } from './paginas/aula/VideoAula.tsx';
import { trocar } from './paginas/turma/ConteudoAba.tsx';
import { opcoes, STATUS_CURSO } from './tipos.ts';

describe('reordenação', () => {
  const itens = [{ id: 10 }, { id: 20 }, { id: 30 }];

  it('troca o item com o vizinho de cima e de baixo', () => {
    expect(trocar(itens, 1, 0)).toEqual([20, 10, 30]);
    expect(trocar(itens, 1, 2)).toEqual([10, 30, 20]);
  });

  it('não altera a lista original', () => {
    trocar(itens, 0, 1);
    expect(itens.map((i) => i.id)).toEqual([10, 20, 30]);
  });
});

describe('tipo do vídeo', () => {
  it('usa o tipo informado pelo navegador quando é aceito', () => {
    expect(tipoDoVideo(new File([''], 'a.bin', { type: 'video/webm' }))).toBe('video/webm');
  });

  it('deduz pela extensão quando o navegador não informa', () => {
    expect(tipoDoVideo(new File([''], 'Aula 1.MKV'))).toBe('video/x-matroska');
    expect(tipoDoVideo(new File([''], 'aula.mov'))).toBe('video/quicktime');
  });

  it('recusa o que não é vídeo aceito', () => {
    expect(tipoDoVideo(new File([''], 'aula.avi', { type: 'video/x-msvideo' }))).toBeNull();
    expect(tipoDoVideo(new File([''], 'notas.pdf', { type: 'application/pdf' }))).toBeNull();
  });
});

describe('formatação', () => {
  it('tamanho em bytes', () => {
    expect(bytes(512)).toBe('512 B');
    expect(bytes(1536)).toBe('1,5 KB');
    expect(bytes(5 * 1024 * 1024)).toBe('5 MB');
    expect(bytes(null)).toBe('—');
  });

  it('duração em minutos e segundos', () => {
    expect(duracao(3)).toBe('0:03');
    expect(duracao(125)).toBe('2:05');
  });

  it('dia do campo de data vira o dia inteiro no fuso local, e volta', () => {
    const inicio = new Date(inicioDoDia('2026-03-10'));
    const fim = new Date(fimDoDia('2026-03-10'));
    expect([inicio.getHours(), inicio.getMinutes()]).toEqual([0, 0]);
    expect([fim.getHours(), fim.getMinutes()]).toEqual([23, 59]);
    expect(diaLocal(inicioDoDia('2026-03-10'))).toBe('2026-03-10');
    expect(diaLocal(null)).toBe('');
  });
});

describe('opções de seleção', () => {
  it('saem dos rótulos dos status', () => {
    expect(opcoes(STATUS_CURSO)).toEqual([
      { valor: 'RASCUNHO', rotulo: 'Rascunho' },
      { valor: 'PUBLICADO', rotulo: 'Publicado' },
      { valor: 'ARQUIVADO', rotulo: 'Arquivado' },
    ]);
  });
});
