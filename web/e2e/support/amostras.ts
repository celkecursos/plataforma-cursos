import path from 'node:path';

/** Vídeo de 3 s usado também nos testes do backend (não duplicamos o binário). */
export const VIDEO_3S = path.resolve(
  process.cwd(),
  '../backend/src/test/resources/amostras/video-3s.mp4',
);

/** PNG 1×1 transparente. */
export const PNG = {
  name: 'capa.png',
  mimeType: 'image/png',
  buffer: Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
    'base64',
  ),
};

export const TXT = {
  name: 'roteiro.txt',
  mimeType: 'text/plain',
  buffer: Buffer.from('Roteiro da aula gerado pelo teste.\n'),
};

export const PDF_FALSO = {
  name: 'aula.pdf',
  mimeType: 'application/pdf',
  buffer: Buffer.from('%PDF-1.4\n%%EOF\n'),
};
