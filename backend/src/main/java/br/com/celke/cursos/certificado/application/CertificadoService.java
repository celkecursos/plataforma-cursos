package br.com.celke.cursos.certificado.application;

import br.com.celke.cursos.certificado.domain.Certificado;
import br.com.celke.cursos.certificado.domain.Certificado.DadosImpressos;
import br.com.celke.cursos.certificado.domain.CodigoValidacao;
import br.com.celke.cursos.certificado.domain.NomeCertificado;
import br.com.celke.cursos.certificado.domain.RegraConclusao;
import br.com.celke.cursos.certificado.infra.CertificadoRepository;
import br.com.celke.cursos.certificado.infra.GeradorPdf;
import br.com.celke.cursos.curso.application.ConteudoTurmaService;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.RegraCertificado;
import br.com.celke.cursos.curso.application.ConteudoTurmaService.TurmaResumo;
import br.com.celke.cursos.progresso.application.ProgressoService;
import br.com.celke.cursos.progresso.application.ProgressoService.ProgressoTurma;
import br.com.celke.cursos.shared.armazenamento.StorageService;
import br.com.celke.cursos.shared.erro.ConflitoException;
import br.com.celke.cursos.shared.erro.NaoEncontradoException;
import br.com.celke.cursos.shared.erro.RegraNegocioException;
import java.net.URL;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Certificado da turma: o aluno com matrícula vigente que viu o percentual mínimo de aulas da turma
 * confirma o nome completo e recebe o PDF, gerado uma vez e guardado no bucket. O código impresso valida
 * o certificado publicamente. Emitir de novo devolve o mesmo certificado.
 */
@Service
public class CertificadoService {

    static final Duration VALIDADE_DOWNLOAD = Duration.ofMinutes(5);

    private final CertificadoRepository certificados;
    private final ProgressoService progressoService;
    private final ConteudoTurmaService conteudoTurmaService;
    private final GeradorPdf geradorPdf;
    private final StorageService storage;
    private final Clock clock;

    public CertificadoService(
            CertificadoRepository certificados,
            ProgressoService progressoService,
            ConteudoTurmaService conteudoTurmaService,
            GeradorPdf geradorPdf,
            StorageService storage,
            Clock clock) {
        this.certificados = certificados;
        this.progressoService = progressoService;
        this.conteudoTurmaService = conteudoTurmaService;
        this.geradorPdf = geradorPdf;
        this.storage = storage;
        this.clock = clock;
    }

    /** Quanto falta para o certificado e, se já emitido, o certificado (exige matrícula vigente). */
    public Situacao situacao(Long usuarioId, Long turmaId) {
        ProgressoTurma progresso = progressoService.daTurma(usuarioId, turmaId);
        RegraCertificado regra = regra(turmaId);
        return new Situacao(
                progresso.percentual(),
                regra.percentualConclusao(),
                RegraConclusao.concluiu(progresso.percentual(), regra.percentualConclusao(), progresso.totalAulas()),
                certificados.findByUsuarioIdAndTurmaId(usuarioId, turmaId));
    }

    /**
     * Emite o certificado com o nome confirmado pelo aluno. Já emitido: devolve o existente (o nome não muda).
     * Sem atingir o mínimo de aulas vistas: 409.
     */
    public Emissao emitir(Long usuarioId, Long turmaId, String nomeInformado) {
        Situacao situacao = situacao(usuarioId, turmaId);
        if (situacao.certificado().isPresent()) {
            return new Emissao(situacao.certificado().get(), false);
        }
        String nome = NomeCertificado.normalizar(nomeInformado);
        if (nome == null) {
            throw new RegraNegocioException(
                    HttpStatus.BAD_REQUEST, "Informe o nome completo como deve sair no certificado.");
        }
        if (!situacao.concluiu()) {
            throw new ConflitoException("Você ainda não concluiu a turma: %d%% das aulas vistas, de %d%% exigidos."
                    .formatted(situacao.percentualVisto(), situacao.percentualExigido()));
        }
        TurmaResumo turma = conteudoTurmaService
                .turma(turmaId)
                .orElseThrow(() -> new NaoEncontradoException("Turma não encontrada."));
        DadosImpressos dados = new DadosImpressos(
                nome, turma.cursoTitulo(), turma.turmaNome(), regra(turmaId).cargaHorariaHoras());
        Instant agora = clock.instant();
        String codigo = CodigoValidacao.gerar();
        String chave = "certificados/%d/%s.pdf".formatted(turmaId, codigo);
        storage.gravar(chave, geradorPdf.gerar(dados, codigo, agora), "application/pdf");
        try {
            return new Emissao(
                    certificados.saveAndFlush(new Certificado(
                            usuarioId, turmaId, codigo, dados, situacao.percentualVisto(), chave, agora)),
                    true);
        } catch (DataIntegrityViolationException e) {
            // Duas emissões ao mesmo tempo: fica a que gravou primeiro
            storage.excluir(chave);
            return new Emissao(
                    certificados.findByUsuarioIdAndTurmaId(usuarioId, turmaId).orElseThrow(() -> e), false);
        }
    }

    public List<Certificado> meus(Long usuarioId) {
        return certificados.findByUsuarioIdOrderByEmitidoEmDesc(usuarioId);
    }

    /** URL do PDF do próprio aluno (de outro aluno, 404), válida por 5 minutos. */
    public Download download(Long usuarioId, Long certificadoId) {
        Certificado certificado = certificados
                .findByIdAndUsuarioId(certificadoId, usuarioId)
                .orElseThrow(() -> new NaoEncontradoException("Certificado não encontrado."));
        URL url = storage.urlDownload(
                certificado.getPdfChave(), VALIDADE_DOWNLOAD, "certificado-%s.pdf".formatted(certificado.getCodigo()));
        return new Download(url, clock.instant().plus(VALIDADE_DOWNLOAD));
    }

    /** Validação pública pelo código; formato inválido ou código inexistente: 404. */
    public Certificado validar(String codigo) {
        String normalizado = CodigoValidacao.normalizar(codigo);
        return Optional.ofNullable(normalizado)
                .flatMap(certificados::findByCodigo)
                .orElseThrow(() -> new NaoEncontradoException("Certificado não encontrado."));
    }

    private RegraCertificado regra(Long turmaId) {
        return conteudoTurmaService
                .regraCertificado(turmaId)
                .orElseThrow(() -> new NaoEncontradoException("Turma não encontrada."));
    }

    public record Situacao(
            int percentualVisto, int percentualExigido, boolean concluiu, Optional<Certificado> certificado) {}

    public record Download(URL url, Instant expiraEm) {}

    /** @param novo {@code false} quando o certificado já existia */
    public record Emissao(Certificado certificado, boolean novo) {}
}
