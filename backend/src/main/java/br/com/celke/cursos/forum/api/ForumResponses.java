package br.com.celke.cursos.forum.api;

import br.com.celke.cursos.forum.application.AnexoForumService.EnvioAnexo;
import br.com.celke.cursos.forum.application.LeituraForum.AnexoVisao;
import br.com.celke.cursos.forum.application.LeituraForum.RespostaDetalhe;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoDetalhe;
import br.com.celke.cursos.forum.application.LeituraForum.TopicoResumo;
import br.com.celke.cursos.forum.domain.Topico;
import java.time.Instant;
import java.util.List;

/**
 * Respostas do fórum. O texto sai só como HTML filtrado ({@code corpoHtml}), nunca o Markdown cru.
 * {@code oculto} só vem preenchido na moderação (na área do aluno, mensagem oculta não aparece).
 */
public final class ForumResponses {

    private ForumResponses() {}

    public record TopicoResumoResponse(
            Long id,
            Long turmaId,
            Long aulaId,
            String titulo,
            String autorNome,
            boolean fixado,
            boolean respondidoInstrutor,
            long respostas,
            Boolean oculto,
            Instant criadoEm) {

        public static TopicoResumoResponse aluno(TopicoResumo r) {
            return de(r, null);
        }

        public static TopicoResumoResponse moderacao(TopicoResumo r) {
            return de(r, r.topico().isOculto());
        }

        private static TopicoResumoResponse de(TopicoResumo r, Boolean oculto) {
            Topico t = r.topico();
            return new TopicoResumoResponse(
                    t.getId(),
                    t.getTurmaId(),
                    t.getAulaId(),
                    t.getTitulo(),
                    r.autorNome(),
                    t.isFixado(),
                    t.isRespondidoInstrutor(),
                    r.respostas(),
                    oculto,
                    t.getCriadoEm());
        }
    }

    public record TopicoResponse(
            Long id,
            Long turmaId,
            Long aulaId,
            String titulo,
            String corpoHtml,
            String autorNome,
            boolean fixado,
            boolean respondidoInstrutor,
            Boolean oculto,
            Instant criadoEm,
            List<AnexoVisao> anexos,
            List<RespostaResponse> respostas) {

        public static TopicoResponse aluno(TopicoDetalhe d) {
            return de(d, false);
        }

        public static TopicoResponse moderacao(TopicoDetalhe d) {
            return de(d, true);
        }

        private static TopicoResponse de(TopicoDetalhe d, boolean moderacao) {
            Topico t = d.topico();
            return new TopicoResponse(
                    t.getId(),
                    t.getTurmaId(),
                    t.getAulaId(),
                    t.getTitulo(),
                    t.getCorpoHtml(),
                    d.autorNome(),
                    t.isFixado(),
                    t.isRespondidoInstrutor(),
                    moderacao ? t.isOculto() : null,
                    t.getCriadoEm(),
                    d.anexos(),
                    d.respostas().stream()
                            .map(r -> RespostaResponse.de(r, moderacao))
                            .toList());
        }
    }

    public record RespostaResponse(
            Long id,
            String corpoHtml,
            String autorNome,
            boolean instrutor,
            Boolean oculto,
            Instant criadoEm,
            List<AnexoVisao> anexos) {

        static RespostaResponse de(RespostaDetalhe d, boolean moderacao) {
            var r = d.resposta();
            return new RespostaResponse(
                    r.getId(),
                    r.getCorpoHtml(),
                    d.autorNome(),
                    r.isInstrutor(),
                    moderacao ? r.isOculto() : null,
                    r.getCriadoEm(),
                    d.anexos());
        }
    }

    /** Onde enviar a imagem: PUT na {@code url} com o {@code Content-Type} informado; depois, a chave vai em {@code anexos}. */
    public record EnvioAnexoResponse(String chave, String url, String contentType, Instant expiraEm) {

        public static EnvioAnexoResponse de(EnvioAnexo envio) {
            return new EnvioAnexoResponse(envio.chave(), envio.url().toString(), envio.contentType(), envio.expiraEm());
        }
    }
}
