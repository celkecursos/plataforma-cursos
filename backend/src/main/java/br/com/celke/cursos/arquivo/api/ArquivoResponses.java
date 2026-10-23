package br.com.celke.cursos.arquivo.api;

import br.com.celke.cursos.arquivo.application.ArquivoAdmService.EnvioArquivo;
import br.com.celke.cursos.arquivo.application.ArquivoAlunoService.Download;
import br.com.celke.cursos.arquivo.domain.ArquivoApoio;
import java.time.Instant;

/** Respostas das rotas de arquivos de apoio (ADM e aluno). A chave do bucket nunca sai daqui. */
public final class ArquivoResponses {

    private ArquivoResponses() {}

    public record Arquivo(
            Long id,
            Long aulaId,
            Long moduloId,
            String titulo,
            String nomeArquivo,
            String contentType,
            long tamanho,
            Instant criadoEm) {

        public static Arquivo de(ArquivoApoio a) {
            return new Arquivo(
                    a.getId(),
                    a.getAulaId(),
                    a.getModuloId(),
                    a.getTitulo(),
                    a.getNomeArquivo(),
                    a.getContentType(),
                    a.getTamanho(),
                    a.getCriadoEm());
        }
    }

    /** Onde e como enviar: PUT na {@code url} com o cabeçalho {@code Content-Type} informado. */
    public record EnvioResponse(String chave, String url, String contentType, Instant expiraEm) {

        public static EnvioResponse de(EnvioArquivo envio) {
            return new EnvioResponse(envio.chave(), envio.url().toString(), envio.contentType(), envio.expiraEm());
        }
    }

    /** URL temporária do download (baixa como anexo, com o nome do arquivo). */
    public record DownloadResponse(String url, Instant expiraEm) {

        public static DownloadResponse de(Download download) {
            return new DownloadResponse(download.url().toString(), download.expiraEm());
        }
    }
}
