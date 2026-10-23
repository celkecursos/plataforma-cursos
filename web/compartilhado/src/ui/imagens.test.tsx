import { ProvedorUi } from '@celke/kole-react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { CampoImagens } from './CampoImagens.tsx';
import { imagensColadas, LIMITES_FORUM, triarImagens, type ImagemLocal } from './imagens.ts';

const MB = 1024 * 1024;

function arquivo(nome: string, tipo: string, tamanho = 1000): File {
  const f = new File(['x'], nome, { type: tipo });
  Object.defineProperty(f, 'size', { value: tamanho });
  return f;
}

describe('triagem das imagens', () => {
  it('aceita JPEG e PNG dentro dos limites', () => {
    const { aceitas, recusas } = triarImagens(
      0,
      [arquivo('a.jpg', 'image/jpeg'), arquivo('b.png', 'image/png')],
      LIMITES_FORUM,
    );
    expect(aceitas.map((f) => f.name)).toEqual(['a.jpg', 'b.png']);
    expect(recusas).toEqual([]);
  });

  it('recusa tipo errado, tamanho acima do limite e o que passa da quantidade', () => {
    const { aceitas, recusas } = triarImagens(
      1,
      [
        arquivo('anim.gif', 'image/gif'),
        arquivo('doc.pdf', 'application/pdf'),
        arquivo('grande.png', 'image/png', 6 * MB),
        arquivo('c.png', 'image/png'),
        arquivo('d.png', 'image/png'),
        arquivo('e.png', 'image/png'),
      ],
      LIMITES_FORUM,
    );
    expect(aceitas.map((f) => f.name)).toEqual(['c.png', 'd.png']);
    expect(recusas).toEqual([
      'anim.gif e doc.pdf: envie imagens JPG ou PNG.',
      'grande.png: cada imagem pode ter até 5 MB.',
      'e.png: anexe no máximo 3 imagens por mensagem.',
    ]);
  });

  it('imagem colada sem nome aparece como "imagem colada"', () => {
    const { recusas } = triarImagens(0, [arquivo('', 'image/gif')], LIMITES_FORUM);
    expect(recusas).toEqual(['imagem colada: envie imagens JPG ou PNG.']);
  });

  it('do que foi colado, fica só o que é imagem', () => {
    const png = arquivo('print.png', 'image/png');
    const dados = {
      items: [
        { kind: 'string', type: 'text/plain', getAsFile: () => null },
        { kind: 'file', type: 'image/png', getAsFile: () => png },
        {
          kind: 'file',
          type: 'application/pdf',
          getAsFile: () => arquivo('a.pdf', 'application/pdf'),
        },
      ],
    } as unknown as DataTransfer;
    expect(imagensColadas(dados)).toEqual([png]);
    expect(imagensColadas(null)).toEqual([]);
  });
});

function Campo() {
  const [imagens, setImagens] = useState<ImagemLocal[]>([]);
  return (
    <ProvedorUi>
      <CampoImagens
        rotulo="Imagens da pergunta"
        valor={imagens}
        aoMudar={setImagens}
        limites={LIMITES_FORUM}
      />
    </ProvedorUi>
  );
}

describe('campo de imagens', () => {
  afterEach(cleanup);

  beforeAll(() => {
    URL.createObjectURL = vi.fn((f: Blob) => `blob:${(f as File).name}`);
    URL.revokeObjectURL = vi.fn();
  });

  it('escolhe pelo seletor, mostra a prévia e remove', async () => {
    const usuario = userEvent.setup();
    render(<Campo />);
    await usuario.upload(
      screen.getByLabelText('Anexar imagens da pergunta'),
      arquivo('tela.png', 'image/png'),
    );

    expect(screen.getByRole('img', { name: 'Prévia de tela.png' })).toBeInTheDocument();
    await usuario.click(screen.getByRole('button', { name: 'Remover tela.png' }));
    expect(screen.queryByRole('img', { name: 'Prévia de tela.png' })).not.toBeInTheDocument();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:tela.png');
  });

  it('arrastar um PDF é recusado (o accept do seletor não vale para arrastar)', () => {
    render(<Campo />);
    fireEvent.drop(screen.getByRole('group', { name: 'Imagens da pergunta' }), {
      dataTransfer: { files: [arquivo('doc.pdf', 'application/pdf')] },
    });
    expect(screen.getByRole('alert')).toHaveTextContent('doc.pdf: envie imagens JPG ou PNG.');
    expect(screen.queryByRole('list', { name: 'Imagens anexadas' })).not.toBeInTheDocument();
  });

  it('colar um print adiciona a imagem; no limite, o botão fica desabilitado', () => {
    render(<Campo />);
    const zona = screen.getByRole('group', { name: 'Imagens da pergunta' });
    for (const nome of ['1.png', '2.png', '3.png']) {
      const png = arquivo(nome, 'image/png');
      fireEvent.paste(zona, {
        clipboardData: { items: [{ kind: 'file', type: 'image/png', getAsFile: () => png }] },
      });
    }
    expect(screen.getAllByRole('img')).toHaveLength(3);
    expect(screen.getByRole('button', { name: 'Anexar imagem' })).toBeDisabled();
    expect(screen.getByText('Limite de 3 imagens atingido.')).toBeInTheDocument();
  });
});
