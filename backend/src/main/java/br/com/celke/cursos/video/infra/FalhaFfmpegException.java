package br.com.celke.cursos.video.infra;

/** O ffmpeg ou o ffprobe terminou com erro ou passou do tempo limite (o job tenta de novo). */
public class FalhaFfmpegException extends RuntimeException {

    public FalhaFfmpegException(String mensagem) {
        super(mensagem);
    }
}
