import { describe, expect, it } from 'vitest';
import { pontoDeRetomada, precisaDeToken } from './paginas/aula/PlayerVideo.tsx';
import { localizarAula, type TurmaAluno } from './tipos.ts';

describe('player', () => {
  const origem = 'https://aluno.celke.uno';

  it('manda o token só para as playlists da API, nunca para o armazenamento', () => {
    expect(precisaDeToken('/api/aulas/7/video.m3u8', origem)).toBe(true);
    expect(precisaDeToken('https://aluno.celke.uno/api/aulas/7/video/720p.m3u8', origem)).toBe(
      true,
    );
    expect(
      precisaDeToken('https://media.celke.uno/midia/hls/seg-001.ts?X-Amz-Signature=x', origem),
    ).toBe(false);
    expect(precisaDeToken('https://aluno.celke.uno/assets/app.js', origem)).toBe(false);
  });

  it('retoma de onde parou, mas recomeça se faltava pouco para o fim', () => {
    expect(pontoDeRetomada(42, 300)).toBe(42);
    expect(pontoDeRetomada(0, 300)).toBe(0);
    expect(pontoDeRetomada(297, 300)).toBe(0);
    // Vídeo curto: a margem é 10% da duração
    expect(pontoDeRetomada(1, 3)).toBe(1);
    expect(pontoDeRetomada(2.8, 3)).toBe(0);
  });
});

describe('navegação entre aulas', () => {
  const turma: TurmaAluno = {
    matriculaId: 1,
    turmaId: 2,
    turmaNome: 'T',
    cursoTitulo: 'C',
    cursoSlug: 'c',
    inicio: '2026-01-01T00:00:00Z',
    fim: null,
    modulos: [
      {
        id: 10,
        titulo: 'M1',
        aulas: [
          { id: 1, titulo: 'A1', gratuita: false },
          { id: 2, titulo: 'A2', gratuita: false },
        ],
      },
      { id: 20, titulo: 'M2', aulas: [{ id: 3, titulo: 'A3', gratuita: false }] },
    ],
  };

  it('anterior e próxima atravessam os módulos', () => {
    const meio = localizarAula(turma, 2)!;
    expect(meio.modulo.id).toBe(10);
    expect(meio.anterior?.id).toBe(1);
    expect(meio.proxima?.id).toBe(3);
  });

  it('a primeira não tem anterior e a última não tem próxima', () => {
    expect(localizarAula(turma, 1)!.anterior).toBeNull();
    expect(localizarAula(turma, 3)!.proxima).toBeNull();
  });

  it('aula fora da turma', () => {
    expect(localizarAula(turma, 99)).toBeNull();
  });
});
