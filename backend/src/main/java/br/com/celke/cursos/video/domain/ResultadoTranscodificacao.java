package br.com.celke.cursos.video.domain;

/**
 * O que a conversão gerou e já está no bucket.
 *
 * @param tamanhoFinal soma dos arquivos HLS (playlists e segmentos), em bytes
 * @param variantes alturas geradas, da menor para a maior (ex.: {@code 720p,1080p})
 */
public record ResultadoTranscodificacao(
        String playlistChave,
        String thumbnailChave,
        long tamanhoFinal,
        int duracaoSegundos,
        int largura,
        int altura,
        String variantes,
        long tempoProcessamentoMs) {}
