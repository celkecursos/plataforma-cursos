package br.com.celke.cursos.certificado.api;

import br.com.celke.cursos.certificado.application.CertificadoService.Situacao;
import br.com.celke.cursos.certificado.domain.Certificado;
import java.time.Instant;

/** Respostas das rotas de certificado. A validação pública não expõe e-mail nem ids internos. */
public final class CertificadoResponses {

    private CertificadoResponses() {}

    public record CertificadoAluno(
            Long id,
            Long turmaId,
            String codigo,
            String nomeCompleto,
            String cursoTitulo,
            String turmaNome,
            Integer cargaHorariaHoras,
            Instant emitidoEm) {

        public static CertificadoAluno de(Certificado c) {
            return new CertificadoAluno(
                    c.getId(),
                    c.getTurmaId(),
                    c.getCodigo(),
                    c.getNomeCompleto(),
                    c.getCursoTitulo(),
                    c.getTurmaNome(),
                    c.getCargaHorariaHoras(),
                    c.getEmitidoEm());
        }
    }

    /** {@code certificado} só vem quando já foi emitido. */
    public record SituacaoResponse(
            int percentualVisto, int percentualExigido, boolean concluiu, CertificadoAluno certificado) {

        public static SituacaoResponse de(Situacao s) {
            return new SituacaoResponse(
                    s.percentualVisto(),
                    s.percentualExigido(),
                    s.concluiu(),
                    s.certificado().map(CertificadoAluno::de).orElse(null));
        }
    }

    /** O que a página "validar certificado" mostra. */
    public record Validacao(
            String codigo,
            String nomeCompleto,
            String cursoTitulo,
            String turmaNome,
            Integer cargaHorariaHoras,
            Instant emitidoEm) {

        public static Validacao de(Certificado c) {
            return new Validacao(
                    c.getCodigo(),
                    c.getNomeCompleto(),
                    c.getCursoTitulo(),
                    c.getTurmaNome(),
                    c.getCargaHorariaHoras(),
                    c.getEmitidoEm());
        }
    }

    public record DownloadResponse(String url, Instant expiraEm) {}
}
