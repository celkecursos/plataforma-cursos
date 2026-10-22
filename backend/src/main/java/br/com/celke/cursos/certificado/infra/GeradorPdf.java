package br.com.celke.cursos.certificado.infra;

import br.com.celke.cursos.certificado.domain.Certificado.DadosImpressos;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Gera o PDF do certificado: o template Thymeleaf {@code certificado/certificado} (HTML bem formado, com os
 * dados escapados pelo Thymeleaf) é convertido pelo openhtmltopdf, sem acesso a rede nem a arquivos externos.
 */
@Component
public class GeradorPdf {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", PT_BR);

    private final TemplateEngine templateEngine;

    public GeradorPdf(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public byte[] gerar(DadosImpressos dados, String codigo, Instant emitidoEm) {
        Context contexto = new Context(PT_BR);
        contexto.setVariable("nome", dados.nomeCompleto());
        contexto.setVariable("curso", dados.cursoTitulo());
        contexto.setVariable("turma", dados.turmaNome());
        contexto.setVariable("cargaHoraria", dados.cargaHorariaHoras());
        contexto.setVariable("emitidoEm", DATA.format(emitidoEm.atZone(BRASILIA)));
        contexto.setVariable("codigo", codigo);
        String html = templateEngine.process("certificado/certificado", contexto);
        try (ByteArrayOutputStream pdf = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withHtmlContent(html, null)
                    .toStream(pdf)
                    .run();
            return pdf.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gerar o PDF do certificado.", e);
        }
    }
}
