package br.com.celke.cursos.video.application;

/**
 * Dados do job de transcodificação. A chave do original identifica o envio: se o vídeo recebeu outro
 * envio depois deste job, ele é descartado.
 */
public record PayloadTranscodificacao(long videoId, String uploadChave) {}
